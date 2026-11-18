package dev.denfry.promptcritical.mod.refinery;

import dev.denfry.promptcritical.mod.gas.CentrifugalCompressorBlockEntity;
import dev.denfry.promptcritical.mod.gas.PortHandler;
import dev.denfry.promptcritical.mod.ironworks.Ironworks;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.production.FeedTank;
import dev.denfry.promptcritical.mod.production.ProductTank;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.mod.uranium.UraniumProcessBlockEntity;
import dev.denfry.promptcritical.sim.gasprocessing.CentrifugalCompressor;
import dev.denfry.promptcritical.sim.refining.CatalystState;
import dev.denfry.promptcritical.sim.refining.CatalyticCracking;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Установка каталитического крекинга ({@link CatalyticCracking}, 09 §9.9.5): лифт-реактор, реактор-сепаратор,
 * регенератор кипящего слоя и главная колонна в одном аппарате. Сырьё — вакуумный газойль, трубой к любой грани.
 * Продукты: бензин каткрекинга — снизу, сухой газ (в сеть товарного газа как топливо) — сверху, лёгкий газойль —
 * с лицевой грани, тяжёлый ({@code decant_oil}) — с задней, СУГ (пропан и бутан-бутилены) — слева, пропилен —
 * справа.
 *
 * <p>Воздух в регенератор подаёт центробежный компрессор ({@link CentrifugalCompressorBlockEntity}) в
 * {@value #BLOWER_RADIUS} блоках — воздуходувка FCC (09 §9.8.5, §9.9.5): расход его линии к расчётному
 * ({@link CentrifugalCompressor#Q_DESIGN}) — доля расчётного воздуха {@value #AIR_DESIGN_KGS} кг/с [BALANCE: машина
 * подобрана под эту установку], ещё его дросселирует заслонка щита. Встал компрессор или ушёл в помпаж — нет воздуха,
 * перепад на шиберной задвижке падает, ПАЗ закрывает задвижки ({@code FCC_SLIDE_VALVE_DP_LOW}); сброс — пуск со щита.
 *
 * <p>Катализатор — мешки {@code catalyst_fcc} по {@value #BAG_KG} кг в слот щита: первые {@link #fullKg()} кг —
 * загрузка, дальше — подпитка; равновесный катализатор, выведенный при подпитке, выходит мешками в склад снизу с
 * компонентом {@code catalyst_state}. Сырьё подогрето до {@value #FEED_C} °C теплом циркуляционных орошений главной
 * колонны [допущение]. Коксуемость газойля — {@value #FEED_CCR} [BALANCE: прямогонный ВГО].
 */
public final class FccUnitBlockEntity extends UraniumProcessBlockEntity {
    public static final int MB = 8_000, BLOWER_RADIUS = 8;
    /** Производительность — 0,75 л/с газойля (весь газойль вакуумной колонны) [BALANCE]. */
    public static final double FEED_L_S = 0.75, VGO_KG_L = 0.91, FEED_C = 250, FEED_CCR = 0.005;
    public static final double AIR_DESIGN_KGS = 0.55, BAG_KG = 100, RISER_MIN_C = 490, RISER_MAX_C = 550;
    /** Плотности продуктов, кг/л; сухой газ (H₂, C₁–C₂) — 0,9 кг/нм³ [допущение]. */
    public static final double GASOLINE_KG_L = 0.74, LCO_KG_L = 0.94, DECANT_KG_L = 1.05, LPG_KG_L = 0.54, PROPYLENE_KG_L = 0.514,
            DRY_GAS_KG_NM3 = 0.9;

    private final FeedTank feed = new FeedTank(PcFluids.VACUUM_GAS_OIL.source.get(), MB);
    private final ProductTank gasoline = new ProductTank(PcFluids.FCC_GASOLINE.source.get(), MB);
    private final ProductTank dryGas = new ProductTank(PcFluids.SALES_GAS.source.get(), MB);
    private final ProductTank lco = new ProductTank(PcFluids.LIGHT_CYCLE_OIL.source.get(), MB);
    private final ProductTank decant = new ProductTank(PcFluids.DECANT_OIL.source.get(), MB);
    private final ProductTank lpg = new ProductTank(PcFluids.LPG.source.get(), MB);
    private final ProductTank propylene = new ProductTank(PcFluids.PROPYLENE.source.get(), MB);
    private final IFluidHandler down = new PortHandler(gasoline, feed), up = new PortHandler(dryGas, feed), front = new PortHandler(lco, feed),
            back = new PortHandler(decant, feed), left = new PortHandler(lpg, feed), right = new PortHandler(propylene, feed);
    private final CatalyticCracking fcc = new CatalyticCracking();
    private double riserC = 520, damper = 1, feedKgS, airKgS, withdrawnKg, ageH;
    private boolean blower;
    private CatalyticCracking.Step last;

    public FccUnitBlockEntity(BlockPos pos, BlockState state) {
        super(RefineryBlockEntities.FCC_UNIT.get(), pos, state, 1, 2, s -> s.is(PcItems.CATALYST_FCC.get()));
    }

    public CatalyticCracking unit() {
        return fcc;
    }

    @Nullable
    public CatalyticCracking.Step last() {
        return last;
    }

    public FeedTank feed() {
        return feed;
    }

    public ProductTank gasoline() {
        return gasoline;
    }

    public ProductTank propylene() {
        return propylene;
    }

    public double airKgS() {
        return airKgS;
    }

    public double damper() {
        return damper;
    }

    public boolean blower() {
        return blower;
    }

    /** Расчётный запас катализатора, кг (20 т на 10 кг/с сырья, §9.9.5). */
    public static double fullKg() {
        return CatalyticCracking.INVENTORY_PER_KGS * FEED_L_S * VGO_KG_L;
    }

    public void setRiserC(double c) {
        riserC = Math.clamp(c, RISER_MIN_C, RISER_MAX_C);
        setChanged();
    }

    public void setDamper(double d) {
        damper = Math.clamp(Math.round(d * 20) / 20.0, 0, 1.2);
        setChanged();
    }

    /** Воздуходувка: ближайший центробежный компрессор; расход его линии к расчётному. */
    private double blowerShare(ServerLevel level) {
        double best = -1, bestD = Double.MAX_VALUE;
        for (CentrifugalCompressorBlockEntity c : Ironworks.blockEntities(level, new AABB(worldPosition).inflate(BLOWER_RADIUS),
                CentrifugalCompressorBlockEntity.class)) {
            double d = c.getBlockPos().distSqr(worldPosition);
            if (d < bestD) {
                bestD = d;
                best = Math.max(0, c.compressor().lineFlowM3s()) / CentrifugalCompressor.Q_DESIGN;
            }
        }
        return best;
    }

    @Override
    protected void process(ServerLevel level, double dt) {
        ItemStack bag = takeOne(s -> s.is(PcItems.CATALYST_FCC.get()));
        if (!bag.isEmpty()) {
            CatalystState cs = bag.get(RefineryComponents.CATALYST_STATE.get());
            double a = cs == null ? 1 : cs.activityPerm();
            if (fcc.inventoryKg() < 1e-9) ageH = 0;
            withdrawnKg += fcc.addCatalyst(BAG_KG, a, fullKg());
        }
        while (withdrawnKg >= BAG_KG && hasRoom(PcItems.CATALYST_FCC.get())) {
            ItemStack out = new ItemStack(PcItems.CATALYST_FCC.get());
            out.set(RefineryComponents.CATALYST_STATE.get(), CatalystState.fccEquilibrium(fcc.activity(), fcc.crc(), ageH));
            emit(out);
            withdrawnKg -= BAG_KG;
        }
        double share = blowerShare(level);
        blower = share >= 0;
        airKgS = AIR_DESIGN_KGS * Math.max(0, share) * damper;
        double kgMax = on ? Math.min(FEED_L_S * dt, feed.amount()) * VGO_KG_L / dt : 0;
        // место в баках: по выходам последнего шага (или проектным)
        CatalyticCracking.Yields y = last != null && last.yields().conversion() > 0 ? last.yields()
                : CatalyticCracking.yields(CatalyticCracking.weekman(riserC + 273.15, 6, CatalyticCracking.CONTACT_S,
                        CatalyticCracking.ACTIVITY_REF), FEED_CCR);
        kgMax = Math.min(kgMax, room(gasoline, y.gasoline(), GASOLINE_KG_L, dt));
        kgMax = Math.min(kgMax, room(lco, y.lightCycleOil(), LCO_KG_L, dt));
        kgMax = Math.min(kgMax, room(decant, y.decantOil(), DECANT_KG_L, dt));
        kgMax = Math.min(kgMax, room(lpg, lpgYield(y), LPG_KG_L, dt));
        kgMax = Math.min(kgMax, room(propylene, y.propanePropylene() * CatalyticCracking.PROPYLENE_IN_PP, PROPYLENE_KG_L, dt));
        kgMax = Math.min(kgMax, dryGas.room() * DRY_GAS_KG_NM3 / Math.max(1e-9, y.dryGas()) / dt);
        feedKgS = Math.max(0, kgMax);
        last = fcc.step(dt, feedKgS, FEED_C + 273.15, FEED_CCR, riserC + 273.15, airKgS, AIR_DESIGN_KGS, fullKg());
        if (last.catKgS() <= 0) feedKgS = 0;
        if (feedKgS <= 0) return;
        ageH += dt / 3600;
        CatalyticCracking.Yields r = last.yields();
        double kg = feedKgS * dt;
        feed.take(kg / VGO_KG_L);
        gasoline.add(kg * r.gasoline() / GASOLINE_KG_L);
        lco.add(kg * r.lightCycleOil() / LCO_KG_L);
        decant.add(kg * r.decantOil() / DECANT_KG_L);
        lpg.add(kg * lpgYield(r) / LPG_KG_L);
        propylene.add(kg * r.propanePropylene() * CatalyticCracking.PROPYLENE_IN_PP / PROPYLENE_KG_L);
        dryGas.add(kg * r.dryGas() / DRY_GAS_KG_NM3);
    }

    private static double lpgYield(CatalyticCracking.Yields y) {
        return y.propanePropylene() * (1 - CatalyticCracking.PROPYLENE_IN_PP) + y.butaneButylene();
    }

    private static double room(ProductTank t, double yield, double kgL, double dt) {
        return yield <= 0 ? Double.MAX_VALUE : t.room() * kgL / yield / dt;
    }

    @Override
    protected boolean running() {
        return feedKgS > 0;
    }

    @Override
    public IFluidHandler fluids(@Nullable Direction side) {
        if (side == null || side == Direction.DOWN) return down;
        if (side == Direction.UP) return up;
        Direction f = getBlockState().hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                ? getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING) : Direction.NORTH;
        if (side == f) return front;
        if (side == f.getOpposite()) return back;
        return side == f.getCounterClockWise() ? left : right;
    }

    @Override
    public PanelLayout panelLayout() {
        return ConversionPanels.FCC;
    }

    @Override
    public void readPanel(int[] v) {
        Arrays.fill(v, 0);
        CatalyticCracking.Step s = last;
        v[ConversionPanels.FC_RISER_C] = (int) Math.round(s != null && s.catKgS() > 0 ? s.riserK() - 273.15 : riserC);
        v[ConversionPanels.FC_REGEN_C] = (int) Math.round(fcc.regenK() - 273.15);
        v[ConversionPanels.FC_C2O_X10] = s == null ? 0 : (int) Math.round(s.catToOil() * 10);
        v[ConversionPanels.FC_CONV_PERMIL] = s == null ? 0 : (int) Math.round(s.yields().conversion() * 1000);
        v[ConversionPanels.FC_ACT_PERMIL] = (int) Math.round(fcc.activity() * 1000);
        v[ConversionPanels.FC_TANK] = (int) Math.round(gasoline.fill() * 1000);
        v[ConversionPanels.FC_INV_KG] = (int) Math.round(fcc.inventoryKg());
        v[ConversionPanels.FC_CRC_X1000] = (int) Math.round(fcc.crc() * 1e5);
        v[ConversionPanels.FC_AIR_X1000] = (int) Math.round(airKgS * 1000);
        v[ConversionPanels.FC_DAMPER_PCT] = (int) Math.round(damper * 100);
        v[ConversionPanels.FC_DP_KPA] = s == null ? 0 : (int) Math.round(s.slideDpMpa() * 1000);
        v[ConversionPanels.FC_FEED_LH] = (int) Math.round(feedKgS / VGO_KG_L * 3600);
        v[ConversionPanels.FC_FLAGS] = (on ? 1 << ConversionPanels.F_ON : 0) | (running() ? 1 << ConversionPanels.F_RUN : 0)
                | (s != null && s.afterburn() ? 1 << ConversionPanels.F_ALARM : 0) | (fcc.tripped() ? 1 << ConversionPanels.F_WARN : 0)
                | (!blower ? 1 << ConversionPanels.F_AUX : 0) | (fcc.inventoryKg() < 0.5 * fullKg() ? 1 << ConversionPanels.F_LOW : 0);
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        switch (id) {
            case ConversionPanels.B_RUN -> {
                if (fcc.tripped()) fcc.resetTrip();
                else use(player);
            }
            case ConversionPanels.B_DOWN -> setRiserC(riserC - 5);
            case ConversionPanels.B_UP -> setRiserC(riserC + 5);
            case ConversionPanels.B_DOWN2 -> setDamper(damper - 0.05);
            case ConversionPanels.B_UP2 -> setDamper(damper + 0.05);
            default -> {
            }
        }
        setChanged();
    }

    @Override
    protected void saveExtra(CompoundTag tag, HolderLookup.Provider registries) {
        feed.save(tag, "feed");
        gasoline.save(tag, "gasoline");
        dryGas.save(tag, "dry_gas");
        lco.save(tag, "lco");
        decant.save(tag, "decant");
        lpg.save(tag, "lpg");
        propylene.save(tag, "propylene");
        tag.putDouble("riser", riserC);
        tag.putDouble("damper", damper);
        tag.putDouble("regen", fcc.regenK());
        tag.putDouble("coke", fcc.cokeOnCatKg());
        tag.putDouble("inventory", fcc.inventoryKg());
        tag.putDouble("activity", fcc.activity());
        tag.putBoolean("tripped", fcc.tripped());
        tag.putDouble("withdrawn", withdrawnKg);
        tag.putDouble("age", ageH);
    }

    @Override
    protected void loadExtra(CompoundTag tag, HolderLookup.Provider registries) {
        feed.load(tag, "feed");
        gasoline.load(tag, "gasoline");
        dryGas.load(tag, "dry_gas");
        lco.load(tag, "lco");
        decant.load(tag, "decant");
        lpg.load(tag, "lpg");
        propylene.load(tag, "propylene");
        if (tag.contains("riser")) riserC = tag.getDouble("riser");
        if (tag.contains("damper")) damper = tag.getDouble("damper");
        if (tag.contains("regen"))
            fcc.restore(tag.getDouble("regen"), tag.getDouble("coke"), tag.getDouble("inventory"), tag.getDouble("activity"),
                    tag.getBoolean("tripped"));
        withdrawnKg = tag.getDouble("withdrawn");
        ageH = tag.getDouble("age");
    }
}
