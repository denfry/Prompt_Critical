package dev.denfry.promptcritical.sim.grid;

import dev.denfry.promptcritical.sim.core.FastContext;
import dev.denfry.promptcritical.sim.core.Severity;
import dev.denfry.promptcritical.sim.core.SimConfig;
import dev.denfry.promptcritical.sim.core.SimId;
import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;
import dev.denfry.promptcritical.sim.core.Subsystem;
import dev.denfry.promptcritical.sim.core.TimeDomain;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.SplittableRandom;
import java.util.TreeMap;

/**
 * Электросеть измерения (07 §7.1–7.6, этап 8 M3): двойники устройств мира ({@link GridUnit}) и пролёты ВЛ
 * ({@link LineUnit}) собираются в одну {@link PowerGrid}. Узлы — ключи связных частей кабелей и шин, которые считает
 * мод; класс шины задают генераторы, обмотки трансформаторов, ячейки КРУ и щиты 0,4 кВ (наибольший из них).
 *
 * <p>Любая смена схемы (выводы, устройства, пролёты, касание провода) пересобирает сеть в начале следующего тика:
 * состояние машин, выключателей, двигателей и нагрев переносятся, электрические переходные — нет
 * ({@link PowerGrid#startCold}). Команды игрока (выключатели, возбуждение, пускатели) — прямые вызовы юнитов на
 * серверном потоке между тиками; ход выключателя, начатый до пересборки, завершается мгновенно.
 */
public final class GridSubsystem implements Subsystem, GridEventSink {
    public static final String ID = "power_grid";
    /** Вехи для достижений и маршрута (11): включение на параллельную работу, действие РЗА, отключение АЧР. */
    public static final String SYNC_CLOSED = "promptcritical:sync_closed", RELAY_OPERATED = "promptcritical:relay_operated",
            LOAD_SHED = "promptcritical:load_shed";
    /** Фаза SLOW-шага нагрева проводов и трансформаторов. */
    static final int SLOW_PHASE = 5;

    private GridConfig cfg;
    private final NavigableMap<SimId, GridUnit> units = new TreeMap<>();
    private final NavigableMap<Long, LineUnit> lines = new TreeMap<>();
    private final Map<Long, BusId> buses = new HashMap<>();
    private final GridOutputs out = new GridOutputs();
    private final Inputs inputs = new Inputs();
    private final SplittableRandom rng = new SplittableRandom(0x5f3759dfL);
    private PowerGrid grid;
    private boolean dirty = true;
    private GeneratorUnit[] machineUnits = new GeneratorUnit[0];
    private Object[] branchOwners = new Object[0];
    private ConsumerUnit[] loadUnits = new ConsumerUnit[0];
    private MotorUnit[] motorUnits = new MotorUnit[0];
    private GridUnit[] relayOwners = new GridUnit[0];
    private double ambientC = 15, convectionWm2K = 15, solarWPerM;
    private FastContext ctx;
    private int rebuilds;

    public GridSubsystem(GridConfig cfg) {
        this.cfg = Objects.requireNonNull(cfg, "cfg");
    }

    @Override
    public String id() {
        return ID;
    }

    public GridConfig config() {
        return cfg;
    }

    // ================================================================ двойники

    public GeneratorUnit generator(SimId id) {
        return unit(id, GeneratorUnit.class, () -> new GeneratorUnit(id));
    }

    public SwitchUnit switchUnit(SimId id, SwitchUnit.Kind kind) {
        GridUnit u = units.get(id);
        if (u instanceof SwitchUnit s && s.kind == kind) return s;
        return add(new SwitchUnit(id, kind));
    }

    public TransformerUnit transformer(SimId id) {
        return unit(id, TransformerUnit.class, () -> new TransformerUnit(id));
    }

    public MotorUnit motor(SimId id) {
        return unit(id, MotorUnit.class, () -> new MotorUnit(id));
    }

    public ConsumerUnit consumer(SimId id) {
        return unit(id, ConsumerUnit.class, () -> new ConsumerUnit(id));
    }

    /** Вход блока питания автоматики от шины 0,4 кВ (этап 9). */
    public AuxSupplyUnit auxSupply(SimId id) {
        return unit(id, AuxSupplyUnit.class, () -> new AuxSupplyUnit(id));
    }

    public ConnectorUnit connector(SimId id, boolean mediumVoltage) {
        GridUnit u = units.get(id);
        if (u instanceof ConnectorUnit c && c.mediumVoltage == mediumVoltage) return c;
        return add(new ConnectorUnit(id, mediumVoltage));
    }

