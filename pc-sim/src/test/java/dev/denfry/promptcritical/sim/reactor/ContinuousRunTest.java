package dev.denfry.promptcritical.sim.reactor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Сто суток без автоматической АЗ (11 §11.2.2 {@code hundred_days}). */
class ContinuousRunTest {
    private static final double DAY = 86_400;

    @Test
    void hundredDaysAtPowerFireOnce() {
        ContinuousRun r = new ContinuousRun();
        for (int d = 0; d < 99; d++) assertFalse(r.update(DAY, 1, false));
        assertFalse(r.update(DAY / 2, 0.01, false), "time shut down does not count");
        assertTrue(r.update(DAY, 0.8, false));
        assertFalse(r.update(DAY, 0.8, false));
        assertEquals(101, r.days(), 1e-9);
    }

    @Test
    void automaticTripResetsButManualDoesNot() {
        ContinuousRun r = new ContinuousRun();
        for (int d = 0; d < 50; d++) r.update(DAY, 1, false);
        r.update(DAY, 0, false); // ручная АЗ: мощность ноль, счёт стоит
        assertEquals(50, r.days(), 1e-9);
        r.update(0.05, 0.9, true);
        assertEquals(0, r.days());
    }

    @Test
    void restoredRunDoesNotReportTwice() {
        ContinuousRun r = new ContinuousRun();
        r.restore(ContinuousRun.TARGET_S + 10);
        assertFalse(r.update(DAY, 1, false));
        ContinuousRun s = new ContinuousRun();
        s.restore(ContinuousRun.TARGET_S - 10);
        assertTrue(s.update(20, 1, false));
    }
}
