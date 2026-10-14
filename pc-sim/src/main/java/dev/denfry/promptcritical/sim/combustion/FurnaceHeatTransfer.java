package dev.denfry.promptcritical.sim.combustion;

/**
 * Тепловосприятие топки — нормативный метод теплового расчёта котлов, формула Гурвича (ЦКТИ/ВТИ, 1973; 07 §7.9.2):
 * T''_т / T_a = Bo^0,6 / (M·a_т^0,6 + Bo^0,6), Bo = φ·B·V̄c / (σ₀·ψ·F_ст·T_a³).
 */
public final class FurnaceHeatTransfer {
    /** Постоянная Стефана — Больцмана, Вт/(м²·К⁴). */
    public static final double SIGMA = 5.670374419e-8;
    /** Параметр положения ядра факела M. */
    public static final double M_DEFAULT = 0.45;
    /** Коэффициент сохранения тепла φ. */
    public static final double PHI_DEFAULT = 0.98;

    private FurnaceHeatTransfer() {}

    /**
     * Отношение температуры газов на выходе из топки к адиабатной.
     *
     * @param boltzmannNumber   Bo
     * @param furnaceEmissivity степень черноты топки a_т (пылеугольная 0,5–0,7, газ 0,3–0,4)
     * @param m                 параметр M
     */
    public static double exitTempRatio(double boltzmannNumber, double furnaceEmissivity, double m) {
        double b = Math.pow(boltzmannNumber, 0.6);
        return b / (m * Math.pow(furnaceEmissivity, 0.6) + b);
    }

    /**
     * Число Больцмана Bo.
     *
     * @param phi              коэффициент сохранения тепла φ
     * @param heatCapacityRate B·V̄c — суммарная теплоёмкость продуктов сгорания, Вт/К
     * @param psi              коэффициент тепловой эффективности экранов ψ (0,45 уголь, 0,65 газ)
     * @param wallAreaM2       площадь стен топки F_ст, м²
     * @param adiabaticK       адиабатная температура T_a, К
     */
    public static double boltzmannNumber(double phi, double heatCapacityRate, double psi, double wallAreaM2,
            double adiabaticK) {
        return phi * heatCapacityRate / (SIGMA * psi * wallAreaM2 * adiabaticK * adiabaticK * adiabaticK);
    }

    /** Тепло, отданное в топке экранам, Q_т = φ·B·V̄c·(T_a − T''_т), Вт. */
    public static double furnaceHeat(double phi, double heatCapacityRate, double adiabaticK, double exitK) {
        return phi * heatCapacityRate * (adiabaticK - exitK);
    }
}
