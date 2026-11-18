package dev.denfry.promptcritical.sim.economy;

/**
 * Цена товара: тренд плюс процесс Орнштейна — Уленбека для логарифма (однофакторная модель Шварца, 1997; 14 §14.6.2):
 * ln p_t = ln p̄_t + x_t, p̄_t = p̄₀·(1 + g)^{t/T_per}. Шаг дискретный и точный:
 * x_{n+1} = x_n·e^{−θΔt} + σ·√((1 − e^{−2θΔt})/(2θ))·ξ. Шум ξ ~ N(0,1) берётся детерминированно из (зерно мира, товар,
 * номер шага) — состояние процесса сводится к (x, n) и сохраняется без состояния генератора.
 */
public final class PriceProcess {
    private PriceProcess() {}

    /** Одна точная итерация OU-шага. */
    public static double step(double x, double theta, double sigma, double dtS, double xi) {
        double decay = Math.exp(-theta * dtS);
        double sd = sigma * Math.sqrt(-Math.expm1(-2 * theta * dtS) / (2 * theta));
        return x * decay + sd * xi;
    }

    /** Трендовая цена p̄_t = p̄₀·(1 + g)^{t/T_per}. */
    public static double trendPrice(double basePrice, double trend, double tPhysS, double periodS) {
        return basePrice * Math.pow(1 + trend, tPhysS / periodS);
    }

    /** Детерминированное N(0,1) по зерну, ключу товара и номеру шага (SplitMix64 + Бокс — Мюллер). */
    public static double gaussian(long seed, String key, long stepIndex) {
        long h = mix(seed ^ mix(key.hashCode() * 0x9E3779B97F4A7C15L) ^ mix(stepIndex + 0x632BE59BD9B4E019L));
        long h2 = mix(h + 0x9E3779B97F4A7C15L);
        double u1 = ((h >>> 11) + 1) * 0x1.0p-53; // (0, 1]
        double u2 = (h2 >>> 11) * 0x1.0p-53;
        return Math.sqrt(-2 * Math.log(u1)) * Math.cos(2 * Math.PI * u2);
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
