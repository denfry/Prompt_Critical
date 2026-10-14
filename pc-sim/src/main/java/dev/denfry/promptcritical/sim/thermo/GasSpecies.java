package dev.denfry.promptcritical.sim.thermo;

/**
 * Компоненты идеальной газовой смеси (01 §1.3.10). Теплоёмкость — 7-коэффициентные
 * полиномы NASA по данным Glenn (McBride, Zehe, Gordon, NASA TP-2002-211556) в форме
 * NASA-7 из {@code nasa_gas.yaml} Cantera 3.0: диапазоны 200–1000 и 1000–6000 К,
 * c_p/R = a₁ + a₂T + a₃T² + a₄T³ + a₅T⁴, h/(RT) = a₁ + a₂T/2 + a₃T²/3 + a₄T³/4 + a₅T⁴/5 + a₆/T.
 */
public enum GasSpecies {
    N2(0.0280134,
            new double[] {3.53100528, -1.23660987e-04, -5.02999437e-07, 2.43530612e-09, -1.40881235e-12,
                -1046.97628},
            new double[] {2.95257626, 1.39690057e-03, -4.92631691e-07, 7.86010367e-11, -4.60755321e-15,
                -923.948645}),
    O2(0.0319988,
            new double[] {3.78245636, -2.99673415e-03, 9.847302e-06, -9.68129508e-09, 3.24372836e-12,
                -1063.94356},
            new double[] {3.66096083, 6.56365523e-04, -1.41149485e-07, 2.05797658e-11, -1.29913248e-15,
                -1215.97725}),
    H2(0.00201588,
            new double[] {2.34433112, 7.98052075e-03, -1.9478151e-05, 2.01572094e-08, -7.37611761e-12,
                -917.935173},
            new double[] {2.93286579, 8.26607967e-04, -1.46402335e-07, 1.54100359e-11, -6.88804432e-16,
                -813.065597}),
    H2O_VAPOR(0.01801528,
            new double[] {4.19864056, -2.0364341e-03, 6.52040211e-06, -5.48797062e-09, 1.77197817e-12,
                -3.02937267e+04},
            new double[] {2.67703787, 2.97318329e-03, -7.7376969e-07, 9.44336689e-11, -4.26900959e-15,
                -2.98858938e+04}),
    AR(0.039948, monatomic(), monatomic()),
    CO2(0.0440095,
            new double[] {2.35677352, 8.98459677e-03, -7.12356269e-06, 2.45919022e-09, -1.43699548e-13,
                -4.83719697e+04},
            new double[] {4.63659493, 2.74131991e-03, -9.95828531e-07, 1.60373011e-10, -9.16103468e-15,
                -4.90249341e+04}),
    HE(0.004002602, monatomic(), monatomic());

    /** Универсальная газовая постоянная, Дж/(моль·К). */
    public static final double R_U = 8.314462618;
    public static final double T_MIN = 200.0, T_MID = 1000.0, T_MAX = 6000.0;

    private final double molarMass;
    private final double[] low, high;

    GasSpecies(double molarMass, double[] low, double[] high) {
        this.molarMass = molarMass;
        this.low = low;
        this.high = high;
    }

    private static double[] monatomic() {
        return new double[] {2.5, 0, 0, 0, 0, -745.375};
    }

    /** Молярная масса, кг/моль. */
    public double molarMass() {
        return molarMass;
    }

    /** Изобарная молярная теплоёмкость идеального газа, Дж/(моль·К). */
    public double cp(double t) {
        double[] a = t < T_MID ? low : high;
        return R_U * (a[0] + t * (a[1] + t * (a[2] + t * (a[3] + t * a[4]))));
    }

    /** Молярная энтальпия идеального газа (включая энтальпию образования), Дж/моль. */
    public double enthalpy(double t) {
        double[] a = t < T_MID ? low : high;
        return R_U * (t * (a[0] + t * (a[1] / 2 + t * (a[2] / 3 + t * (a[3] / 4 + t * a[4] / 5)))) + a[5]);
    }
}
