package dev.denfry.promptcritical.sim.hydrogen;

import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;

/**
 * Модуль топливных элементов PEM 200 кВт класса Ballard FCwave с сетевым инвертором. GDD 17 §17.3.8 описывает
 * щелочной элемент Бэкона; модель здесь — PEM, как у нарисованного модуля (решение оркестратора 2026-11-24,
 * отклонение для §0.13): та же кривая поляризации, другие коэффициенты.
 *
 * <p>Напряжение элемента — модель Larminie и Dicks («Fuel Cell Systems Explained», 2003, §3.7):
 * V = E − A·ln((i + iₙ)/i₀) − (i + iₙ)·r − m·exp(n·i), i — А/см². E — Нернст при 1,5 бар (1,19 В при 70 °C),
 * iₙ = 2 мА/см² — внутренний ток (проникание водорода): поэтому напряжение холостого хода около 1,0 В, а не 1,2.
 * Тафель A = 0,03 В, i₀ при 70 °C = 10 мкА/см², растёт с температурой по Аррениусу (66 кДж/моль, восстановление
 * кислорода на платине). Сопротивление мембраны 0,1 Ом·см² при 70 °C, по Springer (1991) — exp(1268·(1/T − 1/303)).
 * Холодный модуль поэтому слабее: до прогрева он не даёт номинала.
 *
 * <p>Номинал: 1,04 А/см², 0,73 В на элементе, 200 кВт переменного тока, КПД по низшей теплоте около 50 % (стек 58 %,
 * минус воздуходувка 8 % и инвертор 3 %). Водород: N·I/(2F), использование 99 % (продувки анода). Вода: моль на моль
 * водорода. Тепло — всё, что ниже 1,253 В (низшая теплота, вода уходит паром), уносит радиатор; термостат держит
 * 70 °C, выше 85 °C — отключение.
 */
public final class PemFuelCell {
    public static final int CELLS = 600;
    public static final double CELL_CM2 = 500;
    public static final double RATED_AC_W = 200e3;
    public static final double MAX_A_CM2 = 1.6;
    static final double TAFEL = 0.03, I0_343 = 1e-5, I_N = 2e-3, R_343 = 0.10, M_CONC = 2e-5, N_CONC = 5;
    static final double EA = 66e3;
    public static final double LHV_V = 1.253;
    public static final double BLOWER_FRACTION = 0.08, INVERTER_EFF = 0.97, UTILISATION = 0.99;
    /** Наименьшее давление водорода перед регулятором, Па абс. */
    public static final double MIN_SUPPLY_PA = 3e5;
    public static final double SETPOINT_K = 343.15, TRIP_K = 358.15;
    static final double HEAT_CAPACITY = 1.5e6, RADIATOR_W_K = 8000, LOSS_W_K = 50;

    public enum Trip { NONE, OVERTEMP, LOW_H2 }

    private boolean on;
    private double setpointW = RATED_AC_W;
    private double tempK = 293.15;
    private Trip trip = Trip.NONE;
    private double iCm2, cellV, grossW, acW, h2MolS, heatW;

    public boolean on() {
        return on;
    }

    public void setOn(boolean on) {
        this.on = on;
        if (on) trip = Trip.NONE;
    }

    public Trip trip() {
        return trip;
    }

    /** Задание мощности переменного тока, Вт. */
    public void setSetpointW(double w) {
        setpointW = Math.max(0, Math.min(RATED_AC_W, w));
    }

    public double setpointW() {
        return setpointW;
    }

    public static double nernstV(double tempK) {
        double e = 1.229 - 0.85e-3 * (tempK - 298.15);
        return e + HydrogenGas.R * tempK / (2 * HydrogenGas.F) * Math.log(1.5 * Math.sqrt(0.21 * 1.5));
    }

    /** Напряжение элемента при плотности тока {@code aCm2}, А/см². */
    public static double cellVoltage(double aCm2, double tempK) {
        double i = Math.max(0, aCm2) + I_N;
        double i0 = I0_343 * Math.exp(-EA / HydrogenGas.R * (1 / tempK - 1 / SETPOINT_K));
        double r = R_343 * Math.exp(1268 * (1 / tempK - 1 / SETPOINT_K));
        return nernstV(tempK) - TAFEL * Math.log(i / i0) - i * r - M_CONC * Math.exp(N_CONC * aCm2);
    }

    /** Мощность переменного тока при плотности тока {@code aCm2}, Вт. */
    public static double acPowerW(double aCm2, double tempK) {
        double gross = CELLS * CELL_CM2 * aCm2 * Math.max(0, cellVoltage(aCm2, tempK));
        return gross * (1 - BLOWER_FRACTION) * INVERTER_EFF;
    }

