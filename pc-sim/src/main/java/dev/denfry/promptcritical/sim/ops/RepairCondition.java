package dev.denfry.promptcritical.sim.ops;

/** Условия ремонта строки панели (13 §13.6.5); в срезе M3 проверяется только {@code STOP} и состояние котла. */
public enum RepairCondition {
    /** Машина стоит, вал расцеплен. */
    STOP,
    /** Котёл остыл. */
    COLD,
    /** Котёл сдренирован. */
    DRAINED,
    LOTO,
    PERMIT_HOT
}
