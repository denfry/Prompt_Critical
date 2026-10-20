package dev.denfry.promptcritical.sim.metallurgy;

/** Период воздухонагревателя (08 §8.5.2): «на газе» ≈ 2 ч, «на дутье» ≈ 1 ч физ. */
public enum StoveMode {
    ON_GAS,
    ON_BLAST,
    /** Отсечён клапанами (перекидка, ремонт). */
    ISOLATED
}
