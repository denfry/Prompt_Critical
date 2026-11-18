package dev.denfry.promptcritical.sim.radiation;

/**
 * Радиометр загрязнённости с плоским зондом α/β (двойной сцинтиллятор ZnS(Ag) на пластике — обычная схема зондов
 * загрязнённости): прямое измерение у поверхности. Модель ISO 7503-1:2016, п. 7:
 * <pre>
 *   R = A_s · W · ε_i · ε_s + R_B,   A_s = (R − R_B) / (W · ε_i · ε_s),
 * </pre>
 * где W — площадь окна зонда, ε_i — эффективность прибора к частицам, вылетевшим с поверхности (2π), ε_s —
 * эффективность выхода ({@link ContaminationLimits}). Прямое измерение видит и снимаемое, и фиксированное загрязнение.
 * Предел обнаружения — по Карри для парного измерения с фоном (ISO 11929, приближение):
 * L_D = 2,71 + 4,65·√(R_B·t) отсчётов за время t.
 *
 * @param areaCm2   окно зонда, см²
 * @param effAlpha  ε_i по α
 * @param effBeta   ε_i по β
 * @param bgAlpha   фон α-канала, имп/с
 * @param bgBeta    фон β-канала, имп/с
 * @param countS    время счёта, с
 */
public record ContaminationProbe(double areaCm2, double effAlpha, double effBeta, double bgAlpha, double bgBeta, double countS) {
    /**
     * Типовой зонд: окно 100 см², ε_i 0,35 по α (Pu-239) и 0,30 по β (Cs-137/Sr-90), фон 0,05 и 3 имп/с при фоне γ около
     * 0,1 мкЗв/ч, счёт 10 с — паспортные порядки таких зондов.
     */
    public static final ContaminationProbe STANDARD = new ContaminationProbe(100, 0.35, 0.30, 0.05, 3, 10);

    /** Скорость счёта α-канала над поверхностью, имп/с (среднее, без пуассоновского разброса). */
    public double alphaCps(SurfaceContamination c) {
        return c.alpha() * areaCm2 * effAlpha * ContaminationLimits.EPS_S_ALPHA + bgAlpha;
    }

    public double betaCps(SurfaceContamination c) {
        return c.beta() * areaCm2 * effBeta * ContaminationLimits.EPS_S_BETA + bgBeta;
    }

    /** Активность по α из показания, Бк/см² (за вычетом фона; меньше нуля не бывает). */
    public double alphaBqCm2(double cps) {
        return Math.max(0, cps - bgAlpha) / (areaCm2 * effAlpha * ContaminationLimits.EPS_S_ALPHA);
    }

    public double betaBqCm2(double cps) {
        return Math.max(0, cps - bgBeta) / (areaCm2 * effBeta * ContaminationLimits.EPS_S_BETA);
    }

    /** Минимально детектируемая активность по α, Бк/см². */
    public double alphaMda() {
        return mda(bgAlpha, effAlpha, ContaminationLimits.EPS_S_ALPHA);
    }

    public double betaMda() {
        return mda(bgBeta, effBeta, ContaminationLimits.EPS_S_BETA);
    }

    private double mda(double bg, double eff, double epsS) {
        double ldCounts = 2.71 + 4.65 * Math.sqrt(bg * countS);
        return ldCounts / countS / (areaCm2 * eff * epsS);
    }
}
