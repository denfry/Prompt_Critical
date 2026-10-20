package dev.denfry.promptcritical.sim.metallurgy;

/**
 * Тушение кокса (08 §8.4.3): мокрое в {@code quench_tower} и сухое в {@code cdq_chamber} + {@code hrsg} (07).
 */
public final class CokeQuenching {
    /** Испаряется воды при мокром тушении, кг/кг кокса (0,4–0,5 м³/т). */
    public static final double WET_WATER_KG_PER_KG = 0.45;
    /** Влага кокса после мокрого тушения [сверить; практика 2–5 %]. */
    public static final double WET_COKE_MOISTURE = 0.04;
    /** Средняя теплоёмкость кокса, Дж/(кг·К), и температура на выходе СТК, К. */
    public static final double COKE_CP = 1_500, CDQ_OUTLET_K = 473.15;
    /** Пределы газа СТК: O₂ и CO, мольные доли ({@code CDQ_GAS_COMPOSITION}). */
    public static final double CDQ_O2_MAX = 0.01, CDQ_CO_MAX = 0.12;

    private CokeQuenching() {}

    /** Вода, испарённая мокрым тушением, кг (уходит паром с фенолом — 14). */
    public static double wetQuenchSteamKg(double cokeKg) {
        return WET_WATER_KG_PER_KG * cokeKg;
    }

    /** Тепло, отданное коксом в СТК: Q = m·c̄_p·(T_вх − 200 °C), Дж (1,28 ГДж/т при 1050 °C). */
    public static double cdqHeatJ(double cokeKg, double inletK) {
        return cokeKg * COKE_CP * Math.max(0, inletK - CDQ_OUTLET_K);
    }

    /**
     * Пар котла-утилизатора СТК, кг.
     *
     * @param boilerEfficiency  КПД котла (0,85)
     * @param enthalpyRiseJKg   h_пара − h_пит.воды, Дж/кг
     */
    public static double cdqSteamKg(double cokeKg, double inletK, double boilerEfficiency, double enthalpyRiseJKg) {
        return cdqHeatJ(cokeKg, inletK) * boilerEfficiency / enthalpyRiseJKg;
    }

    /** Газ СТК опасен — нужна продувка N₂ ({@code CDQ_GAS_COMPOSITION}). */
    public static boolean cdqGasTrip(double xO2, double xCO) {
        return xO2 > CDQ_O2_MAX || xCO > CDQ_CO_MAX;
    }
}
