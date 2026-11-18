package dev.denfry.promptcritical.sim.gasprocessing;

/**
 * Однократное равновесное испарение (изотермический флеш) по константам равновесия Вильсона
 * K_i = (p_c,i/p)·exp[5,373(1 + ω_i)(1 − T_c,i/T)] (Wilson, 1968; Michelsen, Mollerup, «Thermodynamic Models», гл. 10) и
 * уравнению Рэчфорда–Райса Σ z_i(K_i − 1)/(1 + V(K_i − 1)) = 0 (Rachford, Rice, JPT, 1952). Корреляция Вильсона
 * верна для лёгких углеводородов вдали от критической точки — для НТС и газофракционирования этого достаточно,
 * погрешность констант — десятки процентов у тяжёлых компонентов.
 *
 * @param vapor доля пара по молям
 * @param x     состав жидкости
 * @param y     состав пара
 */
public record Flash(double vapor, double[] x, double[] y) {
    public static double wilsonK(GasComponent c, double t, double p) {
        return c.pcPa / p * Math.exp(5.373 * (1 + c.omega) * (1 - c.tcK / t));
    }

    public static Flash of(double[] z, double t, double p) {
        int n = GasComponent.N;
        double[] k = new double[n];
        for (GasComponent c : GasComponent.values()) k[c.ordinal()] = wilsonK(c, t, p);
        double g0 = rr(z, k, 0), g1 = rr(z, k, 1);
        double v;
        if (g0 <= 0) v = 0;
        else if (g1 >= 0) v = 1;
        else {
            double lo = 0, hi = 1;
            for (int i = 0; i < 100; i++) {
                double mid = 0.5 * (lo + hi);
                if (rr(z, k, mid) > 0) lo = mid;
                else hi = mid;
            }
            v = 0.5 * (lo + hi);
        }
        double[] x = new double[n], y = new double[n];
        for (int i = 0; i < n; i++) {
            x[i] = z[i] / (1 + v * (k[i] - 1));
            y[i] = k[i] * x[i];
        }
        return new Flash(v, GasComponent.normalize(x), GasComponent.normalize(y));
    }

    private static double rr(double[] z, double[] k, double v) {
        double s = 0;
        for (int i = 0; i < z.length; i++) s += z[i] * (k[i] - 1) / (1 + v * (k[i] - 1));
        return s;
    }

    /** Объём жидкости на моль при 15 °C, м³/моль: аддитивность объёмов компонентов. */
    public static double liquidM3PerMol(double[] x) {
        double v = 0;
        for (GasComponent c : GasComponent.values()) v += x[c.ordinal()] * c.molarMassG / 1000 / c.liquidKgM3;
        return v;
    }
}
