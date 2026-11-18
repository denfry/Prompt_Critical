package dev.denfry.promptcritical.sim.economy;

/**
 * Эластичный спрос NPC-рынка против «печатного станка» (14 §14.6.3). Рынок помнит накопленный объём продаж V,
 * который рассасывается: dV/dt = s(t) − V/τ_rec. Предельная цена p(V) = p_t·(1 + V/D)^{−1/ε}, выручка партии —
 * интеграл R(ΔV) = p_t·D·ε/(ε−1)·[(1 + (V+ΔV)/D)^{(ε−1)/ε} − (1 + V/D)^{(ε−1)/ε}] (при ε = 1 — p_t·D·ln). При ε < 1
 * выручка от сколь угодно большой партии ограничена p_t·D·ε/(1−ε). Покупка у NPC зеркальна:
 * p(V_b) = p_t·(1 + V_b/D)^{1/ε}.
 */
public final class ElasticDemand {
    /** τ_rec = 2 сут физического времени (market.recovery_days, §14.6.3 [BALANCE]). */
    public static final double DEFAULT_RECOVERY_S = 2 * Commodity.DAY_S;

    private ElasticDemand() {}

    /** Предельная цена продажи при накопленном объёме V. */
    public static double marginalSellPrice(double pt, double depth, double eps, double v) {
        return pt * Math.pow(1 + v / depth, -1 / eps);
    }

    /** Предельная цена покупки у NPC при накопленном объёме покупок V_b (без спреда). */
    public static double marginalBuyPrice(double pt, double depth, double eps, double vb) {
        return pt * Math.pow(1 + vb / depth, 1 / eps);
    }

    /** Выручка R(ΔV) от продажи партии ΔV ≥ 0 начиная с объёма V. */
    public static double saleRevenue(double pt, double depth, double eps, double v, double dv) {
        if (dv <= 0) return 0;
        double a = 1 + v / depth, b = 1 + (v + dv) / depth;
        if (Math.abs(eps - 1) < 1e-12) return pt * depth * Math.log(b / a);
        double k = (eps - 1) / eps;
        return pt * depth * eps / (eps - 1) * (Math.pow(b, k) - Math.pow(a, k));
    }

    /** Верхняя граница выручки при ΔV → ∞; бесконечность при ε ≥ 1. */
    public static double saleRevenueLimit(double pt, double depth, double eps, double v) {
        if (eps >= 1) return Double.POSITIVE_INFINITY;
        return pt * depth * eps / (1 - eps) * Math.pow(1 + v / depth, (eps - 1) / eps);
    }

    /**
     * Обратная к {@link #saleRevenue}: объём ΔV, выручка за который равна {@code revenue}; бесконечность, если такая
     * выручка недостижима (ε < 1 и revenue ≥ предела).
     */
    public static double volumeForRevenue(double pt, double depth, double eps, double v, double revenue) {
        if (revenue <= 0) return 0;
        double a = 1 + v / depth;
        if (Math.abs(eps - 1) < 1e-12) return depth * a * Math.expm1(revenue / (pt * depth));
        double k = (eps - 1) / eps;
        double base = Math.pow(a, k) + revenue * k / (pt * depth);
        if (base <= 0) return Double.POSITIVE_INFINITY;
        return depth * (Math.pow(base, 1 / k) - a);
    }

    /** Стоимость покупки ΔV ≥ 0 у NPC начиная с объёма покупок V_b (без спреда). */
    public static double purchaseCost(double pt, double depth, double eps, double vb, double dv) {
        if (dv <= 0) return 0;
        double k = (eps + 1) / eps;
        return pt * depth / k * (Math.pow(1 + (vb + dv) / depth, k) - Math.pow(1 + vb / depth, k));
    }

    /** Точное рассасывание накопленного объёма за dt без новых сделок: V·e^{−dt/τ}. */
    public static double recover(double v, double dtS, double tauS) {
        return v * Math.exp(-dtS / tauS);
    }
}
