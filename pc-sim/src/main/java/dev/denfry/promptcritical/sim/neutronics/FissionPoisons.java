package dev.denfry.promptcritical.sim.neutronics;

import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;

/**
 * Отравление: цепочки I-135 → Xe-135 и Pm-149 → Sm-149 (Lamarsh, гл. 7.4; Stacey, гл. 6):
 * <pre>
 * dI/dt  = γ_I·F − λ_I·I
 * dX/dt  = γ_X·F + λ_I·I − (λ_X + σ_X·φ)·X
 * dP/dt  = γ_P·F − λ_P·P
 * dS/dt  = λ_P·P − σ_S·φ·S
 * </pre>
 * При постоянных F и φ на шаге каждая строка — dx/dt = A + B·e^{−μt} − k·x, и шаг интегрируется точно, при любой
 * длине и при k → 0 (самарий после останова).
 *
 * <p>Концентрации — на см³ ячейки (средние по узлу), F — делений в см³ ячейки в секунду, φ — поток в соглашении
 * Уэсткотта (скорость реакции = σ_эфф·φ), σ_эфф передаёт вызывающий: σ₀·g(T)·(1/v-фактор).
 *
 * <p>Данные: σ₀ Xe-135 2,665·10⁶ б, Sm-149 4,051·10⁴ б (ENDF/B-VII.1, захват при 2200 м/с); T½ I-135 6,57 ч, Xe-135
 * 9,14 ч, Pm-149 53,08 ч; выходы на деление U-235 тепловыми: γ_I 0,0639, γ_X 0,00237 (прямой), γ_P 0,0113 (Lamarsh,
 * табл. 7.5).
 */
public final class FissionPoisons {
    public static final double LAMBDA_I = Math.log(2) / (6.57 * 3600), LAMBDA_XE = Math.log(2) / (9.14 * 3600),
            LAMBDA_PM = Math.log(2) / (53.08 * 3600);
    public static final double SIGMA_XE_B = 2.665e6, SIGMA_SM_B = 4.051e4;
    public static final double YIELD_I = 0.0639, YIELD_XE = 0.00237, YIELD_PM = 0.0113;
    private static final double BARN = 1e-24;

    private double iodine, xenon, promethium, samarium;

    /**
     * Шаг {@code dtS} при постоянных скорости делений и потоке.
     *
     * @param fissionRate делений в см³ в секунду
     * @param flux        поток, см⁻²·с⁻¹
     * @param sigmaXeB    эффективное сечение поглощения Xe-135, б
     * @param sigmaSmB    эффективное сечение поглощения Sm-149, б
     */
    public void step(double dtS, double fissionRate, double flux, double sigmaXeB, double sigmaSmB) {
        double f = Math.max(0, fissionRate), phi = Math.max(0, flux);
        double i0 = iodine, p0 = promethium;
        iodine = relax(i0, YIELD_I * f, 0, 0, LAMBDA_I, dtS);
        xenon = relax(xenon, (YIELD_XE + YIELD_I) * f, LAMBDA_I * i0 - YIELD_I * f, LAMBDA_I,
                LAMBDA_XE + sigmaXeB * BARN * phi, dtS);
        promethium = relax(p0, YIELD_PM * f, 0, 0, LAMBDA_PM, dtS);
        samarium = relax(samarium, YIELD_PM * f, LAMBDA_PM * p0 - YIELD_PM * f, LAMBDA_PM, sigmaSmB * BARN * phi, dtS);
    }

    /** Σ_a Xe-135, см⁻¹. */
    public double xenonAbs(double sigmaXeB) {
        return sigmaXeB * BARN * xenon;
    }

    /** Σ_a Sm-149, см⁻¹. */
    public double samariumAbs(double sigmaSmB) {
        return sigmaSmB * BARN * samarium;
    }

    public double iodine() {
        return iodine;
    }

    public double xenon() {
        return xenon;
    }

    public double promethium() {
        return promethium;
    }

    public double samarium() {
        return samarium;
    }

    /** Равновесный ксенон при F и φ, на см³: (γ_I + γ_X)·F/(λ_X + σ_X·φ). */
    public static double equilibriumXenon(double fissionRate, double flux, double sigmaXeB) {
        return (YIELD_I + YIELD_XE) * fissionRate / (LAMBDA_XE + sigmaXeB * BARN * flux);
    }

    /** Равновесный самарий, на см³: γ_P·F/(σ_S·φ); при F = Σ_f·φ это γ_P·Σ_f/σ_S — от мощности не зависит. */
    public static double equilibriumSamarium(double fissionRate, double flux, double sigmaSmB) {
        return YIELD_PM * fissionRate / (sigmaSmB * BARN * flux);
    }

    public void save(StateWriter out) {
        out.putDouble("i135", iodine);
        out.putDouble("xe135", xenon);
        out.putDouble("pm149", promethium);
        out.putDouble("sm149", samarium);
    }

    public void load(StateReader in) {
        iodine = in.optDouble("i135", 0);
        xenon = in.optDouble("xe135", 0);
        promethium = in.optDouble("pm149", 0);
        samarium = in.optDouble("sm149", 0);
    }

    /** x(t) для dx/dt = A + B·e^{−μt} − k·x: x₀e^{−kt} + A·t·φ₁(kt) + B·e^{−μt}·t·φ₁((k − μ)t). */
    static double relax(double x0, double a, double b, double mu, double k, double t) {
        return x0 * Math.exp(-k * t) + a * t * phi1(k * t) + b * Math.exp(-mu * t) * t * phi1((k - mu) * t);
    }

    /** φ₁(z) = (1 − e^{−z})/z, φ₁(0) = 1. */
    private static double phi1(double z) {
        return Math.abs(z) < 1e-8 ? 1 - z / 2 : -Math.expm1(-z) / z;
    }
}
