package dev.denfry.promptcritical.sim.thermo.if97;

/**
 * IF97, регион 3 (окрестность критической точки): безразмерная энергия
 * Гельмгольца φ(δ, τ), δ = ρ/322 кг/м³, τ = 647,096 К/T. Таблицы 30, 31.
 */
final class Region3 {
    private static final int[] I = {
        0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5,
        6, 6, 6, 7, 8, 9, 9, 10, 10, 11};
    private static final int[] J = {
        0, 0, 1, 2, 7, 10, 12, 23, 2, 6, 15, 17, 0, 2, 6, 7, 22, 26, 0, 2, 4, 16, 26, 0, 2, 4, 26, 1, 3, 26,
        0, 2, 26, 2, 26, 2, 26, 0, 1, 26};
    private static final double[] N = {
        0.10658070028513e1, -0.15732845290239e2, 0.20944396974307e2, -0.76867707878716e1,
        0.26185947787954e1, -0.28080781148620e1, 0.12053369696517e1, -0.84566812812502e-2,
        -0.12654315477714e1, -0.11524407806681e1, 0.88521043984318, -0.64207765181607,
        0.38493460186671, -0.85214708824206, 0.48972281541877e1, -0.30502617256965e1,
        0.39420536879154e-1, 0.12558408424308, -0.27999329698710, 0.13899799569460e1,
        -0.20189915023570e1, -0.82147637173963e-2, -0.47596035734923, 0.43984074473500e-1,
        -0.44476435428739, 0.90572070719733, 0.70522450087967, 0.10770512626332,
        -0.32913623258954, -0.50871062041158, -0.22175400873096e-1, 0.94260751665092e-1,
        0.16436278447961, -0.13503372241348e-1, -0.14834345352472e-1, 0.57922953628084e-3,
        0.32308904703711e-2, 0.80964802996215e-4, -0.16557679795037e-3, -0.44923899061815e-4};

    private Region3() {}

    /** Производные φ: {f, fd, fdd, ft, ftt, fdt} в переменных (δ, τ). */
    record Helmholtz(double delta, double tau, double f, double fd, double fdd, double ft, double ftt, double fdt) {

        double p(double rho, double t) {
            return rho * If97.R * t * delta * fd;
        }

        double u(double t) {
            return If97.R * t * tau * ft;
        }

        double h(double t) {
            return If97.R * t * (tau * ft + delta * fd);
        }

        double s() {
            return If97.R * (tau * ft - f);
        }

        double cv() {
            return -If97.R * tau * tau * ftt;
        }

        double cp() {
            double a = delta * fd - delta * tau * fdt;
            return If97.R * (-tau * tau * ftt + a * a / (2 * delta * fd + delta * delta * fdd));
        }

        double w(double t) {
            double a = delta * fd - delta * tau * fdt;
            return Math.sqrt(If97.R * t * (2 * delta * fd + delta * delta * fdd - a * a / (tau * tau * ftt)));
        }

        /** (∂h/∂ρ)_T. */
        double dhdrhoT(double t) {
            return If97.R * t * (tau * fdt + fd + delta * fdd) / If97.RHO_C;
        }

        /** (∂h/∂T)_ρ. */
        double dhdTrho() {
            return If97.R * (-tau * tau * ftt + delta * fd - delta * tau * fdt);
        }

        /** (∂p/∂T)_ρ. */
        double dpdTrho(double rho) {
            return rho * If97.R * delta * (fd - tau * fdt);
        }

        /** (∂p/∂ρ)_T. */
        double dpdrhoT(double t) {
            return If97.R * t * (2 * delta * fd + delta * delta * fdd);
        }
    }

    static Helmholtz helmholtz(double rho, double t) {
        double delta = rho / If97.RHO_C;
        double tau = If97.T_C / t;
        double f = N[0] * Math.log(delta);
        double fd = N[0] / delta;
        double fdd = -N[0] / (delta * delta);
        double ft = 0, ftt = 0, fdt = 0;
        for (int k = 1; k < N.length; k++) {
            int i = I[k], j = J[k];
            double n = N[k];
            double di = Math.pow(delta, i), di1 = Math.pow(delta, i - 1);
            double tj = Math.pow(tau, j), tj1 = Math.pow(tau, j - 1);
            f += n * di * tj;
            fd += n * i * di1 * tj;
            fdd += n * i * (i - 1) * Math.pow(delta, i - 2) * tj;
            ft += n * di * j * tj1;
            ftt += n * di * j * (j - 1) * Math.pow(tau, j - 2);
            fdt += n * i * di1 * j * tj1;
        }
        return new Helmholtz(delta, tau, f, fd, fdd, ft, ftt, fdt);
    }
}
