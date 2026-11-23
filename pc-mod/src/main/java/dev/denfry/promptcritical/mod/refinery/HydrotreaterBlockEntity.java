package dev.denfry.promptcritical.mod.refinery;

import dev.denfry.promptcritical.mod.chem.PyrolysisFurnaceBlockEntity;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.production.FeedTank;
import dev.denfry.promptcritical.mod.production.ProductTank;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.mod.uranium.UraniumProcessBlockEntity;
import dev.denfry.promptcritical.sim.refining.Hydrotreating;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Гидроочистка дизельного топлива ({@link Hydrotreating}): реактор с CoMo-катализатором при {@link #PRESSURE_MPA} МПа,
 * печь сырья, циркуляция водорода и аминовая очистка циркулирующего газа в одном аппарате. Прямогонное ДТ (1,2 % серы,
 * {@link #FEED_SULFUR}) и водород — трубой к боковым граням и верху, очищенное ДТ — снизу. Сероводород из амина
 * копится в аппарате и уходит в установку Клауса, которая касается реактора; без Клауса H₂S копится в рецикле и
 * тормозит обессеривание. Катализатор — с руки ({@code catalyst_hydrotreating}).
 */
public final class HydrotreaterBlockEntity extends UraniumProcessBlockEntity {
    public static final int MB = 8_000;
    /** Сырьё: прямогонное ДТ западносибирской нефти, 1,2 % S; насыщение олефинов и ароматики — 0,3 моль H₂/кг. */
    public static final double FEED_SULFUR = 0.012, SATURATION_MOL_KG = 0.3, DIESEL_KG_L = 0.84;
    /** Производительность модели — 1 л/с ДТ при LHSV 1,5 ч⁻¹ (слой 2,4 м³) [BALANCE]. */
    public static final double FEED_L_S = 1, LHSV = 1.5, PRESSURE_MPA = 6, H2_KG_NM3 = 0.0899;
    /** Объём рецикла, нм³: H₂S в нём в % об. считается по накопленному газу. */
    public static final double RECYCLE_NM3 = 50, H2S_HOLDUP_KG = 50, T_MIN_C = 300, T_MAX_C = 420;

    private final FeedTank diesel = new FeedTank(PcFluids.DIESEL_FUEL.source.get(), MB);
    private final FeedTank hydrogen = new FeedTank(PcFluids.HYDROGEN.source.get(), MB);
    private final IFluidHandler inlet = new PyrolysisFurnaceBlockEntity.MultiInlet(diesel, hydrogen);
    private final ProductTank product = new ProductTank(PcFluids.DIESEL_FUEL.source.get(), MB);
    private final Hydrotreating hdt = new Hydrotreating();
    private double inletC = 355, h2sKg, feedLs;
    private Hydrotreating.Result last = new Hydrotreating.Result(FEED_SULFUR, 0, 0, 0, 0, false);

    public HydrotreaterBlockEntity(BlockPos pos, BlockState state) {
        super(RefineryBlockEntities.HYDROTREATER.get(), pos, state, 1, 0, s -> s.is(PcItems.CATALYST_HYDROTREATING.get()));
    }

    public Hydrotreating unit() {
        return hdt;
    }

    public Hydrotreating.Result last() {
        return last;
    }

    public FeedTank diesel() {
        return diesel;
    }

    public FeedTank hydrogen() {
        return hydrogen;
    }

    public ProductTank product() {
        return product;
    }

    public double h2sKg() {
        return h2sKg;
    }

    public void setInletC(double c) {
        inletC = Math.clamp(c, T_MIN_C, T_MAX_C);
    }

    /** H₂S в циркулирующем газе, % об. */
    public double h2sPct() {
        return 100 * h2sKg / Hydrotreating.M_H2S * 22.414e-3 / RECYCLE_NM3;
    }

    /** Отдать сероводород установке Клауса, кг. */
    public double drainH2s(double maxKg) {
        double kg = Math.min(maxKg, h2sKg);
        h2sKg -= kg;
        return kg;
    }

    @Override
    protected void process(ServerLevel level, double dt) {
        feedLs = 0;
        if (!takeOne(s -> s.is(PcItems.CATALYST_HYDROTREATING.get())).isEmpty()) hdt.loadCatalyst();
        if (!on || h2sKg >= H2S_HOLDUP_KG) return;
        double tK = inletC + 273.15;
        last = hdt.treat(FEED_SULFUR, LHSV, tK, PRESSURE_MPA, SATURATION_MOL_KG, h2sPct());
        double h2LPerL = last.h2KgPerKg() * DIESEL_KG_L / H2_KG_NM3 * 1000;
        double l = Math.min(FEED_L_S * dt, Math.min(diesel.amount(), product.room()));
        if (h2LPerL > 0) l = Math.min(l, hydrogen.amount() / h2LPerL);
        if (l <= 0) return;
        diesel.take(l);
        hydrogen.take(l * h2LPerL);
        product.add(l);
        h2sKg += l * DIESEL_KG_L * last.h2sKgPerKg();
        hdt.age(dt, tK + last.bedRiseK(), PRESSURE_MPA);
        feedLs = l / dt;
    }

    /** Компрессор циркулирующего водорода и сырьевой насос, Вт. */
    @Override
    protected double ratedPowerW() {
        return 200e3;
    }

    @Override
    protected boolean running() {
        return feedLs > 0;
    }

    @Override
    public IFluidHandler fluids(@Nullable Direction side) {
        return side == Direction.DOWN ? product : inlet;
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return stack.is(PcItems.CATALYST_HYDROTREATING.get());
    }

    @Override
    public PanelLayout panelLayout() {
        return RefineryPanels.HYDROTREATER;
    }

    @Override
    public void readPanel(int[] v) {
        Arrays.fill(v, 0);
        v[RefineryPanels.T_C] = (int) Math.round(inletC + last.bedRiseK());
        v[RefineryPanels.A] = (int) Math.round(Math.min(9999, last.productSulfurPpm()));
        v[RefineryPanels.B] = (int) Math.round(hdt.activity() * 1000);
        v[RefineryPanels.C] = (int) Math.round(h2sPct() * 100);
        v[RefineryPanels.D] = (int) Math.round(feedLs * 3600);
        v[RefineryPanels.TANK] = (int) Math.round(product.fill() * 1000);
        v[RefineryPanels.FLAGS] = (on ? 1 << RefineryPanels.F_ON : 0) | (running() ? 1 << RefineryPanels.F_RUN : 0)
                | (last.hydrocracking() ? 1 << RefineryPanels.F_WARN : 0) | (h2sKg >= H2S_HOLDUP_KG ? 1 << RefineryPanels.F_AUX : 0);
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        if (id == RefineryPanels.B_RUN) use(player);
        else if (id == RefineryPanels.B_T_DOWN) setInletC(inletC - 5);
        else if (id == RefineryPanels.B_T_UP) setInletC(inletC + 5);
        setChanged();
    }

    @Override
    protected void saveExtra(CompoundTag tag, HolderLookup.Provider registries) {
        diesel.save(tag, "diesel");
        hydrogen.save(tag, "h2");
        product.save(tag, "product");
        tag.putDouble("inlet", inletC);
        tag.putDouble("h2s", h2sKg);
        tag.putDouble("coke", hdt.coke());
    }

    @Override
    protected void loadExtra(CompoundTag tag, HolderLookup.Provider registries) {
        diesel.load(tag, "diesel");
        hydrogen.load(tag, "h2");
        product.load(tag, "product");
        if (tag.contains("inlet")) inletC = tag.getDouble("inlet");
        h2sKg = tag.getDouble("h2s");
        hdt.setCoke(tag.getDouble("coke"));
    }
}
