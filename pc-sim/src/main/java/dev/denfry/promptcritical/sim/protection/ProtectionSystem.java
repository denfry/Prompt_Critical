package dev.denfry.promptcritical.sim.protection;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

/**
 * Аппаратная аварийная защита (01 §1.11): каналы → голосование k из n по группам → задержка t_d →
 * защёлка → размыкание питания приводов → через t_latch освобождение стержней.
 *
 * <p>Шаг вызывается в начале FAST-тика по измерениям прошлого тика (§1.13.4) — отсюда обработка в 1 тик.
 * Автомат — таблица §1.11.5. Независим от Lua: запрос АЗ из Lua — лишь один из входов, отменить АЗ или
 * ослабить уставку нельзя.
 *
 * <p>Отклонения от GDD: разрешения ({@code permissiveMinPowerFrac}) блокируют функцию целиком, а не «по одной
 * петле» (03b {@code PERM_LOW_POWER}); отказ шкафа без отказа на срабатывание записывается причиной
 * {@code LOSS_OF_DRIVE_POWER} и держит АЗ до {@link #repairCabinet()}; опробование каналов (03b) — позже.
 */
public final class ProtectionSystem {
    /** Наибольшее число групп голосования (петель, ПГ, насосов) у одной причины. */
    public static final int MAX_GROUPS = 8;
    /** Предупредительные действия ПР-300 (§1.11.3): блокировка вывода по периоду и мощности, ПЗ-1. */
    public static final double PERIOD_WARNING_S = 20, POWER_WARNING_FRAC = 1.04, STEPBACK_POWER_FRAC = 1.05,
            STEPBACK_PUMP_PERMISSIVE_FRAC = 0.5, STEPBACK_TARGET_FRAC = 0.5;

    private static final TripReason[] REASONS = TripReason.values();
    private static final int N = REASONS.length;
    private static final double EPS = 1e-9;
    private static final ProtectionAction ROD_BLOCK_PERIOD = new ProtectionAction.RodBlock(InterlockReason.PERIOD_WARNING);
    private static final ProtectionAction ROD_BLOCK_POWER = new ProtectionAction.RodBlock(InterlockReason.POWER_WARNING);
    private static final ProtectionAction SI_PRESSURE = new ProtectionAction.SafetyInjection("LOW_PRIMARY_PRESSURE");
    private static final ProtectionAction SI_CONTAINMENT = new ProtectionAction.SafetyInjection("HIGH_CONTAINMENT_PRESSURE");
    private static final ProtectionAction STEPBACK = new ProtectionAction.Stepback(STEPBACK_TARGET_FRAC);

    private final ProtectionConfig cfg;
    private final DnbrCalculator dnbr;
    private final double siPressure;
    private final ProtectionEventSink sink;

    private final TripSetpoint[] setpoint = new TripSetpoint[N];
    private final double[] effective = new double[N];
    private final boolean[][] chTrip = new boolean[N][];
    private final boolean[][] chFault = new boolean[N][];
    private final boolean[][] chBypass = new boolean[N][];
    private final int[] chActive = new int[N];
    private final boolean[] rawVote = new boolean[N];
    private final boolean[] pending = new boolean[N];
    private final double[] pendingTime = new double[N];
    private final long[] voteStart = new long[N];
    private final TripSource[] sourceOf = new TripSource[N];
    private final double[] dnbrValue;

    private final EnumSet<TripReason> active = EnumSet.noneOf(TripReason.class);
    private final EnumSet<TripReason> blocking = EnumSet.noneOf(TripReason.class);
    private final EnumSet<TripReason> tripSet = EnumSet.noneOf(TripReason.class);
    private final List<ProtectionAction> actions = new ArrayList<>();
    private final List<ProtectionAction> actionsView = Collections.unmodifiableList(actions);

    private ProtectionMode mode = ProtectionMode.NORMAL;
    private TripReason firstOut;
    private TripSource tripSource;
    private long tripTick = -1;
    private double latchElapsed;
    private boolean rodsReleased;
    private double signalClearTime = Double.POSITIVE_INFINITY;
    private boolean rodsAtBottom = true;
    private double lastPower;
    private boolean lowFluxBlocked;
    private TripReason requestedReason;
    private TripSource requestedSource;
    private boolean cabinetFailed;
    private long lastTick;
    private boolean siPressureVote;

    /** Без вычислителя DNBR и САОЗ, конфиг по умолчанию. */
    public ProtectionSystem(List<TripSetpoint> setpoints) {
        this(setpoints, ProtectionConfig.defaults(), null, Double.NaN, ProtectionEventSink.NONE);
    }

