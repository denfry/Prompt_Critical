package dev.denfry.promptcritical.sim.nonferrous;

import dev.denfry.promptcritical.sim.nonferrous.SulfideSmelting.Metal;

/**
 * Отражательная печь {@code matte_smelter} (08 §8.8.3: плавка на штейн, {@code Combustor} + O₂) и та же печь на
 * восстановительной плавке оловянного концентрата (§8.8.5). Термохимия — SLOW, топливо горит с тем же множителем
 * (08 §8.0.3).
 *
 * <p>Плавка на штейн: на тонну концентрата {@value #MATTE_HEAT_J_PER_KG} Дж тепла топлива [BALANCE: отражательные печи
 * 3–5 ГДж/т, с кислородом меньше], кислород — на окисление серы и железа ({@link SulfideSmelting}), извлечение в штейн
 * {@value #MATTE_RECOVERY} [BALANCE: потери со шлаком 0,5–1 % Cu].
 *
 * <p>Олово: SnO₂ + 2C → Sn + 2CO, ΔH = +357 кДж/моль (ΔH°f SnO₂ −577,6, CO −110,5) = 3,0 МДж/кг Sn; с нагревом
 * шихты до 1250 °C и КПД отражательной печи 0,3 [BALANCE] — {@value #TIN_HEAT_J_PER_KG} Дж/кг Sn. Углерод — 0,2 кг на кг
 * Sn по реакции с избытком {@value #TIN_CARBON_EXCESS}; кокс — {@value #COKE_CARBON} C. Извлечение
 * {@value #TIN_RECOVERY} [BALANCE: остальное — в шлаке на переплав].
 */
public final class MatteSmelter {
    public static final double MATTE_HEAT_J_PER_KG = 3.0e6, MATTE_RECOVERY = 0.97;
    /** Концентрат на шаг, кг/с физ. [BALANCE]. */
    public static final double CONCENTRATE_KG_S = 0.3;
    public static final double TIN_HEAT_J_PER_KG = 16e6, TIN_CARBON_EXCESS = 1.5, COKE_CARBON = 0.85, TIN_RECOVERY = 0.95;
    public static final double TIN_CARBON_PER_SN = 2 * SulfideSmelting.M_C / SulfideSmelting.M_SN;
    /** Олово на шаг, кг/с физ. [BALANCE]. */
    public static final double TIN_KG_S = 0.05;

    private MatteSmelter() {
    }

    /** Тепло на кг металла линии при плавке на штейн, Дж. */
    public static double matteHeatPerMetal(Metal m) {
        return MATTE_HEAT_J_PER_KG / m.concentrateGrade;
    }

    /** Кокс на кг олова, кг. */
    public static double cokePerTin() {
        return TIN_CARBON_PER_SN * TIN_CARBON_EXCESS / COKE_CARBON;
    }

    /**
     * Сколько металла линии (кг) печь переплавит на штейн за шаг, ограничивая паспортной производительностью и
     * наличными металлом, теплом, кислородом и флюсом.
     */
    public static double matteKg(Metal m, double dtS, double metalKg, double heatJ, double o2Kg, double fluxKg) {
        double kg = Math.min(CONCENTRATE_KG_S * m.concentrateGrade * dtS, Math.max(0, metalKg));
        kg = Math.min(kg, Math.max(0, heatJ) / matteHeatPerMetal(m));
        double o2 = SulfideSmelting.smeltingO2(m);
        if (o2 > 0) kg = Math.min(kg, Math.max(0, o2Kg) / o2);
        double flux = SulfideSmelting.smeltingFlux(m);
        if (flux > 0) kg = Math.min(kg, Math.max(0, fluxKg) / flux);
        return Math.max(0, kg);
    }

    /** Сколько олова (кг в концентрате) переплавит печь за шаг при наличных олове, тепле и коксе. */
    public static double tinKg(double dtS, double tinKg, double heatJ, double cokeKg) {
        double kg = Math.min(TIN_KG_S * dtS, Math.max(0, tinKg));
        kg = Math.min(kg, Math.max(0, heatJ) / TIN_HEAT_J_PER_KG);
        kg = Math.min(kg, Math.max(0, cokeKg) / cokePerTin());
        return Math.max(0, kg);
    }
}
