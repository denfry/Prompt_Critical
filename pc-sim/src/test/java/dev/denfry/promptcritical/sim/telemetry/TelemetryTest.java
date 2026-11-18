package dev.denfry.promptcritical.sim.telemetry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class TelemetryTest {
    /** Запас расходуется пачкой, дальше — не чаще скорости пополнения; пополнение не выше запаса. */
    @Test
    void tokenBucketLimitsBurstAndRate() {
        TokenBucket b = new TokenBucket(2, 4);
        for (int i = 0; i < 4; i++) assertThat(b.tryTake(100, 1)).isTrue();
        assertThat(b.tryTake(100, 1)).isFalse();
        assertThat(b.tryTake(105, 1)).isFalse();
        assertThat(b.tryTake(110, 1)).isTrue();
        assertThat(b.available(10_000)).isCloseTo(4, within(1e-9));
        // спам: 1000 запросов за тик проходят не больше запаса
        int passed = 0;
        for (int i = 0; i < 1000; i++) if (b.tryTake(10_000, 1)) passed++;
        assertThat(passed).isEqualTo(4);
    }

    @Test
    void scanCycleKeepsOneSnapshotPerPeriod() {
        ScanCycle c = new ScanCycle(20);
        assertThat(c.due(0)).isTrue();
        assertThat(c.ageSeconds(0)).isNaN();
        c.scanned(0);
        assertThat(c.due(19)).isFalse();
        assertThat(c.ageSeconds(10)).isCloseTo(0.5, within(1e-12));
        assertThat(c.due(20)).isTrue();
        // сброс часов мира (загрузка старого сохранения) — опрос сразу
        assertThat(c.due(-5)).isTrue();
    }
}
