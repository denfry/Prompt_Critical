package dev.denfry.promptcritical.sim.neutronics;

/**
 * Уравнение обратных часов (01 §1.5.5): ρ = ωΛ + Σ β_i ω/(ω + λ_i) — связь ступенчатой
 * реактивности со стабильным периодом T = 1/ω. Оракул тестов и подсказка HMI.
 */
public final class Inhour {
    private Inhour() {}

    /** ρ(ω). */
    public static double reactivity(double omega, DelayedGroups groups, double generationTime) {
        double rho = omega * generationTime;
        for (int i = 0; i < groups.count(); i++) rho += groups.beta(i) * omega / (omega + groups.lambda(i));
        return rho;
    }

    /**
     * Доминирующий корень ω(ρ): при ρ &gt; 0 — единственный положительный, при ρ &lt; 0 — на
     * интервале (−λ_min, 0), где ρ(ω) монотонна.
     */
    public static double omega(double rho, DelayedGroups groups, double generationTime) {
        if (rho == 0) return 0;
        double lo, hi;
        if (rho > 0) {
            lo = 0;
            hi = 1;
            while (reactivity(hi, groups, generationTime) < rho) hi *= 2;
        } else {
            double lambdaMin = Double.POSITIVE_INFINITY;
            for (int i = 0; i < groups.count(); i++) lambdaMin = Math.min(lambdaMin, groups.lambda(i));
            lo = -lambdaMin;
            hi = 0;
        }
        for (int it = 0; it < 200; it++) {
            double mid = 0.5 * (lo + hi);
            if (mid == lo || mid == hi) break;
            if (reactivity(mid, groups, generationTime) < rho) lo = mid;
            else hi = mid;
        }
        return 0.5 * (lo + hi);
    }

    /** Стабильный период T = 1/ω, с; ∞ при ρ = 0. */
    public static double period(double rho, DelayedGroups groups, double generationTime) {
        double w = omega(rho, groups, generationTime);
        return w == 0 ? Double.POSITIVE_INFINITY : 1 / w;
    }
}
