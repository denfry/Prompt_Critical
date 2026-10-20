package dev.denfry.promptcritical.sim.grid;

/** Реле, установленное в сеть ({@link PowerGrid#addRelay}). */
public record RelayId(int index) {
    public RelayId {
        if (index < 0) throw new IllegalArgumentException("index");
    }
}
