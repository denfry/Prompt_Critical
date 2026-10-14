package dev.denfry.promptcritical.sim.mining;

/**
 * Газы узлового баланса шахтной атмосферы (08 §8.2.4). Азот — остаток до единицы, водяной пар не ведётся.
 */
public enum MineGas {
    CH4(0),
    CO(0),
    CO2(0.0004),
    O2(0.2095);

    private static final MineGas[] VALUES = values();

    /** Мольная доля в наружном (свежем) воздухе. */
    public final double freshAir;

    MineGas(double freshAir) {
        this.freshAir = freshAir;
    }

    static MineGas of(int ordinal) {
        return VALUES[ordinal];
    }

    static final int COUNT = VALUES.length;
}
