package dev.denfry.promptcritical.sim.grid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class InverterSourceTest {
    private static final double DT = 0.05;

    private static InverterSource online(double rated) {
        var s = new InverterSource(rated, 0.96);
        for (double t = 0; t < FeInterface.RECONNECT_S + FeInterface.RAMP_S + 3; t += DT) s.step(DT, 1, 50, rated, rated, rated);
        return s;
    }

    @Test void followsTheAvailablePowerWithAFifthOfASecondLag() {
        var s = online(100e3);
        assertThat(s.powerW()).isCloseTo(100e3, within(1.0));
        s.step(DT, 1, 50, 50e3, 50e3, 0);
        assertThat(s.powerW()).as("one step of the 0.2 s lag").isCloseTo(100e3 - 50e3 * DT / InverterSource.RESPONSE_S, within(1.0));
        for (int i = 0; i < 40; i++) s.step(DT, 1, 50, 50e3, 50e3, 0);
        assertThat(s.powerW()).isCloseTo(50e3, within(10.0));
    }

    @Test void currentLimitCutsPowerOnASaggingBus() {
        var s = online(100e3);
        for (int i = 0; i < 40; i++) s.step(DT, 0.92, 50, 200e3, 200e3, 0);
        assertThat(s.powerW()).isCloseTo(InverterSource.CURRENT_LIMIT_PU * 100e3 * 0.92, within(10.0));
    }

    @Test void tripsWithoutVoltageAndWaitsToReconnect() {
        var s = online(100e3);
        for (double t = 0; t < 0.3; t += DT) s.step(DT, 0, 0, 100e3, 100e3, 0);
        assertThat(s.connected()).isFalse();
        assertThat(s.powerW()).isZero();
        for (double t = 0; t < FeInterface.RECONNECT_S - 1; t += DT) s.step(DT, 1, 50, 100e3, 100e3, 0);
        assertThat(s.connected()).isFalse();
    }

    @Test void syntheticInertiaPushesPowerWhenFrequencyFalls() {
        var a = online(100e3);
        var b = online(100e3);
        b.setSyntheticH(4);
        for (int i = 0; i < 4; i++) {
            double f = 50 - 0.05 * (i + 1);
            a.step(DT, 1, f, 50e3, 100e3, 100e3);
            b.step(DT, 1, f, 50e3, 100e3, 100e3);
        }
        assertThat(b.powerW()).isGreaterThan(a.powerW() + 10e3);
    }

    @Test void batteryChargingDrawsFromTheGrid() {
        var s = online(100e3);
        for (int i = 0; i < 40; i++) s.step(DT, 1, 50, -60e3, 0, 80e3);
        assertThat(s.powerW()).isCloseTo(-60e3, within(10.0));
    }
}
