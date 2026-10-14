package dev.denfry.promptcritical.sim.neutronics;

/**
 * Константы узла (01 §1.6.2–1.6.4, §1.13.3). Сечения — гомогенизированные по узлу, см⁻¹;
 * площади — см². {@code nuSigmaF} — эффективное, νΣ_f = k∞Σ_a (резонансный захват и деление
 * на быстрых свёрнуты в одну группу); {@code sigmaF} масштабировано так же — для формы мощности.
 * Производные k∞ — на К, на кг/м³, на ppm.
 *
 * <p>Сверх сигнатуры GDD: {@code thermalDiffusion} (D тепловой группы, для экстраполированной
 * добавки голой зоны) и {@code slowingDownTime} (t_sd для Λ, §1.5.3).
 */
public record SixFactor(double eta, double f, double p, double epsilon, double kInf, double migrationArea,
        double diffusionLength2, double age, double sigmaA, double nuSigmaF, double sigmaF, double dkdTf,
        double dkdRhoC, double dkdTm, double dkdBoron, double thermalDiffusion, double slowingDownTime) {

    /** D 1,5-групповой нодальной задачи: D = M²Σ_a (§1.6.5). */
    public double diffusionCoefficient() {
        return migrationArea * sigmaA;
    }

    /**
     * Коэффициент реактивности критического реактора dρ/dX = d ln k∞/dX = (dk∞/dX)/k∞ (при k_eff = 1
     * dρ = dk_eff/k_eff, утечка при этом постоянна) — для HMI и квазистатики §1.6.7.
     */
    public double reactivityCoefficient(double dkdx) {
        return dkdx / kInf;
    }

    /** Узел без топлива (отражатель, поглотитель, вода). */
    public boolean multiplying() {
        return nuSigmaF > 0;
    }
}
