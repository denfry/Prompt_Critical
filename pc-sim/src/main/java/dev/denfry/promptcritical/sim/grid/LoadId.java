package dev.denfry.promptcritical.sim.grid;

/** Номер элемента в {@link PowerGrid} (порядок добавления). */
public record LoadId(int index) {
    public LoadId {
        if (index < 0) throw new IllegalArgumentException("index");
    }
}
