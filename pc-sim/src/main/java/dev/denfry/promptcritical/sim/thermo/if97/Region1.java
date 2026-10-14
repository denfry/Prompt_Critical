package dev.denfry.promptcritical.sim.thermo.if97;

/** IF97, регион 1 (жидкость): γ(π, τ), π = p/16,53 МПа, τ = 1386 К/T. Таблица 2. */
final class Region1 {
    private static final double P_STAR = 16.53e6;
    private static final double T_STAR = 1386.0;

    private static final int[] I = {
        0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 3, 3, 3, 4, 4, 4, 5, 8, 8,
        21, 23, 29, 30, 31, 32};
    private static final int[] J = {
        -2, -1, 0, 1, 2, 3, 4, 5, -9, -7, -1, 0, 1, 3, -3, 0, 1, 3, 17, -4, 0, 6, -5, -2, 10, -8, -11, -6,
        -29, -31, -38, -39, -40, -41};
    private static final double[] N = {
        0.14632971213167, -0.84548187169114, -0.37563603672040e1, 0.33855169168385e1,
        -0.95791963387872, 0.15772038513228, -0.16616417199501e-1, 0.81214629983568e-3,
        0.28319080123804e-3, -0.60706301565874e-3, -0.18990068218419e-1, -0.32529748770505e-1,
        -0.21841717175414e-1, -0.52838357969930e-4, -0.47184321073267e-3, -0.30001780793026e-3,
        0.47661393906987e-4, -0.44141845330846e-5, -0.72694996297594e-15, -0.31679644845054e-4,
        -0.28270797985312e-5, -0.85205128120103e-9, -0.22425281908000e-5, -0.65171222895601e-6,
        -0.14341729937924e-12, -0.40516996860117e-6, -0.12734301741641e-8, -0.17424871230634e-9,
        -0.68762131295531e-18, 0.14478307828521e-19, 0.26335781662795e-22, -0.11947622640071e-22,
        0.18228094581404e-23, -0.93537087292458e-25};

    private Region1() {}

    static Gibbs gibbs(double p, double t) {
        double pi = p / P_STAR;
        double tau = T_STAR / t;
        double a = 7.1 - pi;
        double b = tau - 1.222;
        double g = 0, gp = 0, gpp = 0, gt = 0, gtt = 0, gpt = 0;
        for (int k = 0; k < N.length; k++) {
            int i = I[k], j = J[k];
            double n = N[k];
            double ai = Math.pow(a, i), bj = Math.pow(b, j);
            double ai1 = Math.pow(a, i - 1), bj1 = Math.pow(b, j - 1);
            g += n * ai * bj;
            gp -= n * i * ai1 * bj;
            gpp += n * i * (i - 1) * Math.pow(a, i - 2) * bj;
            gt += n * ai * j * bj1;
            gtt += n * ai * j * (j - 1) * Math.pow(b, j - 2);
            gpt -= n * i * ai1 * j * bj1;
        }
        return new Gibbs(pi, tau, g, gp, gpp, gt, gtt, gpt);
    }
}