    /**
     * @param dnbr              вычислитель DNBR; null — каналы {@code LOW_DNBR} берутся из входов как есть
     * @param safetyInjectionPa давление сигнала САОЗ по каналам {@code LOW_PRIMARY_PRESSURE}; NaN — нет
     */
    public ProtectionSystem(List<TripSetpoint> setpoints, ProtectionConfig cfg, DnbrCalculator dnbr,
            double safetyInjectionPa, ProtectionEventSink sink) {
        this.cfg = Objects.requireNonNull(cfg, "cfg");
        this.dnbr = dnbr;
        this.siPressure = safetyInjectionPa;
        this.sink = Objects.requireNonNull(sink, "sink");
        for (TripSetpoint sp : setpoints) {
            int f = sp.reason().ordinal();
            if (setpoint[f] != null) throw new IllegalArgumentException("duplicate setpoint " + sp.reason());
            setpoint[f] = sp;
            effective[f] = sp.value();
            if (!sp.reason().isDiscrete()) {
                int cap = MAX_GROUPS * sp.voting().channels();
                chTrip[f] = new boolean[cap];
                chFault[f] = new boolean[cap];
                chBypass[f] = new boolean[cap];
            }
        }
        TripSetpoint dnbrSp = setpoint[TripReason.LOW_DNBR.ordinal()];
        dnbrValue = new double[dnbrSp == null ? 0 : MAX_GROUPS * dnbrSp.voting().channels()];
        Arrays.fill(dnbrValue, Double.NaN);
    }

    /** Установка с водой под давлением: уставки §1.11.3, вычислитель DNBR, САОЗ. */
    public static ProtectionSystem pressurizedWater(ProtectionConfig cfg, PlantNominals nom, DnbrCalculator dnbr,
            ProtectionEventSink sink) {
        return new ProtectionSystem(ProtectionSetpoints.pressurizedWater(cfg, nom), cfg, dnbr,
                nom.safetyInjectionPressure(), sink);
    }

    // ---------------------------------------------------------------- шаг

    public void step(double dt, ProtectionInputs in, long tick) {
        if (!(dt > 0)) throw new IllegalArgumentException("dt");
        lastTick = tick;
        actions.clear();
        double power = in.measuredPowerFrac();
        lastPower = power;
        if (lowFluxBlocked && !(power > cfg.lowPowerPermissiveFrac())) lowFluxBlocked = false;
        rodsAtBottom = in.rodsAtBottom();
        updateDnbr(in);

        active.clear();
        blocking.clear();
        boolean anyPending = evaluateFunctions(in, power, dt, tick);
        TripReason request = requestedReason;
        TripSource requestSource = requestedSource;
        requestedReason = null;
        requestedSource = null;
        discrete(TripReason.MANUAL, in.manualTrip());
        discrete(TripReason.LUA_REQUEST, in.luaTripRequested());
        if (in.driveTorqueLost()) {
            active.add(TripReason.LOSS_OF_DRIVE_POWER);
            blocking.add(TripReason.LOSS_OF_DRIVE_POWER);
        }
        signalClearTime = blocking.isEmpty() ? signalClearTime + dt : 0;

        switch (mode) {
            case FAILED_ARMED -> {
                return;
            }
            case NORMAL, TRIP_PENDING -> {
                collectTrips(in, request, requestSource, tick, false);
                if (!tripSet.isEmpty()) {
                    enterTrip(tick);
                    releaseIfDue();
                } else mode = anyPending ? ProtectionMode.TRIP_PENDING : ProtectionMode.NORMAL;
            }
            case RESET_ARMED -> {
                collectTrips(in, request, requestSource, tick, true);
                if (!tripSet.isEmpty()) {
                    enterTrip(tick);
                    releaseIfDue();
                } else if (in.rodWithdrawalStarted()) mode = ProtectionMode.NORMAL;
            }
            case TRIPPED -> {
                latchElapsed += dt;
                releaseIfDue();
            }
        }
        if (mode != ProtectionMode.TRIPPED) warnings(in, power);
        if (siPressureVote) actions.add(SI_PRESSURE);
        if (rawVote[TripReason.HIGH_CONTAINMENT_PRESSURE.ordinal()]) actions.add(SI_CONTAINMENT);
    }

