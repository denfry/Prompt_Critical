package dev.denfry.promptcritical.sim.telemetry;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Питание КП удалённой скважины от РИТЭГа (12 §12.5.1): гашение без питания, гистерезис, «Вечная батарейка». */
class RtuPowerTest {
    @Test
    void mainsFedRtuAlwaysRunsAndNeverCountsAsAutonomous() {
        RtuPower p = new RtuPower();
        for (int i = 0; i < 200; i++) assertThat(p.step(1, false, 0, true)).isTrue();
        assertThat(p.everlasting()).isFalse();
    }

    @Test
    void rtgFedRtuBrownsOutAndRestartsOnlyOnFullSupply() {
        RtuPower p = new RtuPower();
        assertThat(p.step(1, true, 1, false)).isTrue();
        assertThat(p.step(1, true, 0.95, false)).isTrue(); // в пределах допуска
        assertThat(p.step(1, true, 0.5, false)).isFalse();
        assertThat(p.step(1, true, 0.95, false)).isFalse(); // гистерезис: ждёт полного питания
        assertThat(p.step(1, true, 1, false)).isTrue();
    }

    @Test
    void aMinuteOfUnbrokenRtgPowerOnAWellIsEverlasting() {
        RtuPower p = new RtuPower();
        for (int i = 0; i < 59; i++) p.step(1, true, 1, true);
        assertThat(p.everlasting()).isFalse();
        p.step(1, true, 0, true); // источник вынули — отсчёт заново
        assertThat(p.autonomousS()).isZero();
        for (int i = 0; i < 59; i++) p.step(1, true, 1, true);
        assertThat(p.everlasting()).isFalse();
        p.step(1, true, 1, true);
        assertThat(p.everlasting()).isTrue();
        // без скважины на опросе отсчёт не идёт
        RtuPower q = new RtuPower();
        for (int i = 0; i < 120; i++) q.step(1, true, 1, false);
        assertThat(q.everlasting()).isFalse();
    }
}
