package dev.denfry.promptcritical.sim.combustion;

/**
 * Компоненты газового топлива и их низшая теплота сгорания (07 §7.8.1): МДж/нм³ при 0 °C и 101,325 кПа
 * (справочник теплотехника). Порядок констант — порядок массива долей в {@link FuelSpec#fromGasComposition}.
 */
public enum FuelGas {
    CH4(1, 4, 0, 0, 0, 35.82),
    C2H6(2, 6, 0, 0, 0, 64.35),
    C3H8(3, 8, 0, 0, 0, 93.18),
    C4H10(4, 10, 0, 0, 0, 123.6),
    C2H4(2, 4, 0, 0, 0, 59.0),
    H2(0, 2, 0, 0, 0, 10.78),
    CO(1, 0, 1, 0, 0, 12.63),
    CO2(1, 0, 2, 0, 0, 0),
    N2(0, 0, 0, 2, 0, 0),
    O2(0, 0, 2, 0, 0, 0),
    H2S(0, 2, 0, 0, 1, 23.4),
    H2O(0, 2, 1, 0, 0, 0);

    /** Атомные массы, кг/моль. */
    static final double M_C = 0.012011, M_H = 0.001008, M_O = 0.015999, M_N = 0.014007, M_S = 0.03206;
    /** Молярный объём идеального газа при нормальных условиях (0 °C, 101,325 кПа), м³/моль. */
    public static final double NORMAL_MOLAR_VOLUME = 0.022414;

    final int c, h, o, n, s;
    /** Молярная масса, кг/моль. */
    public final double molarMass;
    /** Низшая теплота сгорания, Дж/нм³. */
    public final double lhvJPerNm3;

    FuelGas(int c, int h, int o, int n, int s, double lhvMJPerNm3) {
        this.c = c;
        this.h = h;
        this.o = o;
        this.n = n;
        this.s = s;
        this.molarMass = c * M_C + h * M_H + o * M_O + n * M_N + s * M_S;
        this.lhvJPerNm3 = lhvMJPerNm3 * 1e6;
    }

    /** Низшая теплота сгорания смеси по объёмным долям, Дж/нм³ (сумма по компонентам). */
    public static double mixtureLhvJPerNm3(double[] volumeFractions) {
        FuelGas[] all = values();
        if (volumeFractions.length != all.length) throw new IllegalArgumentException("expected " + all.length + " fractions");
        double sum = 0, q = 0;
        for (int i = 0; i < all.length; i++) {
            sum += volumeFractions[i];
            q += volumeFractions[i] * all[i].lhvJPerNm3;
        }
        return q / sum;
    }

    /** Плотность смеси при нормальных условиях, кг/нм³ (идеальный газ). */
    public static double mixtureDensityKgPerNm3(double[] volumeFractions) {
        FuelGas[] all = values();
        double sum = 0, m = 0;
        for (int i = 0; i < all.length; i++) {
            sum += volumeFractions[i];
            m += volumeFractions[i] * all[i].molarMass;
        }
        return m / sum / NORMAL_MOLAR_VOLUME;
    }
}
