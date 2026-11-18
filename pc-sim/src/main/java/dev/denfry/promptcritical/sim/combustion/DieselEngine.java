package dev.denfry.promptcritical.sim.combustion;

import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;
import java.util.SplittableRandom;

/**
 * Дизель аварийного ДГ (07 §7.11.2–7.11.4): пуск сжатым воздухом, вероятность отказа пуска, регулятор частоты,
 * турбонаддув, расход топлива. Моменты — в о.е. на номинальную полную мощность генератора S_ном при его
 * синхронной скорости; скорость ω — в о.е. той же машины. Инерцию агрегата интегрирует владелец (генератор сети):
 * двигатель только отдаёт момент {@link #torquePu} и раз в тик обновляет состояние {@link #step}.
 *
 * <p>Пуск (§7.11.3): воздушный стартер прокручивает вал до 120 об/мин из 750 ({@link #FIRING_SPEED}), затем вспышки —
 * здесь разыгрывается отказ пуска p_fs; при отказе — повторная попытка через 5 с, пока есть воздух. После вспышек
 * регулятор разгоняет двигатель; доступный момент ограничен давлением наддува, которое догоняет подачу топлива с
 * постоянной 1,5 с (§7.11.4): поэтому большая ступень нагрузки «сажает» частоту.
 *
 * <p>Случайность детерминирована: каждое испытание берёт число из {@link SplittableRandom} с зерном агрегата и
 * номером испытания, номер сохраняется.
 */
public final class DieselEngine {
    public enum Status { STANDBY, CRANKING, RETRY_WAIT, ACCELERATING, RUNNING, COASTING, LOCKED_OUT }

    /** Причина последнего отказа или останова. */
    public enum Cause { NONE, STOPPED, FAIL_TO_START, NO_AIR, NO_DC, NO_FUEL, RUN_FAILURE, OVERSPEED }

    public enum Governor { ISOCHRONOUS, DROOP }

    /** Номинальная мощность двигателя — 0,8 S_ном генератора (cos φ = 0,8), о.е. момента при ω = 1. */
    public static final double RATED_TORQUE_PU = 0.8;
    /** Предельный момент при полном наддуве: часовая перегрузка 10 % (ISO 8528-1, мощность PRP + 10 %) [моё]. */
    public static final double MAX_TORQUE_PU = RATED_TORQUE_PU * 1.1;
    /** Доля предельного момента без наддува: T_lim = T_max(0,3 + 0,7 b_тк) (§7.11.4). */
    static final double UNBOOSTED_FRACTION = 0.3;
    /** Постоянная турбонаддува, с (§7.11.3–7.11.4). */
    public static final double TURBO_TAU_S = 1.5;
    /** Вспышки начинаются на 120 об/мин из 750 (§7.11.3). */
    public static final double FIRING_SPEED = 120.0 / 750;
    /** Момент воздушного стартера: до 120 об/мин за ≈ 2,5 с при H = 1 с (§7.11.3: 2–3 с). */
    static final double STARTER_TORQUE_PU = 0.13;
    /** Стартер, не прокрутивший вал за это время, — отказ пуска, с. */
    static final double CRANK_TIMEOUT_S = 8;
    /** Повторная попытка после отказа пуска, с (§7.11.3). */
    public static final double RETRY_DELAY_S = 5;
    /** Двигатель считается разогнанным, о.е. скорости (90 % оборотов, §7.11.3). */
    public static final double RUN_SPEED = 0.9;
    /** Автомат безопасности по разносу, о.е. [моё: типичная уставка 115 %]. */
    public static final double OVERSPEED_TRIP = 1.15;
    /** Баллоны пускового воздуха 3 МПа: не меньше пяти пусков (§7.11.2). */
    public static final int AIR_STARTS = 5;
    /** Компрессор агрегата восполняет один пуск за столько секунд работы [моё]. */
    static final double AIR_RECHARGE_S = 60;
    /** b_min, кг/(кВт·ч) (§7.11.2). */
    public static final double B_MIN_KG_PER_KWH = 0.205;
    /** Интервал обслуживания T_ТО, физ. с (30 суток, §7.11.3). */
    public static final double MAINTENANCE_INTERVAL_S = 30 * 86_400.0;
    /** Отказ работы под нагрузкой, 1/ч (§7.11.3). */
    public static final double RUN_FAILURE_PER_HOUR = 1e-3;
    /** Без подогрева рубашки ниже 5 °C отказ пуска вчетверо вероятнее (§7.11.3). */
    static final double COLD_START_C = 5, COLD_FACTOR = 4;
    /** Цетановое число, ниже которого растёт задержка воспламенения (§7.11.3), и порог отказа 0,3 (09 §9.15.8). */
    static final double CETANE_REFERENCE = 45, CETANE_HARD_FAIL = 38, CETANE_HARD_FAIL_P = 0.3;
    /** Регулятор: пропорциональная часть — 5 % статизма, интеграл, 1/с [моё, настроено на критерии IEEE 387]. */
    static final double ISO_KP = 20, ISO_KI = 8;
    /** Статизм при параллельной работе (§7.2.5: 4–5 %). */
    public static final double DROOP = 0.04;

