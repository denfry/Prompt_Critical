package dev.denfry.promptcritical.sim.automation;

import dev.denfry.promptcritical.sim.core.FastContext;
import dev.denfry.promptcritical.sim.core.SimClock;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.TreeMap;

/**
 * Автоматика A1–A2 измерения (03d §3d.3–3d.4, этап 9 M3): двойники блоков мира ({@link AutomationUnit}) собираются в
 * один {@link RelayBank} и один {@link AnalogBank}. Цепи — ключи связных частей проводов {@code wiring_tool}, которые
 * считает мод: клеммы DISCRETE одной цепи — один узел A1; на цепи A2 сигнал задаёт её единственная выходная клемма
 * (две — {@code DRIVER_CONFLICT}: входы цепи читают 0 и качество BAD).
 *
 * <p>Мосты между уровнями: дискретные входы блоков A2 (режим интегратора, «принуд. РУЧ») — оптроны 0,5 Вт между
 * клеммой и «0» блока; выход компаратора и режим станции — ключи A1 со следующего тика. Банки только растут, поэтому
 * любая смена схемы пересобирает их целиком в начале следующего тика с переносом памяти элементов (состояние якоря,
 * износ, выдержка, индивидуальные погрешности). Предел станции GDD ({@code relays.max_per_station}) действует на
 * всё измерение — в мире один банк [моё: поднят до 4096].
 */
public final class AutomationSubsystem implements Subsystem {
    public static final String ID = "automation";
    /** Фаза SLOW-шага отказов и дрейфа. */
    static final int SLOW_PHASE = 7;
    /** Ёмкость банков измерения. */
    static final int WORLD_LIMIT = 4096;
    /** Механизм под током от схемы из ≥ {@link #RELAY_LOGIC_MIN} реле (11 §11.2.2 «Щёлк-щёлк»); значение — число реле. */
    public static final String RELAY_LOGIC = "promptcritical:relay_logic";
    /** Станция в АВТ с интегратором в схеме держит PV у SP {@link #LOOP_HOLD_S} (11 §11.2.2 «Без компьютера»); значение — ч. */
    public static final String ANALOG_LOOP_HELD = "promptcritical:analog_loop_held";
    public static final int RELAY_LOGIC_MIN = 5;
    /** Допуск |PV − SP|, В (2 % шкалы 10 В), и выдержка — игровые сутки [моё]. */
    public static final double LOOP_BAND_V = 0.2, LOOP_HOLD_S = 1200;

    private AutomationConfig cfg;
    private final NavigableMap<SimId, AutomationUnit> units = new TreeMap<>();
    private final SplittableRandom rng = new SplittableRandom(0x6a09e667f3bcc909L);
    private RelayBank relay;
    private AnalogBank analog;
    private boolean dirty = true;
    private int rebuilds;
    private AutomationUnit[] order = new AutomationUnit[0];
    // A1 → A2: нагрузка-оптрон и сигнал; A2 → A1: ключ, сигнал, инверсия
    private int[] optoLoad = new int[0], optoSignal = new int[0];
    private int[] bridgeSwitch = new int[0], bridgeSignal = new int[0];
    private boolean[] bridgeInvert = new boolean[0];
    // достижения: механизмы и реле их схем, станции с интегратором в схеме и выдержка у уставки
    private AutomationUnit[] actuators = new AutomationUnit[0];
    private int[] actuatorRelays = new int[0];
    private boolean[] actuatorLit = new boolean[0];
    private AutomationUnit[] loopStations = new AutomationUnit[0];
    private double[] loopHeldS = new double[0];

    public AutomationSubsystem(AutomationConfig cfg) {
        this.cfg = Objects.requireNonNull(cfg, "cfg");
    }

    @Override
    public String id() {
        return ID;
    }

    public AutomationConfig config() {
        return cfg;
    }

    // ================================================================ двойники

