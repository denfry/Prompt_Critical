package dev.denfry.promptcritical.sim.utilities;

import dev.denfry.promptcritical.sim.thermo.WaterProperties;

/**
 * Атмосферный деаэратор (08 §8.12): вода нагревается греющим паром до кипения при 0,12 МПа, растворённые газы уходят
 * с выпаром — O₂ до 10–20 мкг/кг. Пар — из барабана котла I1; конденсат греющего пара смешивается с водой.
 *
 * <p>Тепловой баланс смешения: G_п·(h_п − h′) = G_в·(h′ − h_в), откуда воды на 1 кг пара (h_п − h′)/(h′ − h_в);
 * выход деаэрированной воды — G_в + G_п.
 */
public final class Deaerator {
    /** Давление в колонке, Па (атмосферный деаэратор ДА). */
    public static final double PRESSURE_PA = 0.12e6;
    /** Остаточный кислород, кг/кг (15 мкг/кг). */
    public static final double OUTLET_O2 = 15e-9;
    private static final WaterProperties WATER = WaterProperties.tables();

    private Deaerator() {
    }

    /** Температура деаэрированной воды — насыщение при {@link #PRESSURE_PA}, К (≈ 104 °C). */
    public static double outletTempK() {
        return WATER.saturationP(PRESSURE_PA).tSat();
    }

    /**
     * Воды, которую нагревает 1 кг греющего пара, кг.
     *
     * @param steamEnthalpy энтальпия греющего пара, Дж/кг
     * @param inletTempK    температура входящей воды, К
     */
    public static double waterPerSteam(double steamEnthalpy, double inletTempK) {
        double hOut = WATER.saturationP(PRESSURE_PA).hF();
        double hIn = WATER.enthalpyPT(PRESSURE_PA, Math.min(inletTempK, outletTempK() - 1));
        return Math.max(0, steamEnthalpy - hOut) / (hOut - hIn);
    }
}