    private final double ratedVa;
    private final long seed;
    /** p₀ — вероятность отказа пуска на требование [BALANCE {@code diesel.fail_to_start_base}]. */
    private double failToStartBase = 0.005;

    private Status status = Status.STANDBY;
    private Cause cause = Cause.NONE;
    private Governor governor = Governor.ISOCHRONOUS;
    private double speedReference = 1.0;
    private int airStarts = AIR_STARTS;
    private double integral, boost, timer, airCharge, runClock;
    private double secondsSinceTest;
    private long trials;
    private int starts, failures;
    private boolean tripped;

    // условия на этот тик от владельца
    private boolean fuel, dc;
    private double airTempC = 15, cetane = CETANE_REFERENCE;
    private boolean jacketHeater;
    // итоги последнего шага
    private double lastTorque, lastFuelKgS;

    /**
     * @param ratedVa номинальная полная мощность генератора, В·А (база о.е.)
     * @param seed    зерно испытаний агрегата (например, его идентификатор)
     */
    public DieselEngine(double ratedVa, long seed) {
        if (!(ratedVa > 0)) throw new IllegalArgumentException("ratedVa");
        this.ratedVa = ratedVa;
        this.seed = seed;
    }

    // --- условия и команды ---

    /** Условия тика: топливо в расходном баке, оперативный ток, воздух у агрегата, подогрев рубашки, ЦЧ топлива. */
    public void setConditions(boolean fuel, boolean dc, double airTempC, boolean jacketHeater, double cetane) {
        this.fuel = fuel;
        this.dc = dc;
        this.airTempC = airTempC;
        this.jacketHeater = jacketHeater;
        this.cetane = Double.isNaN(cetane) ? CETANE_REFERENCE : cetane;
    }

    public void setFailToStartBase(double p) {
        failToStartBase = Math.max(0, Math.min(1, p));
    }

    /**
     * Сигнал пуска (LOOP, опробование, команда). Принимается только из резерва: без оперативного тока или воздуха
     * пуск не идёт, и это записывается причиной.
     *
     * @return стартер включён
     */
    public boolean start() {
        if (status != Status.STANDBY) return false;
        if (!dc) return refuse(Cause.NO_DC);
        if (airStarts < 1) return refuse(Cause.NO_AIR);
        crank();
        cause = Cause.NONE;
        return true;
    }

    private boolean refuse(Cause c) {
        cause = c;
        return false;
    }

    private void crank() {
        airStarts--;
        status = Status.CRANKING;
        timer = 0;
    }

    /** Останов: рейка на ноль, двигатель выбегает в резерв. */
    public void stop() {
        if (status == Status.STANDBY || status == Status.LOCKED_OUT || status == Status.COASTING) return;
        status = Status.COASTING;
        cause = Cause.STOPPED;
        tripped = false;
    }

    /** Квитирование блокировки после отказа: агрегат снова в резерве (если уже остановился). */
    public boolean reset() {
        if (status != Status.LOCKED_OUT) return false;
        status = Status.STANDBY;
        cause = Cause.NONE;
        return true;
    }

    public void setGovernor(Governor g) {
        governor = g;
        integral = Math.min(integral, 1);
    }

    /** Уставка скорости регулятора со статизмом, о.е. (прибавить/убавить): нагрузка при параллельной работе. */
    public void setSpeedReference(double pu) {
        speedReference = Math.max(0.95, Math.min(1.05, pu));
    }