    /** Водород при плотности тока, моль/с. */
    public static double h2MolS(double aCm2) {
        return CELLS * CELL_CM2 * aCm2 / (2 * HydrogenGas.F) / UTILISATION;
    }

    /**
     * Шаг.
     *
     * @param supplyPa    давление водорода перед регулятором (рампа), Па абс.
     * @param h2AvailMol  водород, который рампа может отдать на шаге, моль
     * @param demandW     сколько мощности сейчас берёт сеть, Вт (не больше задания)
     * @param ambientK    наружная температура, K
     * @param radiator    работают ли вентиляторы радиатора
     * @return водород, взятый на шаге, моль
     */
    public double step(double dtS, double supplyPa, double h2AvailMol, double demandW, double ambientK, boolean radiator) {
        if (on && trip == Trip.NONE && supplyPa < MIN_SUPPLY_PA) trip(Trip.LOW_H2);
        double want = on && trip == Trip.NONE ? Math.min(setpointW, Math.max(0, demandW)) : 0;
        double i = want > 0 ? currentFor(want) : 0;
        if (i > 0 && dtS > 0) {
            double maxByH2 = h2AvailMol / dtS * 2 * HydrogenGas.F * UTILISATION / (CELLS * CELL_CM2);
            i = Math.min(i, Math.max(0, maxByH2));
        }
        iCm2 = i;
        cellV = i > 0 ? cellVoltage(i, tempK) : on && trip == Trip.NONE ? cellVoltage(0, tempK) : 0;
        grossW = CELLS * CELL_CM2 * i * Math.max(0, cellV);
        acW = grossW * (1 - BLOWER_FRACTION) * INVERTER_EFF;
        h2MolS = h2MolS(i);
        heatW = CELLS * CELL_CM2 * i * (LHV_V - Math.max(0, cellV));
        double radW = radiator && tempK > SETPOINT_K ? RADIATOR_W_K * (tempK - ambientK) * Math.min(1, (tempK - SETPOINT_K) / 2) : 0;
        tempK += (heatW - radW - LOSS_W_K * (tempK - ambientK)) * dtS / HEAT_CAPACITY;
        if (on && trip == Trip.NONE && tempK >= TRIP_K) trip(Trip.OVERTEMP);
        return h2MolS * dtS;
    }

    private void trip(Trip t) {
        trip = t;
        on = false;
    }

    /** Плотность тока под мощность {@code w}: бисекция по восходящей ветви кривой мощности. */
    private double currentFor(double w) {
        double hi = MAX_A_CM2;
        // вершина мощности у холодного модуля бывает ниже 1,6 А/см²: ищем её, правее не идём
        double best = 0, bestW = 0;
        for (int k = 1; k <= 32; k++) {
            double a = MAX_A_CM2 * k / 32, p = acPowerW(a, tempK);
            if (p > bestW) {
                bestW = p;
                best = a;
            }
        }
        if (bestW <= w) return best;
        hi = best;
        double lo = 0;
        for (int k = 0; k < 30; k++) {
            double mid = (lo + hi) / 2;
            if (acPowerW(mid, tempK) < w) lo = mid;
            else hi = mid;
        }
        return hi;
    }

    /** Наибольшая мощность при нынешней температуре, Вт. */
    public double availableW() {
        double bestW = 0;
        for (int k = 1; k <= 32; k++) bestW = Math.max(bestW, acPowerW(MAX_A_CM2 * k / 32, tempK));
        return Math.min(setpointW, bestW);
    }

    public double currentDensity() {
        return iCm2;
    }

    public double cellVoltage() {
        return cellV;
    }

    public double stackVoltage() {
        return cellV * CELLS;
    }

    public double grossW() {
        return grossW;
    }

    public double acW() {
        return acW;
    }

    public double h2MolS() {
        return h2MolS;
    }

    /** Вода из элемента, кг/с. */
    public double waterKgS() {
        return h2MolS * UTILISATION * HydrogenGas.M_H2O;
    }

    public double heatW() {
        return heatW;
    }

    /** КПД по низшей теплоте водорода (0 — стоит). */
    public double efficiencyLhv() {
        return h2MolS > 0 ? acW / (h2MolS * HydrogenGas.M * HydrogenGas.LHV) : 0;
    }

    public double tempK() {
        return tempK;
    }

    public void save(StateWriter out) {
        out.putBoolean("on", on);
        out.putDouble("sp", setpointW);
        out.putDouble("t", tempK);
        out.putInt("trip", trip.ordinal());
    }

    public void load(StateReader in) {
        on = in.optInt("on", 0) != 0;
        setpointW = in.optDouble("sp", RATED_AC_W);
        tempK = in.optDouble("t", 293.15);
        int t = in.optInt("trip", 0);
        trip = t >= 0 && t < Trip.values().length ? Trip.values()[t] : Trip.NONE;
    }
}
