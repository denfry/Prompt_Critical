package dev.denfry.promptcritical.sim.combustion;

import dev.denfry.promptcritical.sim.thermo.SaturationState;
import dev.denfry.promptcritical.sim.thermo.WaterProperties;

/**
 * Котёл-утилизатор одного давления без дожига (GDD 07 §7.10.4): газ выхлопа ГТУ проходит пароперегреватель,
 * испаритель и экономайзер. Расчёт — по пинч-точке (Kehlhofer et al., «Combined-Cycle Gas & Steam Turbine Power
 * Plants», 3-е изд., 2009, гл. 5; Ganapathy, «Heat-Recovery Steam Generators», 1990): газ на выходе испарителя на
 * ΔT_pp выше насыщения, вода на выходе экономайзера на ΔT_ap ниже насыщения; пар перегревается до температуры газа на
 * входе минус ΔT_sh. Расход пара — из баланса пароперегревателя с испарителем:
 * ṁ_s(h_пп − h_эк) = ṁ_g c_p,g (T_g,вх − T_s − ΔT_pp); экономайзер догревает питательную воду, газ уходит в трубу при
 * T_ух = T_s + ΔT_pp − ṁ_s(h_эк − h_пв)/(ṁ_g c_p,g). Если газ на холодном конце подошёл бы к питательной воде ближе
 * ΔT_pp, пинч переходит на холодный конец: ṁ_s(h_пп − h_пв) = ṁ_g c_p,g (T_g,вх − T_пв − ΔT_pp).
 */
public final class HeatRecoverySteamGenerator {
    /** Пинч, недогрев экономайзера, недогрев перегрева до газа, К (Kehlhofer: 8–15, 5–10, 20–30). */
    public static final double PINCH_K = 10, APPROACH_K = 5, SUPERHEAT_APPROACH_K = 25;
    /** c_p выхлопных газов ГТУ, Дж/(кг·К) (GDD 07 §7.10.4). */
    public static final double CP_GAS = 1100;
    /** Предел перегрева по металлу пароперегревателя, К. */
    public static final double MAX_STEAM_K = 813.15;

    private HeatRecoverySteamGenerator() {
    }

    /**
     * Итог: пар, кг/с; тепло, Вт (всего и по секциям); температуры газа на выходе испарителя и в трубу, пара, воды
     * после экономайзера, насыщения, К; пинч на холодном конце.
     */
    public record Result(double steamKgS, double dutyW, double superheaterW, double evaporatorW, double economizerW,
            double pinchGasK, double stackK, double steamK, double economizerOutK, double saturationK, boolean coldEndPinch) {
        public static Result none(double gasInK) {
            return new Result(0, 0, 0, 0, 0, gasInK, gasInK, 0, 0, 0, false);
        }
    }

    /**
     * Котёл на газе {@code gasKgS} при {@code gasInK}, пар {@code steamPa} из питательной воды {@code feedK};
     * {@code superheat} — с пароперегревателем (иначе — насыщенный пар в барабан).
     */
    public static Result solve(WaterProperties w, double gasKgS, double gasInK, double steamPa, double feedK, boolean superheat) {
        SaturationState sat = w.saturationP(steamPa);
        double ts = sat.tSat();
        double pinchGas = ts + PINCH_K;
        if (!(gasKgS > 0) || gasInK <= pinchGas) return Result.none(gasInK);
        double steamK = superheat ? Math.min(MAX_STEAM_K, gasInK - SUPERHEAT_APPROACH_K) : ts;
        double hSteam = steamK > ts + 0.5 ? w.enthalpyPT(steamPa, steamK) : sat.hG();
        if (steamK <= ts + 0.5) steamK = ts;
        double hEco = w.enthalpyPT(steamPa, ts - APPROACH_K), hFeed = w.enthalpyPT(steamPa, feedK);
        double c = gasKgS * CP_GAS;
        double mHot = c * (gasInK - pinchGas) / (hSteam - hEco);
        double mCold = c * Math.max(0, gasInK - feedK - PINCH_K) / (hSteam - hFeed);
        boolean coldEnd = mCold < mHot;
        double m = Math.min(mHot, mCold);
        double duty = m * (hSteam - hFeed);
        double stack = gasInK - duty / c;
        double sh = m * (hSteam - sat.hG());
        double eco = coldEnd ? c * (pinchGas - stack) : m * (hEco - hFeed);
        double hEcoOut = hFeed + eco / Math.max(m, 1e-12);
        double ecoOutK = w.temperaturePH(steamPa, Math.min(hEcoOut, sat.hF()));
        return new Result(m, duty, sh, duty - sh - eco, eco, coldEnd ? gasInK - (m * (hSteam - hEcoOut)) / c : pinchGas, stack,
                steamK, ecoOutK, ts, coldEnd);
    }
}
