package dev.denfry.promptcritical.sim.drilling;

/**
 * Механическая скорость проходки шарошечным долотом по модели Бургойна–Янга (Bourgoyne, Young, «A Multiple Regression
 * Approach to Optimal Drilling and Abnormal Pressure Detection», SPE 4238, 1974; Bourgoyne et al., «Applied
 * Drilling Engineering», SPE Textbook Vol. 2, 1986, §5.10):
 *
 * <pre>
 * dD/dt = f₁·f₂·…·f₈, фут/ч; f₁…f₄ = exp(2,303·aⱼxⱼ), f₅, f₆, f₈ = exp(aⱼxⱼ), f₇ = exp(a₇x₇):
 *   x₂ = 10 000 − D                       уплотнение с глубиной
 *   x₃ = D^0,69·(gₚ − 9,0)                 недоуплотнение (АВПД)
 *   x₄ = D·(gₚ − ρ_c)                      дифференциальное давление на забое
 *   x₅ = ln[((W/d) − (W/d)ₜ)/(4 − (W/d)ₜ)]  нагрузка на долото
 *   x₆ = ln(N/60)                          частота вращения
 *   x₇ = −h                                износ вооружения
 *   x₈ = ln(Fⱼ/1000)                       гидромониторный эффект
 * </pre>
 *
 * D — глубина, фут; gₚ — градиент порового давления и ρ_c — эквивалентная циркуляционная плотность, фунт/галлон;
 * W/d — нагрузка на дюйм диаметра, тыс. фунтов/дюйм; N — об/мин; h — доля износа зубьев; Fⱼ — сила удара струй,
 * фунты. Внутри модель работает в промысловых единицах книги, снаружи — СИ. При нормирующих условиях (D = 10 000 фут,
 * gₚ = ρ_c = 9 фунт/галлон, W/d = 4, N = 60, h = 0, Fⱼ = 1000) скорость равна 10^{a₁} фут/ч.
 *
 * <p>Коэффициенты подбираются регрессией по данным скважин; книга даёт диапазоны (табл. 5.6). Здесь — значения из
 * середины диапазонов для мягких пород: a₁ различает глину и песчаник, остальные общие.
 */
public final class BourgoyneYoung {
    private static final double FT = 0.3048, PPG = 119.826_427, LBF = 4.448_222, INCH = 0.0254;

    /**
     * Коэффициенты модели; {@code thresholdWd} — пороговая нагрузка (W/d)ₜ, тыс. фунтов/дюйм.
     */
    public record Coefficients(double a1, double a2, double a3, double a4, double a5, double a6, double a7, double a8,
            double thresholdWd) {
    }

    /** Глина: 10^1,45 ≈ 28 фут/ч (8,6 м/ч) при нормирующих условиях. */
    public static final Coefficients SHALE = new Coefficients(1.45, 9.0e-5, 1.0e-4, 4.0e-5, 1.0, 0.6, 0.5, 0.4, 0.2);
    /** Песчаник-коллектор: 10^1,70 ≈ 50 фут/ч — вскрытие пласта видно как ускорение проходки. */
    public static final Coefficients SANDSTONE = new Coefficients(1.70, 9.0e-5, 1.0e-4, 4.0e-5, 1.0, 0.6, 0.5, 0.4, 0.2);
    /** Выше этой удельной нагрузки долото не углубляется быстрее (зашламование, «flounder point»), тыс. фунтов/дюйм. */
    public static final double FOUNDER_WD = 8;

    private BourgoyneYoung() {
    }

    /**
     * Скорость проходки, м/с.
     *
     * @param depthM          глубина забоя, м
     * @param poreKgM3        эквивалентная плотность порового давления, кг/м³
     * @param ecdKgM3         эквивалентная циркуляционная плотность на забое, кг/м³
     * @param wobN            нагрузка на долото, Н
     * @param bitDiameterM    диаметр долота, м
     * @param rpm             частота вращения, об/мин
     * @param toothWear       износ вооружения, доли 0…1
     * @param jetImpactN      сила удара струй, Н
     */
    public static double ropMs(Coefficients c, double depthM, double poreKgM3, double ecdKgM3, double wobN,
            double bitDiameterM, double rpm, double toothWear, double jetImpactN) {
        double wd = wobN / LBF / 1000 / (bitDiameterM / INCH);
        if (wd <= c.thresholdWd() || rpm <= 0 || jetImpactN <= 0) return 0;
        double d = depthM / FT, gp = poreKgM3 / PPG, rho = ecdKgM3 / PPG;
        double exponent = c.a1() + c.a2() * (10_000 - d) + c.a3() * Math.pow(d, 0.69) * (gp - 9.0) + c.a4() * d * (gp - rho);
        double ftPerH = Math.exp(2.303 * exponent) * Math.exp(-c.a7() * toothWear)
                * Math.pow((Math.min(wd, FOUNDER_WD) - c.thresholdWd()) / (4 - c.thresholdWd()), c.a5())
                * Math.pow(rpm / 60, c.a6())
                * Math.pow(jetImpactN / LBF / 1000, c.a8());
        return ftPerH * FT / 3600;
    }
}
