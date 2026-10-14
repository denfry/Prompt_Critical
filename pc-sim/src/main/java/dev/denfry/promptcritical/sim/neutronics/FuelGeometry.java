package dev.denfry.promptcritical.sim.neutronics;

/** Геометрия топлива в ячейке (01 §1.6.1). */
public enum FuelGeometry {
    /** Открытая решётка стержней в теплоносителе-замедлителе (LWR). */
    ROD_LATTICE,
    /** Кластер стержней в трубе канала внутри отдельного замедлителя (T1, T3). */
    CLUSTER_IN_TUBE,
    /** Блочки в каналах графитовой кладки (T0). */
    SLUG_IN_CHANNEL,
    NONE
}
