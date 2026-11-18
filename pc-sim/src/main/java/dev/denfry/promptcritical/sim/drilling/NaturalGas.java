package dev.denfry.promptcritical.sim.drilling;

/**
 * Сухой природный газ относительной плотности 0,65 (M = 0,65 · 28,97 г/моль) с постоянным коэффициентом
 * сверхсжимаемости Z = 0,9 — типичное значение при 10–30 МПа и 300–360 K по графику Standing–Katz (McCain, гл. 3).
 * Для притока в скважину этого достаточно: ошибка Z при этих давлениях — несколько процентов.
 */
public final class NaturalGas {
    public static final double MOLAR_MASS_KG_MOL = 0.65 * 0.028_97;
    public static final double Z = 0.9;
    public static final double R = 8.314_462;
    /** Нормальные условия для объёма газа: 101,325 кПа и 15 °C (ГОСТ 2939, ISO 13443). */
    public static final double STD_PRESSURE_PA = 101_325, STD_TEMPERATURE_K = 288.15;

    private NaturalGas() {
    }

    /** ρ = pM / (ZRT), кг/м³; давление абсолютное. */
    public static double densityKgM3(double pressureAbsPa, double temperatureK) {
        return pressureAbsPa * MOLAR_MASS_KG_MOL / (Z * R * temperatureK);
    }

    /** Объём n моль, м³. */
    public static double volumeM3(double moles, double pressureAbsPa, double temperatureK) {
        return moles * Z * R * temperatureK / pressureAbsPa;
    }

    /** Количество вещества в объёме, моль. */
    public static double moles(double volumeM3, double pressureAbsPa, double temperatureK) {
        return volumeM3 * pressureAbsPa / (Z * R * temperatureK);
    }

    /** Объём при нормальных условиях (Z = 1), м³. */
    public static double standardVolumeM3(double moles) {
        return moles * R * STD_TEMPERATURE_K / STD_PRESSURE_PA;
    }
}
