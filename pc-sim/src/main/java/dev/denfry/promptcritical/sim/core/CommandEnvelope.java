package dev.denfry.promptcritical.sim.core;

/**
 * Конверт команды (05 §5.2.6): источник и порядок.
 *
 * @param ticket         сквозной номер, возвращаемый отправителю; приходит обратно в {@link SimEvent.CommandResult}
 * @param submitTick     тик мира на момент постановки в очередь
 * @param sourcePriority класс источника ({@link CommandSource#sortClass()})
 * @param sourceSeq      номер команды у источника; порядок внутри одного источника сохраняется
 */
public record CommandEnvelope(SimCommand command, CommandSource source, long ticket, long submitTick,
        int sourcePriority, long sourceSeq) {

    /** Ключ сортировки 05 §5.4.3: класс источника → номер источника → номер у источника → билет. */
    static int compare(CommandEnvelope a, CommandEnvelope b) {
        int c = Integer.compare(a.sourcePriority, b.sourcePriority);
        if (c != 0) return c;
        c = Long.compare(a.source.sortId(), b.source.sortId());
        if (c != 0) return c;
        c = Long.compare(a.sourceSeq, b.sourceSeq);
        if (c != 0) return c;
        return Long.compare(a.ticket, b.ticket);
    }
}
