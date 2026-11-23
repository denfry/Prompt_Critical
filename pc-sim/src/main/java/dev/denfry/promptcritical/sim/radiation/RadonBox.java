package dev.denfry.promptcritical.sim.radiation;

/**
 * Радоновый бокс: накопление Rn-222 в замкнутом объёме воздуха (04b §4b.13.3; 02 §2.1.6).
 * <p>Баланс: dC/dt = S/V − (λ + Q/V)·C, где S — источник (Бк/с), V — объём (м³), Q — вентиляция (м³/с),
 * λ — постоянная распада радона. Шаг решается точно, поэтому устойчив при любом dt.
 * Мощность дозы — в мкЗв/ч.
 */
public final class RadonBox {
    /** Постоянная распада Rn-222, с⁻¹ (T½ = 3,82 сут; 02 §2.1.6). */
    public static final double LAMBDA_RN = 2.098e-6;

    /** Доля эманирования ε руды (02 §2.1.6). */
    private static final double EMANATION = 0.2;
    /** Плотность породы ρ_b, кг/м³ (02 §2.1.6). */
    private static final double BULK_DENSITY = 2600;
    /** Эффективный коэффициент диффузии радона в породе D_e, м²/с (02 §2.1.6). */
    private static final double DIFFUSION = 2e-6;
    /** Дозовый коэффициент: 9 нЗв на Бк·ч·м⁻³ = 9e-3 мкЗв/ч на Бк/м³ при F = 1 (04b §4b.13.3). */
    private static final double DOSE_USV_H_PER_BQ_M3 = 9e-3;

    /** Поверхность-источник, удельный поток радона Бк/(м²·с) (04b §4b.13.3). */
    public enum Surface {
        SOIL(0.016), GRANITE(0.01), NONE(0);

        public final double bqM2S;

        Surface(double bqM2S) {
            this.bqM2S = bqM2S;
        }
    }

    /** Обстановка и коэффициент равновесия F радона с дочерними (04b §4b.13.3). */
    public enum Setting {
        BUILDING(0.4), MINE_VENTILATED(0.3), MINE_STILL(0.6);

        public final double equilibrium;

        Setting(double equilibrium) {
            this.equilibrium = equilibrium;
        }
    }

    private RadonBox() {
    }

    /**
     * Поток радона с поверхности руды, Бк/(м²·с): J = ε·ρ_b·C_Ra·√(λ·D_e) (02 §2.1.6).
     * Руда с 1 % U даёт ≈ 112.
     *
     * @param uraniumBqKg удельная активность радия-226 (в равновесии с ураном), Бк/кг
     */
    public static double oreExhalation(double uraniumBqKg) {
        requireFinite(uraniumBqKg, "uraniumBqKg");
        return EMANATION * BULK_DENSITY * uraniumBqKg * Math.sqrt(LAMBDA_RN * DIFFUSION);
    }

    /**
     * Точный шаг: C(t) = C∞ + (C0 − C∞)·e^(−k·dt), k = λ + Q/V, C∞ = (S/V)/k.
     *
     * @param cBqM3   текущая концентрация, Бк/м³
     * @param sourceBqS источник, Бк/с
     * @param volumeM3 объём, м³ (&gt; 0)
     * @param ventM3S вентиляция, м³/с (≥ 0)
     * @param dtS     шаг, с (≥ 0)
     */
    public static double step(double cBqM3, double sourceBqS, double volumeM3, double ventM3S, double dtS) {
        requireFinite(cBqM3, "cBqM3");
        requireFinite(dtS, "dtS");
        if (dtS < 0) {
            throw new IllegalArgumentException("dtS must be >= 0: " + dtS);
        }
        double cInf = steady(sourceBqS, volumeM3, ventM3S);
        if (dtS == 0) return cBqM3;
        double k = LAMBDA_RN + ventM3S / volumeM3;
        return cInf + (cBqM3 - cInf) * Math.exp(-k * dtS);
    }

    /** Стационарная концентрация, Бк/м³: C∞ = S/(λ·V + Q). */
    public static double steady(double sourceBqS, double volumeM3, double ventM3S) {
        requireFinite(sourceBqS, "sourceBqS");
        requireFinite(volumeM3, "volumeM3");
        requireFinite(ventM3S, "ventM3S");
        if (volumeM3 <= 0) {
            throw new IllegalArgumentException("volumeM3 must be > 0: " + volumeM3);
        }
        if (ventM3S < 0) {
            throw new IllegalArgumentException("ventM3S must be >= 0: " + ventM3S);
        }
        return sourceBqS / (LAMBDA_RN * volumeM3 + ventM3S);
    }

    /** Мощность эффективной дозы, мкЗв/ч: C·F·9e-3 (04b §4b.13.3). */
    public static double doseUSvH(double cBqM3, Setting setting) {
        requireFinite(cBqM3, "cBqM3");
        return cBqM3 * setting.equilibrium * DOSE_USV_H_PER_BQ_M3;
    }

    private static void requireFinite(double v, String name) {
        if (!Double.isFinite(v)) {
            throw new IllegalArgumentException(name + " must be finite: " + v);
        }
    }
}
