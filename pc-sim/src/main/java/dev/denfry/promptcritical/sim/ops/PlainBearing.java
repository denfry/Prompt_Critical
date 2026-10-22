package dev.denfry.promptcritical.sim.ops;

import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;
import java.util.SplittableRandom;

/**
 * Подшипник скольжения с бронзовыми вкладышами (13 §13.6.7): повреждение D_b по Пальмгрену — Майнеру (04a §4a.7.2),
 * стадии 04a §4a.7.3, отказ — заплавление вкладыша ({@code SEIZED}). Стадии проявляются температурой корпуса и
 * стуком: T = T_окр + 30 + 10·h₃ + 30·h₄ [BALANCE, моё]: к концу стадии 4 корпус обжигает (> 353 K).
 */
public final class PlainBearing implements FaultSource {
    /** L_10h,nom машин I0, ч [BALANCE]. */
    public static final double L10H_I0 = 30_000;
    /** Начала стадий 1–4 и отказа по D_b. */
    static final double[] STAGE_START = {0, 0.6, 0.8, 0.9, 0.97, 1.0};
    /** Нагрев корпуса над окружающей средой в работе, К. */
    public static final double RUNNING_RISE_K = 30;
    public static final double WARM_LIMIT_K = 328, HOT_LIMIT_K = 353;

    /** Что чувствует рука на корпусе (13 §13.6.7): выше 353 K — 1 ед. урона. */
    public enum HandFeel { WARM, HOT, SCALDING }

    private final double l10h;
    private double lifeFactor, damage;
    private boolean seized;

    public PlainBearing(double l10h, double lifeFactor) {
        if (!(l10h > 0 && lifeFactor > 0)) throw new IllegalArgumentException("bearing");
        this.l10h = l10h;
        this.lifeFactor = lifeFactor;
    }

    /** Новый подшипник с u_b по Вейбуллу. */
    public static PlainBearing install(double l10h, SplittableRandom rnd) {
        return new PlainBearing(l10h, Weibull.bearingLifeFactor(rnd));
    }

    /**
     * Шаг SLOW: D_b += Δt_phys·k_wear / (u_b·L_10h·a_lub).
     *
     * @return true, если подшипник заплавился на этом шаге
     */
    public boolean step(double dtPhysS, double wearMultiplier, double lubricationFactor) {
        if (seized || !(dtPhysS > 0)) return false;
        damage += dtPhysS / 3600 * wearMultiplier / (lifeFactor * l10h * lubricationFactor);
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

    /** Стадия 0–4 (04a §4a.7.3); заплавленный — 4. */
    public int stage() {
        int s = 0;
        for (int i = 1; i <= 4; i++) if (damage >= STAGE_START[i]) s = i;
        return s;
    }

    /** h_i стадии 1–4: 0 → 1 внутри стадии. */
    public double h(int stage) {
        if (stage < 1 || stage > 4) throw new IllegalArgumentException("stage " + stage);
        double lo = STAGE_START[stage], hi = STAGE_START[stage + 1];
        return Math.clamp((damage - lo) / (hi - lo), 0, 1);
    }

    /** Температура корпуса работающей машины, K. */
    public double housingTempK(double ambientK) {
        return ambientK + RUNNING_RISE_K + 10 * h(3) + 30 * h(4);
    }

    public static HandFeel feel(double housingK) {
        return housingK < WARM_LIMIT_K ? HandFeel.WARM : housingK < HOT_LIMIT_K ? HandFeel.HOT : HandFeel.SCALDING;
    }

    /** Замена вкладыша: D_b → 0, новый u_b. */
    public void replace(double newLifeFactor) {
        if (!(newLifeFactor > 0)) throw new IllegalArgumentException("lifeFactor");
        lifeFactor = newLifeFactor;
        damage = 0;
        seized = false;
    }

    @Override
    public MaintenanceFaultKind kind() {
        return MaintenanceFaultKind.BEARING_WEAR;
    }

    @Override
    public double value() {
        return damage;
    }

    /** Повреждение задано (сценарии 11 §11.3, тесты); D_b = 1 — заплавлен. */
    public void setDamage(double d) {
        if (!(d >= 0 && d <= 1)) throw new IllegalArgumentException("damage: " + d);
        damage = d;
        seized = d >= 1;
    }

    public void save(StateWriter out) {
        out.putDouble("damage", damage);
        out.putDouble("life_factor", lifeFactor);
        out.putBoolean("seized", seized);
    }

    public void load(StateReader in) {
        damage = in.getDouble("damage");
        lifeFactor = in.getDouble("life_factor");
        seized = in.getBoolean("seized");
    }

    /** Ремонт с панели без своего зерна: u_b прежний. Мир с зерном вызывает {@link #replace(double)}. */
    @Override
    public void repair() {
        replace(lifeFactor);
    }
}
