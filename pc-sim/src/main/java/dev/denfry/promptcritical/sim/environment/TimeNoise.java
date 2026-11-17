package dev.denfry.promptcritical.sim.environment;

/**
 * Гладкий детерминированный шум времени: случайные значения в целых узлах (хеш от зерна и номера узла),
 * кубическая эрмитова сплайновая интерполяция между ними. Значение — чистая функция (зерно, t): не зависит от
 * порядка запросов, загруженных чанков и числа игроков, поэтому хеш состояния симуляции воспроизводим.
 * Среднее 0, СКО ≈ {@link #SIGMA}; корреляция спадает за ≈ 1 единицу t.
 */
final class TimeNoise {
    /** СКО значения при равномерных узлах на [−1, 1] и интерполяции Катмулла–Рома (численно, 10⁶ отсчётов). */
    static final double SIGMA = 0.521;

    private final long seed;

    TimeNoise(long seed) {
        this.seed = seed;
    }

    /** Значение в момент {@code t} (в единицах шага узлов), примерно в [−1, 1]. */
    double at(double t) {
        double f = Math.floor(t);
        long i = (long) f;
        double a = t - f;
        double p0 = node(i - 1), p1 = node(i), p2 = node(i + 1), p3 = node(i + 2);
        // Catmull–Rom: проходит через узлы, первая производная непрерывна
        double m1 = 0.5 * (p2 - p0), m2 = 0.5 * (p3 - p1);
        double a2 = a * a, a3 = a2 * a;
        return (2 * a3 - 3 * a2 + 1) * p1 + (a3 - 2 * a2 + a) * m1 + (-2 * a3 + 3 * a2) * p2 + (a3 - a2) * m2;
    }

    /** Нормированное значение: СКО ≈ 1. */
    double normal(double t) {
        return at(t) / SIGMA;
    }

    private double node(long i) {
        long z = seed + i * 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z ^= z >>> 31;
        return (z >>> 11) * 0x1.0p-53 * 2 - 1;
    }
}
