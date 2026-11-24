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

    /** 13 §13.3.4: в машзале 85 дБ(А) рупор покрывает 5,6 м, во дворе 55 дБ(А) — весь предел 48 м. */
    @Test
    void speakerCoverageFollowsTheNoise() {
        assertThat(SpeechRange.speakerMetres(SpeechRange.PA_SPEAKER_DB, 85, SpeechRange.PA_SNR_DB, 48)).isCloseTo(5.62, within(0.01));
        assertThat(SpeechRange.speakerMetres(SpeechRange.PA_SPEAKER_DB, 55, SpeechRange.PA_SNR_DB, 48)).isEqualTo(48);
        assertThat(SpeechRange.speakerMetres(SpeechRange.PA_SPEAKER_DB, Double.NEGATIVE_INFINITY, SpeechRange.PA_SNR_DB, 48)).isEqualTo(48);
    }
}
