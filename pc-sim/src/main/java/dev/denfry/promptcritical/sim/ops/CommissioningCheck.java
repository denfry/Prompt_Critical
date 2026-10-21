package dev.denfry.promptcritical.sim.ops;

/**
 * Проверки пусконаладки (13 §13.6.6). Проверки после {@code TRIAL_RUN} берутся у владельцев следующих эпох
 * (03d, 01, 09, 13 §13.4.4, 07, 04a) и в чек-листах I0 не встречаются.
 */
public enum CommissioningCheck {
    PRESSURE_TEST, RELIEF_VALVE_SET, INSTRUMENT_CHECK, ROTATION_CHECK, PRIMING, BRAKE_TEST, TRIAL_RUN,
    LOOP_CHECK, SIS_PROOF_TEST, FLUSHING, INERT_LEAK_TEST, PURGE, INSULATION_TEST, ALIGNMENT
}
