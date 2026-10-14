package dev.denfry.promptcritical.sim.neutronics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Тест-кейсы 01 §1.16, группа §1.5 (точечная кинетика). */
class PointKineticsValidationTest {
    private static final double BETA = 0.0068, LWR_GEN = 2e-5, DT = 0.05;
    private static final DelayedGroups LWR = DelayedNeutronData.u235(BETA);

    private static double rel(double a, double e) {
        return Math.abs(a - e) / Math.abs(e);
    }

    private static PointKinetics run(double dt, double rho, double seconds) {
        PointKinetics k = new PointKinetics(LWR, LWR_GEN, 1);
        for (int i = 0, n = (int) Math.round(seconds / dt); i < n; i++) k.step(dt, rho, 0);
        return k;
    }

    /** Период по логарифмической производной за последние {@code window} секунд прогона. */
    private static double simulatedPeriod(double rho, double seconds, double window) {
        PointKinetics k = run(DT, rho, seconds - window);
        double before = k.power();
        for (int i = 0, n = (int) Math.round(window / DT); i < n; i++) k.step(DT, rho, 0);
        return window / Math.log(k.power() / before);
    }

    @Test
    @Tag("T-1.5-01")
    void promptJumpOnSmallStep() {
        PointKinetics k = run(DT, 0.1 * BETA, DT);
        assertThat(k.lastSubsteps()).isEqualTo(1);
        assertThat(rel(k.power(), 1.113)).isLessThan(0.005);
        assertThat(k.power()).isGreaterThan(BETA / (BETA - 0.1 * BETA));
    }

    @Test
    @Tag("T-1.5-02")
    void halfDollarStepConvergesInTimeStep() {
        double coarse = run(DT, 0.5 * BETA, 10).power();
        double fine = run(5e-4, 0.5 * BETA, 10).power();
        assertThat(rel(fine, 15.29)).isLessThan(0.005);
        assertThat(rel(coarse, fine)).isLessThan(0.01);
    }

    @Test
    @Tag("T-1.5-03")
    void stablePeriodMatchesInhour() {
        double inhour = Inhour.period(0.3 * BETA, LWR, LWR_GEN);
        assertThat(rel(inhour, 18.1)).isLessThan(0.02);
        assertThat(rel(simulatedPeriod(0.3 * BETA, 100, 10), inhour)).isLessThan(0.02);
    }

    @Test
    @Tag("T-1.5-04")
    void scramPromptDropAndLongestGroupTail() {
        PointKinetics k = run(DT, -10 * BETA, DT);
        assertThat(rel(k.power(), 1.0 / 11)).isLessThan(0.03);
        double period = simulatedPeriod(-10 * BETA, 400, 10);
        assertThat(period).isCloseTo(-80, within(5.0));
    }

    @Test
    @Tag("T-1.5-05")
    void inhourTableU235() {
        DelayedGroups u235 = DelayedNeutronData.u235(0.0065);
        double[] pcm = {10, 50, 100, 200, 300, 500, 600, 650, 750, 1000};
        double[] lwr = {813, 137, 54.9, 17.3, 7.08, 1.19, 0.289, 0.093, 0.019, 0.0057};
        double[] graphite = {823, 139, 56.3, 18.3, 7.87, 1.96, 1.08, 0.83, 0.53, 0.25};
        for (int i = 0; i < pcm.length; i++) {
            double rho = pcm[i] * ReactivityBudget.PCM;
            assertThat(rel(Inhour.period(rho, u235, 2e-5), lwr[i])).as("LWR %s pcm", pcm[i]).isLessThan(0.01);
            assertThat(rel(Inhour.period(rho, u235, 1e-3), graphite[i])).as("graphite %s pcm", pcm[i])
                    .isLessThan(0.01);
        }
    }

    @Test
    @Tag("T-1.5-06")
    void betaDropShortensPeriod() {
        double[] beta = {0.0068, 0.0052, 0.0039};
        double[] period = {7.9, 3.8, 1.2};
        for (int i = 0; i < beta.length; i++) {
            double t = Inhour.period(300 * ReactivityBudget.PCM, DelayedNeutronData.u235(beta[i]), LWR_GEN);
            assertThat(rel(t, period[i])).as("beta %s", beta[i]).isLessThan(0.03);
        }
    }

