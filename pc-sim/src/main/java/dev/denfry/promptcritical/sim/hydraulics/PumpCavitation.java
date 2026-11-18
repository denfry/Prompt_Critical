package dev.denfry.promptcritical.sim.hydraulics;

import dev.denfry.promptcritical.sim.thermo.if97.If97;

/**
 * Кавитация центробежного насоса (04a §4a.3): располагаемый кавитационный запас NPSH_a на всасе против требуемого
 * NPSH_r по критерию 3 % падения напора (ISO 9906), индекс интенсивности κ, падение напора, эрозия колеса и
 * кавитационный слой шума. Чистые функции; состояние износа колеса держит владелец насоса.
 */
public final class PumpCavitation {
    /** g, м/с². */
    public static final double G = 9.80665;
    /** k_i = NPSH_i/NPSH_r: шум появляется раньше падения напора (04a §4a.3.2) [BALANCE]. */
    public static final double INCEPTION_FACTOR = 2;
    /** T_erosion: при κ = 1 колесо съедено за столько часов физического времени (04a §4a.3.3) [BALANCE]. */
    public static final double EROSION_HOURS = 200;
    /** Износ колеса W снижает напор на 0,25·W и поднимает NPSH_r на 50 %·W (04a §4a.3.3). */
    public static final double WEAR_HEAD_LOSS = 0.25, WEAR_NPSH_RISE = 0.5;

    private PumpCavitation() {
    }

    /**
     * NPSH_a = (p_s − p_v(T_s))/(ρg) + v_s²/(2g), м (04a §4a.3.1).
     *
     * @param suctionPa абсолютное статическое давление во всасывающем патрубке, Па
     * @param waterK    температура жидкости на всасе, К
     * @param rho       плотность, кг/м³
     * @param velocity  скорость во всасывающем патрубке, м/с
     */
    public static double npshAvailable(double suctionPa, double waterK, double rho, double velocity) {
        return (suctionPa - If97.psat(waterK)) / (rho * G) + velocity * velocity / (2 * G);
    }

    /** f(q) = 0,35 + 0,65q² + 3·max(0; 0,4 − q)² [BALANCE {@code acoustics.npsh_curve}], f(1) = 1. */
    public static double curve(double q) {
        double low = Math.max(0, 0.4 - q);
        return 0.35 + 0.65 * q * q + 3 * low * low;
    }

    /**
     * NPSH_r(Q, ω) = NPSH_r,bep (ω/ω_n)² f(q), q = (Q/Q_bep)(ω_n/ω); изношенное колесо — ×(1 + 0,5W), м.
     *
     * @param speedRatio ω/ω_n
     * @param flowRatio  Q/Q_bep
     */
    public static double npshRequired(double npshBep, double speedRatio, double flowRatio, double wear) {
        if (!(speedRatio > 0)) return 0;
        double q = flowRatio / speedRatio;
        return npshBep * speedRatio * speedRatio * curve(q) * (1 + WEAR_NPSH_RISE * clamp01(wear));
    }

    /** κ = clamp((NPSH_i − NPSH_a)/(NPSH_i − 0,5 NPSH_r), 0, 1), NPSH_i = k_i NPSH_r (04a §4a.3.3). */
    public static double intensity(double npshA, double npshR) {
        if (!(npshR > 0)) return 0;
        double npshI = INCEPTION_FACTOR * npshR;
        return clamp01((npshI - npshA) / (npshI - 0.5 * npshR));
    }

    /** ΔH/H = min(1; 0,03·exp((NPSH_r − NPSH_a)/(0,15 NPSH_r))) (04a §4a.3.3); без кавитации — 0. */
    public static double headDrop(double npshA, double npshR) {
        if (!(npshR > 0) || intensity(npshA, npshR) <= 0) return 0;
        return Math.min(1, 0.03 * Math.exp((npshR - npshA) / (0.15 * npshR)));
    }

    /** dW/dt = κ³/T_erosion, 1/с физического времени. */
    public static double erosionRate(double kappa) {
        double k = clamp01(kappa);
        return k * k * k / (EROSION_HOURS * 3600);
    }

    /** Кавитационный слой шума L_W,cav = L_W,pump + 15κ − 6, дБ; тихо — −∞ (04a §4a.3.4). */
    public static double soundLayerDb(double pumpLwDb, double kappa) {
        return kappa > 0 ? pumpLwDb + 15 * clamp01(kappa) - 6 : Double.NEGATIVE_INFINITY;
    }

    private static double clamp01(double x) {
        return Math.max(0, Math.min(1, x));
    }
}
