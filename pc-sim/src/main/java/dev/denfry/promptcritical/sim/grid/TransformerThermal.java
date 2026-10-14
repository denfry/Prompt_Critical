package dev.denfry.promptcritical.sim.grid;

/** Тепловая модель трансформатора IEC 60076-7 (07 §7.4.4); интегрирование в SLOW — после M1. */
public record TransformerThermal(double dThetaOrK, double hgrK, double r, double n, double y, double tauOilS,
        double tauWindingS) {
    /** Относительная скорость старения IEC 60076-7. */
    public static double agingRate(double hotSpotC) {
        return Math.pow(2.0, (hotSpotC - 98.0) / 6.0);
    }
}
