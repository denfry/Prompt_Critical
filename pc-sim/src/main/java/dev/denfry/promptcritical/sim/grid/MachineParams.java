package dev.denfry.promptcritical.sim.grid;

/**
 * Паспорт синхронной машины (07 §7.3.1), реактивности — о.е. на S_ном.
 * <p>
 * Двухосная модель (Sauer–Pai §3.4, Kundur гл. 5): E'_q по продольной оси, E'_d по поперечной,
 * x'_q = x'_d — тогда машина входит в сеть одним источником за x'_d. Без x_q ≠ x'_d машина
 * получала бы ложный реактивный момент и держала нагрузку без возбуждения.
 *
 * @param xq          x_q, синхронная реактивность по поперечной оси; не меньше x'_d
 * @param td0PS       T'_d0, с
 * @param tq0PS       T'_q0, с; у явнополюсной машины — демпферная клетка по оси q
 * @param hS          постоянная инерции агрегата (машина + первичный двигатель) на S_ном, с
 * @param dPu         демпфирование успокоительной обмоткой, о.е. (Kundur §3.9)
 * @param raPu        сопротивление статора; модель 3-го порядка его не учитывает (потери — в КПД)
 * @param efficiency  КПД генератора для учёта на валу (03a)
 */
public record MachineParams(double sRatedVa, double vRatedV, int polePairs, double xd, double xq, double xdP,
        double xdPP, double td0PS, double tq0PS, double hS, double dPu, double raPu, double efficiency) {
    public MachineParams {
        if (!(sRatedVa > 0)) throw new IllegalArgumentException("sRatedVa");
        if (!(vRatedV > 0)) throw new IllegalArgumentException("vRatedV");
        if (polePairs < 1) throw new IllegalArgumentException("polePairs");
        if (!(xdP > 0 && xd >= xdP && xq >= xdP && xdPP > 0 && xdPP <= xdP))
            throw new IllegalArgumentException("reactances");
        if (!(td0PS > 0)) throw new IllegalArgumentException("td0PS");
        if (!(tq0PS > 0)) throw new IllegalArgumentException("tq0PS");
        if (!(hS > 0)) throw new IllegalArgumentException("hS");
        if (!(dPu >= 0)) throw new IllegalArgumentException("dPu");
        if (!(raPu >= 0)) throw new IllegalArgumentException("raPu");
        if (!(efficiency > 0 && efficiency <= 1)) throw new IllegalArgumentException("efficiency");
    }

    /** Турбогенератор 3000 об/мин, неявнополюсный: x_q ≈ x_d (Kundur табл. 4.3 и пример 3.1, Anderson–Fouad прил. D). */
    public static MachineParams turbogenerator(double sRatedVa, double vRatedV) {
        return new MachineParams(sRatedVa, vRatedV, 1, 1.8, 1.75, 0.25, 0.18, 7.0, 1.0, 4.0, 2, 0.003, 0.98);
    }

    /** Генератор ДГ 750 об/мин, явнополюсный: x_q ≈ 0,55·x_d; H — вместе с дизелем. */
    public static MachineParams dieselGenerator(double sRatedVa, double vRatedV) {
        return new MachineParams(sRatedVa, vRatedV, 4, 2.2, 1.2, 0.25, 0.15, 3.0, 0.3, 1.0, 1, 0.005, 0.95);
    }

    /** {@code shaft_generator} на валу Create, неявнополюсный 3000 об/мин; H без маховика Create. */
    public static MachineParams shaftGenerator(double sRatedVa, double vRatedV) {
        return new MachineParams(sRatedVa, vRatedV, 1, 2.0, 1.9, 0.30, 0.20, 2.5, 0.5, 0.6, 1, 0.01, 0.94);
    }

    /**
     * Номинальный ток возбуждения i_f,ном = E_fd при V = 1, I = 1, cos φ = 0,8 (табл. §7.3.1), о.е. тока
     * холостого хода: ось q по E_Q = 1 + j·x_q·(0,8 − j0,6), E_fd = |E_Q| + (x_d − x_q)·i_d
     * (§7.3.2, ≈ 2,5 у турбогенератора; при x_q = x_d — |1 + j·x_d·(0,8 − j0,6)|).
     */
    public double ratedFieldPu() {
        double qr = 1 + 0.6 * xq, qi = 0.8 * xq, eQ = Math.hypot(qr, qi);
        double id = (0.8 * qi + 0.6 * qr) / eQ; // I·(sin δ, −cos δ), I = 0,8 − j0,6
        return eQ + (xd - xq) * id;
    }

    /** Момент инерции J = 2HS/ω_m0², кг·м² (§7.2.1). */
    public double inertiaKgM2(double nominalFrequencyHz) {
        double wm = 2 * Math.PI * nominalFrequencyHz / polePairs;
        return 2 * hS * sRatedVa / (wm * wm);
    }
}
