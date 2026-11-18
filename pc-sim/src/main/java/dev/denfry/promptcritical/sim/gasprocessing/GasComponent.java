package dev.denfry.promptcritical.sim.gasprocessing;

/**
 * Компоненты природного и попутного газа: критические параметры, ацентрический фактор, молярная масса (Poling,
 * Prausnitz, O'Connell, «The Properties of Gases and Liquids», 5-е изд., прил. A; GPSA Engineering Data Book, разд.
 * 23), изобарная теплоёмкость идеального газа при 300 K и плотность жидкости при 15 °C (GPSA, рис. 23-2).
 */
public enum GasComponent {
    N2(126.2, 3.394e6, 0.040, 28.014, 29.1, 806),
    CO2(304.1, 7.374e6, 0.225, 44.010, 37.1, 818),
    C1(190.6, 4.599e6, 0.012, 16.043, 35.7, 300),
    C2(305.3, 4.872e6, 0.100, 30.070, 52.5, 356),
    C3(369.8, 4.248e6, 0.152, 44.097, 73.6, 507),
    IC4(408.1, 3.648e6, 0.181, 58.123, 96.8, 563),
    NC4(425.1, 3.796e6, 0.200, 58.123, 98.5, 584),
    IC5(460.4, 3.381e6, 0.227, 72.150, 120.0, 625),
    NC5(469.7, 3.370e6, 0.252, 72.150, 120.1, 631),
    NC6(507.6, 3.025e6, 0.301, 86.177, 143.1, 664);

    public final double tcK, pcPa, omega, molarMassG, cpIdealJMolK, liquidKgM3;

    GasComponent(double tcK, double pcPa, double omega, double molarMassG, double cpIdealJMolK, double liquidKgM3) {
        this.tcK = tcK;
        this.pcPa = pcPa;
        this.omega = omega;
        this.molarMassG = molarMassG;
        this.cpIdealJMolK = cpIdealJMolK;
        this.liquidKgM3 = liquidKgM3;
    }

    public static final int N = values().length;

    /**
     * Попутный нефтяной газ первой ступени сепарации — жирный, с заметной долей пропана и бутанов (Бекиров Т.М.,
     * «Первичная переработка природных газов», гл. 1; типичный состав для нефтяных месторождений Западной Сибири).
     */
    public static double[] associatedGas() {
        double[] x = new double[N];
        x[N2.ordinal()] = 0.02;
        x[CO2.ordinal()] = 0.01;
        x[C1.ordinal()] = 0.73;
        x[C2.ordinal()] = 0.10;
        x[C3.ordinal()] = 0.08;
        x[IC4.ordinal()] = 0.015;
        x[NC4.ordinal()] = 0.025;
        x[IC5.ordinal()] = 0.008;
        x[NC5.ordinal()] = 0.008;
        x[NC6.ordinal()] = 0.004;
        return x;
    }

    /** Чистый компонент. */
    public static double[] pure(GasComponent c) {
        double[] x = new double[N];
        x[c.ordinal()] = 1;
        return x;
    }

    /** Молярная масса смеси, г/моль. */
    public static double molarMass(double[] x) {
        double m = 0;
        for (GasComponent c : values()) m += x[c.ordinal()] * c.molarMassG;
        return m;
    }

    /** Относительная плотность газа по воздуху. */
    public static double gravity(double[] x) {
        return molarMass(x) / 28.964;
    }

    /** Нормировать доли к единице. */
    public static double[] normalize(double[] x) {
        double s = 0;
        for (double v : x) s += v;
        double[] out = new double[x.length];
        if (s <= 0) return out;
        for (int i = 0; i < x.length; i++) out[i] = x[i] / s;
        return out;
    }
}
