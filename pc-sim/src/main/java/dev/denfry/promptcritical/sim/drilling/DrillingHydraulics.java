package dev.denfry.promptcritical.sim.drilling;

/**
 * Гидравлика промывки бинхамовским раствором (Bourgoyne et al., «Applied Drilling Engineering», §4.6–4.8), в СИ.
 *
 * <p>Ламинарный поток: в трубе dp/dL = 32μₚv/d² + 16τ₀/(3d), в кольцевом пространстве (щелевое приближение)
 * dp/dL = 48μₚv/(d₂ − d₁)² + 6τ₀/(d₂ − d₁) — это формулы книги 4.62 и 4.66 (в промысловых единицах
 * μₚv/(1500d²) + τ₀/(225d) и μₚv/(1000Δd²) + τ₀/(200Δd)), переписанные в СИ. Режим — по числу Рейнольдса
 * с кажущейся вязкостью μₐ = μₚ + τ₀d/(6v) (труба) и μₐ = μₚ + τ₀Δd/(8v) (кольцо), гидравлический диаметр кольца
 * 0,816Δd; при Re &gt; 2100 — турбулентный поток с коэффициентом Фаннинга Блазиуса f = 0,0791·Re^−0,25 по
 * пластической вязкости, dp/dL = 2fρv²/d (книга, 4.75–4.80).
 *
 * <p>Долото: перепад на насадках Δp = ρq²/(2C_d²A²) при C_d = 0,95, сила удара струй Fⱼ = ρq·vₙ = ρq²/A
 * (книга, 4.31–4.37).
 */
public final class DrillingHydraulics {
    public static final double G = 9.806_65;
    public static final double NOZZLE_CD = 0.95;
    public static final double CRITICAL_RE = 2100;

    private DrillingHydraulics() {
    }

    /** Градиент потерь на трение в трубе, Па/м. */
    public static double pipeGradientPaM(double flowM3s, double innerDiameterM, double densityKgM3, double pvPaS, double ypPa) {
        if (flowM3s <= 0) return 0;
        double v = flowM3s / (Math.PI / 4 * innerDiameterM * innerDiameterM);
        double mua = pvPaS + ypPa * innerDiameterM / (6 * v);
        double re = densityKgM3 * v * innerDiameterM / mua;
        if (re <= CRITICAL_RE) return 32 * pvPaS * v / (innerDiameterM * innerDiameterM) + 16 * ypPa / (3 * innerDiameterM);
        double f = 0.0791 * Math.pow(densityKgM3 * v * innerDiameterM / pvPaS, -0.25);
        return 2 * f * densityKgM3 * v * v / innerDiameterM;
    }

    /** Градиент потерь на трение в кольцевом пространстве, Па/м. */
    public static double annulusGradientPaM(double flowM3s, double holeDiameterM, double pipeOuterDiameterM,
            double densityKgM3, double pvPaS, double ypPa) {
        if (flowM3s <= 0) return 0;
        double gap = holeDiameterM - pipeOuterDiameterM;
        double area = Math.PI / 4 * (holeDiameterM * holeDiameterM - pipeOuterDiameterM * pipeOuterDiameterM);
        double v = flowM3s / area;
        double mua = pvPaS + ypPa * gap / (8 * v);
        double dh = 0.816 * gap;
        double re = densityKgM3 * v * dh / mua;
        if (re <= CRITICAL_RE) return 48 * pvPaS * v / (gap * gap) + 6 * ypPa / gap;
        double f = 0.0791 * Math.pow(densityKgM3 * v * dh / pvPaS, -0.25);
        return 2 * f * densityKgM3 * v * v / dh;
    }

    /** Перепад давления на насадках долота, Па. */
    public static double bitPressureDropPa(double flowM3s, double densityKgM3, double nozzleAreaM2) {
        if (flowM3s <= 0) return 0;
        return densityKgM3 * flowM3s * flowM3s / (2 * NOZZLE_CD * NOZZLE_CD * nozzleAreaM2 * nozzleAreaM2);
    }

    /** Сила удара струй о забой, Н. */
    public static double jetImpactN(double flowM3s, double densityKgM3, double nozzleAreaM2) {
        return flowM3s <= 0 ? 0 : densityKgM3 * flowM3s * flowM3s / nozzleAreaM2;
    }

    /** Эквивалентная циркуляционная плотность по давлению на забое, кг/м³. */
    public static double equivalentDensityKgM3(double pressurePa, double depthM) {
        return depthM <= 0 ? 0 : pressurePa / (G * depthM);
    }
}
