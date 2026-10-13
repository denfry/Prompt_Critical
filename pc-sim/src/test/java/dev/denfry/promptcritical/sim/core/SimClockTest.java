package dev.denfry.promptcritical.sim.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class SimClockTest {
    private static final double K_SLOW = 72.0;
    private static final double K_STORAGE = 8766.0;

    @Test
    void timeDomainPeriodsMatchContract() {
        assertThat(TimeDomain.FAST.periodTicks).isEqualTo(1);
        assertThat(TimeDomain.SLOW.periodTicks).isEqualTo(20);
        assertThat(TimeDomain.STORAGE.periodTicks).isEqualTo(1200);
    }

    @Test
    void fastTimeAdvancesByTickLength() {
        SimClock clock = new SimClock(K_SLOW, K_STORAGE);
        for (int i = 0; i < 40; i++) clock.advance();
        assertThat(clock.tick()).isEqualTo(40);
        assertThat(clock.simTime()).isCloseTo(2.0, within(1e-12));
    }

    @Test
    void slowPhysTimeCountsOnlyCompletedSlowSteps() {
        SimClock clock = new SimClock(19, K_SLOW, K_STORAGE);
        assertThat(clock.slowPhysTime()).isZero();
        clock.advance();
        assertThat(clock.slowPhysTime()).isEqualTo(K_SLOW);
        assertThat(clock.slowDtPhys()).isCloseTo(K_SLOW, within(1e-12));
    }

    @Test
    void oneMinecraftDayIsOnePhysicalDayInSlow() {
        // 00 §0.4: 1 игровые сутки MC (20 мин = 24 000 тиков) = 1 сутки «физики» при K_slow = 72.
        SimClock clock = new SimClock(24_000, K_SLOW, K_STORAGE);
        assertThat(clock.slowPhysTime()).isCloseTo(86_400.0, within(1e-9));
    }

    @Test
    void storageMakesOneRealHourOnePhysicalYear() {
        // 00 §0.13 R9: k_storage = 8766 ⇒ 1 реальный час = 1 физический год (8766 ч).
        SimClock clock = new SimClock(K_SLOW, K_STORAGE);
        double physSecondsPerRealHour = clock.storageDtPhys() * 60; // 60 STORAGE-шагов в реальном часе
        assertThat(physSecondsPerRealHour / 3600.0).isCloseTo(8766.0, within(1e-6));
    }

    @Test
    void dueRespectsPhase() {
        SimClock clock = new SimClock(45, K_SLOW, K_STORAGE);
        assertThat(clock.due(TimeDomain.FAST, 0)).isTrue();
        assertThat(clock.due(TimeDomain.SLOW, 5)).isTrue();
        assertThat(clock.due(TimeDomain.SLOW, 0)).isFalse();
        assertThat(clock.slowAlpha()).isCloseTo(0.25, within(1e-12));
    }

    @Test
    void invalidArgumentsAreRejected() {
        SimClock clock = new SimClock(K_SLOW, K_STORAGE);
        assertThatThrownBy(() -> clock.due(TimeDomain.SLOW, 20)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SimClock(0.0, K_STORAGE)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SimClock(-1, K_SLOW, K_STORAGE)).isInstanceOf(IllegalArgumentException.class);
    }
}
