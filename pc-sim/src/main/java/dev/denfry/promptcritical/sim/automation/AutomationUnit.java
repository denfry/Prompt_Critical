package dev.denfry.promptcritical.sim.automation;

import dev.denfry.promptcritical.sim.core.Quality;
import dev.denfry.promptcritical.sim.core.SimId;
import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;
import java.util.Arrays;
import java.util.Objects;

/**
 * Двойник блока автоматики мира в {@link AutomationSubsystem} (03d §3d.3–3d.4): клеммы (цепи — ключи связных частей
 * проводов, которые считает мод), уставки игрока, входы от мира (кнопка, вал, процесс, редстоун, механизм) и память
 * элемента банка для переноса при пересборке схемы. Смена цепей или уставок, меняющих схему, пересобирает банки в
 * начале следующего тика; входы и ручные команды действуют сразу.
 *
 * <p>Уставки — массивы {@code int}/{@code double} с раскладкой по виду (константы {@code I_*}/{@code D_*}); входы
 * мира — {@link #setInput} по слотам {@code IN_*}.
 */
public final class AutomationUnit {
    /** Клемма ни с чем не соединена: своя цепь. */
    public static final long NO_NET = 0;

    // --- уставки: целые ---
    /** RELAY: исполнение FORCE_GUIDED (1). PROCESS_SWITCH: вид. TIME_RELAY: исполнение. POWER_SUPPLY: номинал. */
    public static final int I_VARIANT = 0;
    /** TIME_RELAY: режим. PROCESS_SWITCH: «высокое» (1). ANALOG_*: режим (интегратор, множитель). */
    public static final int I_MODE = 1;
    /** SELECTOR_SWITCH: положение 0…2. MANUAL_STATION: команда РУЧ (1)/АВТ (0). SIGNAL_CONVERTER: I/V (1). */
    public static final int I_POSITION = 2;
    /** STEPPING_SWITCH: подавление дребезга (1). */
    public static final int I_DEBOUNCED = 3;
    /** STEPPING_SWITCH: поле 25 × 3 — выход Q1…Q8 (1…8) или 0; CAM_CONTROLLER: НО (1)/НЗ (0) кулачков. */
    public static final int I_TABLE = 4;
    static final int INTS = I_TABLE + RelayBank.STEPPER_POSITIONS * RelayBank.STEPPER_WIPERS;

    // --- уставки: вещественные ---
    /** TIME_RELAY: выдержка, с. PROCESS_SWITCH: уставка. ANALOG_COMPARATOR: зона возврата, В. MANUAL_STATION: SP, В. */
    public static final int D_SET = 0;
    /** PROCESS_SWITCH: шкала. ANALOG_INTEGRATOR: T_i, с. ANALOG_DIFFERENTIATOR: T_d, с. CAM_CONTROLLER: передача. */
    public static final int D_SPAN = 1;
    /** PROCESS_SWITCH: зона возврата (NaN — по умолчанию). ANALOG_DIFFERENTIATOR: N. GAS_DETECTOR: тревога, % НКПР. */
    public static final int D_RETURN = 2;
    /** ANALOG_INTEGRATOR: пределы выхода, В. */
    public static final int D_LO = 3, D_HI = 4;
    /** ANALOG_SUMMER/INTEGRATOR: коэффициенты k₁…k₄; ANALOG_FUNCTION: 11 ординат; CAM_CONTROLLER: углы вкл/выкл. */
    public static final int D_TABLE = 5;
    static final int DOUBLES = D_TABLE + 2 * RelayBank.CAMS;

    // --- входы мира ---
    /** PUSHBUTTON: нажата; KEY_SWITCH: импульс «ВКЛ»; PROCESS_SWITCH: величина; CAM_CONTROLLER: об/мин вала;
     * POWER_SUPPLY: вход, о.е.; REDSTONE_INTERFACE: сигнал; GAS_DETECTOR: % НКПР; MANUAL_STATION: ручка −1/0/+1;
     * *_IO: выключатель включён / пускатель включён / потребитель под напряжением. */
    public static final int IN_MAIN = 0;
    /** KEY_SWITCH: импульс «ОТКЛ». */
    public static final int IN_AUX = 1;
    static final int INPUTS = 2;

    /** Мощность электромагнитов выключателя на 24 В, Вт [BALANCE]; реле контроля цепи и пускатель. */
    static final double BREAKER_COIL_W = 10, MONITOR_W = 1, STARTER_COIL_W = 5, OPTO_W = 0.5;

