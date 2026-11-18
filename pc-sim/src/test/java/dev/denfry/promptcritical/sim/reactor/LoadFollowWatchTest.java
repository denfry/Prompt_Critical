package dev.denfry.promptcritical.sim.reactor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.denfry.promptcritical.sim.grid.DailyLoadProfile;
import org.junit.jupiter.api.Test;

/** Суточный график 07 §7.6.4 и следование ему реактором (11 §11.2.2 {@code load_follow}). */
class LoadFollowWatchTest {
    private static final double DT = 60;

    /** Таблица §7.6.4: провал 0,60 в 3:30, пик 1,00 в 19:30, 0,92 в 9:00. */
    @Test
    void profileMatchesTheTable() {
        assertEquals(0.60, DailyLoadProfile.shape(3.5), 0.015);
        assertEquals(1.00, DailyLoadProfile.shape(19.5), 0.005);
        assertEquals(0.92, DailyLoadProfile.shape(9), 0.02);
        assertEquals(0.78, DailyLoadProfile.shape(0), 0.02);
        assertEquals(6, DailyLoadProfile.hourOfDay(0, 1), 1e-9);
        assertEquals(18, DailyLoadProfile.hourOfDay(12_000, 1), 1e-9);
        assertEquals(12, DailyLoadProfile.hourOfDay(12_000, 2), 1e-9);
        assertEquals(1.2, DailyLoadProfile.temperatureFactor(5), 1e-9);
        assertEquals(1.0, DailyLoadProfile.temperatureFactor(25), 1e-9);
    }

    /** Шум Орнштейна–Уленбека держит σ ≈ 2 %. */
    @Test
    void noiseHasTheStatedSigma() {
        java.util.SplittableRandom rng = new java.util.SplittableRandom(1);
        double xi = 0, s2 = 0;
        int n = 200_000;
        for (int i = 0; i < n; i++) {
            xi = DailyLoadProfile.noiseStep(xi, 30, rng);
            s2 += xi * xi;
        }
        assertEquals(DailyLoadProfile.NOISE_SIGMA, Math.sqrt(s2 / n), 0.003);
    }

    /** Мощность по графику сутки — событие; постоянная мощность — нет. */
    @Test
    void followingTheCurveForADayCounts() {
        LoadFollowWatch follow = new LoadFollowWatch(), flat = new LoadFollowWatch();
        boolean fired = false, flatFired = false;
        for (double t = 0; t <= 26 * 3600; t += DT) {
            double h = (8 + t / 3600) % 24, shape = DailyLoadProfile.shape(h);
            fired |= follow.update(DT, shape, 0.95 * shape, false);
            flatFired |= flat.update(DT, shape, 0.9, false);
        }
        assertTrue(fired);
        assertFalse(flatFired);
        assertTrue(flat.hours() < 12, "flat power drifts off the curve within hours: " + flat.hours());
    }

    @Test
    void scramResetsTheDay() {
        LoadFollowWatch w = new LoadFollowWatch();
        for (double t = 0; t < 20 * 3600; t += DT) w.update(DT, DailyLoadProfile.shape(t / 3600), 0.9 * DailyLoadProfile.shape(t / 3600), false);
        assertTrue(w.hours() > 19);
        w.update(DT, 0.8, 0.0, true);
        assertEquals(0, w.hours());
    }
}
