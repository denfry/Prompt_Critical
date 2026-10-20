package dev.denfry.promptcritical.sim.grid;

import java.util.Objects;

/**
 * Расцепитель автомата или плавкая вставка 0,4 кВ (07 §7.4.6): тепловой t = k_т/((I/I_н)² − 1) при
 * I > 1,13 I_н и электромагнитный — 1 тик при I > m·I_н. Действует прямо на механизм, без оперативного
 * тока.
 *
 * @param ratedA   номинал I_н, А
 * @param thermalKS k_т, с ({@code grid.lv_breaker_thermal_k_s})
 * @param fuse     плавкая вставка: перегорает и меняется вручную
 */
public record LvTripUnit(double ratedA, Curve curve, double thermalKS, boolean fuse) {
    /** Порог теплового расцепителя, кратность I_н. */
    public static final double THERMAL_PICKUP = 1.13;
    /** Остывание биметалла: накопленный нагрев уходит за столько секунд ниже порога [BALANCE]. */
    static final double COOL_S = 60;

    /** Характеристика мгновенного расцепления: B — 5 I_н, C — 10 (по умолчанию), D — 20 (тяжёлый пуск). */
    public enum Curve {
        B(5), C(10), D(20);

        public final double magneticMultiple;

        Curve(double m) {
            magneticMultiple = m;
        }
    }

    public LvTripUnit {
        Objects.requireNonNull(curve, "curve");
        if (!(ratedA > 0 && thermalKS > 0)) throw new IllegalArgumentException("settings");
    }

    public static LvTripUnit breaker(double ratedA, Curve curve, GridConfig cfg) {
        return new LvTripUnit(ratedA, curve, cfg.lvBreakerThermalKS(), false);
    }

    public static LvTripUnit fuse(double ratedA, GridConfig cfg) {
        return new LvTripUnit(ratedA, Curve.C, cfg.lvBreakerThermalKS(), true);
    }

    /** Время отключения при токе I, с: 0 — мгновенно (1 тик), бесконечность — не отключит. */
    public double tripTimeS(double currentA) {
        double m = currentA / ratedA;
        if (m > curve.magneticMultiple) return 0;
        if (m <= THERMAL_PICKUP) return Double.POSITIVE_INFINITY;
        return thermalKS / (m * m - 1);
    }

    /** Накопленный нагрев {@code heat} (1 — отключение) за тик; результат — новое значение. */
    double heat(double heat, double currentA, double dt) {
        double t = tripTimeS(currentA);
        if (t == 0) return 1;
        if (Double.isInfinite(t)) return Math.max(0, heat - dt / COOL_S);
        return heat + dt / t;
    }
}
