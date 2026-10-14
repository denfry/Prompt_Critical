package dev.denfry.promptcritical.sim.metallurgy;

/** Модель отливки {@code casting_pattern} (08 §8.9). {@code TURBINE_BLADE} — (B). */
public enum CastingKind {
    VALVE_BODY, PUMP_CASING, IMPELLER, GEAR_BLANK, WHEEL, FLYWHEEL, INGOT_MOLD, BEARING_HOUSING;

    /** Корпус под давлением — испытывается на {@code hydrotest_bench}. */
    public boolean pressureRetaining() {
        return this == VALVE_BODY || this == PUMP_CASING;
    }
}
