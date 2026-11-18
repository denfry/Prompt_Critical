package dev.denfry.promptcritical.sim.reactor.pwr;

import dev.denfry.promptcritical.sim.thermo.SaturationState;
import dev.denfry.promptcritical.sim.thermo.WaterProperties;

/**
 * Второй контур горизонтального парогенератора, сосредоточенный: насыщенная вода (масса M_w) и металл трубного пучка
 * на линии насыщения при давлении P_s, пар уходит на турбину через регулирующий клапан критическим истечением
 * (G = открытие · G₀ · P_s/P₀), питательная вода — регулятором уровня (пропорциональный по уровню плюс компенсация
 * расхода пара). Номинал ПГВ ПР-300 (01 §1.14.1): 6,4 МПа, питательная вода 220 °C.
 *
 * <p>Для автономных расчётов (тесты, справочник); в мире второй контур — котлы и турбины {@code SteamPlant}.
 */
public final class SecondarySide {
    public static final double P_NOMINAL = 6.4e6, FEED_K = 493.15;
    /** Металл трубного пучка и корпуса ПГ, Дж/К [оценка: ≈ 60 т стали × 500 Дж/(кг·К) на 100 МВт]. */
    public static final double METAL_J_PER_K = 3.0e7;

    private final WaterProperties water;
    private final double nominalSteamKgS, nominalWaterKg;
    private double waterKg, pressure, valve = 1;
    private double steamKgS, feedKgS;
    private final double[] satBuf = new double[17];
    private final SaturationState sat = new SaturationState();

    /**
     * @param nominalPowerW тепловая мощность ПГ на номинале, Вт
     * @param waterKg       масса воды второго контура на номинальном уровне, кг
     */
    public SecondarySide(WaterProperties water, double nominalPowerW, double waterKg) {
        this.water = water;
        SaturationState s = water.saturationP(P_NOMINAL);
        this.nominalSteamKgS = nominalPowerW / (s.hG() - water.enthalpyPT(P_NOMINAL, FEED_K));
        this.nominalWaterKg = waterKg;
        this.waterKg = waterKg;
        this.pressure = P_NOMINAL;
    }

    /** Регулирующий клапан турбины, доля открытия на номинале (1 — номинальный расход пара). */
    public void setValve(double v) {
        valve = Math.max(0, Math.min(1.2, v));
    }

    public double valve() {
        return valve;
    }

    /** Шаг с теплом из первого контура {@code qW}, Вт. */
    public void step(double dt, double qW) {
        water.saturationP(pressure, satBuf);
        SaturationState s = sat.set(pressure, satBuf);
        steamKgS = valve * nominalSteamKgS * pressure / P_NOMINAL;
        double level = level();
        feedKgS = Math.max(0, steamKgS + 0.02 * nominalWaterKg * (0.5 - level) / 10);
        double hFeed = water.enthalpyPT(pressure, FEED_K);
        double capacity = waterKg * s.cpF() + METAL_J_PER_K;
        double dT = (qW - steamKgS * (s.hG() - s.hF()) - feedKgS * (s.hF() - hFeed)) / capacity * dt;
        double tNew = Math.max(373.15, Math.min(620, s.tSat() + dT));
        pressure = water.saturationPressure(tNew);
        waterKg = Math.max(0, waterKg + (feedKgS - steamKgS) * dt);
    }

    /** Уровень, доля шкалы (0,5 — номинальный). */
    public double level() {
        return 0.5 * waterKg / nominalWaterKg;
    }

    public double pressure() {
        return pressure;
    }

    public double temperatureK() {
        return water.saturationP(pressure).tSat();
    }

    public double steamKgS() {
        return steamKgS;
    }

    public double feedKgS() {
        return feedKgS;
    }
}