    final SimId id;
    final AutomationKind kind;
    AutomationSubsystem owner;
    private long[] nets;
    final int[] ip = new int[INTS];
    final double[] dp = new double[DOUBLES];
    final double[] in = new double[INPUTS];
    /** Память элемента банка: состояние, износ, индивидуальные погрешности. */
    double[] memo;

    // Индексы в собранных банках (только до следующей пересборки)
    int element = -1;
    int[] loads = new int[0], switches = new int[0], signals = new int[0];
    boolean conflict;

    AutomationUnit(SimId id, AutomationKind kind) {
        this.id = Objects.requireNonNull(id, "id");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.nets = new long[kind.terminalCount()];
        defaults();
    }

    private void defaults() {
        switch (kind) {
            case TIME_RELAY -> {
                ip[I_VARIANT] = TimeRelayType.PNEUMATIC.ordinal();
                ip[I_MODE] = TimeRelayMode.ON_DELAY.ordinal();
                dp[D_SET] = 5;
            }
            case CAM_CONTROLLER -> {
                dp[D_SPAN] = 1;
                Arrays.fill(ip, I_TABLE, I_TABLE + RelayBank.CAMS, 1);
            }
            case PROCESS_SWITCH -> processDefaults(ProcessSwitchKind.PRESSURE);
            case POWER_SUPPLY -> ip[I_VARIANT] = 1;
            case GAS_DETECTOR -> {
                dp[D_SET] = 20;
                dp[D_RETURN] = 40;
            }
            case ANALOG_SUMMER -> Arrays.fill(dp, D_TABLE, D_TABLE + 4, 1);
            case ANALOG_INTEGRATOR -> {
                Arrays.fill(dp, D_TABLE, D_TABLE + 3, 1);
                dp[D_SPAN] = 1;
                dp[D_LO] = -AnalogBank.U_SAT;
                dp[D_HI] = AnalogBank.U_SAT;
                ip[I_MODE] = IntegratorMode.OPERATE.ordinal();
            }
            case ANALOG_DIFFERENTIATOR -> {
                dp[D_SPAN] = 1;
                dp[D_RETURN] = 10;
            }
            case ANALOG_FUNCTION -> {
                for (int i = 0; i < 11; i++) dp[D_TABLE + i] = -10 + 2 * i;
            }
            case ANALOG_COMPARATOR -> dp[D_SET] = 0.1;
            case MANUAL_STATION -> {
                dp[D_SET] = 5;
                ip[I_POSITION] = 1;
            }
            case SIGNAL_CONVERTER -> ip[I_POSITION] = 1;
            default -> {
            }
        }
    }

    /** Уставка реле процесса по умолчанию для вида: шкала манометра котла, уровень стекла, термостат, поток. */
    private void processDefaults(ProcessSwitchKind k) {
        ip[I_VARIANT] = k.ordinal();
        switch (k) {
            case PRESSURE -> set3(1.0e6, 1.6e6, 1);
            case LEVEL -> set3(0.6, 1.0, 0);
            case TEMPERATURE -> set3(353.15, 400, 1);
            case FLOW -> set3(1.0, 10, 0);
        }
        dp[D_RETURN] = Double.NaN;
    }

    private void set3(double set, double span, int high) {
        dp[D_SET] = set;
        dp[D_SPAN] = span;
        ip[I_MODE] = high;
    }

    public SimId id() {
        return id;
    }

    public AutomationKind kind() {
        return kind;
    }

    public long net(int terminal) {
        return nets[terminal];
    }

    /** Цепи клемм (по одной на клемму, {@link #NO_NET} — не подключена). */
    public void setNets(long... keys) {
        if (keys.length != nets.length) throw new IllegalArgumentException("terminals: " + nets.length);
        if (Arrays.equals(nets, keys)) return;
        nets = keys.clone();
        changed();
    }

    public boolean wired(int terminal) {
        return nets[terminal] != NO_NET;
    }

    /** Клемма — источник сигнала своей цепи (выход A2 или выход преобразователя по направлению). */
    public boolean drives(int terminal) {
        AutomationKind.Terminal t = kind.terminal(terminal);
        return switch (t.role()) {
            case OUTPUT -> true;
            case CONFIGURED -> (ip[I_POSITION] == 1) == (t.type() == TerminalType.ANALOG_VOLTAGE);
            default -> false;
        };
    }

