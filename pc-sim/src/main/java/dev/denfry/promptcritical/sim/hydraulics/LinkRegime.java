package dev.denfry.promptcritical.sim.hydraulics;

/** Режим связи на последнем шаге. */
public enum LinkRegime {
    NORMAL,
    /** Расход ограничен критическим C_D·A·G_c (01 §1.2.8). */
    CHOKED,
    /** Арматура закрыта: связь исключена из системы (§1.2.6). */
    CLOSED,
    /** Обратный клапан сел: ζ_rev (§1.2.2). */
    REVERSE_BLOCKED
}
