package dev.denfry.promptcritical.sim.combustion;

/** Отказ датчика пламени горелки (03b §3b.4.3.5). */
public enum FlameDetectorFault {
    NONE,
    /** Видит факел всегда — опасный отказ, как байпас ключом. */
    STUCK_FLAME,
    /** Не видит факел — ложное MFT. */
    STUCK_DARK
}
