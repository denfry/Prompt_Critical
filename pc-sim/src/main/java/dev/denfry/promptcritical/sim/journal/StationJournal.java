package dev.denfry.promptcritical.sim.journal;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Журнал станций с кольцевым буфером на станцию (04d §4d.8.2): старше {@code maxEntriesPerStation} записей
 * вытесняются. Сквозной номер общий для всех станций и не сбрасывается при вытеснении. Однопоточный: пишется и
 * читается в серверном тике.
 */
public final class StationJournal implements EventJournal {
    /** {@code journal.max_entries_per_station} [BALANCE] (§4d.8.2, ≈ 1,5 МБ на станцию). */
    public static final int DEFAULT_MAX_ENTRIES_PER_STATION = 10_000;

    private final int maxEntriesPerStation;
    private final Map<UUID, ArrayDeque<JournalEntry>> byStation = new LinkedHashMap<>();
    private long nextSeq = 1;

    public StationJournal(int maxEntriesPerStation) {
        if (maxEntriesPerStation < 1) throw new IllegalArgumentException("maxEntriesPerStation < 1");
        this.maxEntriesPerStation = maxEntriesPerStation;
    }

    public StationJournal() {
        this(DEFAULT_MAX_ENTRIES_PER_STATION);
    }

    @Override
    public JournalEntry append(JournalEntry e) {
        JournalEntry stored = e.withSeq(nextSeq++);
        ArrayDeque<JournalEntry> ring = byStation.computeIfAbsent(e.stationId(), k -> new ArrayDeque<>());
        ring.addLast(stored);
        while (ring.size() > maxEntriesPerStation) ring.removeFirst();
        return stored;
    }

    /**
     * Восстановление из сохранения: записи кладутся как есть, с их номерами; следующий номер — больше любого
     * восстановленного.
     */
    public void restore(JournalEntry e) {
        ArrayDeque<JournalEntry> ring = byStation.computeIfAbsent(e.stationId(), k -> new ArrayDeque<>());
        ring.addLast(e);
        while (ring.size() > maxEntriesPerStation) ring.removeFirst();
        nextSeq = Math.max(nextSeq, e.seq() + 1);
    }

    @Override
    public List<JournalEntry> query(UUID station, JournalFilter f, int limit) {
        ArrayDeque<JournalEntry> ring = byStation.get(station);
        if (ring == null || limit <= 0) return List.of();
        ArrayList<JournalEntry> out = new ArrayList<>(Math.min(limit, ring.size()));
        for (Iterator<JournalEntry> it = ring.descendingIterator(); it.hasNext() && out.size() < limit; ) {
            JournalEntry e = it.next();
            if (f.test(e)) out.add(e);
        }
        Collections.reverse(out);
        return out;
    }

    /** Все записи станции в порядке записи (экспорт, сохранение). */
    public List<JournalEntry> entries(UUID station) {
        ArrayDeque<JournalEntry> ring = byStation.get(station);
        return ring == null ? List.of() : List.copyOf(ring);
    }

    public Iterable<UUID> stations() {
        return Collections.unmodifiableSet(byStation.keySet());
    }

    public void forget(UUID station) {
        byStation.remove(station);
    }

    public long nextSeq() {
        return nextSeq;
    }

    public int maxEntriesPerStation() {
        return maxEntriesPerStation;
    }
}
