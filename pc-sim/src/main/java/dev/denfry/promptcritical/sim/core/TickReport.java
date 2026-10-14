package dev.denfry.promptcritical.sim.core;

/**
 * Отчёт о последнем тике (05 §5.2.1). В отличие от записи GDD — изменяемый объект мира, переписываемый каждый
 * тик: так {@link SimulationWorld#tick} не выделяет память. Хранить ссылку между тиками нельзя.
 */
public final class TickReport {
    private long tick;
    private long fastNanos;
    private long slowMergeNanos;
    private int commandsApplied;
    private int eventsEmitted;
    private DegradationLevel degradation = DegradationLevel.L0;

    void set(long tick, long fastNanos, long slowMergeNanos, int commandsApplied, int eventsEmitted,
            DegradationLevel degradation) {
        this.tick = tick;
        this.fastNanos = fastNanos;
        this.slowMergeNanos = slowMergeNanos;
        this.commandsApplied = commandsApplied;
        this.eventsEmitted = eventsEmitted;
        this.degradation = degradation;
    }

    public long tick() {
        return tick;
    }

    public long fastNanos() {
        return fastNanos;
    }

    public long slowMergeNanos() {
        return slowMergeNanos;
    }

    public int commandsApplied() {
        return commandsApplied;
    }

    public int eventsEmitted() {
        return eventsEmitted;
    }

    public DegradationLevel degradation() {
        return degradation;
    }
}
