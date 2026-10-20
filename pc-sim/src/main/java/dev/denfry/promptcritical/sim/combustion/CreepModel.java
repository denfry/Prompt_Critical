package dev.denfry.promptcritical.sim.combustion;

/** Ползучесть металла труб (Ларсон–Миллер), 07 §7.9.3. Данные — по {@code steel_grade} (08). */
public interface CreepModel {
    /** Время до разрушения при напряжении {@code stressPa} и температуре металла {@code metalTempK}, ч (физ.). */
    double ruptureTimeH(double stressPa, double metalTempK);
}
