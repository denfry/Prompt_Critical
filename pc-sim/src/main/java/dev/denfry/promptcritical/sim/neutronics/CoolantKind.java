package dev.denfry.promptcritical.sim.neutronics;

/** Теплоноситель ячейки (01 §1.6.1). */
public enum CoolantKind {
    H2O(Medium.H2O), D2O(Medium.D2O), AIR(Medium.AIR), NA(Medium.NA), NONE(null);

    final Medium medium;

    CoolantKind(Medium medium) {
        this.medium = medium;
    }
}
