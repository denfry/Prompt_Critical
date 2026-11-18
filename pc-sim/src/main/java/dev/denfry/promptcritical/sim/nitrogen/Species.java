package dev.denfry.promptcritical.sim.nitrogen;

import dev.denfry.promptcritical.sim.thermo.GasSpecies;

/**
 * Компоненты азотной химии: природный газ, синтез-газ, аммиак и нитрозные газы. Энтальпия идеального газа (с
 * энтальпией образования) — 7-коэффициентные полиномы NASA: для N₂, O₂, H₂, H₂O, Ar, CO, CO₂ — из {@link GasSpecies};
 * для CH₄, NH₃, NO, NO₂ — GRI-Mech 3.0 (Smith et al., 1999; Cantera {@code gri30.yaml}), диапазоны 200–1000 и
 * 1000–3500 К. Потоки в расчётах — массивы мольных расходов по {@link #ordinal()}.
 */
public enum Species {
    CH4(0.01604246, new double[] {5.14987613, -1.36709788e-02, 4.91800599e-05, -4.84743026e-08, 1.66693956e-11, -1.02466476e+04},
            new double[] {7.48514950e-02, 1.33909467e-02, -5.73285809e-06, 1.22292535e-09, -1.01815230e-13, -9.46834459e+03}),
    H2O(GasSpecies.H2O_VAPOR),
    CO(GasSpecies.CO),
    CO2(GasSpecies.CO2),
    H2(GasSpecies.H2),
    N2(GasSpecies.N2),
    AR(GasSpecies.AR),
    O2(GasSpecies.O2),
    NH3(0.01703052, new double[] {4.28602740, -4.66052300e-03, 2.17185130e-05, -2.28088870e-08, 8.26380460e-12, -6.74172850e+03},
            new double[] {2.63445210, 5.66625600e-03, -1.72786760e-06, 2.38671610e-10, -1.25787860e-14, -6.54469580e+03}),
    NO(0.0300061, new double[] {4.21847630, -4.63897600e-03, 1.10410220e-05, -9.33613540e-09, 2.80357700e-12, 9.84462300e+03},
            new double[] {3.26060560, 1.19110430e-03, -4.29170480e-07, 6.94576690e-11, -4.03360990e-15, 9.92097460e+03}),
    NO2(0.0460055, new double[] {3.94403120, -1.58542900e-03, 1.66578120e-05, -2.04754260e-08, 7.83505640e-12, 2.89661790e+03},
            new double[] {4.88475420, 2.17239560e-03, -8.28069060e-07, 1.57475100e-10, -1.05108950e-14, 2.31649830e+03});

    public static final int N = values().length;
    public static final double R = GasSpecies.R_U;
    /** Нормальный кубометр (0 °C, 101,325 кПа), моль. */
    public static final double MOL_PER_NM3 = 1 / 0.022414;
    private static final Species[] ALL = values();

    private final GasSpecies base;
    private final double molarMass;
    private final double[] low, high;

    Species(GasSpecies base) {
        this.base = base;
        this.molarMass = base.molarMass();
        this.low = null;
        this.high = null;
    }

    Species(double molarMass, double[] low, double[] high) {
        this.base = null;
        this.molarMass = molarMass;
        this.low = low;
        this.high = high;
    }

    /** Молярная масса, кг/моль. */
    public double molarMass() {
        return molarMass;
    }

    /** Молярная энтальпия идеального газа с энтальпией образования, Дж/моль. */
    public double enthalpy(double t) {
        if (base != null) return base.enthalpy(t);
        double[] a = t < GasSpecies.T_MID ? low : high;
        return R * (t * (a[0] + t * (a[1] / 2 + t * (a[2] / 3 + t * (a[3] / 4 + t * a[4] / 5)))) + a[5]);
    }

    /** Изобарная молярная теплоёмкость, Дж/(моль·К). */
    public double cp(double t) {
        if (base != null) return base.cp(t);
        double[] a = t < GasSpecies.T_MID ? low : high;
        return R * (a[0] + t * (a[1] + t * (a[2] + t * (a[3] + t * a[4]))));
    }

    // --- потоки ---

    public static double[] flow() {
        return new double[N];
    }

    public static double total(double[] n) {
        double s = 0;
        for (double v : n) s += v;
        return s;
    }

    /** Мольная доля в сухом газе (без воды). */
    public static double dryFraction(double[] n, Species s) {
        double dry = total(n) - n[H2O.ordinal()];
        return dry > 0 ? n[s.ordinal()] / dry : 0;
    }

    public static double fraction(double[] n, Species s) {
        double t = total(n);
        return t > 0 ? n[s.ordinal()] / t : 0;
    }

    /** Поток энтальпии, Вт (при мольных расходах в моль/с). */
    public static double enthalpy(double[] n, double t) {
        double h = 0;
        for (Species s : ALL) if (n[s.ordinal()] != 0) h += n[s.ordinal()] * s.enthalpy(t);
        return h;
    }

    public static double heatCapacity(double[] n, double t) {
        double c = 0;
        for (Species s : ALL) if (n[s.ordinal()] != 0) c += n[s.ordinal()] * s.cp(t);
        return c;
    }

    /** Температура потока с заданной энтальпией (Ньютон по теплоёмкости), К. */
    public static double temperatureFor(double[] n, double h, double guessK) {
        double t = guessK;
        for (int i = 0; i < 50; i++) {
            double c = heatCapacity(n, t);
            if (!(c > 0)) return t;
            double dt = (h - enthalpy(n, t)) / c;
            t = Math.max(200, Math.min(3000, t + dt));
            if (Math.abs(dt) < 1e-6) break;
        }
        return t;
    }

    /** Массовый расход потока, кг/с. */
    public static double mass(double[] n) {
        double m = 0;
        for (Species s : ALL) m += n[s.ordinal()] * s.molarMass;
        return m;
    }

    /** Атмосферный воздух (сухой), доли: O₂ 0,2095, N₂ 0,7812, Ar 0,0093. */
    public static double[] air(double molS) {
        double[] a = flow();
        a[O2.ordinal()] = 0.2095 * molS;
        a[N2.ordinal()] = 0.7812 * molS;
        a[AR.ordinal()] = 0.0093 * molS;
        return a;
    }

    public static void add(double[] into, double[] n) {
        for (int i = 0; i < N; i++) into[i] += n[i];
    }
}
