package dev.denfry.promptcritical.sim.mining;

/** Показание бензиновой лампы (08 §8.2.5). */
public enum LampState {
    NORMAL,
    /** голубой ореол над приспущенным пламенем: 1–5 % CH₄ */
    HALO,
    /** смесь горит внутри сетки: ≥ 5 % CH₄ — немедленно уходить */
    FLAME_IN_GAUZE,
    /** пламя вышло наружу — источник воспламенения {@link IgnitionSource#LAMP} */
    IGNITES,
    /** погасла: O₂ &lt; 17 % */
    EXTINGUISHED
}
