package dev.denfry.promptcritical.sim.protection;

/** Физическая причина АЗ (01 §1.11.3, 00 §0.7.4). Порядок констант — приоритет first-out при равенстве. */
public enum TripReason {
    MANUAL, HIGH_FLUX, SHORT_PERIOD, HIGH_PRIMARY_PRESSURE, LOW_PRIMARY_PRESSURE, LOW_COOLANT_FLOW,
    HIGH_COOLANT_TEMP, LOW_DNBR, LOW_SG_LEVEL, HIGH_CONTAINMENT_PRESSURE, LOSS_OF_DRIVE_POWER, TURBINE_TRIP,
    SEISMIC, LUA_REQUEST;

    /** Причина задаётся дискретным сигналом (кнопка, запрос), а не каналами измерения. */
    public boolean isDiscrete() {
        return this == MANUAL || this == LUA_REQUEST;
    }
}
