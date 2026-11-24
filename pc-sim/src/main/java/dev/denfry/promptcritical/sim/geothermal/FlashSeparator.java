package dev.denfry.promptcritical.sim.geothermal;

import dev.denfry.promptcritical.sim.thermo.if97.If97;

/**
 * Одноступенчатый флеш-сепаратор геотермальной ТЭС (15 §15.6.2). Рассол с устьевой энтальпией {@code h} дросселируется
 * до давления сепаратора; доля пара — из IF97:
 * <pre>x = (h − h'(p_сеп)) / (h''(p_сеп) − h'(p_сеп))</pre>
 * Неконденсирующиеся газы (CO₂, H₂S) растворены в рассоле и при вскипании почти целиком уходят в пар
 * (DiPippo, «Geothermal Power Plants», 4-е изд., 2016, гл. 5): их расход — доля {@code ncgMassFrac} от
 * общего расхода рассола.
 */
public final class FlashSeparator {
    /** Нижняя граница давления сепаратора, Па (ниже — уже конденсатор). */
    public static final double MIN_PRESSURE_PA = 0.2e6;

    private final double ncgMassFrac;

    /** @param ncgMassFrac доля НКГ в рассоле по массе, 0…0,05 */
    public FlashSeparator(double ncgMassFrac) {
        this.ncgMassFrac = Double.isFinite(ncgMassFrac) ? Math.max(0, Math.min(0.05, ncgMassFrac)) : 0;
    }

    public double ncgMassFrac() {
        return ncgMassFrac;
    }

    /** Доля пара при флеше энтальпии {@code hJkg} до {@code pSepPa}; 0, если рассол не вскипает, 1 — если уже пар. */
    public static double steamFraction(double hJkg, double pSepPa) {
        if (!(pSepPa >= If97.P_MIN && pSepPa < If97.P_C) || !Double.isFinite(hJkg)) return 0;
        double hl = If97.satLiquid(pSepPa).h(), hv = If97.satVapor(pSepPa).h();
        return Math.max(0, Math.min(1, (hJkg - hl) / (hv - hl)));
    }

    /**
     * Флеш потока {@code massKgS} с энтальпией {@code enthalpyJkg} при давлении {@code pSepPa}.
     * НКГ уходят с паром, если пар есть; без вскипания остаются в рассоле.
     */
    public FlashResult separate(double enthalpyJkg, double massKgS, double pSepPa) {
        if (!(massKgS > 0)) return FlashResult.NONE;
        double x = steamFraction(enthalpyJkg, pSepPa);
        double ncg = x > 0 ? massKgS * ncgMassFrac : 0;
        double water = massKgS - ncg;
        return new FlashResult(x, water * x, water * (1 - x), ncg);
    }

    /**
     * Температура сепаратора по правилу равного деления (DiPippo, 2016, §5.3): середина между пластовой
     * температурой и температурой конденсатора, К.
     */
    public static double optimalSeparatorTempK(double reservoirK, double condenserK) {
        return 0.5 * (reservoirK + condenserK);
    }
}
