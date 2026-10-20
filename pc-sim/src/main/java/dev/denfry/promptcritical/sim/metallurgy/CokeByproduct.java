package dev.denfry.promptcritical.sim.metallurgy;

/** Продукты улавливания коксового газа (08 §8.4.2, §8.4.4), упрощённо — сразу в счётчики батареи. */
public enum CokeByproduct {
    /** {@code coal_tar} */
    TAR,
    /** {@code crude_benzol} */
    CRUDE_BENZOL,
    /** NH₃ — в сатуратор (08 §8.4.4) */
    AMMONIA,
    /** H₂S */
    HYDROGEN_SULFIDE,
    /** {@code ammonia_liquor} — надсмольная вода */
    AMMONIA_LIQUOR
}