    // ================================================================ уставки

    public int intParam(int i) {
        return ip[i];
    }

    public double param(int i) {
        return dp[i];
    }

    /** Целая уставка; меняет схему — банки пересобираются (память элементов переносится). */
    public void setIntParam(int i, int v) {
        if (ip[i] == v) return;
        if (kind == AutomationKind.PROCESS_SWITCH && i == I_VARIANT) {
            processDefaults(ProcessSwitchKind.values()[Math.floorMod(v, ProcessSwitchKind.values().length)]);
        } else {
            ip[i] = v;
        }
        changed();
    }

    /** Вещественная уставка (ручки GUI); недопустимое значение банк отвергает при сборке — тогда блок не работает. */
    public void setParam(int i, double v) {
        if (Double.compare(dp[i], v) == 0) return;
        dp[i] = v;
        if (kind == AutomationKind.MANUAL_STATION && i == D_SET && owner != null && owner.ready() && element >= 0) {
            owner.analog().setSetpoint(element, v);
            return;
        }
        changed();
    }

    // ================================================================ входы мира

    public double input(int slot) {
        return in[slot];
    }

    public void setInput(int slot, double v) {
        in[slot] = v;
    }

    /** Станция: режим РУЧ/АВТ (кнопки «Р»/«А» на блоке). */
    public void setStationManual(boolean manual) {
        ip[I_POSITION] = manual ? 1 : 0;
        if (owner != null && owner.ready() && element >= 0)
            owner.analog().setStationMode(element, manual ? StationMode.MANUAL : StationMode.AUTO);
    }

    /** Замена отказавшего элемента новым (реле, искатель, реле процесса, лампа блока A2). */
    public void replace() {
        if (owner == null || !owner.ready() || element < 0) {
            memo = null;
            return;
        }
        switch (kind) {
            case RELAY, RELAY_LATCHING, TIME_RELAY -> owner.relay().replaceRelay(element);
            case STEPPING_SWITCH -> owner.relay().replaceStepper(element);
            case PROCESS_SWITCH -> owner.relay().repairProcessSwitch(element);
            case ANALOG_SUMMER, ANALOG_INTEGRATOR, ANALOG_DIFFERENTIATOR, ANALOG_MULTIPLIER, ANALOG_FUNCTION,
                    ANALOG_COMPARATOR, SIGNAL_CONVERTER -> owner.analog().replaceTube(element);
            default -> {
            }
        }
    }

    /** Балансировка нуля интегратора ({@code instrument_calibrator}); {@code false} — блок в OPERATE. */
    public boolean calibrate() {
        return kind == AutomationKind.ANALOG_INTEGRATOR && ready() && owner.analog().calibrate(element);
    }

    // ================================================================ показания

    private boolean ready() {
        return live() && element >= 0;
    }

    /** Банки собраны по нынешней схеме (у ламп, кнопок и клемм механизмов элемента банка нет). */
    private boolean live() {
        return owner != null && owner.ready();
    }

    /** Блок собран в схему (уставки приняты банком). */
    public boolean built() {
        return live() && (element >= 0 || loads.length > 0 || switches.length > 0 || signals.length > 0);
    }

    /** Две выходные клеммы на одной цепи (DRIVER_CONFLICT, 03d §3d.2.1). */
    public boolean conflict() {
        return conflict;
    }

    public RelayState relayState() {
        return ready() ? owner.relay().relayState(element) : RelayState.DROPPED;
    }

    public boolean relayPicked() {
        return ready() && owner.relay().isPicked(element);
    }

    public long relayOps() {
        return ready() ? owner.relay().opsCount(element) : 0;
    }

    public double coilVoltage() {
        return ready() ? owner.relay().coilVoltage(element) : 0;
    }

    public boolean timerOutput() {
        return ready() && owner.relay().timerOutput(element);
    }

    public double timerDelayS() {
        return ready() ? owner.relay().actualDelayS(element) : dp[D_SET];
    }

    public int stepperPosition() {
        return ready() ? owner.relay().stepperPosition(element) : 1;
    }

    public boolean stepperStuck() {
        return ready() && owner.relay().stepperStuck(element);
    }

    public long missedSteps() {
        return ready() ? owner.relay().missedSteps(element) : 0;
    }

    public double camAngleDeg() {
        return ready() ? owner.relay().camAngleDeg(element) : 0;
    }

    public boolean processTripped() {
        return ready() && owner.relay().processOutput(element);
    }

