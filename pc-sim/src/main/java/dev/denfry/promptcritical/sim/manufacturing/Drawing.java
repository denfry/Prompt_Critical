package dev.denfry.promptcritical.sim.manufacturing;

/**
 * Волочение (18 §18.6.7): напряжение по формуле Закса σ_d = σ̄·(1+B)/B·[1 − (A₁/A₀)^B], B = μ·ctg α, со средним
 * сопротивлением σ̄ по кривой Холломона на участке деформации прохода. Обрыв — если σ_d не меньше предела
 * текучести выходящего сечения, обжатие больше {@link #MAX_REDUCTION} или накопленный наклёп выше предельного:
 * тогда нужен отжиг. Сила волочения ограничена тягой (рука с воротом или стан на валу).
 */
public final class Drawing {
    private Drawing() {}

    /** Полуугол волоки, рад (8°) [сверить]. */
    public static final double DIE_HALF_ANGLE_RAD = Math.toRadians(8);
    /** Трение в волоке: всухую и с мыльной смазкой [сверить]. */
    public static final double MU_DRY = 0.15, MU_LUBRICATED = 0.08;
    /** Предельное обжатие по площади за проход [сверить: 25–35 %]. */
    public static final double MAX_REDUCTION = 0.35;
    /** Тяга волочильной доски с воротом, Н [BALANCE: тонкая медь и мягкое железо, не стальной пруток]. */
    public static final double HAND_FORCE_N = 1500;
    /** Постоянная времени рекристаллизации при температуре отжига, с [BALANCE]. */
    public static final double ANNEAL_TAU_S = 60;
    /** Выше температуры отжига τ падает в e раз на столько кельвинов [BALANCE]. */
    public static final double ANNEAL_TAU_K = 100;

    public enum Outcome { OK, BROKE, TOO_STRONG, NO_REDUCTION }

    /** Итог прохода: новая проволока (или прежняя), исход и потребная сила, Н. */
    public record Pass(Wire wire, Outcome outcome, double forceN) {}

    /** Напряжение волочения, Па, для прохода с площади {@code a0} на {@code a1} при деформации до прохода {@code e0}. */
    public static double stressPa(Wire.Metal m, double e0, double a0, double a1, double mu) {
        double e1 = e0 + Math.log(a0 / a1);
        double mean = meanFlowPa(m, e0, e1);
        double b = mu / Math.tan(DIE_HALF_ANGLE_RAD);
        return mean * (1 + b) / b * (1 - Math.pow(a1 / a0, b));
    }

    /** Среднее сопротивление на участке [e0, e1]: K·(e1ⁿ⁺¹ − e0ⁿ⁺¹)/((n+1)(e1 − e0)), не ниже отожжённого. */
    static double meanFlowPa(Wire.Metal m, double e0, double e1) {
        double hollomon = m.kPa * (Math.pow(e1, m.n + 1) - Math.pow(e0, m.n + 1)) / ((m.n + 1) * (e1 - e0));
        return Math.max(m.yieldAnnealedPa, hollomon);
    }

    /** Проход через волоку диаметра {@code dieM} с трением {@code mu} и тягой не больше {@code forceLimitN}. */
    public static Pass pass(Wire w, double dieM, double mu, double forceLimitN) {
        if (!(dieM > 0) || !(mu > 0) || !(forceLimitN > 0)) {
            throw new IllegalArgumentException("die, friction and force limit must be positive");
        }
        if (dieM >= w.diameterM()) return new Pass(w, Outcome.NO_REDUCTION, 0);
        double a0 = w.areaM2(), a1 = Math.PI * dieM * dieM / 4;
        double e1 = w.strain() + Math.log(a0 / a1);
        double sigma = stressPa(w.metal(), w.strain(), a0, a1, mu);
        double force = sigma * a1;
        if (force > forceLimitN) return new Pass(w, Outcome.TOO_STRONG, force);
        boolean breaks = sigma >= w.metal().yieldPa(e1) || 1 - a1 / a0 > MAX_REDUCTION || e1 > w.metal().maxStrain;
        if (breaks) {
            // обрыв у волоки: остаток прежнего диаметра, наклёп дошёл до предела — тянуть дальше только после отжига
            return new Pass(new Wire(w.metal(), w.diameterM(), w.lengthM(), Math.max(w.strain(),
                    Math.min(e1, w.metal().maxStrain))), Outcome.BROKE, force);
        }
        return new Pass(new Wire(w.metal(), dieM, w.lengthM() * a0 / a1, e1), Outcome.OK, force);
    }

    /** Отжиг: выше температуры рекристаллизации наклёп снимается экспоненциально, ниже — не меняется. */
    public static Wire anneal(Wire w, double tempK, double seconds) {
        if (!(seconds >= 0) || !Double.isFinite(tempK)) throw new IllegalArgumentException("seconds must be >= 0");
        if (tempK < w.metal().annealK) return w;
        double tau = ANNEAL_TAU_S * Math.exp(-(tempK - w.metal().annealK) / ANNEAL_TAU_K);
        double left = w.strain() * Math.exp(-seconds / tau);
        return new Wire(w.metal(), w.diameterM(), w.lengthM(), left < 1e-3 ? 0 : left);
    }
}