    /** Двойник блока вида {@code kind}; блок другого вида с тем же id заменяется. */
    public AutomationUnit unit(SimId id, AutomationKind kind) {
        AutomationUnit u = units.get(id);
        if (u != null && u.kind == kind) return u;
        u = new AutomationUnit(id, kind);
        u.owner = this;
        units.put(id, u);
        markDirty();
        return u;
    }

    /** Двойник; {@code null} — его нет. */
    public AutomationUnit unit(SimId id) {
        return units.get(id);
    }

    public Collection<AutomationUnit> units() {
        return Collections.unmodifiableCollection(units.values());
    }

    @Override
    public void onDeviceRemoved(SimId device) {
        if (units.remove(device) != null) markDirty();
    }

    void markDirty() {
        dirty = true;
    }

    /** Банки собраны по нынешней схеме. */
    public boolean ready() {
        return relay != null && !dirty;
    }

    RelayBank relay() {
        return relay;
    }

    AnalogBank analog() {
        return analog;
    }

    public int rebuilds() {
        return rebuilds;
    }

    // ================================================================ тик

    @Override
    public void stepFast(FastContext ctx) {
        if (dirty) rebuild();
        for (AutomationUnit u : order) applyInputs(u);
        relay.stepFast();
        observeAchievements(ctx);
        for (int i = 0; i < optoLoad.length; i++) analog.setSignal(optoSignal[i], relay.lampLit(optoLoad[i]) ? 1 : 0);
        analog.stepFast();
        for (int i = 0; i < bridgeSwitch.length; i++)
            relay.setSwitch(bridgeSwitch[i], (analog.signal(bridgeSignal[i]) > 0.5) != bridgeInvert[i]);
        if (ctx.clock().due(TimeDomain.SLOW, SLOW_PHASE)) {
            double dt = ctx.clock().slowDtPhys();
            relay.stepSlow(dt);
            analog.stepSlow(dt);
        }
    }

    /** Входы мира в банки: кнопки, ключи, процесс, вал, питание, механизмы. Без аллокаций. */
    private void applyInputs(AutomationUnit u) {
        if (u.element < 0 && u.switches.length == 0 && u.signals.length == 0) return;
        double x = u.in[AutomationUnit.IN_MAIN];
        int[] sw = u.switches;
        switch (u.kind) {
            case PUSHBUTTON -> {
                relay.setSwitch(sw[0], x > 0);
                relay.setSwitch(sw[1], x <= 0);
            }
            case SELECTOR_SWITCH -> {
                for (int i = 0; i < sw.length; i++) relay.setSwitch(sw[i], u.ip[AutomationUnit.I_POSITION] == i);
            }
            case KEY_SWITCH -> {
                relay.setSwitch(sw[0], x > 0);
                relay.setSwitch(sw[1], u.in[AutomationUnit.IN_AUX] > 0);
            }
            case PROCESS_SWITCH -> relay.setProcessValue(u.element, x);
            case CAM_CONTROLLER -> relay.setCamShaftRpm(u.element, x);
            case POWER_SUPPLY -> relay.setSupplyInput(u.element, Math.max(0, x));
            case REDSTONE_INTERFACE, MOTOR_IO, CONSUMER_IO -> relay.setSwitch(sw[0], x > 0);
            case BREAKER_IO -> {
                relay.setSwitch(sw[0], x > 0);
                relay.setSwitch(sw[1], x <= 0);
            }
            case GAS_DETECTOR -> {
                boolean warn = x >= u.dp[AutomationUnit.D_SET], alarm = x >= u.dp[AutomationUnit.D_RETURN];
                relay.setSwitch(sw[0], !warn);
                relay.setSwitch(sw[1], warn);
                relay.setSwitch(sw[2], !alarm);
                relay.setSwitch(sw[3], alarm);
                analog.setSignal(u.signals[0], Math.max(3.8, Math.min(20.5, 4 + 0.16 * x)));
            }
            case MANUAL_STATION -> analog.setManualRamp(u.element, (int) Math.signum(x));
            default -> {
            }
        }
    }

    // ================================================================ сборка