    /** Техническое обслуживание: сбрасывает «пропуск ТО» и заправляет баллоны. */
    public void service() {
        secondsSinceTest = 0;
        airStarts = AIR_STARTS;
    }

    // --- динамика ---

    /** Рейка топливного насоса при скорости ω, доля предельного момента. */
    private double rack(double w) {
        double r = switch (governor) {
            case ISOCHRONOUS -> integral + ISO_KP * (1 - w);
            case DROOP -> (speedReference - w) / DROOP * RATED_TORQUE_PU / MAX_TORQUE_PU;
        };
        return Math.max(0, Math.min(1, r));
    }

    private boolean firing() {
        return status == Status.ACCELERATING || status == Status.RUNNING;
    }

    /** Момент на валу при скорости ω, о.е. (без трения генератора — его считает владелец). */
    public double torquePu(double w) {
        if (status == Status.CRANKING) return STARTER_TORQUE_PU;
        if (!firing()) return 0;
        double limit = MAX_TORQUE_PU * (UNBOOSTED_FRACTION + (1 - UNBOOSTED_FRACTION) * boost);
        return Math.min(rack(w) * MAX_TORQUE_PU, limit);
    }

    /** Шаг тика при скорости агрегата ω: регулятор, наддув, пуск и отказы, расход топлива. */
    public void step(double dt, double w) {
        secondsSinceTest += dt;
        switch (status) {
            case CRANKING -> {
                timer += dt;
                if (w >= FIRING_SPEED) {
                    if (trial() < failToStartProbability()) failStart();
                    else {
                        status = Status.ACCELERATING;
                        integral = 0; // на разгоне рейку открывает пропорциональная часть (упор до 0,95 ω)
                        starts++;
                    }
                } else if (timer > CRANK_TIMEOUT_S) failStart();
            }
            case RETRY_WAIT -> {
                timer += dt;
                if (timer >= RETRY_DELAY_S) {
                    if (!dc) lockOut(Cause.NO_DC);
                    else if (airStarts < 1) lockOut(Cause.NO_AIR);
                    else crank();
                }
            }
            case ACCELERATING, RUNNING -> stepFiring(dt, w);
            case COASTING -> {
                if (w < 0.01) {
                    status = tripped ? Status.LOCKED_OUT : Status.STANDBY;
                    tripped = false;
                }
            }
            case STANDBY, LOCKED_OUT -> {
            }
        }
        if (!firing()) {
            boost += (0 - boost) * (1 - Math.exp(-dt / TURBO_TAU_S));
            integral = 0;
        }
        lastTorque = torquePu(w);
        double kw = firing() ? Math.max(0, lastTorque * w * ratedVa / 1e3) : 0;
        lastFuelKgS = fuelKgPerHour(kw, RATED_TORQUE_PU * ratedVa / 1e3) / 3600;
    }

    /** Часовой расход при мощности {@code kw}: b(P) = b_min[1 + 0,3(1 − P/P_н)²] (§7.11.2), кг/ч. */
    public static double fuelKgPerHour(double kw, double ratedKw) {
        double x = 1 - Math.min(1, Math.max(0, kw) / ratedKw);
        return B_MIN_KG_PER_KWH * (1 + 0.3 * x * x) * Math.max(0, kw);
    }

    private void stepFiring(double dt, double w) {
        if (!fuel) {
            trip(Cause.NO_FUEL);
            return;
        }
        if (w > OVERSPEED_TRIP) {
            trip(Cause.OVERSPEED);
            return;
        }
        if (status == Status.ACCELERATING && w >= RUN_SPEED) {
            status = Status.RUNNING;
            secondsSinceTest = 0; // удачный пуск — опробование (§7.11.3)
        }
        if (governor == Governor.ISOCHRONOUS) {
            double e = 1 - w;
            double unclamped = integral + ISO_KP * e;
            // интегратор не накапливает, пока рейка упёрта в ограничение в ту же сторону
            if (!(unclamped >= 1 && e > 0 || unclamped <= 0 && e < 0)) integral += ISO_KI * e * dt;
            integral = Math.max(-1, Math.min(1, integral));
        }
        double target = rack(w);
        boost += (target - boost) * (1 - Math.exp(-dt / TURBO_TAU_S));
        airCharge += dt;
        if (airCharge >= AIR_RECHARGE_S) {
            airCharge -= AIR_RECHARGE_S;
            if (airStarts < AIR_STARTS) airStarts++;
        }
        runClock += dt;
        while (runClock >= 1) {
            runClock -= 1;
            if (trial() < RUN_FAILURE_PER_HOUR / 3600) {
                trip(Cause.RUN_FAILURE);
                return;
            }
        }
    }

