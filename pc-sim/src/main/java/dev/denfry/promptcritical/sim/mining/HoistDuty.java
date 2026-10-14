package dev.denfry.promptcritical.sim.mining;

/** Назначение подъёма — по нему требуемый запас прочности каната (08 §8.2.8, ПБ [сверить редакцию]). */
public enum HoistDuty {
    /** Людской: SF ≥ 9. */
    PEOPLE,
    /** Грузо-людской: 7,5. */
    MIXED,
    /** Грузовой: 6,5. */
    CARGO
}
