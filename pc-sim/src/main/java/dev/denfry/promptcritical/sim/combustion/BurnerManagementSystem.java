package dev.denfry.promptcritical.sim.combustion;

import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;
import java.util.Arrays;
import java.util.Objects;

/**
 * Система управления горелками (07 §7.9.6, NFPA 85) — аппаратная защита: оператор только подаёт команды, клапаны
 * топлива открывает BMS. Состояние котла идёт по таблице GDD: продувка (≥ 25 % воздуха, ≥ 5 смен объёма топки,
 * ≥ 300 с) → окно 300 с на запальник → проба запальника ≤ 10 с → проба основного факела ≤ 5 с (газ) / 10 с →
 * {@code RUNNING}; любая неудача, погасание всех факелов, останов вентиляторов, ПАЗ — {@code MFT}: клапаны
 * закрываются за {@link #VALVE_STROKE_S}, затем пост-продувка 300 с.
 *
 * <p>Горелки ведутся по отдельности ({@link BurnerPhase}): первая проходит пробы как состояния котла, остальные
 * разжигаются в {@code RUNNING} от своих запальников; неудача такой горелки или погасание её факела отключает
 * только её. После успешной пробы основного факела запальник гасится (прерывистый запальник), и датчик дальше
 * смотрит только основной факел.
 *
 * <p>Датчик пламени видит физический факел горелки (на шаге — факел прошлого тика). Отказ
 * {@link FlameDetectorFault#STUCK_FLAME} и байпас ключом делают его «всегда видящим»: BMS пропускает пробы и не
 * отсекает топливо без факела — топливо копится в топке (взрыв считает котёл).
 */
public final class BurnerManagementSystem {
    public static final double PURGE_MIN_S = 300, PURGE_MIN_VOLUME_CHANGES = 5, PURGE_MIN_AIR_FRACTION = 0.25;
    public static final double PURGE_COMPLETE_WINDOW_S = 300, POST_PURGE_S = 300;
    public static final double IGNITER_TRIAL_S = 10, MAIN_TRIAL_GAS_S = 5, MAIN_TRIAL_OTHER_S = 10;
    /** Ход отсечного клапана, с: закрытие при MFT — не дольше 1 с. */
    public static final double VALVE_STROKE_S = 0.5;

    /** Состояние отдельной горелки. */
    public enum BurnerPhase { OFF, IGNITER_TRIAL, IGNITER_ON, MAIN_TRIAL, MAIN_ON }

    private final int n;
    private final double mainTrialS;
    private final BoilerEvents events;
    private final BurnerPhase[] phase;
    private final double[] phaseTimer, valve;
    private final boolean[] bypass, seen;
    private final FlameDetectorFault[] fault;
    private BmsState state = BmsState.OFF;
    private MftCause lastCause;
    private double timer, purgeS, purgeChanges;
    private boolean fansRunning;

    public BurnerManagementSystem(int burners, BurnerKind kind, BoilerEvents events) {
        if (burners < 1) throw new IllegalArgumentException("burners: " + burners);
        this.n = burners;
        this.mainTrialS = kind == BurnerKind.GAS || kind == BurnerKind.LOW_BTU_GAS ? MAIN_TRIAL_GAS_S : MAIN_TRIAL_OTHER_S;
        this.events = Objects.requireNonNull(events, "events");
        phase = new BurnerPhase[n];
        Arrays.fill(phase, BurnerPhase.OFF);
        phaseTimer = new double[n];
        valve = new double[n];
        bypass = new boolean[n];
        seen = new boolean[n];
        fault = new FlameDetectorFault[n];
        Arrays.fill(fault, FlameDetectorFault.NONE);
    }

    public int burners() {
        return n;
    }

    public BmsState state() {
        return state;
    }

    /** Причина последнего MFT; null — не было. */
    public MftCause lastMftCause() {
        return lastCause;
    }

    public BurnerPhase burnerPhase(int b) {
        return phase[b];
    }

    /** Положение отсечного клапана основного топлива горелки, [0, 1]. */
    public double mainValve(int b) {
        return valve[b];
    }

