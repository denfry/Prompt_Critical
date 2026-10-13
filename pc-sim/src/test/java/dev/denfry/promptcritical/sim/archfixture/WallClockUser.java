package dev.denfry.promptcritical.sim.archfixture;

/** Нарушитель правила noWallClock. */
public final class WallClockUser {
    public long now() {
        return System.currentTimeMillis();
    }
}
