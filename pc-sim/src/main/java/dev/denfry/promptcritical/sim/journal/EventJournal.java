package dev.denfry.promptcritical.sim.journal;

import java.util.List;
import java.util.UUID;

/** Журнал станций (04d §4d.8.1); пишется из серверного потока. */
public interface EventJournal {
    /** Добавляет запись под следующим сквозным номером и возвращает её с этим номером. */
    JournalEntry append(JournalEntry e);

    /** Последние {@code limit} записей станции, прошедших фильтр, в порядке записи. */
    List<JournalEntry> query(UUID station, JournalFilter f, int limit);
}
