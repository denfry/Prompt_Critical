package dev.denfry.promptcritical.sim.grid;

import java.util.Objects;

/**
 * Марка провода (07 §7.4.1): сечение, R₂₀, длительно допустимый ток, масса, разрывное усилие. Нагрев —
 * упрощённый баланс IEEE Std 738, провисание — параболическое уравнение состояния без упругого
 * удлинения (§7.4.2).
 */
public record ConductorSpec(Conductor material, double crossSectionMm2, double r20OhmPerKm, double ampacityA,
        double massKgPerM, double rtsN) {
    /** Температурный коэффициент сопротивления, 1/К. */
    public static final double RESISTANCE_TEMP_COEFF = 0.004;
    /** Излучательная способность провода. */
    static final double EMISSIVITY = 0.5;
    /** Повив: наружный диаметр больше диаметра круга того же сечения. */
    static final double STRANDING = 1.15;
    private static final double SIGMA = 5.670e-8;
    /** Шаг явного интегрирования нагрева при длинном SLOW-шаге, с (τ провода — минуты). */
    private static final double MAX_THERMAL_STEP_S = 10;

    public ConductorSpec {
        Objects.requireNonNull(material, "material");
        if (!(crossSectionMm2 > 0 && r20OhmPerKm > 0 && ampacityA > 0 && massKgPerM > 0 && rtsN > 0))
            throw new IllegalArgumentException("conductor data");
    }

    // Каталог §7.4.1; масса и разрывное усилие — [сверить] по ГОСТ 839-2019.
    public static ConductorSpec m35() {
        return new ConductorSpec(Conductor.COPPER, 35, 0.52, 170, 0.311, 13.6e3);
    }

    public static ConductorSpec m70() {
        return new ConductorSpec(Conductor.COPPER, 70, 0.27, 265, 0.618, 26.8e3);
    }

    public static ConductorSpec ac70() {
        return new ConductorSpec(Conductor.ACSR, 70, 0.429, 265, 0.276, 24.1e3);
    }

    public static ConductorSpec ac120() {
        return new ConductorSpec(Conductor.ACSR, 120, 0.249, 390, 0.471, 41.5e3);
    }

    public static ConductorSpec ac240() {
        return new ConductorSpec(Conductor.ACSR, 240, 0.118, 605, 0.921, 75.1e3);
    }

    /** Наружный диаметр, м. */
    public double diameterM() {
        return STRANDING * Math.sqrt(4 * crossSectionMm2 * 1e-6 / Math.PI);
    }

    /** R(T) = R₂₀(1 + 0,004(T − 20)), Ом/м. */
    public double resistanceOhmPerM(double tempC) {
        return r20OhmPerKm * 1e-3 * (1 + RESISTANCE_TEMP_COEFF * (tempC - 20));
    }

    /** Потери трёхфазной линии ΔP = 3I²r₀l при 20 °C, Вт. */
    public double lossW(double currentA, double lengthM) {
        return 3 * currentA * currentA * r20OhmPerKm * lengthM * 1e-3;
    }

    /**
     * Температура провода через {@code dtS}: m·c_p·dT/dt = I²R(T) + q_s − h_c·πd·(T − T_a) − εσπd(T⁴ − T_a⁴).
     *
     * @param convectionWm2K h_c — 5–15 Вт/(м²·К) при ветре 0–2 м/с
     * @param solarWPerM     q_s — 0–15 Вт/м днём
     */
    public double stepTemperatureC(double tempC, double currentA, double ambientC, double convectionWm2K,
            double solarWPerM, double dtS) {
        double mc = massKgPerM * material.specificHeat, pd = Math.PI * diameterM();
        double ta = ambientC + 273.15, ta4 = ta * ta * ta * ta;
        int n = (int) Math.ceil(dtS / MAX_THERMAL_STEP_S);
        double h = dtS / n, t = tempC;
        for (int k = 0; k < n; k++) {
            double tk = t + 273.15;
            double q = currentA * currentA * resistanceOhmPerM(t) + solarWPerM - convectionWm2K * pd * (t - ambientC)
                    - EMISSIVITY * SIGMA * pd * (tk * tk * tk * tk - ta4);
            t += h * q / mc;
        }
        return t;
    }

    /**
     * Стрела провеса пролёта {@code spanM} при температуре T, если при T₀ она была f₀:
     * f(T) = √(f₀² + 3l²/8·α(T − T₀)) (§7.4.2, [сверить] по методике механического расчёта ВЛ).
     */
    public double sagM(double spanM, double sag0M, double t0C, double tC) {
        double f2 = sag0M * sag0M + 3 * spanM * spanM / 8 * material.expansionPerK * (tC - t0C);
        return Math.sqrt(Math.max(0, f2));
    }
}
