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
    /**
     * Конвекция от диаметра: h ∝ d^−0,48 (вынужденная конвекция IEEE Std 738, Nu ∝ Re^0,52); заданный h_c — для
     * диаметра провода АС-120.
     */
    static final double CONVECTION_D_EXPONENT = -0.48, CONVECTION_REF_D_M = STRANDING * Math.sqrt(4 * 120e-6 / Math.PI);
    /** Шаг явного интегрирования нагрева при длинном SLOW-шаге, с (τ провода — минуты). */
    private static final double MAX_THERMAL_STEP_S = 10;

    public ConductorSpec {
        Objects.requireNonNull(material, "material");
        if (!(crossSectionMm2 > 0 && r20OhmPerKm > 0 && ampacityA > 0 && massKgPerM > 0 && rtsN > 0))
            throw new IllegalArgumentException("conductor data");
    }

    // Каталог §7.4.1; ток — ПУЭ табл. 1.3.29, вне помещений, как для ВЛ; масса и разрывное усилие — [сверить] по
    // ГОСТ 839-2019.
    public static ConductorSpec m35() {
        return new ConductorSpec(Conductor.COPPER, 35, 0.52, 223, 0.311, 13.6e3);
    }

    public static ConductorSpec m70() {
        return new ConductorSpec(Conductor.COPPER, 70, 0.27, 337, 0.618, 26.8e3);
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

    /**
     * h_c провода АС-120 по ветру — IEEE Std 738-2012, формулы 3a, 3b и 5a (СИ): вынужденная конвекция
     * q = K_угла·[1,01 + 1,35·Re^0,52]·k_f·ΔT, при больших Re — 0,754·K_угла·Re^0,6·k_f·ΔT, естественная
     * 3,645·ρ_f^0,5·D^0,75·ΔT^1,25; берётся наибольшая, h_c = q/(πD·ΔT). Свойства воздуха плёнки — формулы 13a–15a
     * того же стандарта. Ветер под 45° к проводу (K_угла = 0,855) и перегрев 40 К для естественной конвекции
     * [допущение: направление пролётов не учитывается].
     *
     * @param windMs      скорость ветра на высоте провода, м/с
     * @param airTempC    температура воздуха, °C
     * @param elevationM  высота над уровнем моря, м
     */
    public static double convectionWm2K(double windMs, double airTempC, double elevationM) {
        double d = CONVECTION_REF_D_M, dT = 40, tFilm = airTempC + dT / 2;
        double mu = 1.458e-6 * Math.pow(tFilm + 273, 1.5) / (tFilm + 383.4);
        double rho = (1.293 - 1.525e-4 * elevationM + 6.379e-9 * elevationM * elevationM) / (1 + 0.00367 * tFilm);
        double k = 2.424e-2 + 7.477e-5 * tFilm - 4.407e-9 * tFilm * tFilm;
        double re = d * rho * Math.max(0, windMs) / mu, kAngle = 0.855;
        double forced = Math.max(kAngle * (1.01 + 1.35 * Math.pow(re, 0.52)), kAngle * 0.754 * Math.pow(re, 0.6)) * k * dT;
        double natural = 3.645 * Math.sqrt(rho) * Math.pow(d, 0.75) * Math.pow(dT, 1.25);
        return Math.max(forced, natural) / (Math.PI * d * dT);
    }

    /** Нагрев солнцем провода АС-120, Вт/м: поглощение 0,5 × радиация × диаметр (IEEE Std 738, формула 8). */
    public static double solarWPerM(double irradianceWm2) {
        return 0.5 * Math.max(0, irradianceWm2) * CONVECTION_REF_D_M;
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
     * @param convectionWm2K h_c провода АС-120 — 5–15 Вт/(м²·К) при ветре 0–2 м/с; у других ×(d/d₁₂₀)^−0,48
     * @param solarWPerM     q_s — 0–15 Вт/м днём
     */
    public double stepTemperatureC(double tempC, double currentA, double ambientC, double convectionWm2K,
            double solarWPerM, double dtS) {
        double mc = massKgPerM * material.specificHeat, pd = Math.PI * diameterM();
        double hc = convectionWm2K * Math.pow(diameterM() / CONVECTION_REF_D_M, CONVECTION_D_EXPONENT);
        double ta = ambientC + 273.15, ta4 = ta * ta * ta * ta;
        int n = (int) Math.ceil(dtS / MAX_THERMAL_STEP_S);
        double h = dtS / n, t = tempC;
        for (int k = 0; k < n; k++) {
            double tk = t + 273.15;
            double q = currentA * currentA * resistanceOhmPerM(t) + solarWPerM - hc * pd * (t - ambientC)
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
