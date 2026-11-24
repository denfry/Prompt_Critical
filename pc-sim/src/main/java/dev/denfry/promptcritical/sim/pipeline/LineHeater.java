package dev.denfry.promptcritical.sim.pipeline;

/**
 * Путевой подогреватель с промежуточным теплоносителем (водяная баня; прототип ПП-0,63, 0,63 МВт): горелка греет
 * воду в корпусе, нефть идёт по змеевику в бане. Нефть нагревается до температуры бани минус недогрев змеевика, не
 * выше того, что позволяет тепловая мощность: ΔT = Q / (ṁ·c_p). Топливо — попутный газ с низшей теплотой сгорания
 * {@value #GAS_LHV_J_NM3} Дж/нм³ (09 §9.7, факельный газ; [сверить] по составу), КПД печи {@value #EFFICIENCY}
 * (типовой для печей с промежуточным теплоносителем 0,7–0,8).
 */
public final class LineHeater {
    /** Тепловая мощность ПП-0,63, Вт. */
    public static final double DUTY_W = 0.63e6;
    public static final double EFFICIENCY = 0.75;
    public static final double GAS_LHV_J_NM3 = 38e6;
    /** Теплоёмкость нефти, Дж/(кг·К) (типовая 1,9–2,1 кДж/(кг·К) при 20–80 °C). */
    public static final double OIL_CP = 2000;
    /** Температура водяной бани при атмосферном давлении и недогрев змеевика, °C [моё]. */
    public static final double BATH_C = 95, APPROACH_C = 10;

    private LineHeater() {
    }

    /** Температура нефти на выходе, °C, при подаче {@code massKgS} и доле мощности горелки {@code firing}. */
    public static double outletC(double inletC, double massKgS, double firing) {
        double f = Math.clamp(firing, 0, 1);
        if (f <= 0) return inletC;
        double cap = Math.max(inletC, BATH_C - APPROACH_C);
        if (massKgS <= 0) return cap;
        return Math.min(cap, inletC + f * DUTY_W / (massKgS * OIL_CP));
    }

    /** Тепло, переданное нефти, Вт. */
    public static double heatToOilW(double inletC, double outletC, double massKgS) {
        return Math.max(0, outletC - inletC) * Math.max(0, massKgS) * OIL_CP;
    }

    /**
     * Расход газа, нм³/с: на подогрев нефти и на поддержание бани (потери корпуса, доля {@value #STANDBY} мощности),
     * делённые на КПД.
     */
    public static double fuelNm3s(double heatToOilW, double firing) {
        if (firing <= 0) return 0;
        return (heatToOilW + STANDBY * DUTY_W) / EFFICIENCY / GAS_LHV_J_NM3;
    }

    /** Потери корпуса при горячей бане, доля мощности [моё]. */
    public static final double STANDBY = 0.05;
}