    public boolean igniterOpen(int b) {
        BurnerPhase p = phase[b];
        return p == BurnerPhase.IGNITER_TRIAL || p == BurnerPhase.IGNITER_ON || p == BurnerPhase.MAIN_TRIAL;
    }

    /** Искра запальника (только на пробе). */
    public boolean spark(int b) {
        return phase[b] == BurnerPhase.IGNITER_TRIAL;
    }

    /** Показание датчика пламени горелки на последнем шаге. */
    public boolean flameDetected(int b) {
        return seen[b];
    }

    public double purgeElapsedS() {
        return purgeS;
    }

    public double purgeVolumeChanges() {
        return purgeChanges;
    }

    /** Байпас датчика пламени ключом: BMS считает, что факел есть. */
    public void setFlameDetectorBypass(int b, boolean on) {
        bypass[b] = on;
    }

    public void setFlameDetectorFault(int b, FlameDetectorFault f) {
        fault[b] = Objects.requireNonNull(f, "fault");
    }

    public boolean flameDetectorBypass(int b) {
        return bypass[b];
    }

    public FlameDetectorFault flameDetectorFault(int b) {
        return fault[b];
    }

    /** Время в текущем состоянии котла (окно запальника, пост-продувка), с. */
    public double stateTimerS() {
        return timer;
    }

    public void save(StateWriter out) {
        out.putInt("state", state.ordinal());
        out.putInt("cause", lastCause == null ? -1 : lastCause.ordinal());
        out.putDouble("timer", timer);
        out.putDouble("purge_s", purgeS);
        out.putDouble("purge_changes", purgeChanges);
        out.putBoolean("fans", fansRunning);
        int[] ph = new int[n], fl = new int[n];
        byte[] by = new byte[n];
        for (int b = 0; b < n; b++) {
            ph[b] = phase[b].ordinal();
            fl[b] = fault[b].ordinal();
            by[b] = (byte) ((bypass[b] ? 1 : 0) | (seen[b] ? 2 : 0));
        }
        out.putInts("phase", ph, n);
        out.putInts("fault", fl, n);
        out.putBytes("flags", by, n);
        out.putDoubles("phase_timer", phaseTimer, n);
        out.putDoubles("valve", valve, n);
    }

    /** Загрузка; число горелок в сохранении может отличаться — лишние отбрасываются, недостающие выключены. */
    public void load(StateReader in) {
        state = BmsState.values()[in.getInt("state")];
        int c = in.getInt("cause");
        lastCause = c < 0 ? null : MftCause.values()[c];
        timer = in.getDouble("timer");
        purgeS = in.getDouble("purge_s");
        purgeChanges = in.getDouble("purge_changes");
        fansRunning = in.getBoolean("fans");
        int[] ph = in.getInts("phase"), fl = in.getInts("fault");
        byte[] by = in.getBytes("flags");
        double[] pt = in.getDoubles("phase_timer"), va = in.getDoubles("valve");
        for (int b = 0; b < n; b++) {
            boolean has = b < ph.length;
            phase[b] = has ? BurnerPhase.values()[ph[b]] : BurnerPhase.OFF;
            fault[b] = has ? FlameDetectorFault.values()[fl[b]] : FlameDetectorFault.NONE;
            bypass[b] = has && (by[b] & 1) != 0;
            seen[b] = has && (by[b] & 2) != 0;
            phaseTimer[b] = has ? pt[b] : 0;
            valve[b] = has ? va[b] : 0;
        }
    }

    /** Продувка засчитана без 300 с ожидания — для тестов и отладки: из {@code OFF} сразу {@code PURGE_COMPLETE}. */
    public void presetPurged() {
        if (state != BmsState.OFF) return;
        purgeS = PURGE_MIN_S;
        purgeChanges = PURGE_MIN_VOLUME_CHANGES;
        timer = 0;
        setState(BmsState.PURGE_COMPLETE);
    }

    // --- команды оператора ---

    public BmsResponse requestPurge() {
        if (state != BmsState.OFF) return BmsResponse.INTERLOCK_STATE;
        if (!fansRunning) return BmsResponse.INTERLOCK_FANS;
        if (!valvesClosed()) return BmsResponse.INTERLOCK_FUEL_VALVES;
        purgeS = purgeChanges = 0;
        setState(BmsState.PURGE);
        return BmsResponse.ACCEPTED;
    }