    private void updateDnbr(ProtectionInputs in) {
        if (dnbrValue.length == 0 || dnbr == null) return;
        int count = Math.min(Math.max(in.channelCount(TripReason.LOW_DNBR), 0), dnbrValue.length);
        for (int c = 0; c < dnbrValue.length; c++) {
            dnbrValue[c] = c >= count ? Double.NaN : dnbr.calculate(in.dnbrSignal(DnbrSignal.PRESSURE, c),
                    in.dnbrSignal(DnbrSignal.INLET_TEMPERATURE, c), in.dnbrSignal(DnbrSignal.FLOW_FRAC, c),
                    in.dnbrSignal(DnbrSignal.POWER_FRAC, c), in.dnbrSignal(DnbrSignal.AXIAL_PEAKING, c));
        }
    }

    /** Каналы, голосование, таймеры t_d. Возвращает, есть ли функция, ждущая t_d. */
    private boolean evaluateFunctions(ProtectionInputs in, double power, double dt, long tick) {
        boolean anyPending = false;
        siPressureVote = false;
        for (int f = 0; f < N; f++) {
            rawVote[f] = false;
            TripSetpoint sp = setpoint[f];
            if (sp == null || chTrip[f] == null || !sp.enabled()) {
                pending[f] = false;
                chActive[f] = 0;
                continue;
            }
            TripReason r = REASONS[f];
            int n = sp.voting().channels(), k = sp.voting().required();
            int groups = Math.max(1, Math.min(MAX_GROUPS, in.groupCount(r)));
            int installed = Math.min(in.channelCount(r), groups * n);
            if (installed <= 0) {
                Arrays.fill(chTrip[f], false);
                Arrays.fill(chFault[f], false);
                pending[f] = false;
                chActive[f] = 0;
                continue;
            }
            chActive[f] = groups * n;
            double threshold = threshold(f);
            boolean siCheck = f == TripReason.LOW_PRIMARY_PRESSURE.ordinal() && !Double.isNaN(siPressure);
            boolean anyTripped = false;
            for (int g = 0; g < groups; g++) {
                int yes = 0, siYes = 0;
                for (int c = 0; c < n; c++) {
                    int ch = g * n + c;
                    double v = ch < installed ? value(r, f, in, ch) : Double.NaN;
                    boolean fault = Double.isNaN(v);
                    chFault[f][ch] = fault;
                    chTrip[f][ch] = !fault && compare(sp, chTrip[f][ch], v, threshold);
                    if (chBypass[f][ch]) continue;
                    if (fault || chTrip[f][ch]) {
                        yes++;
                        anyTripped = true;
                    }
                    if (siCheck && (fault || v < siPressure)) siYes++;
                }
                if (yes >= k) rawVote[f] = true;
                if (siYes >= k) siPressureVote = true;
            }
            for (int ch = groups * n; ch < chTrip[f].length; ch++) {
                chTrip[f][ch] = false;
                chFault[f][ch] = false;
            }
            boolean armed = !(power < sp.permissiveMinPowerFrac());
            if (armed && anyTripped) blocking.add(r);
            if (armed && rawVote[f]) {
                active.add(r);
                if (!pending[f]) {
                    pending[f] = true;
                    pendingTime[f] = 0;
                    voteStart[f] = tick;
                } else {
                    pendingTime[f] += dt;
                }
                anyPending = true;
            } else {
                pending[f] = false;
            }
        }
        return anyPending;
    }

    private double value(TripReason r, int f, ProtectionInputs in, int ch) {
        if (r == TripReason.LOW_DNBR && dnbr != null) return dnbrValue[ch];
        double v = in.channelValue(r, ch);
        // Период: отрицательный или нулевой — мощность падает или стоит, к «короткому периоду» не относится.
        if (r == TripReason.SHORT_PERIOD && v <= 0) return Double.POSITIVE_INFINITY;
        return v;
    }

    private double threshold(int f) {
        if (f == TripReason.HIGH_FLUX.ordinal() && !lowFluxBlocked)
            return Math.min(effective[f], cfg.highFluxLowSetpointFrac());
        return effective[f];
    }

    private static boolean compare(TripSetpoint sp, boolean wasTripped, double v, double threshold) {
        double h = wasTripped ? sp.hysteresis() : 0;
        return sp.cmp() == Comparison.ABOVE ? v > threshold - h : v < threshold + h;
    }

    private void discrete(TripReason r, boolean signal) {
        TripSetpoint sp = setpoint[r.ordinal()];
        if (!signal || (sp != null && !sp.enabled())) return;
        active.add(r);
        blocking.add(r);
    }