    private void failStart() {
        failures++;
        cause = Cause.FAIL_TO_START;
        status = Status.RETRY_WAIT;
        timer = 0;
    }

    private void lockOut(Cause c) {
        cause = c;
        status = Status.LOCKED_OUT;
    }

    private void trip(Cause c) {
        cause = c;
        status = Status.COASTING;
        tripped = true;
        failures++;
    }

    private double trial() {
        return new SplittableRandom(seed ^ (++trials * 0x9E3779B97F4A7C15L)).nextDouble();
    }

    /**
     * p_fs = p₀·(1 + 4·max(0, (t − T_ТО)/T_ТО))·(1 + max(0, 45 − ЦЧ)/5)·k_хол (§7.11.3); «пропуск ТО» — только
     * просрочка сверх интервала. ЦЧ ниже 38 добавляет 0,3 (09 §9.15.8).
     */
    public double failToStartProbability() {
        double overdue = Math.max(0, (secondsSinceTest - MAINTENANCE_INTERVAL_S) / MAINTENANCE_INTERVAL_S);
        double cold = airTempC < COLD_START_C && !jacketHeater ? COLD_FACTOR : 1;
        double p = failToStartBase * (1 + 4 * overdue) * (1 + Math.max(0, CETANE_REFERENCE - cetane) / 5) * cold;
        if (cetane < CETANE_HARD_FAIL) p += CETANE_HARD_FAIL_P;
        return Math.min(1, p);
    }

    // --- показания ---

    public Status status() {
        return status;
    }

    public Cause cause() {
        return cause;
    }

    public Governor governor() {
        return governor;
    }

    public double speedReference() {
        return speedReference;
    }

    /** Двигатель на топливе (разгоняется или работает). */
    public boolean fired() {
        return firing();
    }

    public int airStarts() {
        return airStarts;
    }

    /** Давление наддува, доля номинального. */
    public double boost() {
        return boost;
    }

    /** Момент последнего шага, о.е. */
    public double torquePu() {
        return lastTorque;
    }

    /** Расход дизтоплива последнего шага, кг/с. */
    public double fuelKgS() {
        return lastFuelKgS;
    }

    public double secondsSinceTest() {
        return secondsSinceTest;
    }

    /** Удачных пусков. */
    public int starts() {
        return starts;
    }

    /** Отказов пуска и работы. */
    public int failures() {
        return failures;
    }

    // --- сохранение ---

    public void save(StateWriter out) {
        out.putString("status", status.name());
        out.putString("cause", cause.name());
        out.putString("governor", governor.name());
        out.putDouble("ref", speedReference);
        out.putInt("air", airStarts);
        out.putDouble("integral", integral);
        out.putDouble("boost", boost);
        out.putDouble("timer", timer);
        out.putDouble("air_charge", airCharge);
        out.putDouble("run_clock", runClock);
        out.putDouble("since_test", secondsSinceTest);
        out.putLong("trials", trials);
        out.putInt("starts", starts);
        out.putInt("failures", failures);
        out.putBoolean("tripped", tripped);
    }

    public void load(StateReader in) {
        status = Status.valueOf(in.getString("status"));
        cause = Cause.valueOf(in.getString("cause"));
        governor = Governor.valueOf(in.getString("governor"));
        speedReference = in.getDouble("ref");
        airStarts = in.getInt("air");
        integral = in.getDouble("integral");
        boost = in.getDouble("boost");
        timer = in.getDouble("timer");
        airCharge = in.getDouble("air_charge");
        runClock = in.getDouble("run_clock");
        secondsSinceTest = in.getDouble("since_test");
        trials = in.getLong("trials");
        starts = in.getInt("starts");
        failures = in.getInt("failures");
        tripped = in.getBoolean("tripped");
    }
}
