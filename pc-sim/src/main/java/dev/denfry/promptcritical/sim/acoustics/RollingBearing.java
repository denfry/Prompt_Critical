package dev.denfry.promptcritical.sim.acoustics;

import dev.denfry.promptcritical.sim.ops.Weibull;
import java.util.SplittableRandom;

/**
 * Подшипник качения: накопление повреждения D_b по Пальмгрену — Майнеру и стадии развития дефекта (04a §4a.7).
 *
 * <p>Ресурс по ISO 281:2007: L_10 = (C/P)^p·10⁶ оборотов, p = 3 для шариковых. При заданной нагрузке ресурс в часах
 * обратно пропорционален скорости: L_10h(n) = L_10h,nom·n_nom/n; рост нагрузки P/P₀ укорачивает его в (P/P₀)^p раз.
 * Шаг: D_b += Δt_phys·k_wear / (u_b·L_10h·a_lub), где u_b — множитель ресурса экземпляра по Вейбуллу (β = 1,5,
 * P(u_b &lt; 1) = 0,1 — определение L_10, {@link Weibull#bearingLifeFactor}).
 *
 * <p>Стадии (04a §4a.7.3; классическая четырёхстадийная картина, напр. Berry J. E., «How to track rolling element bearing
 * health with vibration signature analysis», Sound and Vibration, Nov. 1991): 1 — рост огибающей ВЧ, 2 — дискретные
 * пики дефектных частот, 3 — гармоники, слышный шум, нагрев корпуса, 4 — широкополосный шум, рост 1x; D_b = 1 —
 * заклинивание.
 */
public final class RollingBearing {
    /** Проектный ресурс подшипников машин, ч (04a §4a.7.1). */
    public static final double L10H_NOM = 50_000;
    /** Показатель ресурса шарикового подшипника. */
    public static final double LIFE_EXPONENT_BALL = 3;
    /** Начала стадий 1–4 и отказа по D_b. */
    static final double[] STAGE_START = {0, 0.6, 0.8, 0.9, 0.97, 1.0};
    /** Нагрев корпуса над окружающей средой на номинальной скорости, К [BALANCE]: трение растёт со скоростью. */
    public static final double RUNNING_RISE_K = 30;

    private double lifeFactor, damage;
    private boolean seized;

    public RollingBearing(double lifeFactor, double damage) {
        if (!(lifeFactor > 0) || !(damage >= 0 && damage <= 1)) throw new IllegalArgumentException("bearing");
        this.lifeFactor = lifeFactor;
        this.damage = damage;
        this.seized = damage >= 1;
    }

    public static RollingBearing install(SplittableRandom rnd) {
        return new RollingBearing(Weibull.bearingLifeFactor(rnd), 0);
    }

    /**
     * L_10h при относительной скорости и нагрузке, ч.
     *
     * @param speedRatio n/n_nom (больше нуля)
     * @param loadRatio  P/P₀ (не меньше 1 в модели машины)
     */
    public static double l10h(double speedRatio, double loadRatio) {
        return L10H_NOM / speedRatio / Math.pow(loadRatio, LIFE_EXPONENT_BALL);
    }

    /**
     * Шаг износа.
     *
     * @param dtPhysS        физическое время, с
     * @param speedRatio     n/n_nom; 0 — вал стоит, износа нет
     * @param loadRatio      P/P₀
     * @param wearMultiplier k_wear ({@code acoustics.bearing_wear_multiplier})
     * @param lubrication    a_lub: 1 — норма
     * @return true, если подшипник заклинило на этом шаге
     */
    public boolean step(double dtPhysS, double speedRatio, double loadRatio, double wearMultiplier, double lubrication) {
        if (seized || !(dtPhysS > 0) || !(speedRatio > 0)) return false;
        damage += dtPhysS / 3600 * wearMultiplier / (lifeFactor * l10h(speedRatio, loadRatio) * lubrication);
        if (damage >= 1) {
            damage = 1;
            seized = true;
            return true;
        }
        return false;
    }

    public double damage() {
        return damage;
    }

    public double lifeFactor() {
        return lifeFactor;
    }

    public boolean seized() {
        return seized;
    }

    /** Стадия 0–4; заклинивший — 4. */
    public int stage() {
        int s = 0;
        for (int i = 1; i <= 4; i++) if (damage >= STAGE_START[i]) s = i;
        return s;
    }

    /** h_i стадии 1–4: 0 → 1 внутри стадии, дальше 1. */
    public double h(int stage) {
        if (stage < 1 || stage > 4) throw new IllegalArgumentException("stage " + stage);
        double lo = STAGE_START[stage], hi = STAGE_START[stage + 1];
        return Math.clamp((damage - lo) / (hi - lo), 0, 1);
    }

    /** Температура корпуса, K: нагрев трением ∝ скорости и стадии 3–4 (04a §4a.7.3: +10 K и +30 K). */
    public double housingTempK(double ambientK, double speedRatio) {
        return ambientK + RUNNING_RISE_K * Math.max(0, speedRatio) + 10 * h(3) + 30 * h(4);
    }

    /** Замена подшипника: D_b → 0, новый u_b. */
    public void replace(double newLifeFactor) {
        if (!(newLifeFactor > 0)) throw new IllegalArgumentException("lifeFactor");
        lifeFactor = newLifeFactor;
        damage = 0;
        seized = false;
    }
}