    /** Двойник устройства; {@code null} — его нет. */
    public GridUnit unit(SimId id) {
        return units.get(id);
    }

    public Collection<GridUnit> units() {
        return Collections.unmodifiableCollection(units.values());
    }

    private <U extends GridUnit> U unit(SimId id, Class<U> type, java.util.function.Supplier<U> create) {
        GridUnit u = units.get(id);
        return type.isInstance(u) ? type.cast(u) : add(create.get());
    }

    private <U extends GridUnit> U add(U u) {
        u.owner = this;
        units.put(u.id, u);
        markDirty();
        return u;
    }

    @Override
    public void onDeviceRemoved(SimId device) {
        if (units.remove(device) != null) markDirty();
        if (lines.values().removeIf(l -> l.a.equals(device) || l.b.equals(device))) markDirty();
    }

    // ================================================================ пролёты ВЛ

    /** Натянуть пролёт между коннекторами {@code a} и {@code b} (ключ — его выбирает мир). */
    public LineUnit putLine(long key, SimId a, SimId b, String conductor, boolean insulated, double lengthM) {
        LineUnit l = new LineUnit(key, a, b, conductor, insulated, lengthM);
        LineUnit old = lines.put(key, l);
        if (old != null) {
            l.carried = old.carried;
            l.touchAt = old.touchAt;
        }
        markDirty();
        return l;
    }

    public void removeLine(long key) {
        if (lines.remove(key) != null) markDirty();
    }

    public LineUnit line(long key) {
        return lines.get(key);
    }

    public Collection<LineUnit> lines() {
        return Collections.unmodifiableCollection(lines.values());
    }

    /** Блок в габарите пролёта (точка вдоль пролёта 0…1) или NaN — габарит свободен. */
    public void setLineTouch(long key, double at) {
        LineUnit l = lines.get(key);
        if (l == null) return;
        boolean was = l.touching(), now = !Double.isNaN(at);
        if (was == now && (!now || Math.abs(l.touchAt - at) < 1e-9)) return;
        l.touchAt = at;
        if (!l.insulated) markDirty();
    }

    /**
     * Молния в пролёт (§7.15.1): перекрытие изоляции — однофазное КЗ, самоустраняющееся с вероятностью
     * {@code grid.ar_success_lightning} (гаснет в бестоковую паузу АПВ). {@code false} — пролёт не в сети.
     */
    public boolean strikeLine(long key, double at) {
        LineUnit l = lines.get(key);
        if (l == null || l.branch == null || !ready()) return false;
        boolean selfClearing = rng.nextDouble() < cfg.arSuccessLightning();
        grid.applyBranchFault(l.branch, Math.max(0.01, Math.min(0.99, at)), FaultType.SINGLE_LINE_GROUND, 0, selfClearing);
        return true;
    }

    /** Температура провода пролёта, °C. */
    public double lineTemperatureC(long key) {
        LineUnit l = lines.get(key);
        if (l == null) return 20;
        if (l.branch != null && ready()) return grid.branchTemperatureC(l.branch);
        return l.carried != null ? l.carried[4] : 20;
    }

    public double lineCurrentA(long key) {
        LineUnit l = lines.get(key);
        return l != null && l.branch != null && ready() ? grid.branchCurrentA(l.branch) : 0;
    }

    /** Погода для нагрева проводов: температура воздуха, теплоотдача (ветер, дождь), солнце. */
    public void setWeather(double ambientC, double convectionWm2K, double solarWPerM) {
        this.ambientC = ambientC;
        this.convectionWm2K = convectionWm2K;
        this.solarWPerM = solarWPerM;
        if (grid != null) grid.setWeather(ambientC, convectionWm2K, solarWPerM);
    }

    // ================================================================ состояние сети

    void markDirty() {
        dirty = true;
    }

    /** Сеть собрана по нынешней схеме: юниты читают её напрямую. */
    public boolean ready() {
        return grid != null && !dirty;
    }

    /** Модель сети (тесты и отладка); {@code null} до первой сборки. */
    public PowerGrid grid() {
        return grid;
    }

    /** Сколько раз сеть пересобиралась. */
    public int rebuilds() {
        return rebuilds;
    }

    BusId busOf(long node) {
        return buses.get(node);
    }

    /** Линейное напряжение узла, В (0 — без источника или узла нет в сети). */
    public double voltageV(long node) {
        BusId b = ready() ? buses.get(node) : null;
        return b == null ? 0 : grid.busVoltageV(b);
    }

    public double frequencyHz(long node) {
        BusId b = ready() ? buses.get(node) : null;
        return b == null ? 0 : grid.busFrequencyHz(b);
    }

    SplittableRandom random() {
        return rng;
    }

