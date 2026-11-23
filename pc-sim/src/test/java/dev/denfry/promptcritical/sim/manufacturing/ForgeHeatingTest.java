package dev.denfry.promptcritical.sim.manufacturing;

import static dev.denfry.promptcritical.sim.manufacturing.ManufacturingFixtures.bar45;
import static dev.denfry.promptcritical.sim.manufacturing.ManufacturingFixtures.gradeShKh15;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.denfry.promptcritical.sim.metallurgy.AirCooling;
import org.junit.jupiter.api.Test;

class ForgeHeatingTest {
    @Test void fortyMillimetreBarReachesForgingHeatInMinutesNotSeconds() {
        var w = bar45(0.040, 293.15);
        assertThat(ForgeHeating.heat(w, 1573.15, 60).tempK()).isLessThan(1373.15);
        assertThat(ForgeHeating.heat(w, 1573.15, 600).tempK()).isGreaterThanOrEqualTo(1373.15);
    }

    @Test void billetScaleMatchesReheatingFurnaceOrder() { // 08 §8.7.2: 1-2 % per reheating
        var w = bar45(0.150, 1423.15);
        var h = ForgeHeating.heat(w, 1423.15, 1800);
        double lossFrac = 1 - h.massKg() / w.massKg();
        assertThat(lossFrac).isBetween(0.005, 0.02);
    }

    @Test void fullBlastBurnsBearingSteel() { // ShKh15 burns at about 1246 C, below the 1350 C fire
        var h = ForgeHeating.heat(Workpiece.bar(gradeShKh15(), Workpiece.Section.ROUND, 0.020, 0.020, 0.3, 1500),
                ForgeHeating.fireTempK(1.0), 900);
        assertThat(h.severity(PartDefect.BURNT)).isEqualTo(1.0);
    }

    @Test void halfBlastDoesNotBurn() {
        var h = ForgeHeating.heat(bar45(0.020, 293.15), ForgeHeating.fireTempK(0.5), 3600);
        assertThat(h.defects()).doesNotContainKey(PartDefect.BURNT);
    }

    @Test void zeroSecondsIsIdentity() {
        var w = bar45(0.040, 293.15);
        assertThat(ForgeHeating.heat(w, 1573.15, 0)).isEqualTo(w);
    }

    @Test void dayInTheFireStaysFiniteAndAboveMinimumSection() {
        var h = ForgeHeating.heat(bar45(0.010, 293.15), 1473.15, 86_400);
        assertThat(h.tempK()).isLessThanOrEqualTo(1473.15).isNotNaN();
        assertThat(h.aM()).isGreaterThanOrEqualTo(Forging.MIN_SECTION_M);
    }

    @Test void coolingDelegatesToAirCooling() {
        var w = bar45(0.040, 1373.15);
        assertThat(ForgeHeating.cool(w, 293.15, 60).tempK()).isCloseTo(
                AirCooling.tempAfter(1373.15, 293.15, w.equivalentThicknessM(), 60), within(1e-9));
    }

    @Test void fireAndBurningTemperaturesMatchTheirFormulas() {
        assertThat(ForgeHeating.fireTempK(0)).isEqualTo(1173.15);
        assertThat(ForgeHeating.fireTempK(7)).isEqualTo(1623.15);
        assertThat(ForgeHeating.burningK(gradeShKh15())).isCloseTo(1246 + 273.15, within(1.0));
    }

    @Test void flatBarLosesBothDimensions() {
        var w = Workpiece.bar(ManufacturingFixtures.grade45(), Workpiece.Section.FLAT, 0.020, 0.060, 0.3, 1423.15);
        var h = ForgeHeating.heat(w, 1423.15, 1800);
        assertThat(h.aM()).isLessThan(w.aM());
        assertThat(h.bM()).isLessThan(w.bM());
    }
}
