package dev.denfry.promptcritical.sim.petrochem;

/**
 * Компоненты пирогаза. Критические константы и ацентрический фактор — Poling, Prausnitz, O'Connell, «The Properties of
 * Gases and Liquids», 5-е изд., 2001, прил. A; доля водорода по массе — по формуле. Псевдокомпоненты: {@link #C4} —
 * бутены и бутаны (константы н-бутана), {@link #PYGAS} — пиробензин C5–C9 (константы бензола: в нём до 60 % аренов),
 * {@link #FUEL_OIL} — смола пиролиза C10+ (константы нафталина).
 */
public enum PyroComponent {
    H2(2.016, 33.19, 1.313e6, -0.216, 1.0, 20.3),
    CH4(16.043, 190.56, 4.599e6, 0.011, 4 * 1.008 / 16.043, 111.7),
    C2H2(26.038, 308.30, 6.138e6, 0.187, 2 * 1.008 / 26.038, 189.2),
    C2H4(28.054, 282.34, 5.041e6, 0.087, 4 * 1.008 / 28.054, 169.4),
    C2H6(30.070, 305.32, 4.872e6, 0.099, 6 * 1.008 / 30.070, 184.6),
    C3H6(42.081, 364.90, 4.600e6, 0.142, 6 * 1.008 / 42.081, 225.5),
    C3H8(44.097, 369.83, 4.248e6, 0.152, 8 * 1.008 / 44.097, 231.0),
    C4H6(54.092, 425.00, 4.320e6, 0.195, 6 * 1.008 / 54.092, 268.7),
    C4(57.0, 425.12, 3.796e6, 0.200, 0.155, 272.7),
    PYGAS(85.0, 562.05, 4.895e6, 0.210, 0.090, 353.2),
    FUEL_OIL(150.0, 748.40, 4.051e6, 0.302, 0.075, 491.1),
    CO2(44.010, 304.12, 7.374e6, 0.225, 0, 194.7),
    H2S(34.081, 373.40, 8.963e6, 0.090, 2 * 1.008 / 34.081, 212.8);

    public static final int N = values().length;

    public final double molarMassG, tcK, pcPa, omega, hydrogenMassFraction, boilingK;

    PyroComponent(double molarMassG, double tcK, double pcPa, double omega, double hydrogenMassFraction, double boilingK) {
        this.molarMassG = molarMassG;
        this.tcK = tcK;
        this.pcPa = pcPa;
        this.omega = omega;
        this.hydrogenMassFraction = hydrogenMassFraction;
        this.boilingK = boilingK;
    }

    /** Константа равновесия Вильсона K = (P_c/P)·exp[5,373(1 + ω)(1 − T_c/T)] (Wilson, 1968). */
    public double wilsonK(double tK, double pPa) {
        return pcPa / pPa * Math.exp(5.373 * (1 + omega) * (1 - tcK / tK));
    }

    /** Массы по мольным количествам, кг; моли — моль. */
    public static double massKg(double[] moles) {
        double m = 0;
        for (PyroComponent c : values()) m += moles[c.ordinal()] * c.molarMassG / 1000;
        return m;
    }

    /** Массовые доли из мольных количеств. */
    public static double[] massFractions(double[] moles) {
        double total = massKg(moles);
        double[] w = new double[N];
        if (total <= 0) return w;
        for (PyroComponent c : values()) w[c.ordinal()] = moles[c.ordinal()] * c.molarMassG / 1000 / total;
        return w;
    }

    /** Моли из масс, кг → моль. */
    public static double[] molesFromMass(double[] kg) {
        double[] n = new double[N];
        for (PyroComponent c : values()) n[c.ordinal()] = kg[c.ordinal()] * 1000 / c.molarMassG;
        return n;
    }

    /** Доля водорода по массе в смеси с массовыми долями {@code w}. */
    public static double hydrogenContent(double[] w) {
        double h = 0, s = 0;
        for (PyroComponent c : values()) {
            h += w[c.ordinal()] * c.hydrogenMassFraction;
            s += w[c.ordinal()];
        }
        return s > 0 ? h / s : 0;
    }
}
