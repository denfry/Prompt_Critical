package dev.denfry.promptcritical.sim.grid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class MvDriveTest {
    private static final double DT = 0.05;

    private static void run(MvDrive d, double seconds, double vPu, double fHz) {
        for (double t = 0; t < seconds; t += DT) d.step(DT, vPu, fHz);
    }

    @Test void healthyBusGivesFullSpeedAndRatedDemand() {
        var d = new MvDrive();
        assertThat(d.step(DT, 1, 50)).isCloseTo(1, within(1e-12));
        assertThat(d.demandW(800e3)).isEqualTo(800e3);
    }

    @Test void speedFollowsTheGridFrequency() {
        var d = new MvDrive();
        assertThat(d.step(DT, 1, 48.5)).isCloseTo(0.97, within(1e-12));
        assertThat(d.step(DT, 1, 51)).isCloseTo(1, within(1e-12));
    }

    @Test void dipShorterThanTheDelayRidesThrough() {
        var d = new MvDrive();
        run(d, 1, 1, 50);
        run(d, 0.3, 0.7, 50);
        assertThat(d.factor()).isZero();
        run(d, 0.1, 1, 50);
        assertThat(d.on()).isTrue();
        assertThat(d.factor()).isCloseTo(1, within(1e-12));
    }

    @Test void undervoltageBelowPoint85TripsAndTheMachineDoesNotRestartItself() {
        var d = new MvDrive();
        run(d, 1, 1, 50);
        run(d, 0.6, 0.84, 50);
        assertThat(d.tripped()).isTrue();
        assertThat(d.on()).isFalse();
        run(d, 5, 1, 50);
        assertThat(d.factor()).isZero();
        assertThat(d.demandW(800e3)).isZero();
        d.start();
        assertThat(d.step(DT, 1, 50)).isCloseTo(1, within(1e-12));
    }

    @Test void justAboveTheSettingKeepsRunning() {
        var d = new MvDrive();
        run(d, 1, 1, 50);
        run(d, 3, 0.86, 50);
        assertThat(d.on()).isTrue();
        assertThat(d.factor()).isCloseTo(1, within(1e-12));
    }

    @Test void machinePlacedBeforeTheStationStartsWaitsWithoutTripping() {
        var d = new MvDrive();
        run(d, 10, 0, 0);
        assertThat(d.tripped()).isFalse();
        assertThat(d.step(DT, 1, 50)).isCloseTo(1, within(1e-12));
    }

    @Test void stopOpensTheBreaker() {
        var d = new MvDrive();
        run(d, 1, 1, 50);
        d.stop();
        assertThat(d.step(DT, 1, 50)).isZero();
        assertThat(d.tripped()).isFalse();
        assertThat(d.demandW(1e6)).isZero();
    }

    @Test void stateSurvivesSaveAndLoad() {
        var d = new MvDrive();
        run(d, 1, 1, 50);
        run(d, 1, 0.5, 50);
        var copy = new MvDrive();
        copy.restore(d.bits());
        assertThat(copy.tripped()).isTrue();
        assertThat(copy.on()).isFalse();
        run(copy, 1, 1, 50);
        assertThat(copy.factor()).isZero();
    }

    @Test void rejectsNonPositiveStep() {
        assertThatThrownBy(() -> new MvDrive().step(0, 1, 50)).isInstanceOf(IllegalArgumentException.class);
    }
}
