package dev.denfry.promptcritical.sim.manufacturing;

/** Событие серии ударов или термообработки заготовки (GDD 18 §18.15). Константы не переименовываются. */
public enum ForgeEvent {
    OK,
    /** Трещина от ковки ниже температурного интервала. */
    CRACKED,
    /** Продольный изгиб при осадке слишком длинной заготовки. */
    BUCKLED,
    /** Сечение стало бы тоньше {@link Forging#MIN_SECTION_M}: удар не принят. */
    TOO_THIN,
    BURNT,
    NOT_HARDENED,
    NOT_THROUGH_HARDENED,
    QUENCH_CRACKED
}
