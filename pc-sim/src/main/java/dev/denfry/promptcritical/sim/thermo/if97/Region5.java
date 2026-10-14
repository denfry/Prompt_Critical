package dev.denfry.promptcritical.sim.thermo.if97;

/**
 * IF97, регион 5 (высокотемпературный пар, 1073,15–2273,15 К, p ≤ 50 МПа):
 * γ = γ° + γʳ, π = p/1 МПа, τ = 1000 К/T. Таблицы 37, 38 (ревизия 2007).
 */
final class Region5 {
    private static final double P_STAR = 1.0e6;
    private static final double T_STAR = 1000.0;

    private static final int[] J0 = {0, 1, -3, -2, -1, 2};
    private static final double[] N0 = {
        -0.13179983674201e2, 0.68540841634434e1, -0.24805148933466e-1, 0.36901534980333,
        -0.31161318213925e1, -0.32961626538917};

    private static final int[] I = {1, 1, 1, 2, 2, 3};
    private static final int[] J = {1, 2, 3, 3, 9, 7};
    private static final double[] N = {
        0.15736404855259e-2, 0.90153761673944e-3, -0.50270077677648e-2, 0.22440037409485e-5,
        -0.41163275453471e-5, 0.37919454822955e-7};

    private Region5() {}

    static Gibbs gibbs(double p, double t) {
        double pi = p / P_STAR;
        double tau = T_STAR / t;
        double g = Math.log(pi), gp = 1.0 / pi, gpp = -1.0 / (pi * pi), gt = 0, gtt = 0, gpt = 0;
        for (int k = 0; k < N0.length; k++) {
            int j = J0[k];
            g += N0[k] * Math.pow(tau, j);
            gt += N0[k] * j * Math.pow(tau, j - 1);
            gtt += N0[k] * j * (j - 1) * Math.pow(tau, j - 2);
        }
        for (int k = 0; k < N.length; k++) {
            int i = I[k], j = J[k];
            double n = N[k];
            double pii = Math.pow(pi, i), pii1 = Math.pow(pi, i - 1);
            double tj = Math.pow(tau, j), tj1 = Math.pow(tau, j - 1);
            g += n * pii * tj;
            gp += n * i * pii1 * tj;
            gpp += n * i * (i - 1) * Math.pow(pi, i - 2) * tj;
            gt += n * pii * j * tj1;
            gtt += n * pii * j * (j - 1) * Math.pow(tau, j - 2);
            gpt += n * i * pii1 * j * tj1;
        }
        return new Gibbs(pi, tau, g, gp, gpp, gt, gtt, gpt);
    }
}