    @Test
    @Tag("T-1.5-07")
    void promptSupercriticalSubstepsAndGrowthRate() {
        PointKinetics k = run(DT, 2 * BETA, DT);
        double before = k.power();
        k.step(DT, 2 * BETA, 0);
        assertThat(rel(k.lastSubsteps(), 340)).isLessThan(0.05);
        assertThat(rel(Math.log(k.power() / before) / DT, (2 * BETA - BETA) / LWR_GEN)).isLessThan(0.05);
    }

    @Test
    @Tag("T-1.5-08")
    void fuchsHansenBurstWithAdiabaticDoppler() {
        double pNom = 300e6, gamma = 5.8e-12, rho0 = 1.2 * BETA, excess = rho0 - BETA, dt = 1e-3;
        PointKinetics k = new PointKinetics(LWR, LWR_GEN, 1e-4);
        double energy = 0, peak = 0, energyAtPeak = 0;
        int n = (int) Math.round(0.6 / dt), iPeak = 0;
        double[] p = new double[n];
        for (int i = 0; i < n; i++) {
            double n0 = k.power(), n1 = k.step(dt, rho0 - gamma * energy, 0, -gamma * pNom);
            energy += pNom * 0.5 * (n0 + n1) * dt;
            p[i] = pNom * n1;
            if (p[i] > peak) {
                peak = p[i];
                iPeak = i;
                energyAtPeak = energy;
            }
        }
        int lo = iPeak, hi = iPeak;
        while (lo > 0 && p[lo] > peak / 2) lo--;
        while (hi < n - 1 && p[hi] > peak / 2) hi++;
        // В пике ρ = β: до него выделена ровно половина энергии симметричной вспышки.
        assertThat(rel(2 * energyAtPeak, 2 * excess / gamma)).isLessThan(0.15);
        assertThat(rel(2 * energyAtPeak, 470e6)).isLessThan(0.15);
        assertThat(rel(peak, 8e9)).isLessThan(0.15);
        assertThat(rel((hi - lo) * dt, 0.052)).isLessThan(0.15);

        // Тот же разгон тиками 0,05 с: адиабата внутри подшагов держит энергию.
        PointKinetics coarse = new PointKinetics(LWR, LWR_GEN, 1e-4);
        double coarseEnergy = 0;
        for (int i = 0; i < 12; i++) {
            double n0 = coarse.power(), n1 = coarse.step(DT, rho0 - gamma * coarseEnergy, 0, -gamma * pNom);
            coarseEnergy += pNom * 0.5 * (n0 + n1) * DT;
        }
        assertThat(rel(coarseEnergy, energy)).isLessThan(0.1);
    }

    @Test
    @Tag("T-1.5-09")
    void subcriticalMultiplicationOfSource() {
        double pNom = 1e6, rho = -1000 * ReactivityBudget.PCM;
        double s = PointKinetics.normalizedSource(5.4e5, 2.43, LWR_GEN, pNom);
        PointKinetics k = new PointKinetics(LWR, LWR_GEN, PointKinetics.MIN_POWER);
        for (int i = 0; i < 40_000; i++) k.step(DT, rho, s);
        assertThat(rel(k.power(), -s * LWR_GEN / rho)).isLessThan(1e-3);
        double fissions = PointKinetics.fissionRate(k.power(), pNom);
        assertThat(rel(fissions, 2.2e7)).isLessThan(0.03);
        assertThat(rel(fissions * PointKinetics.FISSION_ENERGY, 0.7e-3)).isLessThan(0.03);
    }

    @Test
    @Tag("T-1.5-10")
    void delayedGroupMixing() {
        assertThat(rel(DelayedNeutronData.mixedBeta(new double[] {0.50, 0.07, 0.38, 0.05}), 0.00535))
                .isLessThan(0.01);
        double[][] f = {{0.93, 0.07, 0, 0}, {0.05, 0.08, 0.65, 0.22}, {0.95, 0.05, 0, 0}};
        double[] beta = {0.00708, 0.00406, 0.00692};
        for (int i = 0; i < f.length; i++) {
            DelayedGroups g = DelayedNeutronData.mix(f[i], 1);
            assertThat(rel(g.betaTotal(), beta[i])).isLessThan(0.01);
            assertThat(g.betaTotal()).isCloseTo(DelayedNeutronData.mixedBeta(f[i]), within(1e-15));
        }
        // Чистый U-235 возвращает собственные группы Кипина, средняя задержка 13,0 с.
        DelayedGroups u = DelayedNeutronData.mix(new double[] {1, 0, 0, 0}, 1);
        assertThat(u.lambda(0)).isCloseTo(0.0124, within(1e-12));
        assertThat(1 / u.lambdaEffective()).isCloseTo(13.0, within(0.05));
    }
}
