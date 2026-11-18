package dev.denfry.promptcritical.sim.nitrogen;

import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;

/**
 * Тонкая очистка азотоводородной смеси: абсорбция CO₂ 20 %-ным раствором моноэтаноламина (МЭА) с регенерацией в
 * отпарной колонне и метанирование остатков CO и CO₂ на никеле (CO + 3H₂ → CH₄ + H₂O, CO₂ + 4H₂ → CH₄ + 2H₂O).
 * Кислородсодержащие соединения — яд для железного катализатора синтеза аммиака, поэтому их держат ниже 10 ppm.
 *
 * <p>Абсорбер — по Кремсеру с 12 теоретическими тарелками: фактор абсорбции A = L·Δα·C_МЭА / n_CO₂, рабочая ёмкость
 * раствора Δα = 0,30 моль CO₂ на моль МЭА при 3,3 кмоль МЭА/м³; остаток CO₂ не ниже 200 ppm — равновесие над тощим
 * раствором (Kohl, Nielsen, «Gas Purification», 5th ed., 1997, гл. 2; «Справочник азотчика», т. 1, гл. 5). Тепло
 * регенерации — теплота десорбции 1,9 МДж/кг CO₂, нагрев раствора на 12 К после рекуперации и отпарной пар 0,02 кг/кг
 * раствора: при проектной циркуляции 4–5 МДж/кг CO₂. Если технологического тепла не хватает, раствор недорегенерирован
 * и ёмкость падает.
 *
 * <p>Метанатор — адиабатический, вход 300 °C, превращение до 2 ppm CO и CO₂ (равновесие при 300–400 °C — доли ppm);
 * ниже 250 °C реакция не зажигается. Подъём температуры ≈ 74 К на 1 % CO и 60 К на 1 % CO₂ — расчёт по энтальпиям.
 * Выше 450 °C — блокировка (спекание никеля): газ сбрасывается, нужен сброс блокировки.
 */
public final class Purification {
    public static final int STAGES = 12;
    public static final double CAPACITY_MOL_M3 = 0.30 * 3300, MIN_CO2 = 200e-6, DESIGN_MARGIN = 1.8;
    public static final double DESORPTION_J_KG = 1.9e6, SOLVENT_KG_M3 = 1010, SOLVENT_CP = 3800, SOLVENT_DT = 12, STRIP_STEAM = 0.02,
            STEAM_LATENT_J_KG = 2.2e6;
    public static final double METHANATOR_IN_K = 573.15, LIGHT_OFF_K = 523.15, TRIP_K = 723.15, RESIDUAL = 2e-6;
    /** Синтез-газ в норме: H₂:N₂ и CO + CO₂. */
    public static final double MIN_RATIO = 2.7, MAX_RATIO = 3.3, MAX_OXYGENATES = 10e-6;
    private static final int CO = Species.CO.ordinal(), CO2 = Species.CO2.ordinal(), H2 = Species.H2.ordinal(),
            CH4 = Species.CH4.ordinal(), H2O = Species.H2O.ordinal(), N2 = Species.N2.ordinal();

    private double circulation = 1;
    private boolean trip;
    private Result last;

    /**
     * Итог шага: синтез-газ после метанатора (моль/с, сухой), CO₂ на сброс, температура метанатора, тепло
     * регенерации (нужное), остаток CO₂ после абсорбера и годность газа.
     */
    public record Result(double[] syngas, double co2VentMolS, double methanatorOutK, double regenW, double co2SlipDry,
            double oxygenates, double ratio, boolean onSpec) {
    }

    public double circulation() {
        return circulation;
    }

    /** Циркуляция раствора в долях проектной (проектная — 1,8 от минимальной при номинальной нагрузке). */
    public void setCirculation(double c) {
        circulation = Math.max(0, Math.min(2, c));
    }

    public boolean tripped() {
        return trip;
    }

    public void resetTrip() {
        trip = false;
    }

