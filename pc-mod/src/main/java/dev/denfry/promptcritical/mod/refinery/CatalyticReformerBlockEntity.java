package dev.denfry.promptcritical.mod.refinery;

import dev.denfry.promptcritical.mod.chem.PyrolysisFurnaceBlockEntity;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.production.FeedTank;
import dev.denfry.promptcritical.mod.production.ProductTank;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.mod.uranium.MvProcessBlockEntity;
import dev.denfry.promptcritical.sim.refining.CatalyticReforming;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Каталитический риформинг ({@link CatalyticReforming}): три адиабатических реактора с Pt-Re катализатором и печи
 * подогрева перед каждым, 2,5 МПа, H₂/сырьё 6. Сырьё — бензиновая фракция атмосферной колонны (тяжёлая нафта, уже
 * гидроочищенная, {@link #FEED_SULFUR_PPM}) трубой к боковым граням; туда же топливный газ печей (товарный газ) и
 * хлор для оксихлорирования. Реформат (бензин с высоким ОЧИ) — снизу, водород — сверху (его ест гидроочистка),
 * избыток своего газа гидрокрекинга — с боковых граней. Печи сначала жгут свой газ, недостачу добирают товарным.
 * Регенерация — кнопкой на остановленной установке: выжиг кокса, с хлором — ещё и редиспергирование платины.
 */
public final class CatalyticReformerBlockEntity extends MvProcessBlockEntity {
    /** Циркуляционный компрессор водородсодержащего газа, сырьевые насосы и дымососы печей, Вт. */
    @Override
    protected double mvRatedW() {
        return 1.2e6;
    }

    public static final int MB = 8_000, CHLORINE_MB = 1_000;
    /** 1 л/с сырья при LHSV 1,5 ч⁻¹ [BALANCE], давление и кратность циркуляции по Л-35-11/600. */
    public static final double FEED_L_S = 1, LHSV = 1.5, PRESSURE_MPA = 2.5, H2_RATIO = 6, FEED_SULFUR_PPM = 0.5;
    public static final double NAPHTHA_KG_L = CatalyticReforming.NAPHTHA_KG_M3 / 1000, H2_KG_NL = 0.0899e-3;
    /** Газ C₁–C₅: 46 МДж/кг; топливо считается в нм³ товарного газа по 36 МДж (как у печи пиролиза), КПД печей 0,85. */
    public static final double GAS_J_KG = 46e6, SALES_GAS_J_NM3 = 36e6, FURNACE_EFF = 0.85;
    public static final double T_MIN_C = 470, T_MAX_C = 540, CHLORINE_PER_REGEN_MB = 100;
    /** Пересчёт реакторов (3 × 400 ячеек) раз в секунду мира, а не каждый тик. */
    private static final int RECALC_TICKS = 20;

    private final FeedTank naphtha = new FeedTank(PcFluids.GASOLINE.source.get(), MB);
    private final FeedTank fuel = new FeedTank(PcFluids.SALES_GAS.source.get(), MB);
    private final FeedTank chlorine = new FeedTank(PcFluids.CHLORINE.source.get(), CHLORINE_MB);
    private final ProductTank reformate = new ProductTank(PcFluids.REFORMATE.source.get(), MB);
    private final ProductTank hydrogen = new ProductTank(PcFluids.HYDROGEN.source.get(), MB);
    private final ProductTank fuelGas = new ProductTank(PcFluids.SALES_GAS.source.get(), MB);
    private final IFluidHandler inlet = new PyrolysisFurnaceBlockEntity.MultiInlet(naphtha, fuel, chlorine);
    private final IFluidHandler sides = new SideHandler();
    private final CatalyticReforming unit = new CatalyticReforming();
    private double inletC = 500, feedLs, furnaceW;
    private int recalc;
    private boolean fuelShort;
    private CatalyticReforming.Result last;

    public CatalyticReformerBlockEntity(BlockPos pos, BlockState state) {
        super(RefineryBlockEntities.CATALYTIC_REFORMER.get(), pos, state, 1, 0, s -> s.is(PcItems.CATALYST_REFORMING.get()));
    }

    public CatalyticReforming unit() {
        return unit;
    }

    /** Последний расчёт реакторов; до первого расчёта считается сразу. */
    public CatalyticReforming.Result last() {
        if (last == null) recompute();
        return last;
    }

    public FeedTank naphtha() {
        return naphtha;
    }

    public FeedTank fuel() {
        return fuel;
    }

    public FeedTank chlorine() {
        return chlorine;
    }

    public ProductTank reformate() {
        return reformate;
    }

    public ProductTank hydrogen() {
        return hydrogen;
    }

    public ProductTank fuelGas() {
        return fuelGas;
    }

    public double inletC() {
        return inletC;
    }

    public void setInletC(double c) {
        inletC = Math.clamp(c, T_MIN_C, T_MAX_C);
        recalc = 0;
    }

    /** Мощность печей подогрева, Вт, при текущем расходе. */
    public double furnaceW() {
        return furnaceW;
    }

    /** Перепад температуры в первом (самом эндотермичном) реакторе, К. */
    public double firstReactorDropK() {
        return inletC + 273.15 - last().outletK()[0];
    }

    private void recompute() {
        last = unit.run(CatalyticReforming.Feed.HEAVY_NAPHTHA, inletC + 273.15, PRESSURE_MPA, H2_RATIO, LHSV, FEED_SULFUR_PPM);
        recalc = RECALC_TICKS;
    }

    /** Тепло печей на литр сырья, Дж: подогрев смеси сырья с циркулирующим H₂ после каждого реактора до входной T. */
    private double dutyJPerL(CatalyticReforming.Result r) {
        double cpJKgK = (CatalyticReforming.CP_HC + H2_RATIO * CatalyticReforming.CP_H2)
                / CatalyticReforming.Feed.HEAVY_NAPHTHA.molarMass();
        double sumDrop = 0;
        for (double out : r.outletK()) sumDrop += Math.max(0, inletC + 273.15 - out);
        return NAPHTHA_KG_L * cpJKgK * 1000 * sumDrop / FURNACE_EFF;
    }

    /**
     * Регенерация катализатора на остановленной установке: выжиг кокса; с хлором ({@link #CHLORINE_PER_REGEN_MB} мБ) —
     * оксихлорирование, без него платина не редиспергируется и теряет 15 % активности.
     *
     * @return false, если установка не остановлена
     */
    public boolean regenerate(boolean withChlorine) {
        if (on) return false;
        boolean cl = withChlorine && chlorine.amount() >= CHLORINE_PER_REGEN_MB;
        if (cl) chlorine.take(CHLORINE_PER_REGEN_MB);
        unit.regenerate(cl);
        recalc = 0;
        setChanged();
        return true;
    }

    @Override
    protected void process(ServerLevel level, double dt) {
        feedLs = 0;
        furnaceW = 0;
        if (!takeOne(s -> s.is(PcItems.CATALYST_REFORMING.get())).isEmpty()) {
            unit.loadCatalyst();
            recalc = 0;
        }
        if (--recalc <= 0 || last == null) recompute();
        if (!on || dt <= 0) return;
        CatalyticReforming.Result r = last;
        double l = Math.min(FEED_L_S * dt, naphtha.amount());
        double volYield = Math.max(1e-6, r.reformateVolume());
        l = Math.min(l, reformate.room() / volYield);
        double h2NlPerL = r.h2Mass() * NAPHTHA_KG_L / H2_KG_NL;
        if (h2NlPerL > 0) l = Math.min(l, hydrogen.room() / h2NlPerL);
        // газ гидрокрекинга в нм³ эквивалента товарного газа: сначала в свои печи, недостачу — из бака топлива
        double gasNm3PerL = r.gasMass() * NAPHTHA_KG_L * GAS_J_KG / SALES_GAS_J_NM3;
        double dutyNm3PerL = dutyJPerL(r) / SALES_GAS_J_NM3;
        double buyPerL = dutyNm3PerL - gasNm3PerL;
        fuelShort = buyPerL > 0 && fuel.amount() < buyPerL * l;
        if (buyPerL > 0) l = Math.min(l, fuel.amount() / buyPerL);
        if (l <= 1e-9) return;
        naphtha.take(l);
        reformate.add(l * volYield);
        hydrogen.add(l * h2NlPerL);
        if (buyPerL > 0) fuel.take(l * buyPerL);
        else fuelGas.add(-l * buyPerL);
        double bedK = inletC + 273.15;
        unit.age(dt, bedK, H2_RATIO, r.aromaticsVol());
        feedLs = l / dt;
        furnaceW = feedLs * dutyNm3PerL * SALES_GAS_J_NM3;
    }

    @Override
    protected boolean running() {
        return feedLs > 0;
    }

    @Override
    public IFluidHandler fluids(@Nullable Direction side) {
        if (side == Direction.DOWN) return reformate;
        if (side == Direction.UP) return hydrogen;
        return sides;
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return stack.is(PcItems.CATALYST_REFORMING.get());
    }

    @Override
    public PanelLayout panelLayout() {
        return RefineryPanels.REFORMER;
    }

    @Override
    public void readPanel(int[] v) {
        Arrays.fill(v, 0);
        CatalyticReforming.Result r = last();
        v[RefineryPanels.T_C] = (int) Math.round(inletC);
        v[RefineryPanels.A] = (int) Math.round(r.ron() * 10);
        v[RefineryPanels.B] = (int) Math.round(unit.activity() * 1000);
        v[RefineryPanels.C] = (int) Math.round(r.h2Mass() * 1e4);
        v[RefineryPanels.D] = (int) Math.round(feedLs * 3600);
        v[RefineryPanels.TANK] = (int) Math.round(reformate.fill() * 1000);
        v[RefineryPanels.FLAGS] = (on ? 1 << RefineryPanels.F_ON : 0) | (running() ? 1 << RefineryPanels.F_RUN : 0)
                | (unit.activity() < 0.5 ? 1 << RefineryPanels.F_WARN : 0) | (on && fuelShort ? 1 << RefineryPanels.F_AUX : 0);
        v[RefineryPanels.DT] = (int) Math.round(firstReactorDropK());
        v[RefineryPanels.COKE] = (int) Math.round(unit.coke() * 1000);
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        if (id == RefineryPanels.B_RUN) use(player);
        else if (id == RefineryPanels.B_T_DOWN) setInletC(inletC - 5);
        else if (id == RefineryPanels.B_T_UP) setInletC(inletC + 5);
        else if (id == RefineryPanels.B_REGEN_CL) regenerate(true);
        else if (id == RefineryPanels.B_REGEN) regenerate(false);
        setChanged();
    }

    @Override
    protected void saveExtra(CompoundTag tag, HolderLookup.Provider registries) {
        naphtha.save(tag, "naphtha");
        fuel.save(tag, "fuel");
        chlorine.save(tag, "cl");
        reformate.save(tag, "reformate");
        hydrogen.save(tag, "h2");
        fuelGas.save(tag, "gas");
        tag.putDouble("inlet", inletC);
        tag.putDouble("coke", unit.coke());
        tag.putDouble("sinter", unit.sinterLoss());
    }

    @Override
    protected void loadExtra(CompoundTag tag, HolderLookup.Provider registries) {
        naphtha.load(tag, "naphtha");
        fuel.load(tag, "fuel");
        chlorine.load(tag, "cl");
        reformate.load(tag, "reformate");
        hydrogen.load(tag, "h2");
        fuelGas.load(tag, "gas");
        if (tag.contains("inlet")) inletC = tag.getDouble("inlet");
        unit.restore(tag.getDouble("coke"), tag.getDouble("sinter"));
        recalc = 0;
    }

    /** Боковые грани: заливка сырья, топлива и хлора; слив — избыток своего топливного газа. */
    private final class SideHandler implements IFluidHandler {
        @Override
        public int getTanks() {
            return inlet.getTanks() + 1;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return tank < inlet.getTanks() ? inlet.getFluidInTank(tank) : fuelGas.getFluidInTank(0);
        }

        @Override
        public int getTankCapacity(int tank) {
            return tank < inlet.getTanks() ? inlet.getTankCapacity(tank) : fuelGas.getTankCapacity(0);
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return tank < inlet.getTanks() && inlet.isFluidValid(tank, stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return inlet.fill(resource, action);
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return fuelGas.drain(resource, action);
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return fuelGas.drain(maxDrain, action);
        }
    }
}