    private void rebuild() {
        if (relay != null) for (AutomationUnit u : units.values()) capture(u);
        for (AutomationUnit u : units.values()) u.unbind();
        relay = new RelayBank(cfg, SimClock.FAST_DT, rng.nextLong());
        analog = new AnalogBank(cfg, SimClock.FAST_DT, rng.nextLong());
        Build b = new Build();
        List<AutomationUnit> built = new ArrayList<>();
        for (AutomationUnit u : units.values()) {
            try {
                build(b, u);
                built.add(u);
            } catch (IllegalArgumentException | IllegalStateException e) {
                u.unbind(); // уставка вне диапазона или банк полон: блок стоит, но не работает
            }
        }
        for (AutomationUnit u : built) wireAnalog(b, u);
        for (AutomationUnit u : built) restore(u);
        order = built.toArray(new AutomationUnit[0]);
        optoLoad = b.optoLoad.stream().mapToInt(Integer::intValue).toArray();
        optoSignal = b.optoSignal.stream().mapToInt(Integer::intValue).toArray();
        bridgeSwitch = b.bridgeSwitch.stream().mapToInt(Integer::intValue).toArray();
        bridgeSignal = b.bridgeSignal.stream().mapToInt(Integer::intValue).toArray();
        bridgeInvert = new boolean[b.bridgeInvert.size()];
        for (int i = 0; i < bridgeInvert.length; i++) bridgeInvert[i] = b.bridgeInvert.get(i);
        indexSchemes(built);
        dirty = false;
        rebuilds++;
    }

    /** Схемы — связные части цепей через блоки (все клеммы блока — одна схема): механизмы, их реле, станции с интегратором. */
    private void indexSchemes(List<AutomationUnit> built) {
        Map<Long, Long> parent = new HashMap<>();
        for (AutomationUnit u : built) {
            long first = AutomationUnit.NO_NET;
            for (int t = 0; t < u.kind.terminalCount(); t++) {
                long n = u.net(t);
                if (n == AutomationUnit.NO_NET) continue;
                if (first == AutomationUnit.NO_NET) first = n;
                else parent.put(find(parent, n), find(parent, first));
            }
        }
        Map<Long, Integer> relays = new HashMap<>(), integrators = new HashMap<>();
        for (AutomationUnit u : built) {
            long root = scheme(parent, u);
            if (root == AutomationUnit.NO_NET) continue;
            switch (u.kind) {
                case RELAY, RELAY_LATCHING, TIME_RELAY -> relays.merge(root, 1, Integer::sum);
                case ANALOG_INTEGRATOR -> integrators.merge(root, 1, Integer::sum);
                default -> {
                }
            }
        }
        List<AutomationUnit> acts = new ArrayList<>(), stations = new ArrayList<>();
        for (AutomationUnit u : built) {
            long root = scheme(parent, u);
            switch (u.kind) {
                case REDSTONE_INTERFACE, MOTOR_IO, BREAKER_IO -> {
                    if (relays.getOrDefault(root, 0) >= RELAY_LOGIC_MIN) acts.add(u);
                }
                case MANUAL_STATION -> {
                    if (integrators.getOrDefault(root, 0) > 0) stations.add(u);
                }
                default -> {
                }
            }
        }
        actuators = acts.toArray(new AutomationUnit[0]);
        actuatorRelays = new int[actuators.length];
        actuatorLit = new boolean[actuators.length];
        for (int i = 0; i < actuators.length; i++) {
            actuatorRelays[i] = relays.get(scheme(parent, actuators[i]));
            actuatorLit[i] = energized(actuators[i]);
        }
        // выдержка станций не переживает пересборку схемы [моё]
        loopStations = stations.toArray(new AutomationUnit[0]);
        loopHeldS = new double[loopStations.length];
    }

    private static long scheme(Map<Long, Long> parent, AutomationUnit u) {
        for (int t = 0; t < u.kind.terminalCount(); t++) if (u.net(t) != AutomationUnit.NO_NET) return find(parent, u.net(t));
        return AutomationUnit.NO_NET;
    }

