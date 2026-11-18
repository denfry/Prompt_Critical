package dev.denfry.promptcritical.sim.grid;

import java.util.SplittableRandom;

/**
 * Суточный график нагрузки NPC-потребителя (07 §7.6.4): $P(t) = P_{дог}\,\hat p(h)\,k_T\,(1 + \xi(t))$ — зимний рабочий
 * день с ночным провалом ≈ 0,6 и утренним и вечерним пиками, температурная поправка отопления и шум
 * Орнштейна–Уленбека.
 */
public final class DailyLoadProfile {
    /** Шум ξ: σ = 2 %, τ = 10 мин физ. (§7.6.4). */
    public static final double NOISE_SIGMA = 0.02, NOISE_TAU_S = 600;
    /** Отопление: +2 % на градус ниже 15 °C (§7.6.4, [BALANCE]). */
    public static final double HEATING_PER_K = 0.02, HEATING_BELOW_C = 15;
    private static final double MAX = maxShape();

    private DailyLoadProfile() {
    }

    /** Ненормированная форма p(h), §7.6.4. */
    static double raw(double h) {
        return 0.84 + 0.12 * bump(h, 9, 2) + 0.20 * bump(h, 19.5, 2.2) - 0.22 * bump(h, 3.5, 2.5);
    }

    private static double bump(double h, double h0, double w) {
        double d = Math.abs(h - h0) % 24;
        d = Math.min(d, 24 - d);
        double x = d / w;
        return Math.exp(-x * x);
    }

    private static double maxShape() {
        double m = 0;
        for (int i = 0; i < 2400; i++) m = Math.max(m, raw(i / 100.0));
        return m;
    }

    /** $\hat p(h) = p(h)/\max p$, час суток 0…24. */
    public static double shape(double hour) {
        return raw(hour) / MAX;
    }

    /** $k_T = 1 + 0{,}02\max(0, 15 - T_a)$. */
    public static double temperatureFactor(double ambientC) {
        return 1 + HEATING_PER_K * Math.max(0, HEATING_BELOW_C - ambientC);
    }

    /**
     * Час суток Minecraft: $h = (\text{dayTime}/1000 + 6) \bmod 24$ (0 тиков — 06:00); при {@code daysPerCycle} > 1
     * один цикл графика растянут на столько дней MC ({@code grid.load_profile_days_per_cycle}, §7.17).
     */
    public static double hourOfDay(long dayTime, int daysPerCycle) {
        double t = (double) dayTime / Math.max(1, daysPerCycle);
        double h = (t / 1000.0 + 6) % 24;
        return h < 0 ? h + 24 : h;
    }

    /** Шаг шума Орнштейна–Уленбека на {@code dtS} с: точная дискретизация, стационарное σ = {@link #NOISE_SIGMA}. */
    public static double noiseStep(double xi, double dtS, SplittableRandom rng) {
        double a = Math.exp(-dtS / NOISE_TAU_S);
        return xi * a + NOISE_SIGMA * Math.sqrt(1 - a * a) * gaussian(rng);
    }

    private static double gaussian(SplittableRandom rng) {
        double u = Math.max(1e-300, rng.nextDouble()), v = rng.nextDouble();
        return Math.sqrt(-2 * Math.log(u)) * Math.cos(2 * Math.PI * v);
    }
}
