package dev.denfry.promptcritical.sim.hydraulics;

import java.util.function.DoubleBinaryOperator;

/**
 * Гомологические характеристики насоса в форме Сютера (01 §1.2.7): одна функция угла
 * θ = π + atan2(v, ν) во всех четырёх квадрантах, h/(ν² + v²) = W_H(θ),
 * β/(ν² + v²) = W_B(θ). Таблицы по 36 точек с шагом 10°, линейная интерполяция.
 */
public final class HomologousCurve {
    public static final int POINTS = 36;
    public static final String DEFAULT_ID = "default";
    private static final double STEP = 2 * Math.PI / POINTS;

    private final double[] wh, wb;

    public HomologousCurve(double[] wh, double[] wb) {
        if (wh.length != POINTS || wb.length != POINTS) {
            throw new IllegalArgumentException("homologous curve needs " + POINTS + " points");
        }
        this.wh = wh.clone();
        this.wb = wb.clone();
    }

    /**
     * Характеристика по умолчанию — <b>заглушка</b>: синтетическая квадратичная
     * четырёхквадрантная модель центробежного насоса, а не таблицы Semiscale/LOFT
     * (NUREG/CR-5535; сверка — 06 §6.4). Номинал h = β = 1 при ν = v = 1; напор на
     * закрытую задвижку 1,3, разгон до h = 0 при v ≈ 2,3; при обратном вращении насос
     * всё ещё качает вперёд (0,5); заторможенный ротор — сопротивление 0,2·v|v|.
     */
    public static HomologousCurve defaultCentrifugal() {
        return fromModel(
                (nu, v) -> (nu >= 0 ? 1.3 : 0.5) * nu * nu - 0.1 * nu * v - 0.2 * v * Math.abs(v),
                (nu, v) -> 0.6 * nu * Math.abs(nu) + 0.6 * nu * v - 0.2 * v * Math.abs(v));
    }

    /** Таблицы по однородным второй степени функциям h(ν, v), β(ν, v). */
    public static HomologousCurve fromModel(DoubleBinaryOperator head, DoubleBinaryOperator torque) {
        double[] wh = new double[POINTS], wb = new double[POINTS];
        for (int k = 0; k < POINTS; k++) {
            double t = k * STEP, nu = -Math.cos(t), v = -Math.sin(t);
            wh[k] = head.applyAsDouble(nu, v);
            wb[k] = torque.applyAsDouble(nu, v);
        }
        return new HomologousCurve(wh, wb);
    }

    /** Безразмерный напор h = H/H_R. */
    public double head(double nu, double v) {
        return evaluate(wh, nu, v);
    }

    /** Безразмерный гидравлический момент β = T/T_R (при ρ = ρ_R). */
    public double torque(double nu, double v) {
        return evaluate(wb, nu, v);
    }

    private static double evaluate(double[] w, double nu, double v) {
        double r2 = nu * nu + v * v;
        if (r2 == 0) return 0;
        double t = (Math.PI + Math.atan2(v, nu)) / STEP;
        int k = (int) t;
        double a = t - k;
        k %= POINTS;
        return r2 * (w[k] + a * (w[(k + 1) % POINTS] - w[k]));
    }
}
