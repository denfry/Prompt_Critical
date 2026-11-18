package dev.denfry.promptcritical.mod.mes;

import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.config.ServerConfig;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.ironworks.Ironworks;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.scada.TelemetryGatewayBlockEntity;
import dev.denfry.promptcritical.mod.scada.TelemetryGatewayBlockEntity.Reading;
import dev.denfry.promptcritical.mod.scada.TelemetryGatewayBlockEntity.TelemetryException;
import dev.denfry.promptcritical.sim.mes.EconomicDispatch;
import dev.denfry.promptcritical.sim.mes.GeneratingUnit;
import dev.denfry.promptcritical.sim.telemetry.ScanCycle;
import dev.denfry.promptcritical.sim.telemetry.TokenBucket;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Сервер завода (A5, уровни 3–4 ISA-95): раз в секунду собирает телеизмерения со всех шлюзов телемеханики в
 * {@link #RADIUS} блоках, считает показатели завода и раскладывает задание на выработку между генераторами по
 * равенству приростных стоимостей ({@link EconomicDispatch}). Уставки — рекомендация: сервер не крутит задвижки
 * машин, их выставляет игрок или его программа CC (периферия {@code pc_mes}).
 *
 * <p>Генератор — машина, у которой на щите есть прибор в кВт или МВт без слова «нагрузка/потребление» в надписи;
 * такие приборы с этими словами считаются нагрузкой. Располагаемая мощность агрегата — наибольшая замеченная выработка.
 * Кривая затрат и выброс — по классу топлива из имени машины ({@link #fuelClass}); цена продажи и плата за выбросы —
 * постоянные {@link #TARIFF} и {@link #CO2_FEE}.
 */
public final class MesServerBlockEntity extends DeviceBlockEntity implements FoundryDevice, PanelHost {
    public static final int RADIUS = 16, SCAN_TICKS = 20;
    /** Цена электроэнергии, CR/МВт·ч, и плата за выбросы, CR/т CO2. */
    public static final double TARIFF = 40, CO2_FEE = 25;

    /** Класс топлива: линейная и квадратичная (на долю мощности) части затрат и удельный выброс. */
    record Fuel(double b, double cPerRated, double emission) {
    }

    private record Unit(String id, String type, double outputMw) {
    }

    private final ScanCycle cycle = new ScanCycle(SCAN_TICKS);
    private final Map<String, Double> rated = new LinkedHashMap<>();
    private List<Unit> units = List.of();
    private Map<String, Double> setpoints = Map.of();
    private double generation, load, costRate, lambda, efficiency = 1;
    private int gateways;
    /** Задание на выработку, МВт; NaN — следовать нагрузке. */
    private double target = Double.NaN;
    /**
     * Замкнутый контур (A5, 03d): сервер сам ведёт агрегаты к уставкам экономического распределения импульсами
     * «больше/меньше» на их щиты через телеуправление RTU — как вторичное регулирование с пульта. Без этого сервер
     * только советует.
     */
    private boolean auto;
    /** Кнопки уставки нагрузки на щитах агрегатов: подстройка генератора на валу, ДГ, задание ГАЭС. */
    static final String[] RAISE = {"grid.f_up", "dg.raise", "storage.more"}, LOWER = {"grid.f_down", "dg.lower", "storage.less"};
    /** Мёртвая зона: отклонение от уставки меньше 5 % номинала агрегата (или 50 кВт) не регулируется. */
    static final double DEADBAND_SHARE = 0.05, DEADBAND_MIN_MW = 0.05;
    private TokenBucket calls;

    public MesServerBlockEntity(BlockPos pos, BlockState state) {
        super(MesBlockEntities.MES_SERVER.get(), pos, state);
    }

    private long now() {
        return level == null ? 0 : level.getGameTime();
    }

    static Fuel fuelClass(String type) {
        String t = type.toLowerCase(Locale.ROOT);
        if (t.contains("wind") || t.contains("hydro") || t.contains("water_wheel") || t.contains("solar") || t.contains("tidal"))
            return new Fuel(0, 0, 0);
        if (t.contains("reactor") || t.contains("npp") || t.contains("nuclear")) return new Fuel(8, 1, 0);
        if (t.contains("gas") || t.contains("combined") || t.contains("turbine")) return new Fuel(22, 4, 0.4);
        return new Fuel(16, 6, 0.95); // уголь, мазут, дрова: паровые котлы и машины
    }

    private static boolean isPower(String unit) {
        return "kW".equalsIgnoreCase(unit) || "MW".equalsIgnoreCase(unit);
    }

    private static boolean isLoad(String label) {
        String l = label.toLowerCase(Locale.ROOT);
        return l.contains("load") || l.contains("consum") || l.contains("demand") || l.contains("draw") || l.contains("input");
    }

    @Override
    public void tickDevice() {
        if (!(level instanceof ServerLevel)) return;
        long tick = now();
        if (!cycle.due(tick)) return;
        scan();
        cycle.scanned(tick);
        BlockState st = getBlockState();
        boolean lit = gateways > 0;
        if (st.getValue(BlockStateProperties.LIT) != lit) level.setBlock(worldPosition, st.setValue(BlockStateProperties.LIT, lit), Block.UPDATE_ALL);
    }

    /** Цикл: собрать срезы шлюзов, сложить мощности, разложить задание. */
    void scan() {
        Map<String, Unit> gen = new LinkedHashMap<>();
        double loadMw = 0;
        int found = 0;
        for (TelemetryGatewayBlockEntity rtu : Ironworks.blockEntities(level, new AABB(worldPosition).inflate(RADIUS),
                TelemetryGatewayBlockEntity.class)) {
            found++;
            for (Reading r : rtu.analogReadings()) {
                if (!isPower(r.point().unit())) continue;
                double mw = "kW".equalsIgnoreCase(r.point().unit()) ? r.value() / 1000 : r.value();
                if (isLoad(r.point().label())) loadMw += Math.max(0, mw);
                else {
                    Unit u = gen.get(r.machine());
                    gen.put(r.machine(), new Unit(r.machine(), r.type(), (u == null ? 0 : u.outputMw()) + Math.max(0, mw)));
                }
            }
        }
        gateways = found;
        units = List.copyOf(gen.values());
        unitGateways.clear();
        for (TelemetryGatewayBlockEntity rtu : Ironworks.blockEntities(level, new AABB(worldPosition).inflate(RADIUS), TelemetryGatewayBlockEntity.class))
            for (Reading r : rtu.analogReadings()) unitGateways.putIfAbsent(r.machine(), rtu);
        rated.keySet().retainAll(gen.keySet());
        for (Unit u : units) rated.merge(u.id(), u.outputMw(), Math::max);
        generation = units.stream().mapToDouble(Unit::outputMw).sum();
        load = loadMw;
        dispatch();
        if (auto) regulate();
        setChanged();
    }

    private final Map<String, TelemetryGatewayBlockEntity> unitGateways = new java.util.HashMap<>();

    /** Один импульс на агрегат за цикл: к уставке, если вне мёртвой зоны. */
    private void regulate() {
        for (Unit u : units) {
            Double sp = setpoints.get(u.id());
            TelemetryGatewayBlockEntity rtu = unitGateways.get(u.id());
            if (sp == null || rtu == null) continue;
            double err = sp - u.outputMw(), band = Math.max(DEADBAND_MIN_MW, DEADBAND_SHARE * rated.getOrDefault(u.id(), 0.0));
            if (Math.abs(err) <= band) continue;
            for (String key : err > 0 ? RAISE : LOWER) {
                try {
                    rtu.press(u.id(), key);
                    break;
                } catch (TelemetryException ignored) {
                    // у этого агрегата такой кнопки нет или канал занят — следующая кнопка или следующий цикл
                }
            }
        }
    }

    public boolean auto() {
        return auto;
    }

    public void setAuto(boolean on) {
        auto = on;
        setChanged();
    }

    private List<GeneratingUnit> curves() {
        List<GeneratingUnit> out = new ArrayList<>();
        for (Unit u : units) {
            double pMax = rated.getOrDefault(u.id(), 0.0);
            Fuel f = fuelClass(u.type());
            double c = pMax > 0 ? f.cPerRated() / pMax : 0;
            out.add(new GeneratingUnit(u.id(), 0, f.b(), c, 0, pMax, f.emission()));
        }
        return out;
    }

    private void dispatch() {
        List<GeneratingUnit> curves = curves();
        costRate = 0;
        for (int i = 0; i < curves.size(); i++) costRate += curves.get(i).cost(units.get(i).outputMw());
        EconomicDispatch.Result r = EconomicDispatch.dispatch(curves, effectiveTarget(), CO2_FEE);
        lambda = r.lambda();
        Map<String, Double> sp = new LinkedHashMap<>();
        for (int i = 0; i < curves.size(); i++) sp.put(units.get(i).id(), r.output()[i]);
        setpoints = sp;
        // экономичность: затраты (с платой за выбросы) оптимального распределения той же выработки к фактическим
        EconomicDispatch.Result same = EconomicDispatch.dispatch(curves, generation, CO2_FEE);
        double best = same.cost() + CO2_FEE * emissionsOf(curves, same.output());
        double actual = costRate + CO2_FEE * emissions();
        efficiency = actual > 1e-9 ? Math.min(1, best / actual) : 1;
    }

    private static double emissionsOf(List<GeneratingUnit> curves, double[] p) {
        double s = 0;
        for (int i = 0; i < p.length; i++) s += curves.get(i).emission() * p[i];
        return s;
    }

    /** Выброс CO2 по фактической выработке, т/ч. */
    public double emissions() {
        double s = 0;
        for (Unit u : units) s += fuelClass(u.type()).emission() * u.outputMw();
        return s;
    }

    private double effectiveTarget() {
        return Double.isNaN(target) ? load : target;
    }

    private double capacity() {
        return rated.values().stream().mapToDouble(Double::doubleValue).sum();
    }

    // --- для периферии ---

    private void admit() {
        if (calls == null) calls = new TokenBucket(ServerConfig.CC_TOKEN_RATE.get(), ServerConfig.CC_TOKEN_BURST.get());
        if (!calls.tryTake(now(), 1)) throw new TelemetryException("Plant server is busy: too many requests");
    }

    /** Показатели завода. */
    public Map<String, Object> kpi() {
        admit();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("generation", generation);
        m.put("load", load);
        m.put("target", effectiveTarget());
        m.put("followsLoad", Double.isNaN(target));
        m.put("capacity", capacity());
        m.put("costRate", costRate);
        m.put("revenueRate", TARIFF * generation);
        m.put("profitRate", TARIFF * generation - costRate - CO2_FEE * emissions());
        m.put("emissions", emissions());
        m.put("efficiency", efficiency);
        m.put("lambda", lambda);
        m.put("gateways", gateways);
        m.put("_age", cycle.ageSeconds(now()));
        return m;
    }

    /** Уставки по агрегатам: id, тип, факт, уставка и располагаемая мощность, МВт. */
    public List<Map<String, Object>> dispatchTable() {
        admit();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Unit u : units) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", u.id());
            m.put("type", u.type());
            m.put("output", u.outputMw());
            m.put("setpoint", setpoints.getOrDefault(u.id(), 0.0));
            m.put("rated", rated.getOrDefault(u.id(), 0.0));
            out.add(m);
        }
        return out;
    }

    /** Задание на выработку, МВт; отрицательное — следовать нагрузке. */
    public void setTarget(double mw) {
        admit();
        if (!Double.isFinite(mw)) throw new TelemetryException("Target must be a number");
        target = mw < 0 ? Double.NaN : Math.min(mw, 1e6);
        dispatch();
        setChanged();
    }

    // --- щит ---

    @Override
    public PanelLayout panelLayout() {
        return MesPanels.SERVER;
    }

    @Override
    public void readPanel(int[] out) {
        out[MesPanels.GEN_KW] = clamp(generation * 1000);
        out[MesPanels.LOAD_KW] = clamp(load * 1000);
        out[MesPanels.TARGET_KW] = clamp(effectiveTarget() * 1000);
        out[MesPanels.COST_CR_H] = clamp(costRate);
        out[MesPanels.REVENUE_CR_H] = clamp(TARIFF * generation);
        out[MesPanels.CO2_KG_H] = clamp(emissions() * 1000);
        out[MesPanels.EFF_PERMIL] = clamp(efficiency * 1000);
        out[MesPanels.LAMBDA_CENTS] = clamp(lambda * 100);
        out[MesPanels.UNITS] = units.size();
        int f = 0;
        if (gateways > 0) f |= 1 << MesPanels.F_LINK;
        if (Double.isNaN(target)) f |= 1 << MesPanels.F_FOLLOW;
        if (effectiveTarget() > capacity() + 1e-6) f |= 1 << MesPanels.F_SHORT;
        if (auto) f |= 1 << MesPanels.F_AUTO;
        out[MesPanels.FLAGS] = f;
    }

    private static int clamp(double v) {
        return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, Math.round(v)));
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        double t = effectiveTarget();
        switch (id) {
            case MesPanels.BTN_DOWN -> target = Math.max(0, t * 0.9);
            case MesPanels.BTN_UP -> target = Math.min(1e6, Math.max(t * 1.1, t + 0.01));
            case MesPanels.BTN_FOLLOW -> target = Double.NaN;
            case MesPanels.BTN_AUTO -> auto = !auto;
            default -> {
                return;
            }
        }
        dispatch();
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!Double.isNaN(target)) tag.putDouble("target", target);
        tag.putBoolean("auto", auto);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        target = tag.contains("target") ? tag.getDouble("target") : Double.NaN;
        auto = tag.getBoolean("auto");
    }

    @Override
    @Nullable
    public IItemHandler items(@Nullable Direction side) {
        return null;
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return false;
    }

    @Override
    public ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /** Shift + ПКМ: сводка в строке действия. */
    @Override
    public InteractionResult use(Player player) {
        player.displayClientMessage(MesText.msg("status", gateways, units.size(), String.format(Locale.ROOT, "%.2f", generation),
                String.format(Locale.ROOT, "%.0f", efficiency * 100), MesText.cc()), true);
        return InteractionResult.SUCCESS;
    }
}
