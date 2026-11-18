package dev.denfry.promptcritical.sim.wind;

/**
 * Характеристики ветроколеса C_Q(λ, β) и C_T(λ, β), заранее рассчитанные {@link BladeElementMomentum} на сетке
 * λ = 0…{@link #TSR_MAX} через {@link #TSR_STEP}, β = {@link #PITCH_MIN_DEG}…90° через 1°; между узлами —
 * билинейная интерполяция. Таблица строится один раз на паспорт колеса (≈ 6·10³ решений BEM, доли секунды)
 * и дальше читается без аллокаций в каждом подшаге сети.
 */
public final class RotorPerformance {
    public static final double TSR_MAX = 16, TSR_STEP = 0.25;
    public static final int PITCH_MIN_DEG = -5, PITCH_MAX_DEG = 90;
    private static final int NT = (int) Math.round(TSR_MAX / TSR_STEP) + 1, NB = PITCH_MAX_DEG - PITCH_MIN_DEG + 1;

    private final double[] cq = new double[NT * NB], ct = new double[NT * NB];

    public RotorPerformance(RotorGeometry g, Airfoil foil) {
        for (int i = 0; i < NT; i++) {
            for (int j = 0; j < NB; j++) {
                BladeElementMomentum.Result res = BladeElementMomentum.solve(g, foil, i * TSR_STEP,
                        Math.toRadians(PITCH_MIN_DEG + j), true);
                cq[i * NB + j] = res.cq();
                ct[i * NB + j] = res.ct();
            }
        }
    }

    /** C_Q = Q/(½ρAV²R). За пределом таблицы по λ — момент последнего узла (колесо там тормозит). */
    public double torqueCoefficient(double tsr, double pitchDeg) {
        return lookup(cq, tsr, pitchDeg);
    }

    /** C_P = λ·C_Q. */
    public double powerCoefficient(double tsr, double pitchDeg) {
        return tsr * lookup(cq, tsr, pitchDeg);
    }

    /** C_T = T/(½ρAV²). */
    public double thrustCoefficient(double tsr, double pitchDeg) {
        return lookup(ct, tsr, pitchDeg);
    }

    private static double lookup(double[] table, double tsr, double pitchDeg) {
        double x = Math.max(0, Math.min(TSR_MAX, tsr)) / TSR_STEP;
        double y = Math.max(PITCH_MIN_DEG, Math.min(PITCH_MAX_DEG, pitchDeg)) - PITCH_MIN_DEG;
        int i = Math.min(NT - 2, (int) x), j = Math.min(NB - 2, (int) y);
        double fx = x - i, fy = y - j;
        double v00 = table[i * NB + j], v01 = table[i * NB + j + 1];
        double v10 = table[(i + 1) * NB + j], v11 = table[(i + 1) * NB + j + 1];
        return (v00 * (1 - fy) + v01 * fy) * (1 - fx) + (v10 * (1 - fy) + v11 * fy) * fx;
    }
}