    /** Причины, вызывающие АЗ в этом тике; {@code immediate} — без ожидания t_d (из {@code RESET_ARMED}). */
    private void collectTrips(ProtectionInputs in, TripReason request, TripSource requestSource, long tick,
            boolean immediate) {
        tripSet.clear();
        for (int f = 0; f < N; f++) {
            if (pending[f] && (immediate || pendingTime[f] >= setpoint[f].delaySeconds() - EPS)) {
                tripSet.add(REASONS[f]);
                sourceOf[f] = TripSource.AUTOMATIC;
            }
        }
        if (active.contains(TripReason.MANUAL)) addImmediate(TripReason.MANUAL, TripSource.OPERATOR_PANEL, tick);
        if (active.contains(TripReason.LUA_REQUEST)) addImmediate(TripReason.LUA_REQUEST, TripSource.LUA, tick);
        if (in.driveTorqueLost()) addImmediate(TripReason.LOSS_OF_DRIVE_POWER, TripSource.AUTOMATIC, tick);
        if (request != null) addImmediate(request, requestSource, tick);
    }

    private void addImmediate(TripReason r, TripSource source, long tick) {
        int f = r.ordinal();
        if (tripSet.add(r) || !pending[f]) {
            sourceOf[f] = source;
            voteStart[f] = tick;
        }
    }

    private void enterTrip(long tick) {
        TripReason first = null;
        long firstStart = Long.MAX_VALUE;
        for (TripReason r : tripSet) {
            long start = voteStart[r.ordinal()];
            if (start < firstStart) {
                first = r;
                firstStart = start;
            }
        }
        mode = ProtectionMode.TRIPPED;
        firstOut = first;
        tripSource = sourceOf[first.ordinal()];
        tripTick = tick;
        latchElapsed = 0;
        rodsReleased = false;
        for (TripReason r : tripSet) sink.reactorTrip(r, sourceOf[r.ordinal()], r == first, tick);
    }

    private void releaseIfDue() {
        if (rodsReleased || latchElapsed < cfg.latchTimeS() - EPS) return;
        rodsReleased = true;
        for (TripReason r : tripSet) actions.add(new ProtectionAction.Trip(r, r == firstOut));
    }

    private void warnings(ProtectionInputs in, double power) {
        double period = in.measuredPeriod();
        if (period > 0 && period < PERIOD_WARNING_S) actions.add(ROD_BLOCK_PERIOD);
        if (power > POWER_WARNING_FRAC) actions.add(ROD_BLOCK_POWER);
        if (power > STEPBACK_POWER_FRAC || (in.pumpTripped() && power > STEPBACK_PUMP_PERMISSIVE_FRAC))
            actions.add(STEPBACK);
    }

    // ---------------------------------------------------------------- команды

    /**
     * Сброс АЗ оператором (кнопка шкафа или {@code pc_protection.reset()} с ключом). Разрешён, если ни один канал
     * взведённой функции не в срабатывании или отказе, дискретных сигналов нет, с момента снятия последнего прошло
     * {@code reset_delay_s}, стержни внизу и шкаф исправен. Иначе — {@code ResetDeniedEvent}.
     */
    public boolean reset() {
        if (mode != ProtectionMode.TRIPPED) return false;
        if (!blocking.isEmpty() || cabinetFailed || signalClearTime < cfg.resetDelayS() - EPS || !rodsAtBottom) {
            EnumSet<TripReason> reasons = EnumSet.copyOf(blocking);
            if (cabinetFailed) reasons.add(TripReason.LOSS_OF_DRIVE_POWER);
            sink.resetDenied(Collections.unmodifiableSet(reasons));
            return false;
        }
        mode = ProtectionMode.RESET_ARMED;
        rodsReleased = false;
        firstOut = null;
        tripSource = null;
        tripSet.clear();
        return true;
    }

    /**
     * Запрос АЗ командой ({@code SimCommand.RequestTrip}, 05): исполняется на ближайшем шаге немедленно. Политика
     * сервера — {@code MANUAL} + {@code SERVER_POLICY} (04d §4d.7.4).
     */
    public void requestTrip(TripReason reason, TripSource source) {
        requestedReason = Objects.requireNonNull(reason, "reason");
        requestedSource = Objects.requireNonNull(source, "source");
    }

