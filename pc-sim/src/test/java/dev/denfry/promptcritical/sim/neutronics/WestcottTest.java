package dev.denfry.promptcritical.sim.neutronics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class WestcottTest {
    private static final double K_EV = 8.617333262e-5;

    /**
     * g Xe-135 — по одиночному резонансу Брейта–Вигнера при 0,0842 эВ (Γ_γ 0,0906 эВ, Γ_n 0,0257 эВ при
     * E_r, Γ_n ∝ √E), усреднённому по Максвеллу: g = (2/√π)·√(T/T₀)·∫ σ(xkT)/σ₀ · x·e^{−x} dx. Таблица
     * (Lamarsh, табл. 3.2) сходится с ним до 1,5 %; выше 200 °C резонанс уходит из-под спектра, и g падает.
     */
    @Test
    void xenonFactorFollowsTheResonance() {
        for (double tc : new double[] {20, 100, 200, 400, 600, 800, 1000}) {
            double tn = tc + 273.15;
            assertThat(Westcott.g(Westcott.Factor.XE135_A, tn)).as("%s °C", tc)
                    .isCloseTo(xenonFromResonance(tn), within(0.015 * xenonFromResonance(tn)));
        }
        assertThat(Westcott.g(Westcott.Factor.XE135_A, 1273.15)).isCloseTo(0.8858, within(1e-4));
        assertThat(Westcott.g(Westcott.Factor.XE135_A, 873.15))
                .isLessThan(Westcott.g(Westcott.Factor.XE135_A, 473.15));
    }

    private static double xenonFromResonance(double tn) {
        double kT = K_EV * tn, s0 = sigma(K_EV * Westcott.T0);
        int n = 20_000;
        double xMax = 40, h = xMax / n, acc = 0;
        for (int i = 0; i < n; i++) {
            double x = (i + 0.5) * h;
            acc += sigma(x * kT) / s0 * x * Math.exp(-x) * h;
        }
        return 2 / Math.sqrt(Math.PI) * Math.sqrt(tn / Westcott.T0) * acc;
    }

    /** Форма сечения захвата, без нормировки. */
    private static double sigma(double e) {
        double er = 0.0842, gamma = 0.0906 + 0.0257 * Math.sqrt(e / er);
        return Math.sqrt(er / e) / ((e - er) * (e - er) + gamma * gamma / 4);
    }
}
