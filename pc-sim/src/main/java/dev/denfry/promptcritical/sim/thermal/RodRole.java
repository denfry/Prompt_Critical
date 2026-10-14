package dev.denfry.promptcritical.sim.thermal;

/**
 * Представительный ТВЭЛ ячейки (01 §1.2.3): средний отдаёт тепло в теплоноситель,
 * горячий — только для пределов (DNBR, T_center) и в баланс узла не входит.
 */
public enum RodRole { AVERAGE, HOT }
