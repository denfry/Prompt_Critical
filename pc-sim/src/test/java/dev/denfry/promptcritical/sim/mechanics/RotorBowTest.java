package dev.denfry.promptcritical.sim.mechanics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class RotorBowTest {

    /** 03a §3a.7.6: горячий ротор без валоповорота за 30 физ. мин выгибается полностью — вибрация ×11. */
    @Test
    void stoppedHotRotorBowsInThirtyMinutes() {
        RotorBow r = new RotorBow();
        r.step(60, true, false);
        for (int i = 0; i < 900; i++) r.step(1, false, false);
        assertThat(r.bow()).isCloseTo(0.5, within(1e-9));
        for (int i = 0; i < 900; i++) r.step(1, false, false);
        assertThat(r.bow()).isCloseTo(1, within(1e-9));
        assertThat(r.vibrationFactor()).isCloseTo(11, within(1e-8));
    }

    /** Валоповорот не даёт прогибу расти и выпрямляет ротор; холодный ротор не выгибается. */
    @Test
    void turningGearKeepsTheRotorStraight() {
        RotorBow r = new RotorBow(0.5, 0);
        for (int i = 0; i < 1800; i++) r.step(1, false, true);
        assertThat(r.bow()).isCloseTo(0.5 * Math.exp(-3), within(1e-6));
        RotorBow cold = new RotorBow(0, RotorBow.HOT_TIME_S + 1);
        cold.step(600, false, false);
        assertThat(cold.bow()).isZero();
    }
}
