package dev.denfry.promptcritical.sim.combustion;

import dev.denfry.promptcritical.sim.thermo.WaterProperties;

/**
 * Расчётная точка парогазовой установки (ПГУ) с котлом-утилизатором одного давления без дожига: ГТУ
 * ({@link BraytonCycle}), {@link HeatRecoverySteamGenerator} и конденсационная паровая турбина. Работа пара —
 * изоэнтропийный перепад по IF97 до давления конденсатора, умноженный на внутренний КПД; минус работа питательного
 * насоса. КПД ПГУ η = (N_ГТУ + N_ПТУ)/Q_топлива; для одного давления без промперегрева Kehlhofer (2009, гл. 5–6) даёт
 * 45–50 % при T₃ ≈ 1100 °C, двух- и трёхконтурные котлы с промперегревом — до 55–58 % на машинах 1990-х.
 */
public final class CombinedCycle {
    /** Внутренний КПД паровой турбины, механический КПД, КПД насоса. */
    public static final double ST_EFFICIENCY = 0.85, MECHANICAL = 0.98, PUMP_EFFICIENCY = 0.75;

    private CombinedCycle() {
    }

    /**
     * Итог: мощности ГТУ и паровой турбины, Вт; тепло топлива, Вт; КПД ГТУ и ПГУ; котёл; расход выхлопа, кг/с.
     */
    public record DesignPoint(double gasTurbineW, double steamTurbineW, double fuelW, double gasTurbineEfficiency,
            double efficiency, HeatRecoverySteamGenerator.Result hrsg, double exhaustKgS) {
    }

    /** Мощность паровой турбины на паре {@code steamKgS} при {@code steamPa}, {@code steamK} с конденсатором {@code condenserPa}, Вт. */
    public static double steamTurbineW(WaterProperties w, double steamKgS, double steamPa, double steamK, double condenserPa) {
        if (steamKgS <= 0) return 0;
        double h1 = w.enthalpyPT(steamPa, steamK > w.saturationP(steamPa).tSat() + 0.5 ? steamK : steamK + 0.5);
        double s1 = w.entropyPH(steamPa, h1);
        double h2s = w.enthalpyPS(condenserPa, s1);
        double turbine = steamKgS * (h1 - h2s) * ST_EFFICIENCY * MECHANICAL;
        // насос: v·Δp от конденсатора до котла (вода ≈ 1,01 л/кг)
        double pump = steamKgS * 1.01e-3 * (steamPa - condenserPa) / PUMP_EFFICIENCY;
        return Math.max(0, turbine - pump);
    }

    /** ПГУ на ГТУ {@code gt} мощностью {@code gtPowerW} при воздухе {@code t1K}. */
    public static DesignPoint designPoint(WaterProperties w, BraytonCycle gt, double gtPowerW, double t1K, double steamPa,
            double feedK, double condenserPa) {
        double air = gt.airFlowKgS(gtPowerW, t1K);
        double exhaust = air * (1 + gt.fuelAirRatio(t1K));
        double fuel = gtPowerW / gt.efficiency(t1K);
        HeatRecoverySteamGenerator.Result h = HeatRecoverySteamGenerator.solve(w, exhaust, gt.exhaustK(), steamPa, feedK, true);
        double st = steamTurbineW(w, h.steamKgS(), steamPa, h.steamK(), condenserPa);
        return new DesignPoint(gtPowerW, st, fuel, gt.efficiency(t1K), (gtPowerW + st) / fuel, h, exhaust);
    }
}
