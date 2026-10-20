package dev.denfry.promptcritical.sim.grid;

/**
 * Материал провода или кабеля (07 §7.4.1): коэффициент линейного расширения α (§7.4.2) и удельная
 * теплоёмкость (АС — по массе алюминия и стали).
 */
public enum Conductor {
    COPPER(17e-6, 385), ACSR(19e-6, 790), ALUMINIUM(23e-6, 897), CABLE_PAPER(17e-6, 385), CABLE_XLPE(17e-6, 385);

    /** α, 1/К. */
    public final double expansionPerK;
    /** c_p, Дж/(кг·К). */
    public final double specificHeat;

    Conductor(double alpha, double cp) {
        expansionPerK = alpha;
        specificHeat = cp;
    }
}
