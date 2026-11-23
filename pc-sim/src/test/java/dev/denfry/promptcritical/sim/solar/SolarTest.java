package dev.denfry.promptcritical.sim.solar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class SolarTest {
    @Test void panelPowerMatchesT15_12() {
        // G = 1000, η = 0,18, T_a = 25 °C → T_c = 56,25 °C, 157,5 Вт
        assertThat(PvArray.cellTempC(25, 1000, 45)).isCloseTo(56.25, within(1e-9));
        assertThat(PvArray.panelW(0.18, 1, 1000, 25, -0.004, 45)).isCloseTo(157.5, within(1e-9));
    }

    @Test void directIrradianceAtAirMassTwoMatchesT15_13() {
        assertThat(SolarSky.dniAt(2)).isCloseTo(769, within(1.0));
    }

    @Test void sunRisesInTheEastAndSetsInTheWest() {
        var noon = SolarSky.clearSky(0);
        assertThat(noon.altitudeRad()).isCloseTo(Math.PI / 2, within(1e-9));
        assertThat(noon.dniWm2()).as("Meinel at AM 1: 1361 x 0.7").isCloseTo(952.7, within(0.5));
        assertThat(SolarSky.clearSky(0.80).sunX()).as("morning sun in the east").isPositive();
        assertThat(SolarSky.clearSky(0.20).sunX()).as("afternoon sun in the west").isNegative();
        assertThat(SolarSky.clearSky(0.5).dniWm2()).as("night").isZero();
    }

    @Test void trackerBeatsAHorizontalPanelOverTheDay() {
        var fixed = PvArray.Spec.of(1, false);
        var tracker = PvArray.Spec.of(1, true);
        double eFixed = 0, eTracker = 0;
        for (int i = 0; i < 1000; i++) {
            double angle = 0.75 + i / 2000.0; // восход → закат
            var sun = SolarSky.clearSky(angle % 1);
            eFixed += PvArray.arrayW(fixed, sun, 0, 20, 0, 0, false);
            eTracker += PvArray.arrayW(tracker, sun, 0, 20, 0, 0, false);
        }
        assertThat(eTracker / eFixed).isBetween(1.2, 1.6);
    }

    @Test void rainCutsTheDirectBeam() {
        var sky = new SolarSky();
        var clear = sky.sample(0, SolarSky.Weather.CLEAR);
        var rain = sky.sample(0, SolarSky.Weather.RAIN);
        assertThat(rain.dniWm2()).isCloseTo(0.15 * clear.dniWm2(), within(1e-6));
        assertThat(rain.dhiWm2()).isCloseTo(1.5 * clear.dhiWm2(), within(1e-6));
    }

    @Test void cloudsWanderToTheBiomeMean() {
        var sky = new SolarSky();
        for (int i = 0; i < 2000; i++) sky.step(60, 0.4, 0);
        assertThat(sky.clear()).isCloseTo(0.4, within(0.01));
    }

    @Test void shadedPanelsCutTheString() {
        assertThat(PvArray.shadeFactor(10, 0)).isEqualTo(1);
        assertThat(PvArray.shadeFactor(10, 1)).isCloseTo(0.86, within(1e-9));
        assertThat(PvArray.arrayW(PvArray.Spec.of(4, false), SolarSky.clearSky(0), 0, 20, 0, 0, true)).as("snow").isZero();
    }
}