    public BmsResponse requestIgniter(int b) {
        if (b < 0 || b >= n) return BmsResponse.INTERLOCK_BURNER;
        switch (state) {
            case OFF, PURGE -> {
                return BmsResponse.INTERLOCK_PURGE;
            }
            case PURGE_COMPLETE -> {
                startPhase(b, BurnerPhase.IGNITER_TRIAL);
                setState(BmsState.IGNITER_TRIAL);
                return BmsResponse.ACCEPTED;
            }
            case IGNITER_ON, RUNNING -> {
                if (phase[b] != BurnerPhase.OFF) return BmsResponse.INTERLOCK_STATE;
                startPhase(b, BurnerPhase.IGNITER_TRIAL);
                return BmsResponse.ACCEPTED;
            }
            default -> {
                return BmsResponse.INTERLOCK_STATE;
            }
        }
    }

    public BmsResponse requestMainFuel(int b) {
        if (b < 0 || b >= n) return BmsResponse.INTERLOCK_BURNER;
        if (state == BmsState.OFF || state == BmsState.PURGE || state == BmsState.PURGE_COMPLETE)
            return BmsResponse.INTERLOCK_PURGE;
        if (phase[b] != BurnerPhase.IGNITER_ON) return BmsResponse.INTERLOCK_STATE;
        if (state == BmsState.IGNITER_ON) {
            startPhase(b, BurnerPhase.MAIN_TRIAL);
            setState(BmsState.MAIN_TRIAL);
            return BmsResponse.ACCEPTED;
        }
        if (state == BmsState.RUNNING) {
            startPhase(b, BurnerPhase.MAIN_TRIAL);
            return BmsResponse.ACCEPTED;
        }
        return BmsResponse.INTERLOCK_STATE;
    }

    /** Останов оператором: в топливных состояниях — MFT с пост-продувкой, в продувке — сразу {@code OFF}. */
    public void requestShutdown() {
        if (state == BmsState.PURGE || state == BmsState.PURGE_COMPLETE) setState(BmsState.OFF);
        else if (fuelState()) masterFuelTrip(MftCause.OPERATOR);
    }

    /** ПАЗ котла (07 §7.18) или иная внешняя причина. */
    public void trip(MftCause cause) {
        if (fuelState()) masterFuelTrip(Objects.requireNonNull(cause, "cause"));
    }

    // --- шаг ---

    /**
     * Шаг FAST: датчики, автомат состояний, ход клапанов.
     *
     * @param fans              дымосос и дутьевой вентилятор работают
     * @param airFraction       расход воздуха в долях номинала
     * @param volumeChangesPerS смен объёма топки в секунду (объёмный расход воздуха / V_т)
     * @param igniterFlame      факел запальника по горелкам (физический)
     * @param mainFlame         основной факел по горелкам (физический)
     */
    public void step(double dt, boolean fans, double airFraction, double volumeChangesPerS, boolean[] igniterFlame,
            boolean[] mainFlame) {
        fansRunning = fans;
        for (int b = 0; b < n; b++) seen[b] = detector(b, igniterFlame[b], mainFlame[b]);
        if (!fans && fuelState()) masterFuelTrip(MftCause.FANS_LOST);

        switch (state) {
            case PURGE -> {
                if (!fans || !valvesClosed()) {
                    setState(BmsState.OFF);
                } else if (airFraction >= PURGE_MIN_AIR_FRACTION) {
                    purgeS += dt;
                    purgeChanges += volumeChangesPerS * dt;
                    if (purgeS >= PURGE_MIN_S && purgeChanges >= PURGE_MIN_VOLUME_CHANGES) {
                        timer = 0;
                        setState(BmsState.PURGE_COMPLETE);
                    }
                } else {
                    purgeS = purgeChanges = 0;
                }
            }
            case PURGE_COMPLETE -> {
                timer += dt;
                if (!fans || timer >= PURGE_COMPLETE_WINDOW_S) setState(BmsState.OFF);
            }
            case IGNITER_TRIAL, IGNITER_ON, MAIN_TRIAL, RUNNING -> stepBurners(dt);
            case MFT -> {
                if (valvesClosed()) {
                    timer = 0;
                    setState(BmsState.POST_PURGE);
                }
            }
            case POST_PURGE -> {
                if (fans) timer += dt;
                if (timer >= POST_PURGE_S) setState(BmsState.OFF);
            }
            case OFF -> {}
        }
        double stroke = dt / VALVE_STROKE_S;
        for (int b = 0; b < n; b++) {
            double target = phase[b] == BurnerPhase.MAIN_TRIAL || phase[b] == BurnerPhase.MAIN_ON ? 1 : 0;
            valve[b] = target > valve[b] ? Math.min(target, valve[b] + stroke) : Math.max(target, valve[b] - stroke);
        }
    }

