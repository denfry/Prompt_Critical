package dev.denfry.promptcritical.sim.core;

/** Временные домены симуляции (00 §0.4, 00 §0.13 R9). */
public enum TimeDomain {
    /** Каждый тик: Δt = 0,05 с реального = 0,05 с физики. */
    FAST(1),
    /** Каждые 20 тиков = 1 с реального = K_slow с физики. */
    SLOW(20),
    /** Каждые 1200 тиков = 1 мин реального = K_storage·60 с физики (02). */
    STORAGE(1200);

    public final int periodTicks;

    TimeDomain(int periodTicks) {
        this.periodTicks = periodTicks;
    }
}
