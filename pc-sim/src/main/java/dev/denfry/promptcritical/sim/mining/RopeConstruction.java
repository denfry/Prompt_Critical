package dev.denfry.promptcritical.sim.mining;

/**
 * Конструкция подъёмного каната (08 §8.17). Коэффициенты разрывного усилия K и массы W — ISO 2408:2004
 * (табл. C.6, C.7, C.9, C.12 и D.1; те же классы в EN 12385-4): F_мин = K·d²·R_r, q = W·d². Металлическое
 * сечение — порядок ГОСТ 3077, 7668 [сверить].
 */
public enum RopeConstruction {
    R6X19_FC(0.330, 0.00359, 0.385),
    R6X19_IWRC(0.356, 0.00400, 0.43),
    R6X36_IWRC(0.356, 0.00409, 0.44),
    /** 18×7 с органическим сердечником пряди-центра, K для марки 1770. */
    R18X7_NONROTATING(0.328, 0.00382, 0.41);

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
