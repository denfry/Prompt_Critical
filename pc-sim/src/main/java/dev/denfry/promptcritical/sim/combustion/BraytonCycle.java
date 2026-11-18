package dev.denfry.promptcritical.sim.combustion;

/**
 * Расчётная точка простого цикла Брайтона (GDD 07 §7.10.1): T₂ = T₁[1 + (π^((k_a−1)/k_a) − 1)/η_к],
 * T₄ = T₃[1 − η_т(1 − π^(−(k_g−1)/k_g))], w = (1 + f)c_p,g(T₃ − T₄) − c_p,a(T₂ − T₁), подвод тепла c_p,g(T₃ − T₂) на
 * кг воздуха, доля топлива f = c_p,g(T₃ − T₂)/(Q_н − c_p,g T₃). Потери давления, охлаждающий воздух и механические
 * потери — множитель {@code lossFactor} к работе.
 *
 * @param pressureRatio π
 * @param titK          температура перед турбиной T₃, К
 * @param etaC          изоэнтропийный КПД компрессора
 * @param etaT          изоэнтропийный КПД турбины
 * @param cpAir         c_p воздуха, Дж/(кг·К)
 * @param cpGas         c_p газа, Дж/(кг·К)
 * @param kAir          показатель адиабаты воздуха
 * @param kGas          показатель адиабаты газа
 * @param lhv           низшая теплота сгорания топлива, Дж/кг
 * @param lossFactor    доля идеальной работы, доходящая до вала
 */
public record BraytonCycle(double pressureRatio, double titK, double etaC, double etaT, double cpAir, double cpGas, double kAir,
        double kGas, double lhv, double lossFactor) {
    /** ГТУ первого поколения {@link GasTurbine}: π = 7, 850 °C. */
    public static final BraytonCycle FIRST_GENERATION = new BraytonCycle(GasTurbine.PRESSURE_RATIO, GasTurbine.TIT_K, GasTurbine.ETA_C,
            GasTurbine.ETA_T, GasTurbine.CP_AIR, GasTurbine.CP_GAS, GasTurbine.K_AIR, GasTurbine.K_GAS, 50e6, 1);
    /**
     * Энергетическая ГТУ эпохи I3 (пример T7.13 GDD 07 §7.10.1): π = 12, 1100 °C, η_к 0,86, η_т 0,89, c_p,g 1150,
     * k_g 1,33, потери 0,92 — класс V94.2/MS9001E 1970–80-х (≈ 33 %).
     */
    public static final BraytonCycle HEAVY_DUTY_I3 = new BraytonCycle(12, 1373.15, 0.86, 0.89, 1005, 1150, 1.4, 1.33, 50e6, 0.92);

    public double compressorOutK(double t1K) {
        return t1K * (1 + (Math.pow(pressureRatio, (kAir - 1) / kAir) - 1) / etaC);
    }

    public double exhaustK() {
        return titK * (1 - etaT * (1 - Math.pow(pressureRatio, -(kGas - 1) / kGas)));
    }

    /** Доля топлива, кг на кг воздуха. */
    public double fuelAirRatio(double t1K) {
        return cpGas * (titK - compressorOutK(t1K)) / (lhv - cpGas * titK);
    }

    /** Работа на валу, Дж на кг воздуха. */
    public double specificWork(double t1K) {
        double t2 = compressorOutK(t1K);
        return lossFactor * ((1 + fuelAirRatio(t1K)) * cpGas * (titK - exhaustK()) - cpAir * (t2 - t1K));
    }

    /** Подвод тепла, Дж на кг воздуха. */
    public double heatIn(double t1K) {
        return cpGas * (titK - compressorOutK(t1K));
    }

    public double efficiency(double t1K) {
        return specificWork(t1K) / heatIn(t1K);
    }

    /** Расход воздуха на мощность {@code powerW}, кг/с. */
    public double airFlowKgS(double powerW, double t1K) {
        return powerW / specificWork(t1K);
    }
}
