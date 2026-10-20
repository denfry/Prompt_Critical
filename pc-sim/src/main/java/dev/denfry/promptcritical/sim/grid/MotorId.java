package dev.denfry.promptcritical.sim.grid;

/** Номер элемента в {@link PowerGrid} (порядок добавления). */
public record MotorId(int index) {
    public MotorId {
        if (index < 0) throw new IllegalArgumentException("index");
    }
}
