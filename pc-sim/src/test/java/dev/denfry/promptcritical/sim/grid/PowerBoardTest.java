package dev.denfry.promptcritical.sim.grid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class PowerBoardTest {
    private static final double DT = 0.05;

    private static void run(PowerBoard b, double seconds, double vPu, double fHz) {
        for (double t = 0; t < seconds; t += DT) b.step(DT, vPu, fHz);
    }

    @Test void boardFeedsTheWholeDemandWithinItsRating() {
        var b = new PowerBoard(PowerBoard.Kind.BOARD);
        b.setDemandW(60e3);
        assertThat(b.step(DT, 1, 50)).isCloseTo(60e3, within(1e-6));
        assertThat(b.supplyFactor()).isCloseTo(1, within(1e-9));
    }

    @Test void overloadSlowsTheGroupDownInsteadOfTripping() {
        var b = new PowerBoard(PowerBoard.Kind.SOCKET_3P);
        b.setDemandW(44e3);
        assertThat(b.step(DT, 1, 50)).isCloseTo(22e3, within(1e-6));
        assertThat(b.supplyFactor()).isCloseTo(0.5, within(1e-9));
        assertThat(b.on()).isTrue();
    }

    @Test void motorsFollowTheGridFrequency() {
        var b = new PowerBoard(PowerBoard.Kind.BOARD);
        b.setDemandW(10e3);
        b.step(DT, 1, 49);
        assertThat(b.supplyFactor()).isCloseTo(0.98, within(1e-9));
    }

    @Test void undervoltageDropsTheContactorAndItStaysOff() {
        var b = new PowerBoard(PowerBoard.Kind.BOARD);
        b.setDemandW(10e3);
        run(b, 1, 1, 50);
        run(b, PowerBoard.UV_DELAY_S + 0.1, 0.5, 50);
        assertThat(b.on()).isFalse();
        assertThat(b.tripped()).isTrue();
        run(b, 2, 1, 50);
        assertThat(b.on()).as("no self-start").isFalse();
        assertThat(b.supplyFactor()).isZero();
        b.start();
        b.step(DT, 1, 50);
        assertThat(b.supplyFactor()).isCloseTo(1, within(1e-9));
    }

    @Test void shortDipDoesNotTrip() {
        var b = new PowerBoard(PowerBoard.Kind.SOCKET);
        b.setDemandW(1e3);
        run(b, 1, 1, 50);
        run(b, PowerBoard.UV_DELAY_S - 0.15, 0.6, 50);
        run(b, 1, 1, 50);
        assertThat(b.on()).isTrue();
        assertThat(b.supplyFactor()).isCloseTo(1, within(1e-9));
    }

    @Test void boardPlacedOnADeadBusWaitsForVoltageWithoutTripping() {
        var b = new PowerBoard(PowerBoard.Kind.BOARD);
        b.setDemandW(10e3);
        run(b, 10, 0, 0);
        assertThat(b.tripped()).isFalse();
        b.step(DT, 1, 50);
        assertThat(b.supplyFactor()).isCloseTo(1, within(1e-9));
    }

    @Test void upsRidesThroughABlackoutOnItsBattery() {
        var u = new PowerBoard(PowerBoard.Kind.UPS);
        u.setDemandW(9e3);
        double p = u.step(DT, 1, 50);
        assertThat(p).isGreaterThan(9e3 / PowerBoard.UPS_EFFICIENCY - 1);
        run(u, 600, 0, 0);
        assertThat(u.onBattery()).isTrue();
        assertThat(u.supplyFactor()).isCloseTo(1, within(1e-9));
        assertThat(u.gridW()).isZero();
        assertThat(u.batterySoc()).isLessThan(0.8);
    }

    @Test void upsBatteryRunsOutAndRecharges() {
        var u = new PowerBoard(PowerBoard.Kind.UPS);
        u.setDemandW(9e3);
        run(u, 3600, 0, 0);
        assertThat(u.supplyFactor()).isZero();
        run(u, 3 * 3600, 1, 50);
        assertThat(u.supplyFactor()).isCloseTo(1, within(1e-9));
        assertThat(u.batterySoc()).isGreaterThan(0.2);
    }

    @Test void boardRatingCycles() {
        var b = new PowerBoard(PowerBoard.Kind.BOARD);
        double first = b.ratedW();
        for (int i = 0; i < PowerBoard.BOARD_RATINGS_W.length; i++) b.cycleRating();
        assertThat(b.ratedW()).isEqualTo(first);
        var s = new PowerBoard(PowerBoard.Kind.SOCKET);
        s.cycleRating();
        assertThat(s.ratedW()).isEqualTo(PowerBoard.Kind.SOCKET.defaultRatedW);
    }
}
