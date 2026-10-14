package dev.denfry.promptcritical.sim.neutronics;

/** Отдельный замедлитель или отражатель (01 §1.6.1). */
public enum ModeratorKind {
    GRAPHITE(Medium.GRAPHITE), D2O(Medium.D2O), H2O(Medium.H2O), BE(Medium.BE), NONE(null);

    final Medium medium;

    ModeratorKind(Medium medium) {
        this.medium = medium;
    }
}
