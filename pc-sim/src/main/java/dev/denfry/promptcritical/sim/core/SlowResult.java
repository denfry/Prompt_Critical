package dev.denfry.promptcritical.sim.core;

/** Результат задания {@link SlowJob}; тип знает только подсистема-владелец. */
public interface SlowResult {
    SlowResult NONE = new SlowResult() {
    };
}
