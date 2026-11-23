package dev.denfry.promptcritical.mod.uranium;

import dev.denfry.promptcritical.mod.binding.NbtStateTrees;
import dev.denfry.promptcritical.mod.chem.PyrolysisFurnaceBlockEntity.MultiInlet;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.production.FeedTank;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.core.MutableStateTree;
import dev.denfry.promptcritical.sim.core.TreeStateReader;
import dev.denfry.promptcritical.sim.core.TreeStateWriter;
import dev.denfry.promptcritical.sim.uranium.AduPrecipitation;
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
 * Осадитель диураната аммония ({@link AduPrecipitation}) с фильтр-прессом и сушилкой. Элюат и жидкий аммиак (1 мБ =
 * 1 л, 0,682 кг) — трубами к любой грани; жёлтый кек — мешками по {@value UraniumUnits#U_PER_ITEM_KG} кг урана в склад
 * продукта (воронкой снизу).
 *
 * <p>Тот же бак осаждает криолит для алюминиевых ванн (08 §8.8.2: {@code cryolite} из HF 02 и гидроксида алюминия
 * в {@code precipitation_tank} 02): Al(OH)₃ + 6HF + 3NaOH → Na₃AlF₆ + 6H₂O. Гидроксид — мешками воронкой или рукой,
 * безводный HF и электролитическая щёлочь — трубами; криолит — мешками по {@code metallurgy.bulk_item_mass_kg} в тот же
 * склад. Осадок кека идёт первым, криолит — когда элюата нет.
 */
public final class PrecipitationBlockEntity extends UraniumProcessBlockEntity {
    public static final int MB = 8_000;
    /** Элюат на шаг, л/с [BALANCE]. */
    public static final double ELUATE_LS = 20;

    private final FeedTank eluate = new FeedTank(PcFluids.URANIUM_ELUATE.source.get(), MB);
    private final FeedTank ammonia = new FeedTank(PcFluids.AMMONIA.source.get(), MB / 8);
    private final FeedTank hf = new FeedTank(PcFluids.HYDROGEN_FLUORIDE.source.get(), MB / 2);
    private final FeedTank caustic = new FeedTank(PcFluids.SODIUM_HYDROXIDE_SOLUTION.source.get(), MB);
    private final IFluidHandler inlet = new MultiInlet(eluate, ammonia, hf, caustic);
    private final AduPrecipitation adu = new AduPrecipitation();
    private double cakeKgU, ammoniaShare = 1;
    /** Криолит: гидроксид в баке и готовый осадок, кг. */
    private double hydroxideKg, cryoliteKg;
    /** Криолит на шаг, кг/с физ. [BALANCE]; реагенты на кг криолита по стехиометрии (M Na₃AlF₆ = 209,94 г/моль). */
    public static final double CRYOLITE_KG_S = 0.05, HYDROXIDE_PER_CRYOLITE = 78.004 / 209.94, HF_PER_CRYOLITE = 6 * 20.006 / 209.94,
            NAOH_PER_CRYOLITE = 3 * 39.997 / 209.94;

    public PrecipitationBlockEntity(BlockPos pos, BlockState state) {
        super(UraniumBlockEntities.PRECIPITATION.get(), pos, state, 2, 4, s -> s.is(PcItems.ALUMINIUM_HYDROXIDE.get()));
    }

    public AduPrecipitation adu() {
        return adu;
    }

    @Override
    protected void process(ServerLevel level, double dt) {
        lastCryoliteKgS = 0;
        if (on && eluate.amount() <= 0 && hasRoom(PcItems.CRYOLITE.get())) cryolite(dt);
        if (on && hasRoom(PcItems.YELLOWCAKE.get())) {
            double litres = Math.min(eluate.amount(), ELUATE_LS * dt);
            double u = litres * UraniumUnits.ELUATE_KG_U_L;
            double needL = u * AduPrecipitation.NH3_KG_PER_KG_U / UraniumUnits.NH3_KG_L;
            double nh3L = Math.min(needL, ammonia.amount());
            ammoniaShare = needL > 0 ? nh3L / needL : ammoniaShare;
            eluate.take(litres);
            ammonia.take(nh3L);
            cakeKgU += adu.step(u, nh3L * UraniumUnits.NH3_KG_L);
        }
        while (cakeKgU >= UraniumUnits.U_PER_ITEM_KG && emit(PcItems.YELLOWCAKE.get())) cakeKgU -= UraniumUnits.U_PER_ITEM_KG;
        double bag = dev.denfry.promptcritical.mod.config.ServerConfig.METALLURGY_BULK_ITEM_MASS_KG.get();
        while (cryoliteKg >= bag && emit(PcItems.CRYOLITE.get())) cryoliteKg -= bag;
    }

    /** Криолит из гидроксида, HF и щёлочи; щёлочь — электролитическая, 11 % NaOH (1,24 кг/л). */
    private void cryolite(double dt) {
        double bag = dev.denfry.promptcritical.mod.config.ServerConfig.METALLURGY_BULK_ITEM_MASS_KG.get();
        double want = CRYOLITE_KG_S * dt;
        if (hydroxideKg < want * HYDROXIDE_PER_CRYOLITE && !takeOne(s -> s.is(PcItems.ALUMINIUM_HYDROXIDE.get())).isEmpty()) hydroxideKg += bag;
        double kg = Math.min(want, hydroxideKg / HYDROXIDE_PER_CRYOLITE);
        kg = Math.min(kg, hf.amount() * UraniumUnits.HF_KG_L / HF_PER_CRYOLITE);
        kg = Math.min(kg, caustic.amount() * CAUSTIC_NAOH_KG_L / NAOH_PER_CRYOLITE);
        if (kg <= 0) return;
        hydroxideKg -= kg * HYDROXIDE_PER_CRYOLITE;
        hf.take(kg * HF_PER_CRYOLITE / UraniumUnits.HF_KG_L);
        caustic.take(kg * NAOH_PER_CRYOLITE / CAUSTIC_NAOH_KG_L);
        cryoliteKg += kg;
        lastCryoliteKgS = kg / dt;
    }

    private static final double CAUSTIC_NAOH_KG_L = 0.11 * 1.24;
    private double lastCryoliteKgS;

    public double cryoliteKg() {
        return cryoliteKg;
    }

    /** Мешалка и фильтр-пресс, Вт. */
    @Override
    protected double ratedPowerW() {
        return 20e3;
    }

    @Override
    protected boolean running() {
        return on && (eluate.amount() > 0 || lastCryoliteKgS > 0);
    }

    @Override
    public IFluidHandler fluids(@Nullable Direction side) {
        return inlet;
    }

    @Override
    public PanelLayout panelLayout() {
        return UraniumPanels.ADU;
    }

    @Override
    public void readPanel(int[] v) {
        Arrays.fill(v, 0);
        v[UraniumPanels.A] = (int) Math.round(AduPrecipitation.recovery(ammoniaShare) * 1000);
        v[UraniumPanels.B] = (int) Math.round(ammoniaShare * 100);
        v[UraniumPanels.C] = (int) Math.round(cakeKgU * 100);
        v[UraniumPanels.TANK] = (int) Math.round(eluate.fill() * 1000);
        v[UraniumPanels.TANK2] = (int) Math.round(ammonia.fill() * 1000);
        UraniumPanels.ledger(v, adu.ledger());
        v[UraniumPanels.FLAGS] = (on ? 1 << UraniumPanels.F_ON : 0) | (running() ? 1 << UraniumPanels.F_RUN : 0)
                | (running() && ammoniaShare < 0.99 ? 1 << UraniumPanels.F_WARN : 0);
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        if (id == UraniumPanels.B_RUN) use(player);
        setChanged();
    }

    @Override
    protected void saveExtra(CompoundTag tag, HolderLookup.Provider registries) {
        eluate.save(tag, "eluate");
        ammonia.save(tag, "nh3");
        tag.putDouble("cake", cakeKgU);
        hf.save(tag, "hf");
        caustic.save(tag, "caustic");
        tag.putDouble("hydrate", hydroxideKg);
        tag.putDouble("cryolite", cryoliteKg);
        MutableStateTree t = new MutableStateTree();
        adu.save(new TreeStateWriter(t));
        tag.put("adu", NbtStateTrees.toNbt(t));
    }

    @Override
    protected void loadExtra(CompoundTag tag, HolderLookup.Provider registries) {
        eluate.load(tag, "eluate");
        ammonia.load(tag, "nh3");
        cakeKgU = tag.getDouble("cake");
        hf.load(tag, "hf");
        caustic.load(tag, "caustic");
        hydroxideKg = tag.getDouble("hydrate");
        cryoliteKg = tag.getDouble("cryolite");
        if (tag.contains("adu")) adu.load(new TreeStateReader(NbtStateTrees.fromNbt(tag.getCompound("adu"))));
    }
}
