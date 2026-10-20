package dev.denfry.promptcritical.sim.grid;

/**
 * Паспорт синхронной машины (07 §7.3.1), реактивности — о.е. на S_ном.
 *
 * @param td0PS       T'_d0, с
 * @param hS          постоянная инерции агрегата (машина + первичный двигатель) на S_ном, с
 * @param dPu         демпфирование успокоительной обмоткой, о.е. (Kundur §3.9)
 * @param raPu        сопротивление статора; модель 3-го порядка его не учитывает (потери — в КПД)
 * @param efficiency  КПД генератора для учёта на валу (03a)
 */
public record MachineParams(double sRatedVa, double vRatedV, int polePairs, double xd, double xdP, double xdPP,
        double td0PS, double hS, double dPu, double raPu, double efficiency) {
    public MachineParams {
        if (!(sRatedVa > 0)) throw new IllegalArgumentException("sRatedVa");
        if (!(vRatedV > 0)) throw new IllegalArgumentException("vRatedV");
        if (polePairs < 1) throw new IllegalArgumentException("polePairs");
        if (!(xdP > 0 && xd >= xdP && xdPP > 0 && xdPP <= xdP)) throw new IllegalArgumentException("reactances");
        if (!(td0PS > 0)) throw new IllegalArgumentException("td0PS");
        if (!(hS > 0)) throw new IllegalArgumentException("hS");
        if (!(dPu >= 0)) throw new IllegalArgumentException("dPu");
        if (!(raPu >= 0)) throw new IllegalArgumentException("raPu");
        if (!(efficiency > 0 && efficiency <= 1)) throw new IllegalArgumentException("efficiency");
    }

    /** Турбогенератор 3000 об/мин (Kundur табл. 4.3, Anderson–Fouad прил. D). */
    public static MachineParams turbogenerator(double sRatedVa, double vRatedV) {
        return new MachineParams(sRatedVa, vRatedV, 1, 1.8, 0.25, 0.18, 7.0, 4.0, 2, 0.003, 0.98);
    }

    /** Генератор ДГ 750 об/мин; H — вместе с дизелем. */
    public static MachineParams dieselGenerator(double sRatedVa, double vRatedV) {
        return new MachineParams(sRatedVa, vRatedV, 4, 2.2, 0.25, 0.15, 3.0, 1.0, 1, 0.005, 0.95);
    }

    /** {@code shaft_generator} на валу Create; H без маховика Create. */
    public static MachineParams shaftGenerator(double sRatedVa, double vRatedV) {
        return new MachineParams(sRatedVa, vRatedV, 1, 2.0, 0.30, 0.20, 2.5, 0.6, 1, 0.01, 0.94);
    }

    /**
     * Номинальный ток возбуждения i_f,ном = E_q при V = 1, I = 1, cos φ = 0,8 (табл. §7.3.1),
     * о.е. тока холостого хода: |1 + j·x_d·(0,8 − j0,6)| (§7.3.2, ≈ 2,5 у турбогенератора).
     */
    public double ratedFieldPu() {
        return Math.hypot(1 + 0.6 * xd, 0.8 * xd);
    }

    /** Момент инерции J = 2HS/ω_m0², кг·м² (§7.2.1). */
    public double inertiaKgM2(double nominalFrequencyHz) {
        double wm = 2 * Math.PI * nominalFrequencyHz / polePairs;
        return 2 * hS * sRatedVa / (wm * wm);
    }
}