    /** Выключатель (не разъединитель) на общем узле с {@code d} включён — оперативная блокировка (§7.4.6). */
    boolean adjacentBreakerClosed(SwitchUnit d) {
        for (GridUnit u : units.values()) {
            if (!(u instanceof SwitchUnit s) || s == d) continue;
            if (s.kind != SwitchUnit.Kind.MV_BREAKER && s.kind != SwitchUnit.Kind.LV_BREAKER) continue;
            boolean shared = false;
            for (int t = 0; t < 2; t++) {
                long n = s.node(t);
                shared |= n != GridUnit.NO_NODE && (n == d.node(0) || n == d.node(1));
            }
            if (shared && s.closed()) return true;
        }
        return false;
    }

    // ================================================================ тик

    @Override
    public void stepFast(FastContext ctx) {
        this.ctx = ctx;
        if (dirty) rebuild();
        double dt = ctx.dt();
        long tick = ctx.tick();
        for (GridUnit u : units.values()) if (u instanceof GeneratorUnit g) g.beforeTick(grid, dt, tick);
        if (grid.busCount() > 0) {
            grid.stepFast(dt, inputs, out);
            grid.drainEvents(this);
            if (ctx.clock().due(TimeDomain.SLOW, SLOW_PHASE)) grid.stepSlow(ctx.clock().slowDtPhys());
            for (GridUnit u : units.values()) if (u instanceof SwitchUnit s) s.meter(grid, dt);
        }
        this.ctx = null;
    }

    /** Итоги последнего тика (баланс мощности, острова). */
    public GridOutputs outputs() {
        return out;
    }

    private void rebuild() {
        if (grid != null) {
            for (GridUnit u : units.values()) u.capture(grid);
            for (LineUnit l : lines.values()) l.capture(grid);
        }
        for (GridUnit u : units.values()) u.unbind();
        for (LineUnit l : lines.values()) l.branch = null;
        grid = new PowerGrid(cfg);
        grid.setRandomSeed(rng.nextLong());
        buses.clear();
        TreeMap<Long, VoltageClass> defined = new TreeMap<>(), fallback = new TreeMap<>();
        for (GridUnit u : units.values()) {
            for (int t = 0; t < u.terminals(); t++) {
                long n = u.node(t);
                if (n == GridUnit.NO_NODE) continue;
                classify(u.definesClass(t) ? defined : fallback, n, u.terminalClass(t));
            }
        }
        fallback.putAll(defined);
        for (Map.Entry<Long, VoltageClass> e : fallback.entrySet())
            buses.put(e.getKey(), grid.addBus(e.getValue(), Long.toHexString(e.getKey())));
        Build b = new Build(grid, cfg, buses);
        for (GridUnit u : units.values()) u.build(b);
        for (LineUnit l : lines.values()) {
            l.classMismatch = false;
            if (l.broken || !(units.get(l.a) instanceof ConnectorUnit ca) || !(units.get(l.b) instanceof ConnectorUnit cb))
                continue;
            long na = ca.node(0), nb = cb.node(0);
            if (na == GridUnit.NO_NODE || nb == GridUnit.NO_NODE || na == nb) continue;
            VoltageClass va = fallback.get(na), vb = fallback.get(nb);
            if (va != vb) {
                l.classMismatch = true;
                continue;
            }
            l.build(b, va, buses.get(na), buses.get(nb));
        }
        for (GridUnit u : units.values()) mountRelays(b, u);
        grid.setWeather(ambientC, convectionWm2K, solarWPerM);
        grid.startCold();
        machineUnits = b.machines.toArray(new GeneratorUnit[0]);
        branchOwners = b.branches.toArray();
        loadUnits = b.loads.toArray(new ConsumerUnit[0]);
        motorUnits = b.motors.toArray(new MotorUnit[0]);
        relayOwners = b.relays.toArray(new GridUnit[0]);
        dirty = false;
        rebuilds++;
    }

    private static void classify(Map<Long, VoltageClass> m, long node, VoltageClass vc) {
        m.merge(node, vc, (x, y) -> x.volts >= y.volts ? x : y);
    }

    private void mountRelays(Build b, GridUnit u) {
        for (RelaySetting s : u.relays) {
            ProtectionRelay relay = s.toRelay(cfg);
            RelayMount mount = mount(u, s);
            if (relay == null || mount == null) continue;
            try {
                grid.addRelay(relay, mount);
                b.relays.add(u);
            } catch (IllegalArgumentException | IllegalStateException e) {
                // модуль не применим к присоединению: в шкафу он стоит, но не работает
            }
        }
    }

