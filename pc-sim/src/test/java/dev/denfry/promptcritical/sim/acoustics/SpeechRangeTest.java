package dev.denfry.promptcritical.sim.acoustics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

/** 16 §16.4.2: таблица дальностей речи. */
class SpeechRangeTest {
    private static double r(double noiseDb, double mask) {
        return SpeechRange.metres(SpeechRange.SPEECH_DB, noiseDb, SpeechRange.MIN_M, 48, mask);
    }

    @Test
    void quietYardIsCappedByTheServerRange() {
        assertThat(r(50, 1)).isEqualTo(48);
        assertThat(r(Double.NEGATIVE_INFINITY, 1)).isEqualTo(48);
    }

    @Test
    void pumpHouseLeavesFiveMetres() {
        assertThat(r(70, 1)).isCloseTo(5.0, within(0.02));
    }

    @Test
    void turbineHallFallsToTheFloor() {
        assertThat(r(85, 1)).isEqualTo(2);
    }

    @Test
    void masksMuffleTheWearerButNotBelowTheFloor() {
        assertThat(r(70, SpeechRange.MASK_FACTOR)).isCloseTo(3.0, within(0.02));
        assertThat(r(70, SpeechRange.SCBA_FACTOR)).isCloseTo(4.0, within(0.02));
        assertThat(r(85, SpeechRange.MASK_FACTOR)).isEqualTo(2);
        assertThat(r(50, SpeechRange.MASK_FACTOR)).isCloseTo(28.8, within(1e-9));
    }
}