    private static long find(Map<Long, Long> parent, long n) {
        long r = n;
        for (Long p = parent.get(r); p != null && p != r; p = parent.get(r)) r = p;
        return r;
    }

    private static boolean energized(AutomationUnit u) {
        return u.kind == AutomationKind.BREAKER_IO ? u.loadEnergized(1) || u.loadEnergized(2) : u.loadEnergized(0);
    }

    private void observeAchievements(FastContext ctx) {
        for (int i = 0; i < actuators.length; i++) {
            boolean lit = energized(actuators[i]);
            if (lit && !actuatorLit[i]) {
                ctx.events().milestone(ctx.tick(), actuators[i].id, RELAY_LOGIC, "relays", actuatorRelays[i]);
            }
            actuatorLit[i] = lit;
        }
        for (int i = 0; i < loopStations.length; i++) {
            AutomationUnit st = loopStations[i];
            double pv = st.stationPv();
            boolean held = st.stationMode() == StationMode.AUTO && !Double.isNaN(pv)
                    && Math.abs(pv - st.output(1)) <= LOOP_BAND_V;
            double before = loopHeldS[i];
            loopHeldS[i] = held ? before + SimClock.FAST_DT : 0;
            if (before < LOOP_HOLD_S && loopHeldS[i] >= LOOP_HOLD_S) {
                ctx.events().milestone(ctx.tick(), st.id, ANALOG_LOOP_HELD, "hours", loopHeldS[i] / 3600);
            }
        }
    }

    /** Узлы A1, источники сигналов A2 и мосты собираемой схемы. */
    private final class Build {
        final Map<Long, Integer> nodes = new HashMap<>();
        final Map<Long, Integer> drivers = new HashMap<>();
        final Set<Long> conflicts = new HashSet<>();
        final List<Integer> optoLoad = new ArrayList<>(), optoSignal = new ArrayList<>();
        final List<Integer> bridgeSwitch = new ArrayList<>(), bridgeSignal = new ArrayList<>();
        final List<Boolean> bridgeInvert = new ArrayList<>();

        /** Узел A1 клеммы: цепь мира или своя (не подключена). */
        int node(AutomationUnit u, String label) {
            int t = u.kind.indexOf(label);
            long key = u.net(t);
            if (key == AutomationUnit.NO_NET) return relay.newNode();
            return nodes.computeIfAbsent(key, k -> relay.newNode());
        }

        /** Выходная клемма {@code label} задаёт сигнал своей цепи. */
        void drive(AutomationUnit u, String label, int signal) {
            long key = u.net(u.kind.indexOf(label));
            if (key == AutomationUnit.NO_NET) return;
            if (drivers.putIfAbsent(key, signal) != null) conflicts.add(key);
        }

        /** Сигнал цепи входной клеммы; -1 — нет источника или конфликт. */
        int source(AutomationUnit u, String label) {
            long key = u.net(u.kind.indexOf(label));
            if (key == AutomationUnit.NO_NET) return -1;
            if (conflicts.contains(key)) {
                u.conflict = true;
                return -1;
            }
            return drivers.getOrDefault(key, -1);
        }

        /** Дискретный вход блока A2: оптрон между клеммой и «0», сигнал 0/1. */
        int opto(AutomationUnit u, String label) {
            int load = relay.addLamp(node(u, label), node(u, "0"), AutomationUnit.OPTO_W);
            int s = analog.newSignal();
            optoLoad.add(load);
            optoSignal.add(s);
            return s;
        }

        /** Ключ A1, повторяющий дискретный сигнал A2 ({@code invert} — НЗ). */
        int bridge(AutomationUnit u, String a, String b, int signal, boolean invert) {
            int sw = relay.addSwitch(node(u, a), node(u, b), invert);
            bridgeSwitch.add(sw);
            bridgeSignal.add(signal);
            bridgeInvert.add(invert);
            return sw;
        }
    }

