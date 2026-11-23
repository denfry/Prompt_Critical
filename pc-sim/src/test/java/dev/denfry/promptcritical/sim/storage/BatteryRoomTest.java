package dev.denfry.promptcritical.sim.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class BatteryRoomTest {
    @Test
    void ventilationHoldsTheSteadyStateAtSourceOverAirChanges() {
        BatteryRoom r = new BatteryRoom(400, 2);
        double qM3s = 1e-4; // 0,36 м³/ч
        for (int i = 0; i < 20 * 3600; i++) r.step(1, qM3s * BatteryRoom.H2_KG_M3);
        assertThat(r.h2M3()).isCloseTo(qM3s / (2.0 / 3600), within(1e-3));
        assertThat(r.alarm()).isFalse();
    }

    @Test
    void stepSizeDoesNotChangeTheResult() {
        BatteryRoom a = new BatteryRoom(100, 1), b = new BatteryRoom(100, 1);
        for (int i = 0; i < 3600; i++) a.step(1, 1e-5);
        b.step(3600, 3600 * 1e-5);
        assertThat(a.h2M3()).isCloseTo(b.h2M3(), within(1e-3 * b.h2M3()));
    }

    @Test
    void aSealedRoomReachesTheLowerExplosiveLimit() {
        BatteryRoom r = new BatteryRoom(50, 0);
        r.step(10, 50 * BatteryRoom.LEL * BatteryRoom.H2_KG_M3 * 0.3);
        assertThat(r.lelFraction()).isCloseTo(0.3, within(1e-9));
        assertThat(r.alarm()).isTrue();
        assertThat(r.explosive()).isFalse();
        r.step(10, 50 * BatteryRoom.LEL * BatteryRoom.H2_KG_M3 * 0.8);
        assertThat(r.explosive()).isTrue();
        assertThat(r.burn()).isCloseTo(50 * BatteryRoom.LEL * BatteryRoom.H2_KG_M3 * 1.1, within(1e-9));
        assertThat(r.h2Fraction()).isZero();
    }

    @Test
    void airWashesHydrogenOutWithTheAirChangeTimeConstant() {
        BatteryRoom r = new BatteryRoom(100, 3);
        r.setH2M3(2);
        r.step(1200, 0); // τ = 1/3 ч
        assertThat(r.h2M3()).isCloseTo(2 * Math.exp(-1), within(1e-9));
    }
}
