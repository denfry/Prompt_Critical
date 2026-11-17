package dev.denfry.promptcritical.sim.reactor;

/** Причина аварийной защиты графитового котла. */
public enum PileTrip {
    NONE,
    /** Кнопка АЗ на пульте или отдельная кнопка в зале. */
    MANUAL,
    /** Период разгона короче уставки. */
    SHORT_PERIOD,
    /** Мощность по ионизационной камере выше уставки. */
    HIGH_POWER
}