    private void build(Build b, AutomationUnit u) {
        int[] ip = u.ip;
        double[] dp = u.dp;
        switch (u.kind) {
            case RELAY -> {
                RelayKind k = ip[AutomationUnit.I_VARIANT] == 1 ? RelayKind.FORCE_GUIDED : RelayKind.GENERAL;
                u.element = relay.addRelay(k, 0, b.node(u, "A1"), b.node(u, "A2"));
                contacts(b, u, "11", "12", "14", "21", "22", "24");
            }
            case RELAY_LATCHING -> {
                u.element = relay.addLatchingRelay(0, b.node(u, "A1"), b.node(u, "A2"), b.node(u, "B1"), b.node(u, "B2"));
                contacts(b, u, "11", "12", "14", "21", "22", "24");
            }
            case TIME_RELAY -> {
                TimeRelayType type = TimeRelayType.values()[Math.floorMod(ip[AutomationUnit.I_VARIANT], TimeRelayType.values().length)];
                TimeRelayMode mode = TimeRelayMode.values()[Math.floorMod(ip[AutomationUnit.I_MODE], TimeRelayMode.values().length)];
                u.element = relay.addTimeRelay(type, mode, dp[AutomationUnit.D_SET], 0, b.node(u, "A1"), b.node(u, "A2"));
                contacts(b, u, "15", "16", "18", "25", "26", "28");
            }
            case STEPPING_SWITCH -> {
                int[] commons = {b.node(u, "C1"), b.node(u, "C2"), b.node(u, "C3")};
                u.element = relay.addSteppingSwitch(0, b.node(u, "A1"), b.node(u, "A2"), b.node(u, "B1"), b.node(u, "B2"),
                        commons, ip[AutomationUnit.I_DEBOUNCED] == 1);
                for (int pos = 1; pos <= RelayBank.STEPPER_POSITIONS; pos++) {
                    for (int w = 0; w < RelayBank.STEPPER_WIPERS; w++) {
                        int q = ip[AutomationUnit.I_TABLE + (pos - 1) * RelayBank.STEPPER_WIPERS + w];
                        if (q >= 1 && q <= 8) relay.setStepperBank(u.element, pos, w, b.node(u, "Q" + q));
                    }
                }
            }
            case CAM_CONTROLLER -> {
                double ratio = Math.max(1, Math.min(3600, dp[AutomationUnit.D_SPAN]));
                u.element = relay.addCamController(ratio);
                for (int c = 0; c < RelayBank.CAMS; c++) {
                    relay.addCamContact(u.element, c, ip[AutomationUnit.I_TABLE + c] == 1, b.node(u, "K" + (c + 1) + ".1"),
                            b.node(u, "K" + (c + 1) + ".2"));
                    double from = dp[AutomationUnit.D_TABLE + 2 * c], to = dp[AutomationUnit.D_TABLE + 2 * c + 1];
                    if (from != to) relay.setCamInterval(u.element, c, from, to);
                }
            }
            case PROCESS_SWITCH -> {
                ProcessSwitchKind k = ProcessSwitchKind.values()[Math.floorMod(ip[AutomationUnit.I_VARIANT], ProcessSwitchKind.values().length)];
                u.element = relay.addProcessSwitch(k, ip[AutomationUnit.I_MODE] == 1, dp[AutomationUnit.D_SET],
                        dp[AutomationUnit.D_SPAN], dp[AutomationUnit.D_RETURN], 0);
                int n11 = b.node(u, "11");
                relay.addProcessContact(u.element, false, n11, b.node(u, "12"));
                relay.addProcessContact(u.element, true, n11, b.node(u, "14"));
            }
            case POWER_SUPPLY -> u.element = relay.addSupply(b.node(u, "+24"), b.node(u, "0"), u.supplyRatedA());
            case PUSHBUTTON -> u.switches = new int[] {relay.addSwitch(b.node(u, "13"), b.node(u, "14"), false),
                    relay.addSwitch(b.node(u, "11"), b.node(u, "12"), true)};
            case SELECTOR_SWITCH -> {
                int c = b.node(u, "C");
                u.switches = new int[] {relay.addSwitch(c, b.node(u, "1"), false), relay.addSwitch(c, b.node(u, "2"), false),
                        relay.addSwitch(c, b.node(u, "3"), false)};
            }
            case KEY_SWITCH -> {
                int c = b.node(u, "C");
                u.switches = new int[] {relay.addSwitch(c, b.node(u, "ON"), false), relay.addSwitch(c, b.node(u, "OFF"), false)};
            }
            case INDICATOR_LAMP -> u.loads = new int[] {relay.addLamp(b.node(u, "X1"), b.node(u, "X2"), RelayBank.LAMP_W)};
            case REDSTONE_INTERFACE -> {
                u.loads = new int[] {relay.addLamp(b.node(u, "A1"), b.node(u, "A2"), RelayBank.COIL_W)};
                u.switches = new int[] {relay.addSwitch(b.node(u, "13"), b.node(u, "14"), false)};
            }
            case GAS_DETECTOR -> {
                int n11 = b.node(u, "11"), n21 = b.node(u, "21");
                u.switches = new int[] {relay.addSwitch(n11, b.node(u, "12"), true), relay.addSwitch(n11, b.node(u, "14"), false),
                        relay.addSwitch(n21, b.node(u, "22"), true), relay.addSwitch(n21, b.node(u, "24"), false)};
                u.signals = new int[] {analog.newSignal()};
                analog.setSignal(u.signals[0], 4);
                b.drive(u, "OUT", u.signals[0]);
            }
            case BREAKER_IO -> {
                int minus = b.node(u, "-");
                u.loads = new int[] {relay.addLamp(b.node(u, "+"), minus, AutomationUnit.MONITOR_W),
                        relay.addLamp(b.node(u, "CLOSE"), minus, AutomationUnit.BREAKER_COIL_W),
                        relay.addLamp(b.node(u, "TRIP"), minus, AutomationUnit.BREAKER_COIL_W)};
                u.switches = new int[] {relay.addSwitch(b.node(u, "13"), b.node(u, "14"), false),
                        relay.addSwitch(b.node(u, "21"), b.node(u, "22"), true)};
            }
            case MOTOR_IO -> {
                u.loads = new int[] {relay.addLamp(b.node(u, "A1"), b.node(u, "A2"), AutomationUnit.STARTER_COIL_W)};
                u.switches = new int[] {relay.addSwitch(b.node(u, "13"), b.node(u, "14"), false)};
            }
            case CONSUMER_IO -> u.switches = new int[] {relay.addSwitch(b.node(u, "13"), b.node(u, "14"), false)};
            case ANALOG_SUMMER -> {
                double[] k = new double[4];
                for (int i = 0; i < 4; i++) k[i] = dp[AutomationUnit.D_TABLE + i];
                u.element = analog.addSummer(AnalogTech.TUBE, new int[] {-1, -1, -1, -1}, k);
                driveY(b, u);
            }
            case ANALOG_INTEGRATOR -> {
                double[] k = new double[3];
                for (int i = 0; i < 3; i++) k[i] = dp[AutomationUnit.D_TABLE + i];
                u.element = analog.addIntegrator(AnalogTech.TUBE, new int[] {-1, -1, -1}, k, dp[AutomationUnit.D_SPAN]);
                analog.setIntegratorLimits(u.element, dp[AutomationUnit.D_LO], dp[AutomationUnit.D_HI]);
                analog.setIntegratorMode(u.element, IntegratorMode.values()[Math.floorMod(ip[AutomationUnit.I_MODE], 3)]);
                analog.setIntegratorIc(u.element, -1, b.opto(u, "IC/M"), b.opto(u, "HOLD"));
                driveY(b, u);
            }
            case ANALOG_DIFFERENTIATOR -> {
                u.element = analog.addDifferentiator(AnalogTech.TUBE, -1, dp[AutomationUnit.D_SPAN], dp[AutomationUnit.D_RETURN]);
                driveY(b, u);
            }
            case ANALOG_MULTIPLIER -> {
                MultiplierMode m = MultiplierMode.values()[Math.floorMod(ip[AutomationUnit.I_MODE], MultiplierMode.values().length)];
                u.element = analog.addMultiplier(AnalogTech.TUBE, m, -1, -1);
                driveY(b, u);
            }
            case ANALOG_FUNCTION -> {
                double[] xs = new double[11], ys = new double[11];
                for (int i = 0; i < 11; i++) {
                    xs[i] = -10 + 2 * i;
                    ys[i] = dp[AutomationUnit.D_TABLE + i];
                }
                u.element = analog.addFunction(AnalogTech.TUBE, -1, xs, ys);
                driveY(b, u);
            }
            case ANALOG_COMPARATOR -> {
                u.element = analog.addComparator(AnalogTech.TUBE, -1, -1, dp[AutomationUnit.D_SET]);
                int out = analog.out(u.element);
                u.signals = new int[] {out};
                u.switches = new int[] {b.bridge(u, "11", "14", out, false), b.bridge(u, "11", "12", out, true)};
            }
            case MANUAL_STATION -> {
                u.element = analog.addManualStation(-1, b.opto(u, "MAN"));
                analog.setSetpoint(u.element, dp[AutomationUnit.D_SET]);
                analog.setStationMode(u.element, ip[AutomationUnit.I_POSITION] == 1 ? StationMode.MANUAL : StationMode.AUTO);
                int sp = analog.out(u.element, AnalogBank.STATION_SP), ma = analog.out(u.element, AnalogBank.STATION_OUT_MA);
                int trk = analog.out(u.element, AnalogBank.STATION_TRACK), mode = analog.out(u.element, AnalogBank.STATION_MODE);
                u.signals = new int[] {ma, sp, trk};
                b.drive(u, "SP", sp);
                b.drive(u, "OUT", ma);
                b.drive(u, "TRK", trk);
                u.switches = new int[] {b.bridge(u, "13", "14", mode, false)};
            }
            case SIGNAL_CONVERTER -> {
                boolean iv = ip[AutomationUnit.I_POSITION] == 1;
                u.element = analog.addConverter(AnalogTech.TUBE, iv, -1);
                u.signals = new int[] {analog.out(u.element)};
                b.drive(u, iv ? "U" : "I", u.signals[0]);
            }
        }
    }

