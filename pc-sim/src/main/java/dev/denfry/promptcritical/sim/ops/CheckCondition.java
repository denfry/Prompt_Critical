package dev.denfry.promptcritical.sim.ops;

/** Невыполненное условие проверки (13 §13.6.6): проверка отклонена ({@link CheckResult.Outcome#REFUSED}). */
public enum CheckCondition {
    NOT_IN_CHECKLIST,
    /** Топка не погашена. */
    FIRE_LIT,
    /** Металл ≥ 333 K. */
    METAL_HOT,
    /** Давление ниже p_исп. */
    PRESSURE_LOW,
    /** Выдержка меньше {@code ops.hydrotest_hold_phys_s}. */
    HOLD_SHORT,
    /** Нет контрольного манометра и не пройдена {@code INSTRUMENT_CHECK}. */
    NO_CONTROL_GAUGE,
    /** Насос вращается. */
    UNIT_RUNNING,
    LOAD_LOW,
    /** Пройдены не все прочие проверки. */
    PREREQUISITES,
    RUN_SHORT,
    NO_LOAD_REJECTION
}
