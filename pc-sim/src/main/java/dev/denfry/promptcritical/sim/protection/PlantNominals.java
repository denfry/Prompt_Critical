package dev.denfry.promptcritical.sim.protection;

import dev.denfry.promptcritical.sim.thermo.WaterProperties;

/**
 * Номинальные параметры установки с водой под давлением, из которых выводятся уставки (01 §1.11.3).
 *
 * @param nominalPressure          P_ном, Па
 * @param saturationPressureAtTout P_sat(T_out,ном), Па — база уставки по низкому давлению
 * @param saturationTempAtPnom     T_sat(P_ном), К — база уставки по температуре
 * @param safetyValvePressure      давление открытия ПК КД, Па; уставка по высокому давлению ≤ его − 0,3 МПа
 * @param safetyInjectionPressure  давление сигнала САОЗ, Па; NaN — САОЗ нет
 */
public record PlantNominals(double nominalPressure, double saturationPressureAtTout, double saturationTempAtPnom,
        double safetyValvePressure, double safetyInjectionPressure) {
    public PlantNominals {
        if (!(nominalPressure > 0)) throw new IllegalArgumentException("nominalPressure");
        if (!(saturationPressureAtTout > 0 && saturationPressureAtTout < nominalPressure))
            throw new IllegalArgumentException("saturationPressureAtTout");
        if (!(saturationTempAtPnom > 273.15)) throw new IllegalArgumentException("saturationTempAtPnom");
        if (!(safetyValvePressure > nominalPressure)) throw new IllegalArgumentException("safetyValvePressure");
        if (!Double.isNaN(safetyInjectionPressure) && !(safetyInjectionPressure > 0 && safetyInjectionPressure < nominalPressure))
            throw new IllegalArgumentException("safetyInjectionPressure");
    }

    public static PlantNominals of(WaterProperties water, double nominalPressure, double outletTempNominal,
            double safetyValvePressure, double safetyInjectionPressure) {
        return new PlantNominals(nominalPressure, water.saturationPressure(outletTempNominal),
                water.saturationP(nominalPressure).tSat(), safetyValvePressure, safetyInjectionPressure);
    }
}
