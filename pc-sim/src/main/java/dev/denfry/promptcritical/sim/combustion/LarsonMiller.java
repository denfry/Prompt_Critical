package dev.denfry.promptcritical.sim.combustion;

/**
 * Длительная прочность по параметру Ларсона–Миллера (07 §7.9.3): LMP = T·(C + lg t_r), T в К, t_r в ч;
 * кривая марки LMP(σ) = a − b·lg(σ/1 МПа). Пресеты проведены через две точки предела длительной прочности за
 * 10⁵ ч [сверить]: углеродистая сталь 20 — 55 МПа при 450 °C и 27 МПа при 500 °C; 12Х1МФ — 90 МПа при 560 °C и
 * 50 МПа при 600 °C; аустенитная 12Х18Н10Т — 110 МПа при 600 °C и 65 МПа при 650 °C.
 *
 * @param a кривая марки, К·(ч)
 * @param b наклон на декаду напряжения
 * @param c постоянная Ларсона–Миллера (20)
 */
public record LarsonMiller(double a, double b, double c) implements CreepModel {
    public static final LarsonMiller CARBON = new LarsonMiller(25_120, 4_050, 20);
    public static final LarsonMiller CR_MO_V = new LarsonMiller(28_480, 3_920, 20);
    public static final LarsonMiller AUSTENITIC = new LarsonMiller(32_990, 5_470, 20);

    public LarsonMiller {
        if (!(a > 0 && b > 0 && c > 0)) throw new IllegalArgumentException("Larson-Miller constants must be > 0");
    }

    /** LMP при напряжении {@code stressPa}. */
    public double parameter(double stressPa) {
        return a - b * Math.log10(Math.max(stressPa, 1e3) / 1e6);
    }

    @Override
    public double ruptureTimeH(double stressPa, double metalTempK) {
        if (!(stressPa > 0)) return Double.POSITIVE_INFINITY;
        return Math.pow(10, parameter(stressPa) / metalTempK - c);
    }
}
