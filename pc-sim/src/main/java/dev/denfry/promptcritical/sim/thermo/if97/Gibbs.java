package dev.denfry.promptcritical.sim.thermo.if97;

/**
 * Безразмерная энергия Гиббса γ(π, τ) регионов 1, 2, 5 и её производные;
 * общий перевод в свойства — IF97, таблицы 3, 12, 39.
 */
record Gibbs(double pi, double tau, double g, double gp, double gpp, double gt, double gtt, double gpt) {

    /** Удельный объём, м³/кг: v = R T π γ_π / p. */
    double v(double p, double t) {
        return If97.R * t * pi * gp / p;
    }

    double h(double t) {
        return If97.R * t * tau * gt;
    }

    double u(double t) {
        return If97.R * t * (tau * gt - pi * gp);
    }

    double s() {
        return If97.R * (tau * gt - g);
    }

    double cp() {
        return -If97.R * tau * tau * gtt;
    }

    double cv() {
        double a = gp - tau * gpt;
        return If97.R * (-tau * tau * gtt + a * a / gpp);
    }

    double w(double t) {
        double a = gp - tau * gpt;
        return Math.sqrt(If97.R * t * gp * gp / (a * a / (tau * tau * gtt) - gpp));
    }

    /** (∂ρ/∂p)_T = −ρ² (∂v/∂p)_T, где (∂v/∂p)_T = R T π² γ_ππ / p². */
    double drhodpT(double p, double t) {
        double v = v(p, t);
        double dvdp = If97.R * t * pi * pi * gpp / (p * p);
        return -dvdp / (v * v);
    }
}
