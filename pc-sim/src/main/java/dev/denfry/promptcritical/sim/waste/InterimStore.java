package dev.denfry.promptcritical.sim.waste;

/**
 * Промежуточное хранилище тепловыделяющих отходов с принудительной вентиляцией (как хранилища стекла в Ла-Аг и на
 * «Маяке»): расход воздуха рассчитан на проектную мощность при подогреве {@value #DESIGN_RISE_K} К. Если тепла больше
 * проектного — воздух на выходе горячее, а стеклоблоки — тем более. Без вентиляции (вентиляторы стоят) воздух
 * обновляется только естественной тягой — {@value #NATURAL_DRAFT} от проектного расхода.
 *
 * <p>Воздух: c_p = 1006 Дж/(кг·К); ṁ = Q_проект / (c_p·ΔT_проект); T_вых = T_вх + Q / (ṁ·c_p).
 */
public final class InterimStore {
    public static final double DESIGN_RISE_K = 30, CP_AIR = 1006, NATURAL_DRAFT = 0.15;

    private InterimStore() {
    }

    /** Расход воздуха вентиляции, рассчитанной на {@code designW}, кг/с. */
    public static double airflowKgS(double designW, boolean fans) {
        return designW / (CP_AIR * DESIGN_RISE_K) * (fans ? 1 : NATURAL_DRAFT);
    }

    /** Температура воздуха на выходе из хранилища, К. */
    public static double outletK(double inletK, double heatW, double designW, boolean fans) {
        double m = airflowKgS(designW, fans);
        return m > 0 ? inletK + heatW / (m * CP_AIR) : Double.POSITIVE_INFINITY;
    }
}