    /** Две переключающие группы реле: общий-НЗ-НО группы 0 и 1. */
    private void contacts(Build b, AutomationUnit u, String c0, String nc0, String no0, String c1, String nc1, String no1) {
        int a = b.node(u, c0), c = b.node(u, c1);
        relay.addContact(u.element, 0, false, a, b.node(u, nc0));
        relay.addContact(u.element, 0, true, a, b.node(u, no0));
        relay.addContact(u.element, 1, false, c, b.node(u, nc1));
        relay.addContact(u.element, 1, true, c, b.node(u, no1));
    }

    private void driveY(Build b, AutomationUnit u) {
        u.signals = new int[] {analog.out(u.element)};
        b.drive(u, "Y", u.signals[0]);
    }

    /** Входы блоков A2 — сигналы их цепей. */
    private void wireAnalog(Build b, AutomationUnit u) {
        switch (u.kind) {
            case ANALOG_SUMMER -> {
                for (int i = 0; i < 4; i++) analog.setInput(u.element, i, b.source(u, "X" + (i + 1)));
            }
            case ANALOG_INTEGRATOR -> {
                for (int i = 0; i < 3; i++) analog.setInput(u.element, i, b.source(u, "X" + (i + 1)));
                analog.setInput(u.element, 3, b.source(u, "IC"));
            }
            case ANALOG_DIFFERENTIATOR, ANALOG_FUNCTION -> analog.setInput(u.element, 0, b.source(u, "X"));
            case ANALOG_MULTIPLIER, ANALOG_COMPARATOR -> {
                analog.setInput(u.element, 0, b.source(u, "X1"));
                analog.setInput(u.element, 1, b.source(u, "X2"));
            }
            case MANUAL_STATION -> {
                analog.setInput(u.element, 0, b.source(u, "AUTO"));
                u.pvSignal = b.source(u, "PV");
                for (String out : new String[] {"SP", "OUT", "TRK"}) if (b.conflicts.contains(u.net(u.kind.indexOf(out)))) u.conflict = true;
            }
            case SIGNAL_CONVERTER -> {
                boolean iv = u.ip[AutomationUnit.I_POSITION] == 1;
                analog.setInput(u.element, 0, b.source(u, iv ? "I" : "U"));
                if (b.conflicts.contains(u.net(u.kind.indexOf(iv ? "U" : "I")))) u.conflict = true;
            }
            default -> {
                if (u.isAnalog() && b.conflicts.contains(u.net(u.kind.indexOf("Y")))) u.conflict = true;
            }
        }
        if (u.kind.indexOf("Y") >= 0 && b.conflicts.contains(u.net(u.kind.indexOf("Y")))) u.conflict = true;
        if (u.kind == AutomationKind.GAS_DETECTOR && b.conflicts.contains(u.net(u.kind.indexOf("OUT")))) u.conflict = true;
    }

