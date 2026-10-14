package dev.denfry.promptcritical.sim.consequence;

/** Вид разрушения (04d §4d.2.2). */
public enum DamageKind {
    BLAST,
    OVERPRESSURE_BREACH,
    MELT_THROUGH,
    PIPE_WHIP,
    MISSILE,
    FIRE_SPREAD,
    CORROSION,
    /** Кислотные дожди, увядание листвы, замазученный берег; блоки восстанавливаются при снижении нагрузки (14 §14.4). */
    ENVIRONMENTAL
}
