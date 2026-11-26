package dev.denfry.promptcritical.sim.radiation;

import dev.denfry.promptcritical.sim.consequence.GasDispersion;

/**
 * Выпадение из факела выброса на поверхность (04b §4b.9): сухое осаждение и вымывание осадками,
 * $$\sigma_{dep} = \left[v_d\,C(x,y,0) + \Lambda\,\frac{Q}{\sqrt{2\pi}\,u\,\sigma_y}e^{-y^2/2\sigma_y^2}\right]T_r,$$
 * с истощением факела $\exp(-\int (v_d/(\sigma_z\sqrt{\pi/2}) + \Lambda)\,dx/u)$ («source depletion»). Факел — тот же
 * приземный гауссов, что у дозы от облака ({@link GasDispersion}).
 */
public final class Fallout {
    /** Λ, 1/с: дождь и гроза (04b §4b.9) [BALANCE {@code radiation.washout_rain_per_s}, {@code radiation.washout_thunder_per_s}]. */
    public static final double WASHOUT_RAIN_PER_S = 1e-4, WASHOUT_THUNDER_PER_S = 3e-4;
    /** Дальше след не считается [BALANCE {@code radiation.plume_max_distance_m}]. */
    public static final double MAX_DISTANCE_M = 512;
    private static final double SQRT_PI_2 = Math.sqrt(Math.PI / 2), SQRT_2PI = Math.sqrt(2 * Math.PI);
    private static final int DEPLETION_STEPS = 64;

    private Fallout() {
    }

    /**
     * Скорость сухого осаждения v_d, м/с, по табл. 4b.9.1: йод 10⁻², аэрозоли (цезий, стронций, актиниды, продукты
     * коррозии и активации) 10⁻³; благородные газы и N-16 пара не оседают.
     */
    public static double depositionVelocity(Emitter e) {
        return switch (e) {
            case I131 -> 1e-2;
            case KR85, N16 -> 0;
            default -> 1e-3;
        };
    }

    /** Коэффициент вымывания Λ, 1/с; без осадков 0. */
    public static double washout(boolean raining, boolean thundering) {
        return thundering ? WASHOUT_THUNDER_PER_S : raining ? WASHOUT_RAIN_PER_S : 0;
    }

    /** Доля выброса, ещё оставшаяся в факеле на расстоянии {@code x} по ветру, м. */
    public static double depletion(double vd, double lambda, double windMs, GasDispersion.Stability s, double x) {
        if (x <= 1 || (vd <= 0 && lambda <= 0)) return 1;
        double u = Math.max(1, windMs), h = (x - 1) / DEPLETION_STEPS, sum = 0;
        for (int i = 0; i <= DEPLETION_STEPS; i++) {
            double xi = 1 + i * h;
            double k = vd / (s.sigmaZ(xi) * SQRT_PI_2) + lambda;
            sum += (i == 0 || i == DEPLETION_STEPS ? 0.5 : 1) * k;
        }
        return Math.exp(-sum * h / u);
    }

    /**
     * Поток выпадения на поверхность, Бк/(м²·с), в точке ({@code x} по ветру, {@code y} поперёк, м) от непрерывного
     * приземного выброса {@code qBqS}; против ветра, дальше {@link #MAX_DISTANCE_M} и для того, что не оседает
     * ({@code vd} = 0: благородные газы и дождём не вымываются, табл. 4b.9.1), — 0.
     */
    public static double fluxBqM2S(double qBqS, double windMs, GasDispersion.Stability s, double x, double y, double vd, double lambda) {
        double f = undepletedFluxBqM2S(qBqS, windMs, s, x, y, vd, lambda);
        return f > 0 ? f * depletion(vd, lambda, windMs, s, Math.max(x, 1)) : 0;
    }

    /**
     * То же без истощения факела: истощение меняется по ветру медленно, и его можно взять одно на клетку следа, а
     * узкий у источника факел (σy меньше метра) — считать по каждому блоку.
     */
    public static double undepletedFluxBqM2S(double qBqS, double windMs, GasDispersion.Stability s, double x, double y, double vd,
            double lambda) {
        if (!(qBqS > 0) || x <= 0 || x > MAX_DISTANCE_M || vd <= 0) return 0;
        double xx = Math.max(x, 1), u = Math.max(1, windMs), sy = s.sigmaY(xx);
        double dry = vd * GasDispersion.plumeKgM3(qBqS, u, s, xx, y);
        double wet = lambda * qBqS / (SQRT_2PI * u * sy) * Math.exp(-y * y / (2 * sy * sy));
        return dry + wet;
    }
}
