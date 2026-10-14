package dev.denfry.promptcritical.sim.consequence;

/** Почему бюджет не дал изменить блоки (04d §4d.2.2). */
public enum DenyReason {
    NONE,
    MODE_FORBIDS,
    OUTSIDE_TERRITORY,
    PER_EVENT_CAP,
    PER_CHUNK_CAP,
    PER_REGION_CAP,
    QUEUE_FULL,
    PROTECTED_BLOCK
}
