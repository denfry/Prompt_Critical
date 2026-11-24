package dev.denfry.promptcritical.sim.hydrogen;

import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;

/**
 * Двухступенчатый мембранный компрессор водорода класса PDC (17 §17.3.8): из газгольдера (1,05–1,5 бар абс.) в
 * баллонную рампу до 200 бар. Мембрана отделяет газ от масла — водород остаётся чистым, без утечек по штоку.
 *
 * <p>Ступени делят отношение давлений поровну, r = √(p₂/p₁). Головки охлаждаются водой, поэтому сжатие политропное с
 * n = 1,2 вместо адиабатного 1,405; без воды охлаждения n = 1,35 и нет промежуточного охлаждения. Работа ступени
 * w = n/(n−1)·Z·R·T₁·(r^((n−1)/n) − 1), механический КПД 0,92. На 200 бар это 2,4 кВт·ч/кг — GDD даёт 2,2
 * [сверить], справочные 2–3 кВт·ч/кг (DOE H2A, 2015). Температура нагнетания T₂ = T₁·r^((n−1)/n), отключение выше
 * 230 °C (масло и мембрана).
 *
 * <p>Подача — описанный объём × объёмный КПД × плотность на всасывании: η_v = 1 − c·(r^(1/n) − 1), вредное
 * пространство c = 2 %. Реле давления: стоп на 200 бар нагнетания, пуск на 190; стоп при всасывании ниже 1,05 бар,
 * пуск выше 1,15.
 */
public final class DiaphragmCompressor {
    public static final int STAGES = 2;
    /** Описанный объём первой ступени, м³/с: около 480 Нм³/ч — подача одного электролизёра A485. */
    public static final double SWEPT_M3_S = 0.150;
    public static final double CLEARANCE = 0.02, N_COOLED = 1.2, N_UNCOOLED = 1.35, MECH_EFF = 0.92;
    public static final double STOP_DISCHARGE_PA = 200e5, START_DISCHARGE_PA = 190e5;
    public static final double STOP_SUCTION_PA = 1.05e5, START_SUCTION_PA = 1.15e5;
    public static final double TRIP_K = 503.15;
    /** Номинальная мощность двигателя, Вт (0,4 кВ). */
    public static final double MOTOR_W = 132e3;

    public enum Trip { NONE, DISCHARGE_TEMP }

    private boolean on, loaded;
    private Trip trip = Trip.NONE;
    private double molS, powerW, stage1OutK, stage2OutK, ratio;

    public boolean on() {
        return on;
    }

    public void setOn(boolean on) {
        this.on = on && trip == Trip.NONE;
    }

    public Trip trip() {
        return trip;
    }

    /** Сброс отключения (после того как головки остыли). */
    public void reset() {
        trip = Trip.NONE;
    }

    /** Работает на подачу (не стоит по реле давления). */
    public boolean loaded() {
        return loaded;
    }

    /**
     * Шаг.
     *
     * @param suctionPa   давление всасывания (газгольдер), Па абс.
     * @param suctionK    температура газа на всасывании, K
     * @param dischargePa давление нагнетания (рампа), Па абс.
     * @param speed       доля номинальной скорости от питания, 0…1
     * @param coolingK    температура воды охлаждения, K ({@code NaN} — воды нет)
     * @return подача, моль/с
     */
    public double step(double dtS, double suctionPa, double suctionK, double dischargePa, double speed, double coolingK) {
        if (loaded) {
            if (dischargePa >= STOP_DISCHARGE_PA || suctionPa < STOP_SUCTION_PA) loaded = false;
        } else if (dischargePa <= START_DISCHARGE_PA && suctionPa >= START_SUCTION_PA) loaded = true;
        boolean cooled = !Double.isNaN(coolingK);
        double n = cooled ? N_COOLED : N_UNCOOLED;
        ratio = Math.sqrt(Math.max(1, dischargePa / Math.max(1e3, suctionPa)));
        double tau = Math.pow(ratio, (n - 1) / n);
        double t1 = suctionK;
        stage1OutK = t1 * tau;
        double t2in = cooled ? Math.min(stage1OutK, coolingK + 10) : stage1OutK;
        stage2OutK = t2in * tau;
        boolean run = on && trip == Trip.NONE && loaded && speed > 0;
        if (!run) {
            molS = 0;
            powerW = 0;
            stage1OutK = stage2OutK = t1;
            return 0;
        }
        double etaV = Math.max(0, 1 - CLEARANCE * (Math.pow(ratio, 1 / n) - 1));
        molS = SWEPT_M3_S * speed * etaV * HydrogenGas.molarDensity(suctionPa, suctionK);
        double pMid = suctionPa * ratio;
        double w1 = n / (n - 1) * HydrogenGas.z(suctionPa, t1) * HydrogenGas.R * t1 * (tau - 1);
        double w2 = n / (n - 1) * HydrogenGas.z(pMid, t2in) * HydrogenGas.R * t2in * (tau - 1);
        powerW = molS * (w1 + w2) / MECH_EFF;
        if (Math.max(stage1OutK, stage2OutK) > TRIP_K) {
            trip = Trip.DISCHARGE_TEMP;
            on = false;
        }
        return molS;
    }

    /** Мощность на валу, которую просит двигатель, Вт. */
    public double powerW() {
        return powerW;
    }

    public double molS() {
        return molS;
    }

    public double nm3h() {
        return HydrogenGas.nm3(molS) * 3600;
    }

    /** Удельная работа сжатия, кВт·ч/кг (0 — стоит). */
    public double specificKwhPerKg() {
        return molS > 0 ? powerW / (molS * HydrogenGas.M) / 3.6e6 : 0;
    }

    public double stage1OutK() {
        return stage1OutK;
    }

    public double stage2OutK() {
        return stage2OutK;
    }

    public double stageRatio() {
        return ratio;
    }

    public void save(StateWriter out) {
        out.putBoolean("on", on);
        out.putBoolean("loaded", loaded);
        out.putInt("trip", trip.ordinal());
    }

    public void load(StateReader in) {
        on = in.optInt("on", 0) != 0;
        loaded = in.optInt("loaded", 0) != 0;
        int t = in.optInt("trip", 0);
        trip = t >= 0 && t < Trip.values().length ? Trip.values()[t] : Trip.NONE;
    }
}
