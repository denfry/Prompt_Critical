package dev.denfry.promptcritical.sim.grid;

/** Номер элемента в {@link PowerGrid} (порядок добавления). */
public record MachineId(int index) {
    public MachineId {
        if (index < 0) throw new IllegalArgumentException("index");
    }
}