    public double processMeasured() {
        return ready() ? owner.relay().processMeasured(element) : 0;
    }

    public boolean processStuck() {
        return ready() && owner.relay().processStuck(element);
    }

    public double supplyVoltage() {
        return ready() ? owner.relay().supplyVoltage(element) : 0;
    }

    public double supplyCurrent() {
        return ready() ? owner.relay().supplyCurrent(element) : 0;
    }

    /** Номинальный ток блока питания, А. */
    public double supplyRatedA() {
        return POWER_SUPPLY_RATINGS[Math.floorMod(ip[I_VARIANT], POWER_SUPPLY_RATINGS.length)];
    }

    public static final double[] POWER_SUPPLY_RATINGS = {2, 5, 10};

    /** Нагрузка {@code i} блока (лампа, катушка механизма, вход оптрона) под напряжением. */
    public boolean loadEnergized(int i) {
        return live() && i < loads.length && loads[i] >= 0 && owner.relay().lampLit(loads[i]);
    }

    /** Выход блока A2, В (у станции — выход регулятора в РУЧ/АВТ, В; у преобразователя V/I — мА). */
    public double output() {
        if (!live() || signals.length == 0) return 0;
        return owner.analog().signal(signals[0]);
    }

    /** Сигнал выхода {@code port} (станция: SP, мА, слежение). */
    public double output(int port) {
        return live() && port < signals.length ? owner.analog().signal(signals[port]) : 0;
    }

    public Quality quality() {
        if (!ready() || !isAnalog()) return Quality.BAD;
        return conflict ? Quality.BAD : owner.analog().quality(element);
    }

    public boolean algebraicLoop() {
        return ready() && isAnalog() && owner.analog().algebraicLoop(element);
    }

    public boolean tubeFailed() {
        return ready() && isAnalog() && owner.analog().tubeFailed(element);
    }

    public StationMode stationMode() {
        return ready() ? owner.analog().stationMode(element) : ip[I_POSITION] == 1 ? StationMode.MANUAL : StationMode.AUTO;
    }

    public boolean calDue() {
        return kind == AutomationKind.ANALOG_INTEGRATOR && ready() && owner.analog().calDue(element);
    }

    /** Значение PV станции (вход «PV», В), если подан. */
    public double stationPv() {
        return ready() && pvSignal >= 0 ? owner.analog().signal(pvSignal) : Double.NaN;
    }

    int pvSignal = -1;

    boolean isAnalog() {
        return switch (kind) {
            case ANALOG_SUMMER, ANALOG_INTEGRATOR, ANALOG_DIFFERENTIATOR, ANALOG_MULTIPLIER, ANALOG_FUNCTION,
                    ANALOG_COMPARATOR, MANUAL_STATION, SIGNAL_CONVERTER -> true;
            default -> false;
        };
    }

    void changed() {
        if (owner != null) owner.markDirty();
    }

    void unbind() {
        element = -1;
        loads = new int[0];
        switches = new int[0];
        signals = new int[0];
        pvSignal = -1;
        conflict = false;
    }

    // ================================================================ сохранение

    void save(StateWriter out) {
        out.putString("kind", kind.name());
        out.putLongs("nets", nets, nets.length);
        out.putInts("ip", ip, ip.length);
        out.putDoubles("dp", dp, dp.length);
        out.putDoubles("in", in, in.length);
        if (memo != null) out.putDoubles("memo", memo, memo.length);
    }

    static AutomationUnit load(SimId id, StateReader r) {
        AutomationKind kind;
        try {
            kind = AutomationKind.valueOf(r.getString("kind"));
        } catch (IllegalArgumentException e) {
            return null; // вид из будущей версии
        }
        AutomationUnit u = new AutomationUnit(id, kind);
        long[] n = r.getLongs("nets");
        if (n.length == u.nets.length) u.nets = n;
        int[] i = r.getInts("ip");
        System.arraycopy(i, 0, u.ip, 0, Math.min(i.length, u.ip.length));
        double[] d = r.getDoubles("dp");
        System.arraycopy(d, 0, u.dp, 0, Math.min(d.length, u.dp.length));
        if (r.has("in")) {
            double[] x = r.getDoubles("in");
            System.arraycopy(x, 0, u.in, 0, Math.min(x.length, u.in.length));
        }
        u.memo = r.has("memo") ? r.getDoubles("memo") : null;
        return u;
    }
}
