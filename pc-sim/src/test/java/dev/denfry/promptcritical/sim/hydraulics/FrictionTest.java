package dev.denfry.promptcritical.sim.hydraulics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

/** Трение Дарси и двухфазные множители (01 §1.2.2, §1.2.4). */
class FrictionTest {
    /** Колбрук итерацией — эталон для Хааланда. */
    private static double colebrook(double re, double rr) {
        double f = 0.02;
        for (int i = 0; i < 100; i++) {
            double s = -2 * Math.log10(rr / 3.7 + 2.51 / (re * Math.sqrt(f)));
            f = 1 / (s * s);
        }
        return f;
    }

    @Test
    void haalandWithinOnePointFivePercentOfColebrook() {
        for (double re = 5e3; re <= 1e8; re *= 1.7) {
            for (double rr : new double[] {0, 1e-6, 1e-5, 1e-4, 1e-3, 1e-2}) {
                double f = Friction.haaland(re, rr), ref = colebrook(re, rr);
                assertThat(Math.abs(f - ref) / ref).as("Re=%g eps/D=%g", re, rr).isLessThan(0.015);
            }
        }
    }

    @Test
    void laminarAndTransition() {
        assertThat(Friction.darcy(1000, 1e-4)).isCloseTo(0.064, within(1e-12));
        double lo = Friction.darcy(Friction.RE_LAMINAR, 1e-4), hi = Friction.darcy(Friction.RE_TURBULENT, 1e-4);
        double mid = Friction.darcy(0.5 * (Friction.RE_LAMINAR + Friction.RE_TURBULENT), 1e-4);
        assertThat(mid).isCloseTo(0.5 * (lo + hi), within(1e-12));
    }

    @Test
    void twoPhaseMultipliers() {
        // 7 МПа: ρ_f = 739,7, ρ_g = 36,52, μ_f = 9,1e-5, μ_g = 1,9e-5
        double rf = 739.7, rg = 36.52, mf = 9.1e-5, mg = 1.9e-5, sigma = 0.0176;
        assertThat(Friction.homogeneous(0, rf, rg, mf, mg)).isEqualTo(1);
        double x = 0.1;
        double hem = Friction.homogeneous(x, rf, rg, mf, mg);
        assertThat(hem).isCloseTo((1 + x * (rf / rg - 1)) * Math.pow(1 + x * (mf / mg - 1), -0.25), within(1e-12));
        // Фридель и HEM при больших G согласуются по порядку величины (обе ~ ρ_f/ρ_g·x)
        double fr = Friction.friedel(x, 2000, 0.0106, rf, rg, mf, mg, sigma);
        assertThat(fr / hem).isBetween(0.5, 2.5);
        assertThat(Friction.multiplier(Friction.TwoPhaseMultiplier.HEM, 0, 1000, 0.01, rf, rg, mf, mg, sigma))
                .isEqualTo(1);
    }
}
