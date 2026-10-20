package dev.denfry.promptcritical.sim.combustion;

/** Причина главного топливного отсечения (MFT, 07 §7.9.6). */
public enum MftCause {
    /** Факел запальника не появился за {@link BurnerManagementSystem#IGNITER_TRIAL_S}. */
    IGNITER_TRIAL_FAILED,
    /** Основной факел не появился за время пробы. */
    MAIN_TRIAL_FAILED,
    /** Погасли все основные факелы. */
    FLAME_LOSS,
    /** Остановился дымосос или дутьевой вентилятор. */
    FANS_LOST,
    /** ПАЗ котла (07 §7.18): уровень в барабане, давление, разрыв трубы. */
    PROTECTION,
    /** Останов оператором. */
    OPERATOR
}
