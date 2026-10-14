package dev.denfry.promptcritical.sim.mining;

/**
 * Конструкция подъёмного каната (08 §8.17). Для 6×19 с органическим сердечником 08 §8.2.8 даёт
 * F_разр ≈ 0,33·d²·R_r и q ≈ 0,0036·d² [порядок ГОСТ 3077]; остальные — поправки того же порядка по ГОСТ 7668,
 * 7669, 3088 [сверить].
 */
public enum RopeConstruction {
    R6X19_FC(0.33, 0.0036, 0.385),
    R6X19_IWRC(0.356, 0.0040, 0.43),
    R6X36_IWRC(0.367, 0.0041, 0.44),
    R18X7_NONROTATING(0.31, 0.0039, 0.41);

    /** F_разр = k_F·d²·R_r, Н при d в мм и R_r в МПа. */
    public final double breakingCoeff;
    /** q = k_q·d², кг/м. */
    public final double massCoeff;
    /** Металлическое сечение A = k_A·d², мм². */
    public final double areaCoeff;

    RopeConstruction(double breakingCoeff, double massCoeff, double areaCoeff) {
        this.breakingCoeff = breakingCoeff;
        this.massCoeff = massCoeff;
        this.areaCoeff = areaCoeff;
    }
}
