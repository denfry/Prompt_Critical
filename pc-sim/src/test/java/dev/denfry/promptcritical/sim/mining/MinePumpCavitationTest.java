package dev.denfry.promptcritical.sim.mining;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.denfry.promptcritical.sim.hydraulics.PumpCavitation;
import org.junit.jupiter.api.Test;

/**
 * Насос водоотлива ЦНС на 150 м (08 §8.2.9) и его всас (04a §4a.3): у водосборника тих, высоко над ним кавитирует;
 * горячая вода глубокой шахты съедает часть запаса.
 */
class MinePumpCavitationTest {
    private static final MineSump.PumpSpec PUMP = new MineSump.PumpSpec(100.0 / 3600, 150, 0.7, 64);
    private static final double DEPTH = 150, ROCK_K = 288.15 + 0.03 * DEPTH;

    private static double kappa(double liftM, double rpm, double waterK) {
        double q = PUMP.flowAt(rpm, DEPTH);
        double ps = MineSump.suctionPressurePa(DEPTH, waterK, liftM, q, PUMP);
        double a = PumpCavitation.npshAvailable(ps, waterK, MineSump.RHO, MineSump.suctionVelocity(q));
        double r = PumpCavitation.npshRequired(MineSump.NPSH_R_BEP_M, rpm / PUMP.ratedRpm(), q / PUMP.ratedFlowM3s(), 0);
        return PumpCavitation.intensity(a, r);
    }

    @Test
    void ratedPointIsTheRatedFlow() {
        assertThat(PUMP.flowAt(64, 150)).isCloseTo(PUMP.ratedFlowM3s(), within(1e-9));
    }

    /** Барометрическая ступень: на 150 м воздух на 1,8 % плотнее; потери во всасе — 0,5 м на номинале. */
    @Test
    void suctionPressureCountsAirDepthLiftAndLosses() {
        double p0 = MineSump.suctionPressurePa(0, 288.15, 0, 0, PUMP);
        assertThat(p0).isCloseTo(MineSump.SURFACE_PA, within(1e-6));
        double deep = MineSump.suctionPressurePa(150, 288.15, 0, 0, PUMP);
        assertThat(deep / p0 - 1).isCloseTo(0.0179, within(0.001));
        double lifted = MineSump.suctionPressurePa(150, 288.15, 3, PUMP.ratedFlowM3s(), PUMP);
        assertThat((deep - lifted) / (MineSump.RHO * MineSump.G)).isCloseTo(3.5, within(1e-9));
    }

    @Test
    void pumpAtTheSumpIsQuietAndHighAboveItCavitates() {
        assertThat(kappa(0, 64, ROCK_K)).isZero();
        assertThat(kappa(1, 64, ROCK_K)).isZero();
        double six = kappa(6, 64, ROCK_K);
        assertThat(six).isGreaterThan(0.5); // слышно
        assertThat(kappa(8, 64, ROCK_K)).isEqualTo(1);
    }

    /** Вода, нагретая глубокой породой до 60 °C, теряет ~2 м запаса на давлении пара. */
    @Test
    void hotWaterEatsTheMargin() {
        assertThat(kappa(3, 64, 333.15)).isGreaterThan(kappa(3, 64, 288.15));
    }
}
