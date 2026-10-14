package dev.denfry.promptcritical.sim.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

class DegradationControllerTest {
    @Test
    @Tag("T-05-14")
    void p95Of2point2msEntersL1Within200Ticks() {
        DegradationController c = new DegradationController(2.0);
        int ticks = 0;
        while (c.level() == DegradationLevel.L0 && ticks < 1000) {
            c.record(2_200_000);
            ticks++;
        }
        assertThat(c.level()).isEqualTo(DegradationLevel.L1);
        assertThat(ticks).isLessThanOrEqualTo(200);
    }

    @Test
    void levelsHaveHysteresis() {
        DegradationController c = new DegradationController(2.0);
        for (int i = 0; i < 200; i++) c.record(5_200_000);            // 2,6 бюджета → L3
        assertThat(c.level()).isEqualTo(DegradationLevel.L3);
        for (int i = 0; i < 200; i++) c.record(3_200_000);            // 1,6: выше выхода L3 (1,5)
        assertThat(c.level()).isEqualTo(DegradationLevel.L3);
        for (int i = 0; i < 400; i++) c.record(1_600_000);            // 0,8: спуск по одному уровню до L1
        assertThat(c.level()).isEqualTo(DegradationLevel.L1);
        for (int i = 0; i < 400; i++) c.record(1_000_000);            // 0,5 < 0,7 → L0
        assertThat(c.level()).isEqualTo(DegradationLevel.L0);
    }
}