    /** Вывод канала на обслуживание (только ключом на шкафу, 03b). Не более одного на причину и не ниже k каналов. */
    public boolean setBypass(TripReason r, int ch, boolean bypass) {
        int f = r.ordinal();
        boolean[] b = chBypass[f];
        if (b == null) return false;
        Objects.checkIndex(ch, b.length);
        if (!bypass || b[ch]) {
            b[ch] = bypass;
            return true;
        }
        if (!setpoint[f].voting().allowsBypass()) return false;
        for (boolean other : b) if (other) return false;
        b[ch] = true;
        return true;
    }

    /** Ужесточение уставки на шкафу; ослабить сверх конфига нельзя (§1.11.3). */
    public boolean tightenSetpoint(TripReason r, double value) {
        TripSetpoint sp = setpoint[r.ordinal()];
        if (sp == null || r.isDiscrete() || !sp.isTighteningOf(value)) return false;
        effective[r.ordinal()] = value;
        return true;
    }

    /** Снятие пусковой уставки {@code HIGH_FLUX} оператором — только при N выше разрешения (§1.11.3). */
    public boolean setLowFluxSetpointBlocked(boolean blocked) {
        if (blocked && !(lastPower > cfg.lowPowerPermissiveFrac())) return false;
        lowFluxBlocked = blocked;
        return true;
    }

    /**
     * Отказ шкафа (04c/04d). {@code failToTrip} — отказ на срабатывание (common-cause, «залипание» выключателей):
     * {@code FAILED_ARMED}, условия игнорируются, сценарий ATWS. Иначе шкаф разрушен или обесточен: fail-safe АЗ.
     */
    public void injectCommonCauseFailure(boolean failToTrip) {
        if (failToTrip) {
            mode = ProtectionMode.FAILED_ARMED;
            actions.clear();
            return;
        }
        cabinetFailed = true;
        if (mode == ProtectionMode.TRIPPED) return;
        tripSet.clear();
        tripSet.add(TripReason.LOSS_OF_DRIVE_POWER);
        sourceOf[TripReason.LOSS_OF_DRIVE_POWER.ordinal()] = TripSource.AUTOMATIC;
        voteStart[TripReason.LOSS_OF_DRIVE_POWER.ordinal()] = lastTick;
        enterTrip(lastTick);
    }

    /** Ремонт шкафа: снимает отказ; из {@code FAILED_ARMED} — в {@code NORMAL}, АЗ после отказа сбрасывается {@link #reset()}. */
    public void repairCabinet() {
        cabinetFailed = false;
        if (mode == ProtectionMode.FAILED_ARMED) mode = ProtectionMode.NORMAL;
    }

    // ---------------------------------------------------------------- состояние

    public ProtectionMode mode() {
        return mode;
    }

    public boolean isTripped() {
        return mode == ProtectionMode.TRIPPED;
    }

    /** Питание приводов снято и t_latch истекло: стержни освобождены. */
    public boolean rodsReleased() {
        return rodsReleased;
    }

    public TripReason firstOut() {
        return firstOut;
    }

    public TripSource tripSource() {
        return tripSource;
    }

    /** Тик срабатывания (размыкания питания), −1 — не было. */
    public long tripTick() {
        return tripTick;
    }

    /** Причины, голосующие «да» сейчас (взведённые функции и дискретные сигналы). */
    public EnumSet<TripReason> activeReasons() {
        return EnumSet.copyOf(active);
    }

    public ChannelState channelState(TripReason r, int ch) {
        int f = r.ordinal();
        if (chTrip[f] == null) throw new IllegalArgumentException(r + " has no measurement channels");
        Objects.checkIndex(ch, chTrip[f].length);
        if (chBypass[f][ch]) return ChannelState.BYPASS;
        if (ch >= chActive[f]) return ChannelState.OK;
        if (chFault[f][ch]) return ChannelState.FAULT;
        return chTrip[f][ch] ? ChannelState.TRIP : ChannelState.OK;
    }

    /** Действующая уставка (с учётом ужесточения); NaN — причина не настроена или дискретная. */
    public double setpointValue(TripReason r) {
        return setpoint[r.ordinal()] == null ? Double.NaN : effective[r.ordinal()];
    }

    /** Действия этого тика; список переиспользуется и меняется на следующем шаге. */
    public List<ProtectionAction> pendingActions() {
        return actionsView;
    }

    /** Результат вычислителя DNBR; NaN — отказ или вычислителя нет. */
    public double calculatedDnbr(int calculator) {
        Objects.checkIndex(calculator, Math.max(dnbrValue.length, 1));
        return calculator < dnbrValue.length ? dnbrValue[calculator] : Double.NaN;
    }
}
