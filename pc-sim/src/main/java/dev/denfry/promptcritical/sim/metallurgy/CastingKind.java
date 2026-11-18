package dev.denfry.promptcritical.sim.metallurgy;

/**
 * Модель отливки {@code casting_pattern} (08 §8.9). {@code TURBINE_BLADE} — (B). {@code MACHINE_BED} и
 * {@code BASE_PLATE} — чугунные станина с направляющими и плита для строгального станка; новые модели — только в конец
 * (порядковый номер виден на щитах).
 */
public enum CastingKind {
    VALVE_BODY, PUMP_CASING, IMPELLER, GEAR_BLANK, WHEEL, FLYWHEEL, INGOT_MOLD, BEARING_HOUSING, MACHINE_BED, BASE_PLATE;

    /** Корпус под давлением — испытывается на {@code hydrotest_bench}. */
    public boolean pressureRetaining() {
        return this == VALVE_BODY || this == PUMP_CASING;
    }
}
