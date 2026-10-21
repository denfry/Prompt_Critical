package dev.denfry.promptcritical.sim.metallurgy;

import java.util.ArrayDeque;

/** Очередь событий конвертера для мира: агрегат пишет, BE разбирает между тиками. */
public final class SteelmakingLog implements SteelmakingEvents {
    public enum Kind { PHASE, SLOPPING, OVERBLOWN, LANCE_DAMAGED, SCRAP_UNMELTED }

    /**
     * @param index новая фаза (ordinal)
     * @param value масса выброса или нерасплавленного лома, кг
     */
    public record Entry(Kind kind, int index, double value) {}

    private static final int CAPACITY = 32;
    private final ArrayDeque<Entry> queue = new ArrayDeque<>();

    private void add(Kind kind, int index, double value) {
        if (queue.size() >= CAPACITY) queue.pollFirst();
        queue.addLast(new Entry(kind, index, value));
    }

    /** Следующее событие или {@code null}. */
    public Entry poll() {
        return queue.pollFirst();
    }

    @Override
    public void converterPhaseChanged(ConverterPhase from, ConverterPhase to) {
        add(Kind.PHASE, to.ordinal(), from.ordinal());
    }

    @Override
    public void slopping(double slagKg, double metalKg) {
        add(Kind.SLOPPING, 0, slagKg + metalKg);
    }

    @Override
    public void overblown() {
        add(Kind.OVERBLOWN, 0, 0);
    }

    @Override
    public void lanceDamaged() {
        add(Kind.LANCE_DAMAGED, 0, 0);
    }

    @Override
    public void scrapUnmelted(double kg) {
        add(Kind.SCRAP_UNMELTED, 0, kg);
    }
}
