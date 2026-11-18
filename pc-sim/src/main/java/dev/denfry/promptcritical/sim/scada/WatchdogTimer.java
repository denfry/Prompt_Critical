package dev.denfry.promptcritical.sim.scada;

/**
 * Сторожевое реле {@code watchdog_relay} (03c §3c.7.2): программа регулятора «кормит» реле {@code kick()} после каждой
 * успешной итерации; если корма нет дольше {@code timeout}, реле срабатывает в тот же шаг и держит срабатывание до
 * сброса. Владелец — компьютер, первым взведший реле; команды не-владельца отклоняются ({@code E_ACCESS: NOT_MASTER},
 * T-3c-12). Резервный компьютер перехватывает владение только у сработавшего реле ({@code takeover}, §3c.7.3): «молчание»
 * ведущего может быть временным, а два регулятора на одном клапане хуже, чем ни одного.
 *
 * <p>Таймаут {@value #DEFAULT_TIMEOUT_S} с по умолчанию, диапазон {@value #MIN_TIMEOUT_S}–{@value #MAX_TIMEOUT_S} с
 * (таблица §3c.7.2). Время — физическое, секунды; детерминировано.
 */
public final class WatchdogTimer {
    public static final double DEFAULT_TIMEOUT_S = 2.0, MIN_TIMEOUT_S = 0.5, MAX_TIMEOUT_S = 60;
    /** Владельца нет. */
    public static final int NO_OWNER = -1;
    private static final double EPS = 1e-9;

    public enum State { DISARMED, ARMED, TRIPPED }

    /** Отказ команды: не владелец или неверное состояние. */
    public static final class AccessException extends RuntimeException {
        public AccessException(String message) {
            super(message);
        }
    }

    private State state = State.DISARMED;
    private double timeoutS = DEFAULT_TIMEOUT_S, lastKick = Double.NaN, trippedAt = Double.NaN;
    private int owner = NO_OWNER, trips;

    public State state() {
        return state;
    }

    public double timeoutS() {
        return timeoutS;
    }

    public int owner() {
        return owner;
    }

    public double lastKick() {
        return lastKick;
    }

    public double trippedAt() {
        return trippedAt;
    }

    public int trips() {
        return trips;
    }

    public boolean tripped() {
        return state == State.TRIPPED;
    }

    public void configure(double timeout) {
        if (!(timeout >= MIN_TIMEOUT_S - EPS && timeout <= MAX_TIMEOUT_S + EPS))
            throw new IllegalArgumentException("timeout must be " + MIN_TIMEOUT_S + ".." + (int) MAX_TIMEOUT_S + " s");
        timeoutS = timeout;
    }

    private void owned(int who) {
        if (owner != NO_OWNER && owner != who) throw new AccessException("E_ACCESS: NOT_MASTER");
    }

    /** Взвести: первый взводящий становится владельцем; отсчёт — с этого момента. */
    public void arm(int who, double t) {
        owned(who);
        if (state == State.TRIPPED) throw new AccessException("E_STATE: relay tripped, reset it first");
        owner = who;
        state = State.ARMED;
        lastKick = t;
    }

    /** Корм: только владелец, только взведённое реле. */
    public void kick(int who, double t) {
        owned(who);
        if (state != State.ARMED) throw new AccessException("E_STATE: relay is " + state);
        lastKick = t;
    }

    /** Сброс срабатывания владельцем: реле снято, выход отпущен. */
    public void reset(int who) {
        owned(who);
        state = State.DISARMED;
        trippedAt = Double.NaN;
    }

    /** Перехват владения сработавшего реле резервным компьютером (§3c.7.3). */
    public void takeover(int who) {
        if (state != State.TRIPPED) throw new AccessException("E_STATE: takeover only after the relay has tripped");
        owner = who;
    }

    /** Отпустить реле: снять владельца и взвод (компьютер ушёл штатно). */
    public void release(int who) {
        owned(who);
        owner = NO_OWNER;
        if (state == State.ARMED) state = State.DISARMED;
    }

    /** Шаг на момент {@code t}; {@code true} — реле сработало в этот шаг. */
    public boolean step(double t) {
        if (state != State.ARMED || t - lastKick < timeoutS - EPS) return false;
        state = State.TRIPPED;
        trippedAt = t;
        trips++;
        return true;
    }

    /** Сколько секунд прошло с последнего корма. */
    public double sinceKick(double t) {
        return Double.isNaN(lastKick) ? Double.NaN : t - lastKick;
    }

    public void load(State s, double timeout, double lastKick, double trippedAt, int owner, int trips) {
        this.state = s;
        this.timeoutS = timeout;
        this.lastKick = lastKick;
        this.trippedAt = trippedAt;
        this.owner = owner;
        this.trips = trips;
    }
}
