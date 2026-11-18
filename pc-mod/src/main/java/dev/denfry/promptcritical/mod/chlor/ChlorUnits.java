package dev.denfry.promptcritical.mod.chlor;

/**
 * Единицы жидкостей хлорного производства: газы — 1 мБ = 1 нм³ (22,414 л/моль), жидкости — 1 мБ = 1 л.
 * Плотности жидкостей — при хранении: хлор под давлением при 20 °C, ВХ под давлением, электролитическая щёлочь —
 * 11 % NaOH и 15 % NaCl.
 */
public final class ChlorUnits {
    public static final double NM3_PER_MOL = 0.022414;
    public static final double CL2_KG_NM3 = 0.070906 / NM3_PER_MOL, H2_KG_NM3 = 0.002016 / NM3_PER_MOL, HCL_KG_NM3 = 0.036461 / NM3_PER_MOL;
    public static final double LIQUID_CL2_KG_L = 1.47, BRINE_KG_L = 1.20, LIQUOR_KG_L = 1.24, LIQUOR_NAOH = 0.11;
    public static final double EDC_KG_L = 1.253, EDC_KG_MOL = 0.098959, VCM_KG_L = 0.911, VCM_KG_MOL = 0.062498;
    /** Мешок соли и мешок ПВХ, кг. */
    public static final double SALT_BAG_KG = 25, PVC_BAG_KG = 25;

    private ChlorUnits() {
    }

    public static double nm3ToMol(double nm3) {
        return nm3 / NM3_PER_MOL;
    }

    public static double molToNm3(double mol) {
        return mol * NM3_PER_MOL;
    }

    /** Литры электролитической щёлочи на кг NaOH. */
    public static double liquorLPerNaohKg(double kg) {
        return kg / (LIQUOR_NAOH * LIQUOR_KG_L);
    }
}
