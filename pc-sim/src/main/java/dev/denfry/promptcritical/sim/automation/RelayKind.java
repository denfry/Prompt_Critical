package dev.denfry.promptcritical.sim.automation;

/** Исполнение реле A1 (03d §3d.3.1). */
public enum RelayKind {
    GENERAL,
    /** С принудительным ведением контактов (EN 50205): сварка НО держит все НЗ разомкнутыми. */
    FORCE_GUIDED,
    /** Бистабильное: катушка SET (A1) и RESET (B1), положение держится без питания. */
    LATCHING
}
