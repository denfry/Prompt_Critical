package dev.denfry.promptcritical.sim.enrichment;

/**
 * Ядерная безопасность ёмкостей с обогащённым ураном. Сухой UF₆ сам не критичен: нужен замедлитель. Но влага воздуха
 * при натекании гидролизует UF₆ до UO₂F₂ с водой, и тогда предел задаёт минимальная критическая масса U-235 в
 * оптимально замедленном растворе с отражателем: 0,78 кг при 93 %, около 1 кг при 20 %, около 2 кг при 5 %; ниже
 * ≈ 0,95 % однородная смесь с водой не критична вовсе [аппроксимация кривых LA-10860 (Paxton, Pruvost, 1987) и
 * ARH-600]. Безопасная масса — 0,45 от минимальной критической (двойной запас по ГОСТ/ПБЯ-06-00-96 и ANSI/ANS-8.1).
 *
 * <p>Самоподдерживающаяся цепная реакция (СЦР) в ёмкости — вспышка мощности на 10¹⁷ делений и больше (Токаймура,
 * 1999: 2,5·10¹⁸ за 20 ч), без взрыва, но с нейтронной и гамма-дозой. Дозу считает общая модель излучения:
 * источник вспышки — {@link dev.denfry.promptcritical.sim.radiation.FissionSource#solutionBurst}, ослабление стенами
 * и расстоянием — {@link dev.denfry.promptcritical.sim.radiation.DoseRate}.
 */
public final class Criticality {
    private static final double[][] MIN_CRITICAL = {
            {0.0095, Double.POSITIVE_INFINITY}, {0.02, 8.0}, {0.03, 3.6}, {0.05, 2.1}, {0.10, 1.35}, {0.20, 1.05}, {0.50, 0.85},
            {0.93, 0.78}, {1.0, 0.76}};
    public static final double SAFE_SHARE = 0.45, FISSIONS_REF = 1e17, PU239_MIN_CRITICAL_KG = 0.51;

    private Criticality() {
    }

    /** Минимальная критическая масса U-235 при обогащении {@code x}, кг (бесконечность — не критично). */
    public static double minCriticalU235Kg(double x) {
        if (x < MIN_CRITICAL[0][0]) return Double.POSITIVE_INFINITY;
        for (int i = 1; i < MIN_CRITICAL.length; i++) {
            if (x <= MIN_CRITICAL[i][0]) {
                double[] a = MIN_CRITICAL[i - 1], b = MIN_CRITICAL[i];
                if (Double.isInfinite(a[1])) {
                    // от порога критичности масса падает как 1/(x − x₀)
                    double c = b[1] * (b[0] - a[0]);
                    return c / Math.max(x - a[0], 1e-9);
                }
                double t = Math.log(x / a[0]) / Math.log(b[0] / a[0]);
                return Math.exp(Math.log(a[1]) + t * (Math.log(b[1]) - Math.log(a[1])));
            }
        }
        return MIN_CRITICAL[MIN_CRITICAL.length - 1][1];
    }

    /**
     * Минимальная критическая масса Pu-239 в оптимально замедленном растворе нитрата плутония с водяным отражателем:
     * 0,51 кг (LA-10860, Paxton & Pruvost 1987; ARH-600). Pu-240 поглощает нейтроны и поднимает её: на каждый процент
     * Pu-240 сверх 5 % — около 1,3 % (ANSI/ANS-8.15, оценка); Pu-241 делится и считается как Pu-239.
     */
    public static double minCriticalPuKg(double pu240Fraction) {
        return PU239_MIN_CRITICAL_KG * (1 + 1.3 * Math.max(0, pu240Fraction - 0.05));
    }

    /** Безопасная масса плутония в аппарате — та же доля {@link #SAFE_SHARE} от минимальной критической. */
    public static double safePuKg(double pu240Fraction) {
        return SAFE_SHARE * minCriticalPuKg(pu240Fraction);
    }

    public static double safeU235Kg(double x) {
        return SAFE_SHARE * minCriticalU235Kg(x);
    }

    /** Делений во вспышке: растёт с избытком массы над критической, не больше 10¹⁹. */
    public static double excursionFissions(double u235Kg, double x) {
        return excursionFissionsOver(u235Kg, minCriticalU235Kg(x));
    }

    /** Делений во вспышке для массы делящегося {@code kg} при минимальной критической {@code minKg}. */
    public static double excursionFissionsOver(double kg, double minKg) {
        if (!(kg > minKg)) return 0;
        return Math.min(1e19, FISSIONS_REF * (1 + 10 * (kg / minKg - 1)));
    }
}
