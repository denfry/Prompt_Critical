package dev.denfry.promptcritical.sim.journal;

import dev.denfry.promptcritical.sim.core.Severity;
import java.util.Objects;
import java.util.UUID;

/**
 * Запись журнала станции (04d §4d.8.1).
 *
 * @param seq    сквозной номер в журнале, растёт монотонно
 * @param target объект действия (устройство, координаты), может быть пустым
 */
public record JournalEntry(long seq, long gameTick, double simTime, UUID stationId, Actor actor,
                           JournalAction action, String target, String detail, Severity severity) {
    public JournalEntry {
        Objects.requireNonNull(stationId, "stationId");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(severity, "severity");
        target = target == null ? "" : target;
        detail = detail == null ? "" : detail;
    }

    /** Та же запись под другим номером — для {@link StationJournal#append}. */
    JournalEntry withSeq(long s) {
        return new JournalEntry(s, gameTick, simTime, stationId, actor, action, target, detail, severity);
    }
}
