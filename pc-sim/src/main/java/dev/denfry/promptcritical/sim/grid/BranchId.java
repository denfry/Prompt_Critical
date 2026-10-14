package dev.denfry.promptcritical.sim.grid;

/** Номер элемента в {@link PowerGrid} (порядок добавления). */
public record BranchId(int index) {
    public BranchId {
        if (index < 0) throw new IllegalArgumentException("index");
    }
}