    private RelayMount mount(GridUnit u, RelaySetting s) {
        switch (u) {
            case SwitchUnit sw -> {
                if (sw.branch == null || s.kind().consumerOnly()) return null;
                if (sw.kind != SwitchUnit.Kind.MV_BREAKER && sw.kind != SwitchUnit.Kind.LV_BREAKER) return null;
                if (s.kind() == RelaySetting.Kind.REVERSE_POWER) {
                    for (GridUnit o : units.values()) {
                        if (o instanceof GeneratorUnit g && g.machine != null
                                && (g.node(0) == sw.node(1) || g.node(0) == sw.node(0)))
                            return new RelayMount.Generator(g.machine, sw.branch);
                    }
                    return null;
                }
                return new RelayMount.Feeder(sw.branch, true);
            }
            case MotorUnit m -> {
                return m.motor == null ? null : new RelayMount.Motor(m.motor);
            }
            case ConsumerUnit c -> {
                return c.load == null || s.kind() == RelaySetting.Kind.THERMAL ? null : new RelayMount.Load(c.load);
            }
            default -> {
                return null;
            }
        }
    }

    // ================================================================ события сети

    @Override
    public void syncClosed(SyncClosing c) {
        GeneratorUnit g = c.machine() < machineUnits.length ? machineUnits[c.machine()] : null;
        if (g == null) return;
        g.lastSync = c;
        g.syncCount++;
        g.pendingShockW += c.createShockW();
        if (ctx == null) return;
        Severity sev = c.result() == SyncResult.OK ? Severity.INFO : Severity.WARNING;
        ctx.events().journal(ctx.tick(), g.id, sev, String.format(java.util.Locale.ROOT,
                "generator synchronized: angle %.0f deg, slip %.3f Hz, torque %.2f pu, %s",
                Math.toDegrees(c.scope().angleRad()), c.scope().slipHz(), c.torquePu(), c.result()));
        ctx.events().milestone(ctx.tick(), g.id, SYNC_CLOSED, c.result().name().toLowerCase(java.util.Locale.ROOT),
                c.torquePu());
    }

    @Override
    public void relayOperated(int relay, ProtectionFunction function) {
        GridUnit u = relay < relayOwners.length ? relayOwners[relay] : null;
        if (u == null) return;
        if (u instanceof SwitchUnit s) s.trips++;
        if (ctx == null) return;
        ctx.events().journal(ctx.tick(), u.id, Severity.WARNING, "protection " + function + " operated");
        ctx.events().milestone(ctx.tick(), u.id, RELAY_OPERATED, function.name(), 1);
    }

    @Override
    public void loadSwitched(int load, boolean connected) {
        ConsumerUnit c = load < loadUnits.length ? loadUnits[load] : null;
        if (c == null) return;
        if (connected) {
            c.shed = false;
            return;
        }
        c.shed = true;
        c.shedCount++;
        if (ctx != null) ctx.events().milestone(ctx.tick(), c.id, LOAD_SHED, "ufls", c.demandW() / 1e3);
    }

    @Override
    public void conductorBroken(int branch) {
        if (branch < branchOwners.length && branchOwners[branch] instanceof LineUnit l) {
            l.broken = true;
            markDirty();
            if (ctx != null) ctx.events().journal(ctx.tick(), l.a, Severity.WARNING, "overhead conductor annealed and broke");
        }
    }

    @Override
    public void insulationBreakdown(int bus) {
        if (ctx != null) ctx.events().journal(ctx.tick(), SimId.NONE, Severity.CRITICAL,
                "insulation breakdown on bus " + grid.busLabel(new BusId(bus)) + ": equipment of a lower voltage class");
    }

    @Override
    public void poleSlip(int machine) {
        GeneratorUnit g = machine < machineUnits.length ? machineUnits[machine] : null;
        if (g != null && ctx != null) ctx.events().journal(ctx.tick(), g.id, Severity.WARNING, "pole slip: generator out of step");
    }

    @Override
    public void breakerStateChanged(int branch, BreakerState state) {
        if (state == BreakerState.FAILED_TO_INTERRUPT && ctx != null && branch < branchOwners.length
                && branchOwners[branch] instanceof GridUnit u)
            ctx.events().journal(ctx.tick(), u.id, Severity.CRITICAL, "breaker failed to interrupt the fault current");
    }

    /** Входы тика от Create: мощность вала генераторов, момент нагрузки двигателей. Без аллокаций. */
    private final class Inputs implements GridInputs {
        @Override
        public double createShaftPowerW(int machine) {
            GeneratorUnit g = machine < machineUnits.length ? machineUnits[machine] : null;
            return g == null ? 0 : g.shaftPower(grid.omega[machine]);
        }

