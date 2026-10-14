package dev.denfry.promptcritical.sim.neutronics;

/**
 * Замедляющие и теплоносительные среды ячейки (01 §1.6.2–1.6.3). Все величины — при опорной
 * плотности {@code refDensity}; макросечения и замедляющая способность ∝ ρ, D ∝ 1/ρ, τ ∝ 1/ρ².
 *
 * <p>Тепловые D — по Lamarsh («Introduction to Nuclear Reactor Theory», табл. 5.2); быстрое
 * сечение выведения — табличные значения защиты для нейтронов деления; время замедления —
 * порядок величины по Lamarsh гл. 6 (§1.5.3).
 */
enum Medium {
    //        ρ0     M       σa/мол.  σs эпитерм.  ξΣs     D_th   τ      Σ_rem   t_sd
    H2O(1.000, 18.015, 0.6642, 44.6, 1.37, 0.16, 27, 0.103, 1e-6, true),
    /** 99,75 % D₂O: поглощение определяется примесью лёгкой воды. */
    D2O(1.105, 20.03, 2.87e-3, 10.6, 0.178, 0.87, 131, 0.092, 1e-5, true),
    GRAPHITE(1.65, 12.011, 3.4e-3, 4.7, 0.0614, 0.815, 346, 0.081, 1e-4, false),
    BE(1.85, 9.012, 7.6e-3, 6.1, 0.154, 0.50, 102, 0.132, 7e-5, false),
    AIR(1.2e-3, 28.96, 2.96, 20, 0, 1e4, 1e9, 0, 1e-4, false),
    NA(0.93, 22.99, 0.53, 3.2, 0.0066, 4.4, 1e4, 0.032, 1e-4, false);

    /** Природный бор: σ_a при 2200 м/с, б; молярная масса, г/моль. */
    static final double BORON_SIGMA_A = 767, BORON_MOLAR_MASS = 10.81;

    final double refDensity, molarMass, sigmaA, sigmaS, moderatingPower, diffusion, age, removal, slowingDownTime;
    final boolean liquidWater;

    Medium(double refDensity, double molarMass, double sigmaA, double sigmaS, double moderatingPower,
            double diffusion, double age, double removal, double slowingDownTime, boolean liquidWater) {
        this.refDensity = refDensity;
        this.molarMass = molarMass;
        this.sigmaA = sigmaA;
        this.sigmaS = sigmaS;
        this.moderatingPower = moderatingPower;
        this.diffusion = diffusion;
        this.age = age;
        this.removal = removal;
        this.slowingDownTime = slowingDownTime;
        this.liquidWater = liquidWater;
    }

    /** Σ_a при 2200 м/с, см⁻¹, с бором (ppm по массе) для жидкой воды. */
    double absorption2200(double density, double boronPpm) {
        double s = density * LatticeModel.AVOGADRO_BARN / molarMass * sigmaA;
        return liquidWater ? s + boronAbsorption(density, boronPpm) : s;
    }

    /** Σ_a природного бора при 2200 м/с, см⁻¹, в среде плотности {@code density} (ppm по массе). */
    static double boronAbsorption(double density, double ppm) {
        return density * ppm * 1e-6 * LatticeModel.AVOGADRO_BARN / BORON_MOLAR_MASS * BORON_SIGMA_A;
    }

    /** Эпитепловое Σ_s, см⁻¹ (фактор Данкова). */
    double scattering(double density) {
        return density * LatticeModel.AVOGADRO_BARN / molarMass * sigmaS;
    }

    double moderatingPower(double density) {
        return moderatingPower * density / refDensity;
    }

    double diffusion(double density) {
        return diffusion * refDensity / Math.max(density, 1e-9);
    }

    double removal(double density) {
        return removal * density / refDensity;
    }

    /**
     * Плотность жидкого замедлителя в баке при 0,1 МПа, г/см³, по температуре — формула Тизена
     * для H₂O (0–100 °C), для D₂O масштабированная к ρ₀; твёрдые среды — ρ₀.
     */
    double densityAt(double temperature) {
        if (!liquidWater) return refDensity;
        double t = Math.max(0, Math.min(100, temperature - 273.15));
        double h2o = 1 - (t - 3.9863) * (t - 3.9863) * (t + 288.9414) / (508929.2 * (t + 68.12963));
        return h2o * refDensity / 0.9982;
    }
}
