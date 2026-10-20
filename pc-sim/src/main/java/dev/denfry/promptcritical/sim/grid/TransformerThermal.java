package dev.denfry.promptcritical.sim.grid;

/**
 * Тепловая модель трансформатора IEC 60076-7 (07 §7.4.4): установившиеся превышения
 * Δθ_o = Δθ_or·[(1 + RK²)/(1 + R)]^n, Δθ_h = Hg_r·K^y, к которым температуры масла и обмотки
 * приближаются экспоненциально с τ_o и τ_w. Интегрируется в SLOW ({@link PowerGrid#stepSlow}).
 *
 * @param r R = P_k/P_0
 */
public record TransformerThermal(double dThetaOrK, double hgrK, double r, double n, double y, double tauOilS,
        double tauWindingS) {
    /** Нормальный ресурс бумажной изоляции, ч (физ.). */
    public static final double NORMAL_LIFE_H = 180_000;

    public TransformerThermal {
        if (!(dThetaOrK > 0 && hgrK > 0 && r > 0 && n > 0 && y > 0 && tauOilS > 0 && tauWindingS > 0))
            throw new IllegalArgumentException("thermal data");
    }

    /** Распределительный ONAN (таблица §7.4.4, [сверить] по IEC 60076-7). */
    public static TransformerThermal onanDistribution() {
        return new TransformerThermal(55, 23, 5, 0.8, 1.6, 180 * 60, 4 * 60);
    }

    /** Силовой ONAN. */
    public static TransformerThermal onanPower() {
        return new TransformerThermal(52, 26, 6, 0.8, 1.6, 210 * 60, 10 * 60);
    }

    /** Относительная скорость старения IEC 60076-7 (не термостабилизированная бумага). */
    public static double agingRate(double hotSpotC) {
        return Math.pow(2.0, (hotSpotC - 98.0) / 6.0);
    }

    /** Установившееся превышение температуры верхних слоёв масла при K = S/S_ном, К. */
    public double steadyTopOilRiseK(double k) {
        return dThetaOrK * Math.pow((1 + r * k * k) / (1 + r), n);
    }

    /** Установившееся превышение наиболее нагретой точки над маслом, К. */
    public double steadyHotSpotRiseK(double k) {
        return hgrK * Math.pow(k, y);
    }

    /** Установившаяся температура наиболее нагретой точки, °C. */
    public double steadyHotSpotC(double k, double ambientC) {
        return ambientC + steadyTopOilRiseK(k) + steadyHotSpotRiseK(k);
    }
}