        @Override
        public double motorLoadTorquePu(int motor) {
            MotorUnit m = motor < motorUnits.length ? motorUnits[motor] : null;
            return m == null ? 0 : m.loadTorquePu();
        }
    }

    /** Сборка сети: юниты добавляют элементы и регистрируют их для событий. */
    static final class Build {
        final PowerGrid grid;
        final GridConfig cfg;
        private final Map<Long, BusId> buses;
        final List<GeneratorUnit> machines = new ArrayList<>();
        final List<Object> branches = new ArrayList<>();
        final List<ConsumerUnit> loads = new ArrayList<>();
        final List<MotorUnit> motors = new ArrayList<>();
        final List<GridUnit> relays = new ArrayList<>();

        Build(PowerGrid grid, GridConfig cfg, Map<Long, BusId> buses) {
            this.grid = grid;
            this.cfg = cfg;
            this.buses = buses;
        }

        BusId bus(long node) {
            return Objects.requireNonNull(buses.get(node), "node");
        }

        void machine(MachineId id, GeneratorUnit u) {
            put(machines, id.index(), u);
        }

        void branch(BranchId id, Object owner) {
            put(branches, id.index(), owner);
        }

        void load(LoadId id, ConsumerUnit u) {
            put(loads, id.index(), u);
        }

        void motor(MotorId id, MotorUnit u) {
            put(motors, id.index(), u);
        }

        private static <T> void put(List<T> list, int index, T value) {
            while (list.size() <= index) list.add(null);
            list.set(index, value);
        }
    }

    // ================================================================ конфиг и сохранение

    @Override
    public void onConfigReload(SimConfig config) {
        GridConfig next = config.section(GridConfig.class, cfg);
        if (!next.equals(cfg)) {
            cfg = next;
            markDirty();
        }
    }

    @Override
    public void save(StateWriter w) {
        if (grid != null) {
            for (GridUnit u : units.values()) u.capture(grid);
            for (LineUnit l : lines.values()) l.capture(grid);
        }
        w.putDouble("ambient_c", ambientC);
        w.putDouble("convection", convectionWm2K);
        w.putDouble("solar", solarWPerM);
        w.putInt("units", units.size());
        int i = 0;
        for (GridUnit u : units.values()) {
            w.beginObject("u" + i++);
            w.putLong("id", u.id.raw());
            w.putString("type", typeOf(u));
            u.save(w);
            w.endObject();
        }
        w.putInt("lines", lines.size());
        i = 0;
        for (LineUnit l : lines.values()) {
            w.beginObject("l" + i++);
            l.save(w);
            w.endObject();
        }
    }

    private static String typeOf(GridUnit u) {
        return switch (u) {
            case GeneratorUnit g -> "generator";
            case SwitchUnit s -> "switch:" + s.kind.name();
            case TransformerUnit t -> "transformer";
            case MotorUnit m -> "motor";
            case ConsumerUnit c -> "consumer";
            case ConnectorUnit c -> c.mediumVoltage ? "connector:mv" : "connector:lv";
            case AuxSupplyUnit a -> "aux_supply";
        };
    }

    @Override
    public void load(StateReader r) {
        units.clear();
        lines.clear();
        grid = null;
        dirty = true;
        ambientC = r.optDouble("ambient_c", 15);
        convectionWm2K = r.optDouble("convection", 15);
        solarWPerM = r.optDouble("solar", 0);
        int n = r.optInt("units", 0);
        for (int i = 0; i < n; i++) {
            r.beginObject("u" + i);
            SimId id = new SimId(r.getLong("id"));
            String type = r.getString("type");
            GridUnit u = switch (type) {
                case "generator" -> new GeneratorUnit(id);
                case "transformer" -> new TransformerUnit(id);
                case "motor" -> new MotorUnit(id);
                case "consumer" -> new ConsumerUnit(id);
                case "connector:mv" -> new ConnectorUnit(id, true);
                case "connector:lv" -> new ConnectorUnit(id, false);
                case "aux_supply" -> new AuxSupplyUnit(id);
                default -> type.startsWith("switch:") ? new SwitchUnit(id, SwitchUnit.Kind.valueOf(type.substring(7))) : null;
            };
            if (u != null) {
                u.load(r);
                u.owner = this;
                units.put(id, u);
            }
            r.endObject();
        }
        int nl = r.optInt("lines", 0);
        for (int i = 0; i < nl; i++) {
            r.beginObject("l" + i);
            LineUnit l = LineUnit.load(r);
            lines.put(l.key, l);
            r.endObject();
        }
    }
}
