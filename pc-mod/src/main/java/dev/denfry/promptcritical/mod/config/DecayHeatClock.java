package dev.denfry.promptcritical.mod.config;

/** Часы остаточного тепловыделения, ключ {@code nuclide.decay_heat_clock} (01 §1.9.1, 00 §0.13 R9). */
public enum DecayHeatClock {
    /** Быстрые группы ANS — в FAST в реальном времени, медленные — в SLOW с K_slow. */
    SPLIT,
    /** Все группы в реальном времени (хардкор). */
    REAL_TIME
}
