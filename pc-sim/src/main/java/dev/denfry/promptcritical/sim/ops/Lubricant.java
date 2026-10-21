package dev.denfry.promptcritical.sim.ops;

/** Смазка подшипников скольжения и a_lub (13 §13.6.7, 04a §4a.7.2). */
public enum Lubricant {
    /** Сухой ход. */
    NONE(0.05),
    /** {@code tallow} (тег {@code c:lubricant}), I0. */
    TALLOW(0.6),
    /** {@code lube_oil}, I2. */
    LUBE_OIL(1.0);

    private final double factor;

    Lubricant(double factor) {
        this.factor = factor;
    }

    /** a_lub при свежей смазке. */
    public double factor() {
        return factor;
    }
}
