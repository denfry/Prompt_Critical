package dev.denfry.promptcritical.sim.core;

/**
 * Последствие тика (05 §5.2.6, решение A6). В горячем цикле события пишутся в предвыделенный
 * {@link EventBuffer}; эти записи создаются только при доставке потребителю (CC, сеть, журнал) вне тика.
 * Виды добавляются вместе с подсистемами, которые их порождают.
 */
public sealed interface SimEvent permits SimEvent.CommandResult, SimEvent.Journal, SimEvent.DamageRequested,
        SimEvent.Milestone {
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

    /**
     * Событие домена для достижений и маршрута справочника (11 §11.2.1, триггер {@code promptcritical:sim_event}):
     * {@code event} — ID вида {@code promptcritical:engine_running}, {@code tag}/{@code value} — величина для предиката
     * (например {@code omega_pu} = 0,21). Строки — константы подсистем.
     */
    record Milestone(long tick, SimId source, String event, String tag, double value) implements SimEvent {
    }
}
