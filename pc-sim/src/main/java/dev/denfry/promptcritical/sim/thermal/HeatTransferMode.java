package dev.denfry.promptcritical.sim.thermal;

/** Режим теплоотдачи поверхности (01 §1.4.1). */
public enum HeatTransferMode {
    SINGLE_PHASE_LIQUID,
    NUCLEATE_BOILING,
    /** Кризис пройден (флаг DNB взведён), T_w между T_CHF и T_min. */
    TRANSITION_BOILING,
    FILM_BOILING,
    /** Осушенный узел, α ≥ 0,999. */
    SINGLE_PHASE_VAPOR,
    CONDENSATION,
    NATURAL_CONVECTION_GAS
}
