package dev.denfry.promptcritical.sim.grid;

/**
 * Асинхронный двигатель {@code electric_motor} (07 §7.6.3) — источник вращения для Create.
 *
 * <p>Схема замещения без сопротивления статора: ротор Z_r = R_r/s + jX, поэтому момент — ровно
 * формула Клосса T = 2·T_max·v²/(s/s_k + s_k/s) при X = 1/(2T_max), R_r = s_k·X. Величины — о.е. на
 * P_ном (момент — на P_ном/ω_s). Потери статора ∝ I_r², реактивная мощность Q = Q_μ·v² + k_q·I_r²:
 * Q_μ и k_q подобраны так, что номинальный режим даёт cos φ_ном, а пуск — ток I_пуск. КПД и cos φ
 * при номинале — интерполяция таблицы §7.6.3 по lg P.
 *
 * @param tMaxPu      T_max,ном, о.е. номинального момента
 * @param sK          критическое скольжение s_k
 * @param iStartPu    пусковой ток, о.е. номинального
 * @param inertiaKgM2 момент инерции двигателя с механизмом, кг·м²
 * @param syncRpm     синхронная частота вращения при f₀
 */
public record InductionMotorSpec(double pRatedW, double vRatedV, double tMaxPu, double sK, double iStartPu,
        double inertiaKgM2, double syncRpm) {
    /** H двигателя вместе с механизмом по умолчанию, с [BALANCE]. */
    public static final double DEFAULT_INERTIA_S = 0.5;
    private static final double[] TABLE_P = {15e3, 100e3, 1e6, 5e6};
    private static final double[] TABLE_EFFICIENCY = {0.91, 0.945, 0.96, 0.97};
    private static final double[] TABLE_POWER_FACTOR = {0.85, 0.87, 0.88, 0.89};
    private static final double[] TABLE_START_CURRENT = {6.5, 6.5, 5.5, 5.0};

    public InductionMotorSpec {
        if (!(pRatedW > 0)) throw new IllegalArgumentException("pRatedW");
        if (!(vRatedV > 0)) throw new IllegalArgumentException("vRatedV");
        if (!(tMaxPu > 1)) throw new IllegalArgumentException("tMaxPu");
        if (!(sK > 0 && sK < 1)) throw new IllegalArgumentException("sK");
        if (!(iStartPu > 1)) throw new IllegalArgumentException("iStartPu");
        if (!(inertiaKgM2 > 0)) throw new IllegalArgumentException("inertiaKgM2");
        if (!(syncRpm > 0)) throw new IllegalArgumentException("syncRpm");
    }

    /** 1500 об/мин, T_max = 2,5, s_k = 0,15, I_пуск по таблице §7.6.3, H = 0,5 с с механизмом. */
    public static InductionMotorSpec standard(double pRatedW, double vRatedV) {
        double ws = 2 * Math.PI * 1500 / 60;
        return new InductionMotorSpec(pRatedW, vRatedV, 2.5, 0.15, table(TABLE_START_CURRENT, pRatedW),
                2 * DEFAULT_INERTIA_S * pRatedW / (ws * ws), 1500);
    }

    public double ratedEfficiency() {
        return table(TABLE_EFFICIENCY, pRatedW);
    }

    public double ratedPowerFactor() {
        return table(TABLE_POWER_FACTOR, pRatedW);
    }

    /** Момент Клосса при скольжении s и напряжении v, о.е. */
    public double torquePu(double s, double v) {
        if (s == 0) return 0;
        return 2 * tMaxPu * v * v / (s / sK + sK / s);
    }

    /** Скольжение установившегося режима на рабочей ветви; 1 — момент нагрузки больше T_max(v). */
    public double slipAt(double loadTorquePu, double v) {
        if (loadTorquePu <= 0) return 0;
        double r = tMaxPu * v * v / loadTorquePu;
        if (r < 1) return 1;
        return sK * (r - Math.sqrt(r * r - 1));
    }

    /** Номинальное скольжение: T(s_н) = 1 при v = 1. */
    public double ratedSlip() {
        return slipAt(1, 1);
    }

    /** H = J·ω_s²/(2P_ном), с. */
    public double inertiaS() {
        double ws = 2 * Math.PI * syncRpm / 60;
        return inertiaKgM2 * ws * ws / (2 * pRatedW);
    }

    /** Реактивное сопротивление рассеяния X, о.е. на P_ном. */
    double reactance() {
        return 1 / (2 * tMaxPu);
    }

    /** Приведённое сопротивление ротора R_r = s_k·X. */
    double rotorResistance() {
        return sK * reactance();
    }

    /** I_r² при скольжении s и v: v²s²/(R_r² + X²s²). */
    double rotorCurrentSq(double s, double v) {
        double rr = rotorResistance(), x = reactance();
        return v * v * s * s / (rr * rr + x * x * s * s);
    }

    /**
     * Калибровка потерь и реактивной мощности: {потери на единицу I_r², Q_μ, k_q}. Номинальный режим
     * T = 1 даёт P_эл = (1 − s_н)/η_ном и Q = P_эл·tg φ_ном; пуск — |S| = I_пуск·S_ном. Если КПД
     * таблицы выше 1 − s_н, потери статора принимаются нулевыми.
     */
    double[] calibration() {
        double sn = ratedSlip(), rr = rotorResistance(), x = reactance();
        double irn2 = rotorCurrentSq(sn, 1);
        double pf = ratedPowerFactor();
        double pEl = Math.max(1, (1 - sn) / ratedEfficiency());
        double loss = (pEl - 1) / irn2;
        double qn = pEl * Math.tan(Math.acos(pf));
        double ir2s = 1 / (rr * rr + x * x);
        double ps = (rr + loss) * ir2s, ss = iStartPu * pEl / pf;
        double qs = Math.sqrt(Math.max(0, ss * ss - ps * ps));
        double kq = Math.max(0, Math.min((qs - qn) / (ir2s - irn2), qn / irn2));
        return new double[] {loss, qn - kq * irn2, kq};
    }

    private static double table(double[] y, double p) {
        double lp = Math.log10(p);
        if (p <= TABLE_P[0]) return y[0];
        for (int k = 1; k < TABLE_P.length; k++) {
            if (p <= TABLE_P[k]) {
                double a = Math.log10(TABLE_P[k - 1]), b = Math.log10(TABLE_P[k]);
                return y[k - 1] + (y[k] - y[k - 1]) * (lp - a) / (b - a);
            }
        }
        return y[y.length - 1];
    }
}