    /** Снять память элементов со старых банков. */
    private void capture(AutomationUnit u) {
        if (u.element < 0) return;
        switch (u.kind) {
            case RELAY, RELAY_LATCHING, TIME_RELAY -> u.memo = relay.memoRelay(u.element);
            case STEPPING_SWITCH -> u.memo = relay.memoStepper(u.element);
            case CAM_CONTROLLER -> u.memo = new double[] {relay.camAngleDeg(u.element)};
            case PROCESS_SWITCH -> u.memo = relay.memoProcess(u.element);
            default -> {
                if (u.isAnalog()) u.memo = analog.memo(u.element);
            }
        }
    }

    private void restore(AutomationUnit u) {
        if (u.memo == null || u.element < 0) return;
        switch (u.kind) {
            case RELAY, RELAY_LATCHING, TIME_RELAY -> relay.restoreRelay(u.element, u.memo);
            case STEPPING_SWITCH -> relay.restoreStepper(u.element, u.memo);
            case CAM_CONTROLLER -> relay.restoreCamAngle(u.element, u.memo.length > 0 ? u.memo[0] : 0);
            case PROCESS_SWITCH -> relay.restoreProcess(u.element, u.memo);
            default -> {
                if (u.isAnalog()) analog.restore(u.element, u.memo);
            }
        }
    }

