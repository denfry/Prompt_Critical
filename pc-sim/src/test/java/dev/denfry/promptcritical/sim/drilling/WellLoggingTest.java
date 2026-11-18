package dev.denfry.promptcritical.sim.drilling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

/** Каротаж и керн 09 §9.2.5–9.2.6: табличные значения раздела. */
class WellLoggingTest {
    @Test
    void neutronRatiosMatchTheTable() {
        // плотный песчаник 8,96; водоносный φ = 0,1/0,2/0,3 — 20,2/36,6/59,9; газ S_г = 0,8 при HI = 0,36 — 15,5
        assertThat(WellLogging.neutronRatio(0, 1, 20)).isCloseTo(8.96, within(0.05));
        assertThat(WellLogging.neutronRatio(0.1, 1, 20)).isCloseTo(20.2, within(0.3));
        assertThat(WellLogging.neutronRatio(0.2, 1, 20)).isCloseTo(36.6, within(0.4));
        assertThat(WellLogging.neutronRatio(0.3, 1, 20)).isCloseTo(59.9, within(0.6));
        assertThat(WellLogging.neutronRatio(0.2, 0.36, 20)).isCloseTo(15.5, within(0.3));
        // кажущаяся пористость газонасыщенного пласта 0,064 — газовый эффект
        assertThat(WellLogging.apparentPorosity(WellLogging.neutronRatio(0.2, 0.36, 20), 20)).isCloseTo(0.064, within(0.004));
        assertThat(WellLogging.apparentPorosity(WellLogging.neutronRatio(0.2, 1, 20), 20)).isCloseTo(0.2, within(1e-9));
    }

    @Test
    void archieGivesTheWaterAndOilExamples() {
        assertThat(WellLogging.archieRt(0.05, 0.2, 1)).isCloseTo(1.25, within(1e-9));
        assertThat(WellLogging.archieRt(0.05, 0.2, 0.2)).isCloseTo(31.25, within(1e-9));
    }

    @Test
    void logShowsShaleSandAndTheContact() {
        Trap oil = new Trap(0, 0, 80, 1000, 60, 1010, 20, 0.2, 100, FormationFluid.OIL, PetroleumField.HYDROSTATIC_PA_M);
        Prospect p = new Prospect(oil, 1000);
        double[] gr = WellLogging.log(WellLogging.Curve.GR, p, 990, 1030, 7);
        double[] rt = WellLogging.log(WellLogging.Curve.RT, p, 990, 1030, 7);
        int shale = 10, oilPay = (int) (5 / WellLogging.STEP_M) + (int) (10 / WellLogging.STEP_M), water = (int) (25 / WellLogging.STEP_M);
        assertThat(gr[shale / 2]).isGreaterThan(80);
        assertThat(gr[oilPay]).isLessThan(50);
        assertThat(rt[oilPay - 20]).isCloseTo(WellLogging.archieRt(0.05, 0.2, 0.25), within(1e-9));
        assertThat(rt[water]).isCloseTo(1.25, within(1e-9));
        assertThat(WellLogging.log(WellLogging.Curve.GR, p, 990, 1030, 7)).containsExactly(gr);
        // погрешность счёта 1/√(NΔt): при 100 API и 0,15 м/с — около 8 %
        assertThat(WellLogging.grRelativeError(100)).isCloseTo(1 / Math.sqrt(60 * 1.016), within(1e-9));
    }

    @Test
    void longShaleIntervalsRaiseTheChanceOfStickingTheSonde() {
        Trap oil = new Trap(0, 0, 80, 1000, 60, 1010, 20, 0.2, 100, FormationFluid.OIL, PetroleumField.HYDROSTATIC_PA_M);
        Prospect p = new Prospect(oil, 1000);
        assertThat(WellLogging.stuckProbability(p, 990, 1020)).isEqualTo(0.003);
        assertThat(WellLogging.stuckProbability(p, 600, 1020)).isCloseTo(0.012, within(1e-12));
    }

    @Test
    void coreAnalysisStaysWithinTheStationErrors() {
        WellLogging.Point truth = new WellLogging.Point(0, 0.2, 0.25, FormationFluid.OIL, 0);
        for (long s = 0; s < 200; s++) {
            WellLogging.CoreMeasurement m = WellLogging.analyse(truth, 100, s);
            assertThat(m.porosity()).isCloseTo(0.2, within(0.005));
            assertThat(m.permeabilityMd()).isCloseTo(100, within(10.0));
            assertThat(m.waterSaturation()).isCloseTo(0.25, within(0.03));
        }
    }
}
