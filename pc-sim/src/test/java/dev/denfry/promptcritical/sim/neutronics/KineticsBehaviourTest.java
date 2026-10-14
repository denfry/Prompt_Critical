package dev.denfry.promptcritical.sim.neutronics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

/** Поведение кинетики вне оракулов §1.16: равновесие, смена параметров, прибор периода, баланс. */
class KineticsBehaviourTest {
    private static final DelayedGroups LWR = DelayedNeutronData.u235(0.0068);

    @Test
    void criticalEquilibriumStaysPut() {
        PointKinetics k = new PointKinetics(LWR, 2e-5, 0.7);
        for (int i = 0; i < 10_000; i++) k.step(0.05, 0, 0);
        assertThat(k.power()).isCloseTo(0.7, within(1e-12));
        assertThat(k.periodInstant()).isInfinite();
    }

    @Test
    void powerNeverDropsBelowFloor() {
        PointKinetics k = new PointKinetics(LWR, 2e-5, 1);
        for (int i = 0; i < 200_000; i++) k.step(0.05, -0.5, 0);
        assertThat(k.power()).isGreaterThanOrEqualTo(PointKinetics.MIN_POWER);
    }

    @Test
    void photoneutronGroupsJoinFromEquilibrium() {
        PointKinetics k = new PointKinetics(LWR, 9e-4, 1);
        k.setParameters(DelayedNeutronData.withPhotoneutrons(LWR.scaledTo(0.0058), 5e-4, 9), 9e-4);
        assertThat(k.groupCount()).isEqualTo(9);
        double longest = 0.1 * 5e-4 / (9e-4 * Math.log(2) / (12.8 * 86400)); // c = β n / (Λ λ)
        assertThat(k.precursor(8)).isCloseTo(longest, within(1e-9 * longest));
        // Длинная группа держит хвост мощности после АЗ.
        PointKinetics plain = new PointKinetics(LWR.scaledTo(0.0058), 9e-4, 1);
        for (int i = 0; i < 20 * 600; i++) {
            k.step(0.05, -0.05, 0);
            plain.step(0.05, -0.05, 0);
        }
        assertThat(k.power()).isGreaterThan(5 * plain.power());
    }

    @Test
    void periodMeterFollowsStablePeriodAndShowsInfinityWhenFlat() {
        PeriodMeter m = new PeriodMeter(0.05);
        PointKinetics k = new PointKinetics(LWR, 2e-5, 1);
        for (int i = 0; i < 400; i++) m.sample(k.step(0.05, 0, 0));
        assertThat(m.period()).isInfinite();
        double rho = 0.3 * 0.0068;
        for (int i = 0; i < 1200; i++) m.sample(k.step(0.05, rho, 0));
        assertThat(m.period()).isCloseTo(Inhour.period(rho, LWR, 2e-5), within(0.3));
    }

    @Test
    void budgetSumsOnlyKineticComponents() {
        ReactivityBudget b = new ReactivityBudget();
        b.set(ReactivityComponent.STATIC, 0.002);
        b.set(ReactivityComponent.DOPPLER, -0.0005);
        b.set(ReactivityComponent.XENON, -0.0177);
        b.set(ReactivityComponent.EXTERNAL, 0.0001);
        assertThat(b.total()).isCloseTo(0.0016, within(1e-15));
        assertThat(b.totalPcm()).isCloseTo(160, within(1e-9));
        assertThat(b.dollars(0.0068)).isCloseTo(0.0016 / 0.0068, within(1e-15));
        b.resetFastComponents();
        assertThat(b.component(ReactivityComponent.DOPPLER)).isZero();
        assertThat(b.component(ReactivityComponent.EXTERNAL)).isEqualTo(0.0001);
        assertThat(b.total()).isCloseTo(0.0021, within(1e-15));
        assertThat(b.snapshot()).containsEntry(ReactivityComponent.XENON, -0.0177);
    }
}
