package dev.denfry.promptcritical.sim.gasprocessing;

import dev.denfry.promptcritical.sim.thermo.if97.If97;

/**
 * Равновесное влагосодержание природного газа и точка росы по воде — корреляция Букачека (Bukacek, IGT Research
 * Bulletin 8, 1955; GPSA Engineering Data Book, разд. 20; Бекиров, гл. 3), воспроизводящая номограмму Маккетты–Уэйва
 * в пределах нескольких процентов:
 * W = 47 484·p_s/p + B, lg B = −3083,87/T + 6,69449 (W — фунт/млн фут³, p — psia, T — °R).
 * Давление насыщенного пара воды — IAPWS-IF97 выше 0 °C, ниже — над переохлаждённой водой по Баку (Buck, J. Appl.
 * Meteor. 20, 1981): точка росы ниже нуля — по жидкой воде, как и номограмма.
 */
public final class WaterContent {
    /** 1 фунт/млн фут³ (н. у. 60 °F, 14,696 psia) = 16,018 мг/нм³. */
    public static final double MG_PER_SM3_PER_LB_MMSCF = 16.018;
    private static final double PSI = 6894.757;
    /** Моль газа в нормальном кубометре (15 °C, 101,325 кПа). */
    public static final double MOL_PER_SM3 = 101_325 / (8.314_462 * 288.15);
    public static final double WATER_KG_MOL = 0.018_015;

    private WaterContent() {
    }

    /** Давление насыщенного пара воды, Па. */
    public static double vaporPressurePa(double tK) {
        if (tK >= If97.T_MIN) return If97.psat(tK);
        double c = tK - 273.15;
        return 611.21 * Math.exp((18.678 - c / 234.5) * (c / (257.14 + c)));
    }

    /** Насыщенное влагосодержание газа, мг воды на нм³, при давлении (абсолютном) и температуре. */
    public static double saturatedMgPerSm3(double tK, double pAbsPa) {
        double tr = tK * 1.8;
        double b = Math.pow(10, -3083.87 / tr + 6.69449);
        double lb = 47_484 * vaporPressurePa(tK) / PSI / (pAbsPa / PSI) + b;
        return lb * MG_PER_SM3_PER_LB_MMSCF;
    }

    /** Точка росы по воде газа с влагосодержанием {@code mgPerSm3} при давлении, К. */
    public static double dewPointK(double mgPerSm3, double pAbsPa) {
        if (mgPerSm3 <= 0) return 0;
        double lo = 180, hi = 420;
        for (int i = 0; i < 80; i++) {
            double mid = 0.5 * (lo + hi);
            if (saturatedMgPerSm3(mid, pAbsPa) > mgPerSm3) hi = mid;
            else lo = mid;
        }
        return 0.5 * (lo + hi);
    }

    /** Мольная доля воды в газе по влагосодержанию. */
    public static double moleFraction(double mgPerSm3) {
        return mgPerSm3 * 1e-6 / WATER_KG_MOL / MOL_PER_SM3;
    }

    public static double mgPerSm3(double moleFraction) {
        return moleFraction * MOL_PER_SM3 * WATER_KG_MOL * 1e6;
    }
}