    private void stepBurners(double dt) {
        for (int b = 0; b < n && fuelState(); b++) {
            phaseTimer[b] += dt;
            switch (phase[b]) {
                case IGNITER_TRIAL -> {
                    if (seen[b]) {
                        startPhase(b, BurnerPhase.IGNITER_ON);
                        if (state == BmsState.IGNITER_TRIAL) setState(BmsState.IGNITER_ON);
                    } else if (phaseTimer[b] >= IGNITER_TRIAL_S) {
                        if (state == BmsState.IGNITER_TRIAL) masterFuelTrip(MftCause.IGNITER_TRIAL_FAILED);
                        else burnerTrip(b);
                    }
                }
                case IGNITER_ON -> {
                    if (!seen[b]) burnerTrip(b);
                }
                case MAIN_TRIAL -> {
                    if (seen[b]) {
                        startPhase(b, BurnerPhase.MAIN_ON);
                        if (state == BmsState.MAIN_TRIAL) setState(BmsState.RUNNING);
                    } else if (phaseTimer[b] >= mainTrialS) {
                        if (state == BmsState.MAIN_TRIAL) masterFuelTrip(MftCause.MAIN_TRIAL_FAILED);
                        else burnerTrip(b);
                    }
                }
                case MAIN_ON -> {
                    if (!seen[b]) burnerTrip(b);
                }
                case OFF -> {}
            }
        }
        if (!fuelState()) return;
        boolean main = false, igniter = false;
        for (BurnerPhase p : phase) {
            main |= p == BurnerPhase.MAIN_ON;
            igniter |= p != BurnerPhase.OFF;
        }
        if ((state == BmsState.RUNNING && !main) || (state == BmsState.IGNITER_ON && !igniter))
            masterFuelTrip(MftCause.FLAME_LOSS);
    }

    private boolean detector(int b, boolean igniterFlame, boolean mainFlame) {
        if (bypass[b] || fault[b] == FlameDetectorFault.STUCK_FLAME) return true;
        if (fault[b] == FlameDetectorFault.STUCK_DARK) return false;
        BurnerPhase p = phase[b];
        return p == BurnerPhase.IGNITER_TRIAL || p == BurnerPhase.IGNITER_ON ? igniterFlame || mainFlame : mainFlame;
    }

    private void startPhase(int b, BurnerPhase p) {
        phase[b] = p;
        phaseTimer[b] = 0;
    }

    private void burnerTrip(int b) {
        startPhase(b, BurnerPhase.OFF);
        events.burnerTripped(b);
    }

    private void masterFuelTrip(MftCause cause) {
        Arrays.fill(phase, BurnerPhase.OFF);
        lastCause = cause;
        setState(BmsState.MFT);
        events.masterFuelTrip(cause);
    }

    private boolean fuelState() {
        return state == BmsState.IGNITER_TRIAL || state == BmsState.IGNITER_ON || state == BmsState.MAIN_TRIAL
                || state == BmsState.RUNNING;
    }

    private boolean valvesClosed() {
        for (int b = 0; b < n; b++) if (valve[b] > 0 || igniterOpen(b)) return false;
        return true;
    }

    private void setState(BmsState to) {
        if (to == state) return;
        BmsState from = state;
        state = to;
        events.bmsStateChanged(from, to);
    }
}
