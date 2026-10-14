package dev.denfry.promptcritical.sim.thermo.if97;

/**
 * Транспортные свойства для промышленного применения с IF97 (01 §1.3.1):
 * вязкость — IAPWS 2008 (μ₂ = 1), теплопроводность — IAPWS 2011 с
 * критическим усилением, поверхностное натяжение — IAPWS 2014.
 */
public final class Transport {
    private static final double[] H0 = {1.67752, 2.20462, 0.6366564, -0.241605};
    /** H1[i][j]: i — степень (1/T̄ − 1), j — степень (ρ̄ − 1). */
    private static final double[][] H1 = {
        {5.20094e-1, 2.22531e-1, -2.81378e-1, 1.61913e-1, -3.25372e-2, 0, 0},
        {8.50895e-2, 9.99115e-1, -9.06851e-1, 2.57399e-1, 0, 0, 0},
        {-1.08374, 1.88797, -7.72479e-1, 0, 0, 0, 0},
        {-2.89555e-1, 1.26613, -4.89837e-1, 0, 6.98452e-2, 0, -4.35673e-3},
        {0, 0, -2.57040e-1, 0, 0, 8.72102e-3, 0},
        {0, 1.20573e-1, 0, 0, 0, 0, -5.93264e-4}};

    private static final double[] L0 = {2.443221e-3, 1.323095e-2, 6.770357e-3, -3.454586e-3, 4.096266e-4};
    private static final double[][] L1 = {
        {1.60397357, -0.646013523, 0.111443906, 0.102997357, -0.0504123634, 0.00609859258},
        {2.33771842, -2.78843778, 1.53616167, -0.463045512, 0.0832827019, -0.00719201245},
        {2.19650529, -4.54580785, 3.55777244, -1.40944978, 0.275418278, -0.0205938816},
        {-1.21051378, 1.60812989, -0.621178141, 0.0716373224, 0, 0},
        {-2.7203370, 4.57586331, -3.18369245, 1.1168348, -0.19268305, 0.012913842}};

    /** Коэффициенты 1/ζ(T̄_R, ρ̄) для промышленного применения (IAPWS 2011, табл. 6). */
    private static final double[] RHO_BOUNDS = {0.310559006, 0.776397516, 1.242236025, 1.863354037};
    private static final double[][] A_ZETA = {
        {6.53786807199516, -5.61149954923348, 3.39624167361325, -2.27492629730878, 10.2631854662709, 1.97815050331519},
        {6.52717759281799, -6.30816983387575, 8.08379285492595, -9.82240510197603, 12.1358413791395, -5.54349664571295},
        {5.35500529896124, -3.96415689925446, 8.91990208918795, -12.0338729505790, 9.19494865194302, -2.16866274479712},
        {1.55225959906681, 0.464621290821181, 8.93237374861479, -11.0321960061126, 6.16780999933360, -0.965458722086812},
        {1.11999926419994, 0.595748562571649, 9.88952565078920, -10.3255051147040, 4.66861294457414, -0.503243546373828}};

    /** Газовая постоянная в формуле критического усиления IAPWS 2011, Дж/(кг·К). */
    private static final double R_2011 = 461.51805;

    private Transport() {}

    /** Динамическая вязкость, Па·с (IAPWS 2008, без критического усиления). */
    public static double viscosity(double rho, double t) {
        double tb = t / If97.T_C;
        double rb = rho / If97.RHO_C;
        double s0 = 0;
        for (int i = 0; i < H0.length; i++) s0 += H0[i] / Math.pow(tb, i);
        double mu0 = 100.0 * Math.sqrt(tb) / s0;
        double a = 1.0 / tb - 1.0, b = rb - 1.0;
        double s1 = 0, ai = 1.0;
        for (double[] row : H1) {
            double bj = 1.0, inner = 0;
            for (double h : row) {
                inner += h * bj;
                bj *= b;
            }
            s1 += ai * inner;
            ai *= a;
        }
        return 1e-6 * mu0 * Math.exp(rb * s1);
    }

    /**
     * Теплопроводность, Вт/(м·К) (IAPWS 2011). Критическое усиление λ₂
     * требует cp, cv, (∂ρ/∂p)_T и вязкости в той же точке; при
     * {@code NaN} в любом из них λ₂ опускается.
     */
    public static double conductivity(double rho, double t, double cp, double cv, double drhodpT, double mu) {
        double tb = t / If97.T_C;
        double rb = rho / If97.RHO_C;
        double s0 = 0;
        for (int k = 0; k < L0.length; k++) s0 += L0[k] / Math.pow(tb, k);
        double lambda0 = Math.sqrt(tb) / s0;
        double a = 1.0 / tb - 1.0, b = rb - 1.0;
        double s1 = 0, ai = 1.0;
        for (double[] row : L1) {
            double bj = 1.0, inner = 0;
            for (double l : row) {
                inner += l * bj;
                bj *= b;
            }
            s1 += ai * inner;
            ai *= a;
        }
        double lambda1 = Math.exp(rb * s1);
        double lambda2 = criticalEnhancement(rb, tb, cp, cv, drhodpT, mu);
        return 1e-3 * (lambda0 * lambda1 + lambda2);
    }

    private static double criticalEnhancement(double rb, double tb, double cp, double cv, double drhodpT, double mu) {
        if (!Double.isFinite(cp) || !Double.isFinite(cv) || !Double.isFinite(drhodpT) || !Double.isFinite(mu)) {
            return 0;
        }
        if (cp < 0 || cv <= 0) return 0;
        double zeta = If97.P_C / If97.RHO_C * drhodpT;
        double zetaR = 1.0 / aZeta(rb);
        double dchi = rb * (zeta - zetaR * 1.5 / tb);
        if (dchi <= 0) return 0;
        double xi = 0.13 * Math.pow(dchi / 0.06, 0.630 / 1.239);
        double y = xi / 0.40;
        if (y < 1.2e-7) return 0;
        double kappaInv = cv / cp;
        double z = 2.0 / (Math.PI * y) * ((1.0 - kappaInv) * Math.atan(y) + kappaInv * y
                - (1.0 - Math.exp(-1.0 / (1.0 / y + y * y / (3.0 * rb * rb)))));
        return 177.8514 * rb * (cp / R_2011) * tb / (mu * 1e6) * z;
    }

    private static double aZeta(double rb) {
        int row = 0;
        while (row < RHO_BOUNDS.length && rb > RHO_BOUNDS[row]) row++;
        double[] c = A_ZETA[row];
        double s = 0, pw = 1.0;
        for (double ci : c) {
            s += ci * pw;
            pw *= rb;
        }
        return s;
    }

    /** Поверхностное натяжение на линии насыщения, Н/м (IAPWS 2014); 0 при T ≥ T_c. */
    public static double surfaceTension(double t) {
        if (t >= If97.T_C) return 0;
        double tau = 1.0 - t / If97.T_C;
        return 235.8e-3 * Math.pow(tau, 1.256) * (1.0 - 0.625 * tau);
    }
}
