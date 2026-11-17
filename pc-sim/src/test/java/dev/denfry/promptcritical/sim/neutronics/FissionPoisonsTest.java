package dev.denfry.promptcritical.sim.neutronics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class FissionPoisonsTest {
    private static final double HOUR = 3600;
    private static final double SXE = FissionPoisons.SIGMA_XE_B, SSM = FissionPoisons.SIGMA_SM_B;

    /** Мощный реактор, φ = 10¹³: йод и ксенон за пять суток выходят на равновесие. */
    @Test
    void xenonAndIodineReachEquilibrium() {
        double phi = 1e13, f = 0.05 * phi; // Σ_f ячейки ≈ 0,05 см⁻¹
        FissionPoisons p = new FissionPoisons();
        for (int h = 0; h < 120; h++) p.step(HOUR, f, phi, SXE, SSM);
        assertThat(p.iodine()).isCloseTo(FissionPoisons.YIELD_I * f / FissionPoisons.LAMBDA_I, within(1e-3 * p.iodine()));
        double xeq = FissionPoisons.equilibriumXenon(f, phi, SXE);
        assertThat(p.xenon()).isCloseTo(xeq, within(1e-3 * xeq));
        // при φ ≫ λ_X/σ_X = 7,9·10¹² равновесное Σ_Xe·/Σ_f стремится к (γ_I + γ_X)/... — порядок 2–3 % поглощений
        assertThat(p.xenonAbs(SXE) / 0.05).isBetween(0.02, 0.05);
    }

    /** Шаг точен: сутки одним шагом — то же, что 86 400 шагов по секунде. */
    @Test
    void oneLongStepEqualsManyShortOnes() {
        FissionPoisons a = new FissionPoisons(), b = new FissionPoisons();
        a.step(86_400, 5e11, 1e13, SXE, SSM);
        for (int s = 0; s < 86_400; s++) b.step(1, 5e11, 1e13, SXE, SSM);
        assertThat(a.xenon()).isCloseTo(b.xenon(), within(1e-9 * b.xenon()));
        assertThat(a.samarium()).isCloseTo(b.samarium(), within(1e-9 * b.samarium()));
    }

    /**
     * Йодная яма: после останова с φ = 10¹⁴ ксенон растёт (йод распадается, выгорания нет) в 4,5 раза, пик — через 10 ч,
     * затем спадает. Самарий растёт до S_равн + P_равн: весь прометий становится самарием, а выгорать ему нечем.
     */
    @Test
    void xenonPeaksAfterShutdownAndSamariumOnlyGrows() {
        double phi = 1e14, f = 0.05 * phi;
        FissionPoisons p = new FissionPoisons();
        for (int d = 0; d < 300; d++) p.step(86_400, f, phi, SXE, SSM); // самарий: σ_S·φ — τ ≈ 4 недели
        double x0 = p.xenon(), pm = p.promethium(), sm = p.samarium();
        assertThat(sm).isCloseTo(FissionPoisons.equilibriumSamarium(f, phi, SSM), within(1e-3 * sm));
        double peak = x0, peakT = 0;
        for (int m = 1; m <= 48 * 60; m++) {
            p.step(60, 0, 0, SXE, SSM);
            if (p.xenon() > peak) {
                peak = p.xenon();
                peakT = m / 60.0;
            }
        }
        assertThat(peak / x0).isCloseTo(4.51, within(0.05)); // при 10¹³ было бы лишь 1,21 через 5,6 ч
        assertThat(peakT).isCloseTo(10.15, within(0.1));
        assertThat(p.xenon()).isLessThan(peak);
        for (int d = 0; d < 60; d++) p.step(86_400, 0, 0, SXE, SSM);
        assertThat(p.samarium()).isCloseTo(sm + pm, within(1e-3 * (sm + pm)));
    }

    /** Untermyer–Weills: 6,0 % сразу, 1,6 % через час, 0,73 % через сутки; подгонка группами — в пределах 0,1 %. */
    @Test
    void decayHeatFollowsTheFittedCurve() {
        assertThat(DecayHeat.afterInfiniteOperation(0)).isCloseTo(0.0601, within(1e-4));
        for (double t : new double[] {1, 10, 100, 3600, 86_400, 864_000, 1e7}) {
            double ref = DecayHeat.untermyerWeills(t, Double.POSITIVE_INFINITY);
            assertThat(DecayHeat.afterInfiniteOperation(t)).as("%s s", t).isCloseTo(ref, within(1e-3 * ref));
        }
        // сутки работы на 1 МВт, затем час остановки — по группам то же, что по формуле с конечным временем работы
        DecayHeat d = new DecayHeat();
        for (int h = 0; h < 24; h++) d.step(HOUR, 1e6);
        assertThat(d.powerW() / 1e6).isCloseTo(DecayHeat.untermyerWeills(0, 86_400), within(1e-3 * d.powerW() / 1e6));
        d.step(HOUR, 0);
        assertThat(d.powerW() / 1e6).isCloseTo(DecayHeat.untermyerWeills(HOUR, 86_400), within(2e-3 * d.powerW() / 1e6));
    }
}
