package dev.denfry.promptcritical.sim.core;

/**
 * Последствие тика (05 §5.2.6, решение A6). В горячем цикле события пишутся в предвыделенный
 * {@link EventBuffer}; эти записи создаются только при доставке потребителю (CC, сеть, журнал) вне тика.
 * Виды добавляются вместе с подсистемами, которые их порождают.
 */
public sealed interface SimEvent permits SimEvent.CommandResult, SimEvent.Journal, SimEvent.DamageRequested {
    long tick();

    SimId source();

    /** Итог команды с билетом {@code ticket}; {@code source} — адресат команды. */
    record CommandResult(long tick, SimId source, long ticket, CommandStatus status) implements SimEvent {
    }

    /** Запись журнала станции (04d §4d.8). */
    record Journal(long tick, SimId source, Severity severity, String message) implements SimEvent {
    }

    /** Домен запросил разрушение блоков (04d §4d.2); итог бюджета — для журнала. */
    record DamageRequested(long tick, SimId source, long requestId, int blocksGranted, int blocksDenied)
            implements SimEvent {
    }
}
