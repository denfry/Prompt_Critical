package dev.denfry.promptcritical.sim.utilities;

import java.util.ArrayDeque;

/** Очередь событий ВРУ для мира: установка пишет, BE разбирает между тиками. */
public final class UtilitiesLog implements UtilitiesEvents {
    public enum Kind { ASU_STATE, HYDROCARBON_HIGH, MOLSIEVE_BREAKTHROUGH, ASU_EXPLODED }

    /**
     * @param index новое состояние (ordinal)
     * @param value ppm углеводородов или энергия взрыва, Дж
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
    public void asuStateChanged(AsuState from, AsuState to) {
        add(Kind.ASU_STATE, to.ordinal(), from.ordinal());
    }

    @Override
    public void asuHydrocarbonHigh(double ppm) {
        add(Kind.HYDROCARBON_HIGH, 0, ppm);
    }

    @Override
    public void asuMolsieveBreakthrough() {
        add(Kind.MOLSIEVE_BREAKTHROUGH, 0, 0);
    }

    @Override
    public void asuExploded(double energyJ) {
        add(Kind.ASU_EXPLODED, 0, energyJ);
    }
}
