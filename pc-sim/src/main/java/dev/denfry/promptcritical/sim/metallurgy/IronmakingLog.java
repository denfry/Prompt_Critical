package dev.denfry.promptcritical.sim.metallurgy;

import java.util.ArrayDeque;

/** Очередь событий коксохима и домны для мира: агрегат пишет, BE разбирает между тиками. */
public final class IronmakingLog implements IronmakingEvents {
    public enum Kind { PUSH_OVERLOAD, REFRACTORY_FAILED, FURNACE_STATE, SLIP, TUYERE_BURNT, BURNTHROUGH, GAS_MAIN_EXPLOSION }

    /**
     * @param index камера, новое состояние печи (ordinal) или число прогоревших фурм
     * @param value тяжесть осадки, масса прорыва
     */
    public record Entry(Kind kind, int index, double value) {}

    private static final int CAPACITY = 32;
    private final ArrayDeque<Entry> queue = new ArrayDeque<>();

    void add(Kind kind, int index, double value) {
        if (queue.size() >= CAPACITY) queue.pollFirst();
        queue.addLast(new Entry(kind, index, value));
    }

    /** Следующее событие или {@code null}. */
    public Entry poll() {
        return queue.pollFirst();
    }

    @Override
    public void cokePushOverload(int oven) {
        add(Kind.PUSH_OVERLOAD, oven, 0);
    }

    @Override
    public void refractoryFailed() {
        add(Kind.REFRACTORY_FAILED, 0, 0);
    }

    @Override
    public void furnaceStateChanged(BlastFurnaceState from, BlastFurnaceState to) {
        add(Kind.FURNACE_STATE, to.ordinal(), from.ordinal());
    }

    @Override
    public void slip(double severity) {
        add(Kind.SLIP, 0, severity);
    }

    @Override
    public void tuyereBurnt(int burntTotal) {
        add(Kind.TUYERE_BURNT, burntTotal, 0);
    }

    @Override
    public void burnthrough(double ironKg, double slagKg) {
        add(Kind.BURNTHROUGH, 0, ironKg + slagKg);
    }
}
