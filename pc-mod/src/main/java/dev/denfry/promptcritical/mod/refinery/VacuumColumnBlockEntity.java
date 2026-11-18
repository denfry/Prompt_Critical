package dev.denfry.promptcritical.mod.refinery;

import dev.denfry.promptcritical.mod.chem.PyrolysisFurnaceBlockEntity;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.production.FeedTank;
import dev.denfry.promptcritical.mod.production.ProductTank;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.mod.uranium.UraniumProcessBlockEntity;
import dev.denfry.promptcritical.sim.refining.AtmosphericUnit;
import dev.denfry.promptcritical.sim.refining.CrudeAssay;
import dev.denfry.promptcritical.sim.refining.VacuumUnit;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Вакуумная колонна ({@link VacuumUnit}, 09 §9.9.2): печь греет мазут атмосферной колонны до 380–420 °C, колонна под
 * вакуумом делит его на вакуумный газойль (сверху, сырьё каткрекинга) и гудрон (снизу, сырьё коксования). Мазут,
 * товарный газ для печи и питательную воду для эжекторов подают трубами к боковым граням.
 *
 * <p>Вакуум дают пароэжекторы ({@code steam_ejector}), которые касаются колонны: одна ступень — 8 кПа в зоне питания,
 * две — 5, три — 3 (09 §9.9.2: 3–8 кПа [BALANCE: по ступени на блок]). Пар эжекторов и отпарки — {@value #STEAM_PER_FEED}
 * кг на кг мазута [BALANCE], его поднимает змеевик в конвекционной камере печи из {@code boiler_feedwater}
 * [допущение: отдельного котла нет]. Без воды или без эжекторов колонна работает при атмосферном давлении и почти
 * не даёт газойля. Состав мазута — остаток атмосферной колонны на западносибирской нефти в номинальном режиме.
 */
public final class VacuumColumnBlockEntity extends UraniumProcessBlockEntity {
    public static final int MB = 8_000, MAX_EJECTORS = 3;
    public static final double[] EJECTOR_KPA = {VacuumUnit.P0_KPA, 8, 5, 3};
    /** Производительность — 1,5 л/с мазута: вся кубовая атмосферной колонны 10 м³/ч с запасом [BALANCE]. */
    public static final double FEED_L_S = 1.5, FUEL_OIL_KG_L = 0.95, VGO_KG_L = 0.91, VR_KG_L = 0.99, STEAM_PER_FEED = 0.02;
    public static final double FUEL_J_PER_NM3 = 36e6, EFFICIENCY = 0.85, T_MIN_C = 370, T_MAX_C = 420;
    /** Коксуемость мазута — как у сырья коксования ({@link DelayedCokerBlockEntity#FEED_CCR}). */
    public static final double FEED_CCR = DelayedCokerBlockEntity.FEED_CCR;
    /** Мазут — остаток атмосферной колонны в номинальном режиме (4 ступени на секцию, флегма 1,5, печь 360 °C). */
    static final double[] RESIDUE_SLICES = new AtmosphericUnit(4, 1.5, 360 + 273.15).distill(CrudeAssay.WEST_SIBERIAN).residueSlices();

    private final FeedTank feed = new FeedTank(PcFluids.FUEL_OIL.source.get(), MB);
    private final FeedTank fuel = new FeedTank(PcFluids.SALES_GAS.source.get(), MB);
    private final FeedTank water = new FeedTank(PcFluids.BOILER_FEEDWATER.source.get(), MB);
    private final IFluidHandler inlet = new PyrolysisFurnaceBlockEntity.MultiInlet(feed, fuel, water);
    private final ProductTank vgo = new ProductTank(PcFluids.VACUUM_GAS_OIL.source.get(), MB);
    private final ProductTank residue = new ProductTank(PcFluids.VACUUM_RESIDUE.source.get(), MB);
    private double heaterC = 400, feedLs, pressureKpa = VacuumUnit.P0_KPA, resultKey = Double.NaN;
    private int ejectors;
    private VacuumUnit.Result last;

    public VacuumColumnBlockEntity(BlockPos pos, BlockState state) {
        super(RefineryBlockEntities.VACUUM_COLUMN.get(), pos, state, 0, 0, s -> false);
    }

    public FeedTank feed() {
        return feed;
    }

    public FeedTank fuel() {
        return fuel;
    }

    public FeedTank water() {
        return water;
    }

    public ProductTank vgo() {
        return vgo;
    }

    public ProductTank residue() {
        return residue;
    }

    public double pressureKpa() {
        return pressureKpa;
    }

    public int ejectors() {
        return ejectors;
    }

    public void setHeaterC(double c) {
        heaterC = Math.clamp(c, T_MIN_C, T_MAX_C);
        setChanged();
    }

    /** Результат перегонки при текущих печи и вакууме (кешируется). */
    public VacuumUnit.Result result() {
        double key = heaterC * 1e3 + pressureKpa;
        if (last == null || key != resultKey) {
            last = new VacuumUnit(VacuumUnit.STAGES, VacuumUnit.REFLUX, heaterC + 273.15, pressureKpa)
                    .distill(CrudeAssay.WEST_SIBERIAN, RESIDUE_SLICES);
            resultKey = key;
        }
        return last;
    }

    /** Коксуемость гудрона, доля — для коксования. */
    public double residueCcr() {
        return result().residueCcr(FEED_CCR);
    }

    private int countEjectors(ServerLevel level) {
        int n = 0;
        for (Direction d : Direction.values())
            if (level.getBlockState(worldPosition.relative(d)).is(PcBlocks.STEAM_EJECTOR.get())) n++;
        return Math.min(MAX_EJECTORS, n);
    }

    @Override
    protected void process(ServerLevel level, double dt) {
        feedLs = 0;
        if (level.getGameTime() % 20 == 0 || ejectors == 0) ejectors = countEjectors(level);
        if (!on) {
            pressureKpa = VacuumUnit.P0_KPA;
            return;
        }
        double kgMax = Math.min(FEED_L_S * dt, feed.amount()) * FUEL_OIL_KG_L;
        boolean steam = ejectors > 0 && water.amount() >= kgMax * STEAM_PER_FEED && kgMax > 0;
        pressureKpa = steam ? EJECTOR_KPA[ejectors] : VacuumUnit.P0_KPA;
        VacuumUnit.Result r = result();
        double kg = kgMax;
        kg = Math.min(kg, vgo.room() * VGO_KG_L / Math.max(1e-9, r.vgo()));
        kg = Math.min(kg, residue.room() * VR_KG_L / Math.max(1e-9, r.residue()));
        kg = Math.min(kg, fuel.amount() * FUEL_J_PER_NM3 * EFFICIENCY / Math.max(1, r.dutyJPerKg()));
        if (kg <= 0) return;
        feed.take(kg / FUEL_OIL_KG_L);
        fuel.take(kg * r.dutyJPerKg() / (FUEL_J_PER_NM3 * EFFICIENCY));
        if (steam) water.take(kg * STEAM_PER_FEED);
        vgo.add(kg * r.vgo() / VGO_KG_L);
        residue.add(kg * r.residue() / VR_KG_L);
        feedLs = kg / FUEL_OIL_KG_L / dt;
    }

    @Override
    protected boolean running() {
        return feedLs > 0;
    }

    @Override
    public IFluidHandler fluids(@Nullable Direction side) {
        if (side == Direction.UP) return vgo;
        return side == Direction.DOWN ? residue : inlet;
    }

    @Override
    public PanelLayout panelLayout() {
        return ConversionPanels.VACUUM;
    }

    @Override
    public void readPanel(int[] v) {
        Arrays.fill(v, 0);
        VacuumUnit.Result r = result();
        v[ConversionPanels.VC_T_C] = (int) Math.round(heaterC);
        v[ConversionPanels.VC_P_X10] = (int) Math.round(pressureKpa * 10);
        v[ConversionPanels.VC_VGO_PERMIL] = (int) Math.round(r.vgo() * 1000);
        v[ConversionPanels.VC_CCR_PERMIL] = (int) Math.round(residueCcr() * 1000);
        v[ConversionPanels.VC_TANK] = (int) Math.round(vgo.fill() * 1000);
        v[ConversionPanels.VC_FEED_LH] = (int) Math.round(feedLs * 3600);
        v[ConversionPanels.VC_FLASH_C] = (int) Math.round(r.flashTbK() - 273.15);
        v[ConversionPanels.VC_EJECTORS] = ejectors;
        v[ConversionPanels.VC_FLAGS] = (on ? 1 << ConversionPanels.F_ON : 0) | (running() ? 1 << ConversionPanels.F_RUN : 0)
                | (pressureKpa < VacuumUnit.P0_KPA ? 1 << ConversionPanels.F_AUX : 0)
                | (on && pressureKpa >= VacuumUnit.P0_KPA ? 1 << ConversionPanels.F_WARN : 0);
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        switch (id) {
            case ConversionPanels.B_RUN -> use(player);
            case ConversionPanels.B_DOWN -> setHeaterC(heaterC - 5);
            case ConversionPanels.B_UP -> setHeaterC(heaterC + 5);
            default -> {
            }
        }
        setChanged();
    }

    @Override
    protected void saveExtra(CompoundTag tag, HolderLookup.Provider registries) {
        feed.save(tag, "feed");
        fuel.save(tag, "fuel");
        water.save(tag, "water");
        vgo.save(tag, "vgo");
        residue.save(tag, "residue");
        tag.putDouble("heater", heaterC);
    }

    @Override
    protected void loadExtra(CompoundTag tag, HolderLookup.Provider registries) {
        feed.load(tag, "feed");
        fuel.load(tag, "fuel");
        water.load(tag, "water");
        vgo.load(tag, "vgo");
        residue.load(tag, "residue");
        if (tag.contains("heater")) heaterC = tag.getDouble("heater");
    }
}