    // ================================================================ конфиг и сохранение

    @Override
    public void onConfigReload(SimConfig config) {
        AutomationConfig next = config.section(AutomationConfig.class, cfg);
        if (!next.equals(cfg)) {
            cfg = next;
            markDirty();
        }
    }

    @Override
    public void save(StateWriter w) {
        if (ready()) for (AutomationUnit u : units.values()) capture(u);
        w.putInt("units", units.size());
        int i = 0;
        for (AutomationUnit u : units.values()) {
            w.beginObject("u" + i++);
            w.putLong("id", u.id.raw());
            u.save(w);
            w.endObject();
        }
    }

    @Override
    public void load(StateReader r) {
        units.clear();
        relay = null;
        analog = null;
        dirty = true;
        int n = r.optInt("units", 0);
        for (int i = 0; i < n; i++) {
            r.beginObject("u" + i);
            SimId id = new SimId(r.getLong("id"));
            AutomationUnit u = AutomationUnit.load(id, r);
            if (u != null) {
                u.owner = this;
                units.put(id, u);
            }
            r.endObject();
        }
    }

    /** Конфиг измерения: пределы станции GDD подняты до ёмкости банка измерения. */
    public static AutomationConfig worldConfig(AutomationConfig base) {
        return base.withStationLimits(WORLD_LIMIT, WORLD_LIMIT);
    }
}
