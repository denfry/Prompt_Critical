package dev.denfry.promptcritical.sim.journal;

import dev.denfry.promptcritical.sim.core.Severity;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Свёртка ломания и установки блоков станции (04d §4d.8.2): серия одного игрока, в которой между блоками проходит не
 * больше окна (по умолчанию 10 с = 200 тиков), становится одной записью с числом блоков. Severity — INFO, если хоть
 * один блок серии тронул не-член станции — WARNING.
 */
public final class BlockSeriesAggregator {
    public static final long DEFAULT_WINDOW_TICKS = 10L * 20L;

    private record Key(UUID station, UUID player, JournalAction action) {}

    private static final class Series {
        final Actor.Player actor;
        final long firstTick;
        final String firstTarget;
        long lastTick;
        int count;
        boolean byOutsider;

        Series(Actor.Player actor, long tick, String target, boolean outsider) {
            this.actor = actor;
            this.firstTick = tick;
            this.firstTarget = target;
            this.lastTick = tick;
            this.count = 1;
            this.byOutsider = outsider;
        }
    }

    private final long windowTicks;
    private final Map<Key, Series> open = new LinkedHashMap<>();

    public BlockSeriesAggregator(long windowTicks) {
        this.windowTicks = windowTicks;
    }

    public BlockSeriesAggregator() {
        this(DEFAULT_WINDOW_TICKS);
    }

    /**
     * Отмечает блок. Если предыдущая серия того же игрока на станции прервалась, возвращает её запись (иначе пустой
     * список).
     *
     * @param action {@link JournalAction#BLOCK_BREAK} или {@link JournalAction#BLOCK_PLACE}
     * @param member игрок — член станции
     */
    public List<JournalEntry> note(UUID station, Actor.Player actor, JournalAction action, String target,
                                   boolean member, long tick) {
        if (action != JournalAction.BLOCK_BREAK && action != JournalAction.BLOCK_PLACE)
            throw new IllegalArgumentException("not a block action: " + action);
        Key k = new Key(station, actor.uuid(), action);
        Series s = open.get(k);
        List<JournalEntry> out = List.of();
        if (s != null && tick - s.lastTick > windowTicks) {
            out = List.of(entry(k, s));
            s = null;
        }
        if (s == null) open.put(k, new Series(actor, tick, target, !member));
        else {
            s.count++;
            s.lastTick = tick;
            s.byOutsider |= !member;
        }
        return out;
    }

    /** Закрывает серии, в которых окно истекло к {@code tick}; {@code all} — закрывает все (сохранение, выход). */
    public List<JournalEntry> flush(long tick, boolean all) {
        List<JournalEntry> out = new ArrayList<>();
        for (Iterator<Map.Entry<Key, Series>> it = open.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Key, Series> e = it.next();
            if (all || tick - e.getValue().lastTick > windowTicks) {
                out.add(entry(e.getKey(), e.getValue()));
                it.remove();
            }
        }
        return out;
    }

    private static JournalEntry entry(Key k, Series s) {
        String detail = "count=" + s.count + " ticks=" + s.firstTick + ".." + s.lastTick;
        return new JournalEntry(0, s.lastTick, 0.0, k.station(), s.actor, k.action(), s.firstTarget, detail,
                s.byOutsider ? Severity.WARNING : Severity.INFO);
    }
}
