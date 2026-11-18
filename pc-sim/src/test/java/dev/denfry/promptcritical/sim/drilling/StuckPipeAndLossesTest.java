package dev.denfry.promptcritical.sim.drilling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

/** Прихваты и поглощения 09 §9.5.10. */
class StuckPipeAndLossesTest {
    private static final double G = 9.806_65;

    @Test
    void contactGrowsWithTheSquareRootOfTime() {
        double f1 = DifferentialSticking.forceN(3e6, 10, 3600), f4 = DifferentialSticking.forceN(3e6, 10, 4 * 3600);
        assertThat(f1).isCloseTo(0.15 * 3e6 * 10 * 0.05, within(1.0));
        assertThat(f4 / f1).isCloseTo(2, within(1e-9));
        assertThat(DifferentialSticking.forceN(-1e6, 10, 3600)).isZero();
        // слабейшее звено — труба E-75: 80 % от 1,76 МН
        assertThat(DifferentialSticking.hookLimitN()).isCloseTo(0.8 * 1.76e6, within(1.0));
    }

    @Test
    void stationaryStringOppositeThePayGetsStuckAndRotationPreventsIt() {
        double free = 0.63e6, overpull = DifferentialSticking.overpullN(free);
        DifferentialSticking rotating = new DifferentialSticking(), parked = new DifferentialSticking();
        double stuckAt = Double.NaN;
        for (double t = 0; t < 24 * 3600; t += 60) {
            rotating.step(60, true, 3e6, 15, free);
            parked.step(60, false, 3e6, 15, free);
            if (parked.stuck() && Double.isNaN(stuckAt)) stuckAt = t + 60;
        }
        assertThat(rotating.stuck()).isFalse();
        assertThat(parked.stuck()).isTrue();
        // F = μΔpLw(t) = overpull → t = (overpull/(μΔpL·w1h))² ч
        double expected = Math.pow(overpull / (0.15 * 3e6 * 15 * 0.05), 2) * 3600;
        assertThat(stuckAt).isCloseTo(expected, within(120.0));
        assertThat(parked.workPipe(free)).as("cannot pull free above 80 %% of the pipe").isFalse();
        // репрессию сняли — расхаживание срывает колонну
        parked.step(60, false, 0.1e6, 15, free);
        assertThat(parked.workPipe(free)).isTrue();
        assertThat(parked.contactSeconds()).isZero();
    }

    @Test
    void wellReportsOverbalanceAcrossThePay() {
        Trap oil = new Trap(0, 0, 80, 1500, 60, 1560, 15, 0.2, 50, FormationFluid.OIL, PetroleumField.HYDROSTATIC_PA_M);
        Well w = new Well(WellGeometry.STANDARD, new Prospect(oil, 1500), 1510, 1300, 60);
        w.step(10, 0, 0, 0);
        double mid = 1505;
        assertThat(w.collarsAcrossPayM()).isCloseTo(10, within(1e-9));
        assertThat(w.overbalancePa()).isCloseTo(1300 * G * mid - oil.pressureAt(mid), within(2e3));
    }

    @Test
    void mudHeavierThanTheFractureGradientIsLostAndLcmCutsTheLoss() {
        Well w = new Well(WellGeometry.STANDARD, null, 1500, 1750, 60);
        w.step(10, 0, 0, 0);
        double excess = w.bottomholePressurePa() - 1700 * G * 1500;
        assertThat(excess).isGreaterThan(0);
        w.step(10, 0, 0, 0);
        assertThat(w.lossM3s()).isCloseTo(Well.LOSS_COEFF_M3_S_PA * excess, within(1e-4));
        double pit = w.pitVolumeM3();
        double before = w.lostM3();
        w.step(100, 0, 0, 0);
        assertThat(w.lostM3() - before).isGreaterThan(0.5);
        assertThat(w.pitVolumeM3()).as("the hole is kept full from the pits").isLessThan(pit);
        double plain = w.lossM3s();
        w.addLcm(Well.LCM_HALF_KGM3 * w.pitVolumeM3());
        w.step(10, 0, 0, 0);
        assertThat(w.lossM3s()).isCloseTo(plain / 2, within(0.05 * plain));
    }

    @Test
    void normalMudLosesNothing() {
        Well w = new Well(WellGeometry.STANDARD, null, 1500, 1100, 60);
        for (int i = 0; i < 10; i++) w.step(60, 0.03, 100, 0);
        assertThat(w.lostM3()).isZero();
    }
}