    public Result last() {
        return last;
    }

    /** Проектная циркуляция раствора при номинальной нагрузке агрегата, м³/с. */
    public static double designCirculationM3S() {
        return ReformerTrain.RATED_CH4_MOL_S * 1.0 / CAPACITY_MOL_M3 * DESIGN_MARGIN;
    }

    /** Доля поглощённого по Кремсеру при факторе абсорбции {@code a}. */
    public static double kremser(double a, int n) {
        if (a <= 0) return 0;
        if (Math.abs(a - 1) < 1e-9) return n / (n + 1.0);
        double an = Math.pow(a, n + 1);
        if (Double.isInfinite(an)) return 1;
        return (an - a) / (an - 1);
    }

    /** Тепло регенерации при циркуляции {@code lM3S} и снятом CO₂ {@code co2KgS}, Вт. */
    public static double regenerationW(double lM3S, double co2KgS) {
        return co2KgS * DESORPTION_J_KG + lM3S * SOLVENT_KG_M3 * (SOLVENT_CP * SOLVENT_DT + STRIP_STEAM * STEAM_LATENT_J_KG);
    }

    /**
     * Шаг очистки: газ после конверсии CO {@code gas} (сухой, моль/с) и технологическое тепло для регенерации
     * {@code heatW}.
     */
    public Result step(double[] gas, double heatW) {
        double[] n = gas.clone();
        double co2In = n[CO2], total = Species.total(n);
        double l = designCirculationM3S() * circulation;
        double need = regenerationW(l, co2In * Species.CO2.molarMass());
        // нехватка тепла: раствор недорегенерирован, рабочая ёмкость меньше
        double regenShare = need > 0 ? Math.min(1, heatW / need) : 1;
        double a = co2In > 0 ? l * CAPACITY_MOL_M3 * regenShare / co2In : Double.POSITIVE_INFINITY;
        double absorbed = co2In * kremser(a, STAGES);
        double floor = MIN_CO2 * (total - co2In) / (1 - MIN_CO2);
        absorbed = Math.min(absorbed, Math.max(0, co2In - floor));
        n[CO2] -= absorbed;
        double slip = Species.dryFraction(n, Species.CO2);
        // метанатор
        double hIn = Species.enthalpy(n, METHANATOR_IN_K), outK = METHANATOR_IN_K;
        if (!trip && Species.total(n) > 0) {
            double tot = Species.total(n);
            double co = Math.max(0, n[CO] - RESIDUAL * tot), co2 = Math.max(0, n[CO2] - RESIDUAL * tot);
            co = Math.min(co, n[H2] / 3);
            co2 = Math.min(co2, (n[H2] - 3 * co) / 4);
            n[CO] -= co;
            n[CO2] -= co2;
            n[H2] -= 3 * co + 4 * co2;
            n[CH4] += co + co2;
            n[H2O] += co + 2 * co2;
            outK = Species.temperatureFor(n, hIn, METHANATOR_IN_K + 50);
            if (outK > TRIP_K) trip = true;
        }
        n[H2O] = 0; // конденсат после охлаждения
        double dry = Species.total(n);
        double oxy = dry > 0 ? (n[CO] + n[CO2]) / dry : 0;
        double ratio = n[N2] > 0 ? n[H2] / n[N2] : 0;
        boolean ok = !trip && dry > 0 && oxy <= MAX_OXYGENATES && ratio >= MIN_RATIO && ratio <= MAX_RATIO;
        last = new Result(n, absorbed, trip ? METHANATOR_IN_K : outK, need, slip, oxy, ratio, ok);
        return last;
    }

    public void save(StateWriter out) {
        out.putDouble("circ", circulation);
        out.putBoolean("trip", trip);
    }

    public void load(StateReader in) {
        setCirculation(in.optDouble("circ", 1));
        trip = in.has("trip") && in.getBoolean("trip");
    }
}
