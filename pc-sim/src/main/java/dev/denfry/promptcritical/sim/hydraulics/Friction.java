package dev.denfry.promptcritical.sim.hydraulics;

import dev.denfry.promptcritical.sim.thermo.VoidFraction;

/** Трение и двухфазные множители потерь (01 §1.2.2, §1.2.4). Чистые функции. */
public final class Friction {
    /** Конфиг {@code hydraulics.two_phase_multiplier}. */
    public enum TwoPhaseMultiplier { HEM, FRIEDEL }

    public static final double RE_LAMINAR = 2300, RE_TURBULENT = 4000;
    /** Катаная сталь (Moody). */
    public static final double ROUGHNESS_STEEL = 4.5e-5;
    /** Тянутые трубы, оболочки ТВЭЛов из циркалоя. */
    public static final double ROUGHNESS_DRAWN = 1.5e-6;

    private Friction() {}

    /** Хааланд (1983): 1/√f = −1,8·lg[(ε/D/3,7)^1,11 + 6,9/Re]; ≤ 1,5 % к Колбруку. */
    public static double haaland(double re, double relativeRoughness) {
        double t = Math.pow(relativeRoughness / 3.7, 1.11) + 6.9 / re;
        double s = -1.8 * Math.log10(t);
        return 1.0 / (s * s);
    }

    /**
     * Коэффициент трения Дарси: 64/Re при Re &lt; 2300, Хааланд при Re &gt; 4000,
     * в переходной зоне — линейно по Re.
     */
    public static double darcy(double re, double relativeRoughness) {
        if (re <= RE_LAMINAR) return 64.0 / Math.max(re, 1e-12);
        if (re >= RE_TURBULENT) return haaland(re, relativeRoughness);
        double fl = 64.0 / RE_LAMINAR, ft = haaland(RE_TURBULENT, relativeRoughness);
        return fl + (ft - fl) * (re - RE_LAMINAR) / (RE_TURBULENT - RE_LAMINAR);
    }

    /**
     * Множитель φ²_lo по выбранной модели к потерям «вся смесь как жидкость».
     * {@code massFlux} и {@code dh} нужны только Фриделю.
     */
    public static double multiplier(TwoPhaseMultiplier model, double x, double massFlux, double dh,
            double rhoF, double rhoG, double muF, double muG, double sigma) {
        if (x <= 0) return 1;
        return switch (model) {
            case HEM -> homogeneous(Math.min(x, 1), rhoF, rhoG, muF, muG);
            case FRIEDEL -> friedel(Math.min(x, 1), massFlux, dh, rhoF, rhoG, muF, muG, sigma);
        };
    }

    /**
     * Локально-гомогенная модель: φ² = [1 + x(ρ_f/ρ_g − 1)]·[1 + x(μ_f/μ_g − 1)]^−0,25
     * (вязкость смеси по McAdams, трение по Блазиусу).
     */
    public static double homogeneous(double x, double rhoF, double rhoG, double muF, double muG) {
        return (1 + x * (rhoF / rhoG - 1)) * Math.pow(1 + x * (muF / muG - 1), -0.25);
    }

    /** Фридель (1979), φ²_lo; G ограничен снизу 1 кг/(м²·с), иначе Fr и We вырождаются. */
    public static double friedel(double x, double massFlux, double dh, double rhoF, double rhoG,
            double muF, double muG, double sigma) {
        double g = Math.max(Math.abs(massFlux), 1.0);
        double fFo = darcy(g * dh / muF, 0), fGo = darcy(g * dh / muG, 0);
        double e = (1 - x) * (1 - x) + x * x * rhoF * fGo / (rhoG * fFo);
        double f = Math.pow(x, 0.78) * Math.pow(1 - x, 0.224);
        double h = Math.pow(rhoF / rhoG, 0.91) * Math.pow(muG / muF, 0.19) * Math.pow(1 - muG / muF, 0.7);
        double rhoH = 1.0 / (x / rhoG + (1 - x) / rhoF);
        double fr = g * g / (VoidFraction.GRAVITY * dh * rhoH * rhoH);
        double we = g * g * dh / (Math.max(sigma, 1e-6) * rhoH);
        return e + 3.24 * f * h / (Math.pow(fr, 0.045) * Math.pow(we, 0.035));
    }
}
