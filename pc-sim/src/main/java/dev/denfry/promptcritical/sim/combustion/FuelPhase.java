package dev.denfry.promptcritical.sim.combustion;

/** Агрегатное состояние топлива (07 §7.8.1). */
public enum FuelPhase {
    /** уголь, кокс, дрова; теплоёмкость при подогреве ≈ 1,1 кДж/(кг·К) */
    SOLID(1100),
    /** мазут, дизельное топливо */
    LIQUID(2000),
    /** природный, шахтный, коксовый, доменный газ */
    GAS(2200);

    /** Удельная теплоёмкость топлива для подогрева до горелки, Дж/(кг·К) (порядок величин). */
    public final double heatCapacity;

    FuelPhase(double heatCapacity) {
        this.heatCapacity = heatCapacity;
    }
}
