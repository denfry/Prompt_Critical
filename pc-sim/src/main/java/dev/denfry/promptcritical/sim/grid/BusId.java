package dev.denfry.promptcritical.sim.grid;

/** Номер элемента в {@link PowerGrid} (порядок добавления). */
public record BusId(int index) {
    public BusId {
        if (index < 0) throw new IllegalArgumentException("index");
    }
}
