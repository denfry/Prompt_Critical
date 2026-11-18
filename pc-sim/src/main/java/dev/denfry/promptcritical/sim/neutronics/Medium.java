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
    /** σ_a — ENDF/B-VII.1 при 2200 м/с, 10,0 мб (атлас Мугаббгаба — 8,49 мб); старые 7,6 мб занижали поглощение. */
    BE(1.85, 9.012, 10.0e-3, 6.1, 0.154, 0.50, 102, 0.132, 7e-5, false),
    AIR(1.2e-3, 28.96, 2.96, 20, 0, 1e4, 1e9, 0, 1e-4, false),
    NA(0.93, 22.99, 0.53, 3.2, 0.0066, 4.4, 1e4, 0.032, 1e-4, false);

    /** Природный бор: σ_a при 2200 м/с, б; молярная масса, г/моль. */
    static final double BORON_SIGMA_A = 767, BORON_MOLAR_MASS = 10.81;
    /**
     * Изотопная чистота: константы {@code D2O} заданы при 99,75 ат. % D. Протий на месте дейтерия добавляет на молекулу
     * поглощение 2(σ_H − σ_D), σ_H = 0,3326 б, σ_D = 0,519 мб при 2200 м/с (ENDF/B-VII.1; Mughabghab, «Atlas of Neutron
     * Resonances», 2006) — проверка: 2·(0,0025·0,3326 + 0,9975·0,000519) + 0,00019 (кислород) = 2,89 мб против
     * табличных 2,87. Рассеяние и замедляющая способность ξσ_s на молекулу аддитивны по ядрам и линейны по доле
     * протия между D₂O и H₂O этой же таблицы. D и τ при малой примеси H не пересчитываются: главное — поглощение
     * (на решётке CANDU модель даёт ≈ 12 мк на ат. % чистоты замедлителя и ≈ 1,3 мк — теплоносителя).
     */
    static final double SIGMA_A_H = 0.3326, SIGMA_A_D = 0.519e-3;

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
        return absorption2200(density, boronPpm, CellFeedbackState.REACTOR_GRADE_D2O);
    }

    /** Σ_a при 2200 м/с с изотопной чистотой {@code d2o} (ат. доля D) — для {@code D2O}; прочим средам не важна. */
    double absorption2200(double density, double boronPpm, double d2o) {
        double s = density * LatticeModel.AVOGADRO_BARN / molarMass * (sigmaA + protiumSigmaA(d2o));
        return liquidWater ? s + boronAbsorption(density, boronPpm) : s;
    }

    /** Добавка поглощения на молекулу от протия сверх реакторной чистоты, б (отрицательна для чистоты выше). */
    double protiumSigmaA(double d2o) {
        return this == D2O ? 2 * (CellFeedbackState.REACTOR_GRADE_D2O - d2o) * (SIGMA_A_H - SIGMA_A_D) : 0;
    }

    /** Множитель рассеяния и замедляющей способности {@code D2O} при чистоте {@code d2o}: линейно к H₂O. */
    double protiumFactor(double d2o, boolean moderating) {
        if (this != D2O) return 1;
        double perMoleculeD = (moderating ? moderatingPower / (refDensity * LatticeModel.AVOGADRO_BARN / molarMass) : sigmaS);
        double perMoleculeH = moderating ? H2O.moderatingPower / (H2O.refDensity * LatticeModel.AVOGADRO_BARN / H2O.molarMass) : H2O.sigmaS;
        double shift = (CellFeedbackState.REACTOR_GRADE_D2O - d2o) / CellFeedbackState.REACTOR_GRADE_D2O;
        return (perMoleculeD + shift * (perMoleculeH - perMoleculeD)) / perMoleculeD;
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
