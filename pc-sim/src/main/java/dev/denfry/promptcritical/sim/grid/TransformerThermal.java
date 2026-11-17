package dev.denfry.promptcritical.sim.grid;

/**
 * Тепловая модель трансформатора IEC 60076-7 (07 §7.4.4), разностные уравнения приложения B: масло
 * k11·τ_o·dΔθ_o/dt = Δθ_or·[(1 + RK²)/(1 + R)]^n − Δθ_o; горячая точка Δθ_h = Δθ_h1 − Δθ_h2,
 * k22·τ_w·dΔθ_h1/dt = k21·Hg_r·K^y − Δθ_h1, (τ_o/k22)·dΔθ_h2/dt = (k21 − 1)·Hg_r·K^y − Δθ_h2.
 * Второй член — запаздывание циркуляции масла: при скачке нагрузки горячая точка у ONAN сначала
 * перескакивает установившееся значение. Интегрируется в SLOW ({@link PowerGrid#stepSlow}).
 *
 * @param r R = P_k/P_0
 */
public record TransformerThermal(double dThetaOrK, double hgrK, double r, double n, double y, double k11,
        double k21, double k22, double tauOilS, double tauWindingS) {
    /** Нормальный ресурс бумажной изоляции, ч (физ.). */
    public static final double NORMAL_LIFE_H = 180_000;

    public TransformerThermal {
        if (!(dThetaOrK > 0 && hgrK > 0 && r > 0 && n > 0 && y > 0 && k11 > 0 && k21 >= 1 && k22 > 0
                && tauOilS > 0 && tauWindingS > 0))
            throw new IllegalArgumentException("thermal data");
    }

    /**
     * Распределительный ONAN: x = 0,8, y = 1,6, k11 = 1, k21 = 1, k22 = 2, τ_o = 180 мин, τ_w = 4 мин
     * (IEC 60076-7:2005, табл. 5; превышения — таблица §7.4.4).
     */
    public static TransformerThermal onanDistribution() {
        return new TransformerThermal(55, 23, 5, 0.8, 1.6, 1.0, 1.0, 2.0, 180 * 60, 4 * 60);
    }

    /**
     * Силовой ONAN средней и большой мощности, обмотки без зигзагообразного охлаждения: x = 0,8, y = 1,3,
     * k11 = 0,5, k21 = 2, k22 = 2, τ_o = 210 мин, τ_w = 10 мин (IEC 60076-7:2005, табл. 5).
     */
    public static TransformerThermal onanPower() {
        return new TransformerThermal(52, 26, 6, 0.8, 1.3, 0.5, 2.0, 2.0, 210 * 60, 10 * 60);
    }

    /** Относительная скорость старения IEC 60076-7 (не термостабилизированная бумага). */
    public static double agingRate(double hotSpotC) {
        return Math.pow(2.0, (hotSpotC - 98.0) / 6.0);
    }

    /** Установившееся превышение температуры верхних слоёв масла при K = S/S_ном, К. */
    public double steadyTopOilRiseK(double k) {
        return dThetaOrK * Math.pow((1 + r * k * k) / (1 + r), n);
    }

    /** Шаг превышения масла Δθ_o за dt, с, при нагрузке K. */
    public double stepTopOilRiseK(double rise, double k, double dtS) {
        return rise + (steadyTopOilRiseK(k) - rise) * (1 - Math.exp(-dtS / (k11 * tauOilS)));
    }

    /** Шаг первого члена горячей точки Δθ_h1 (обмотка) за dt, с. */
    public double stepWindingRise1K(double h1, double k, double dtS) {
        return h1 + (k21 * steadyHotSpotRiseK(k) - h1) * (1 - Math.exp(-dtS / (k22 * tauWindingS)));
    }

    /** Шаг второго члена горячей точки Δθ_h2 (запаздывание масла у обмотки) за dt, с. */
    public double stepWindingRise2K(double h2, double k, double dtS) {
        return h2 + ((k21 - 1) * steadyHotSpotRiseK(k) - h2) * (1 - Math.exp(-dtS * k22 / tauOilS));
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
