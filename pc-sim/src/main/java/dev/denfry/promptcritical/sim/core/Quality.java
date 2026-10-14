package dev.denfry.promptcritical.sim.core;

/** Качество опубликованного значения в {@link SimSnapshot} (03b). */
public enum Quality {
    GOOD,
    /** Объект в процессе пересборки топологии или канал только что «проснулся» (05 §5.3.3, §5.4.5). */
    UNCERTAIN,
    BAD
}
