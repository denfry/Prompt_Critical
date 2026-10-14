package dev.denfry.promptcritical.sim.combustion;

/**
 * Центробежный регулятор Уатта (07 §7.9.7) — механическое звено 2-го порядка, пропорциональное со статизмом:
 * ẍ + 2ζω_g·ẋ + ω_g²·(x − x_eq) = 0, y = 1 − x, где x ∈ [0, 1] — подъём муфты между упорами, y — открытие
 * дроссельной заслонки.
 *
 * <p>Отклонение от записи 07: там x_eq = K_g·ω²/ω₀², что при K_g под статизм 5 % (≈ 10) закрывает заслонку уже
 * на ω₀. Здесь подъём отсчитывается от нижнего упора: x_eq = K_g·(ω²/ω₀² − (1 − δ/2)²), K_g = 1/(2δ). Заслонка
 * открыта полностью при ω ≤ ω₀(1 − δ/2) и закрыта при ω ≥ ω₀(1 + δ/2), так что ω₀ — середина полосы статизма.
 * Без масляного катаракта ζ = 0,1, и регулятор «качает» обороты (Максвелл, «On Governors», 1868); модуль
 * {@code governor_dashpot} даёт ζ = 0,7.
 */
public final class FlyballGovernor {
    /** Собственная частота ω_g, рад/с. */
    public static final double NATURAL_FREQUENCY = 2 * Math.PI;
    /** Демпфирование без катаракта и с ним. */
    public static final double ZETA_BARE = 0.1, ZETA_DASHPOT = 0.7;
    /** Статизм δ по умолчанию. */
    public static final double DROOP = 0.05;

    private final double droop, gain, lowSq;
    private double zeta, x, v;

    public FlyballGovernor(double droop, double zeta) {
        if (!(droop > 0 && droop < 0.5)) throw new IllegalArgumentException("droop: " + droop);
        this.droop = droop;
        this.gain = 1 / (2 * droop);
        this.lowSq = (1 - droop / 2) * (1 - droop / 2);
        setDamping(zeta);
    }

    /** Регулятор без катаракта, статизм 5 %. */
    public static FlyballGovernor bare() {
        return new FlyballGovernor(DROOP, ZETA_BARE);
    }

    /** С катарактом {@code governor_dashpot}. */
    public static FlyballGovernor withDashpot() {
        return new FlyballGovernor(DROOP, ZETA_DASHPOT);
    }

    public double droop() {
        return droop;
    }

    /** Установка или снятие катаракта меняет ζ на ходу. */
    public void setDamping(double zeta) {
        if (!(zeta >= 0)) throw new IllegalArgumentException("zeta: " + zeta);
        this.zeta = zeta;
    }

    public double damping() {
        return zeta;
    }

    /** Подъём муфты x ∈ [0, 1]. */
    public double sleeve() {
        return x;
    }

    /** Открытие заслонки y ∈ [0, 1]. */
    public double throttle() {
        return 1 - x;
    }

    /** Равновесный подъём при скорости ω (без упоров). */
    public double equilibriumSleeve(double omega, double omega0) {
        double r = omega / omega0;
        return gain * (r * r - lowSq);
    }

    /** Муфта в покое в равновесии для ω (начальные условия). */
    public void settle(double omega, double omega0) {
        x = clamp01(equilibriumSleeve(omega, omega0));
        v = 0;
    }

    /**
     * Шаг: демпфирование неявно, упругость явно (полунеявный Эйлер); на упорах скорость гасится.
     *
     * @return открытие заслонки y
     */
    public double step(double dtS, double omega, double omega0) {
        double wg = NATURAL_FREQUENCY;
        v = (v - dtS * wg * wg * (x - equilibriumSleeve(omega, omega0))) / (1 + 2 * zeta * wg * dtS);
        x += dtS * v;
        if (x < 0 || x > 1) {
            x = clamp01(x);
            v = 0;
        }
        return 1 - x;
    }

    private static double clamp01(double a) {
        return a < 0 ? 0 : a > 1 ? 1 : a;
    }
}
