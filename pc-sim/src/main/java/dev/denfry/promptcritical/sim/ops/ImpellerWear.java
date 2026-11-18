package dev.denfry.promptcritical.sim.ops;

import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;
import dev.denfry.promptcritical.sim.hydraulics.PumpCavitation;

/**
 * Коррозия с эрозией рабочего колеса {@code mine_drainage_pump} (13 §13.6.7): потеря напора e растёт с наработкой,
 * в кислой {@code mine_water} (pH &lt; 5) втрое быстрее. Напор насоса — H·(1 − e), при обратном вращении ещё ×0,4
 * (13 §13.6.6). Скорость [BALANCE, моё]: 50 % потери за 20 000 ч в нейтральной воде.
 *
 * <p>Кавитационная эрозия W (04a §4a.3.3) — отдельный износ того же колеса: dW/dt = κ³/T_erosion, напор ×(1 − 0,25W),
 * NPSH_r ×(1 + 0,5W) ({@link PumpCavitation}). При W = 1 колесо пробито — насос не качает, пока его не заменят
 * (та же работа {@code pump_impeller}, что и при коррозии).
 */
public final class ImpellerWear implements FaultSource {
    public static final double NEUTRAL_RATE_PER_H = 1.0 / 40_000;
    public static final double ACID_FACTOR = 3;
    public static final double ACID_PH = 5;
    public static final double REVERSED_HEAD_FACTOR = 0.4;
    /** Колесо, съеденное до ступицы, всё же что-то качает. */
    public static final double MAX_EROSION = 0.95;

    private double erosion, cavitation;

    public void step(double dtPhysS, double waterPh) {
        if (!(dtPhysS > 0)) return;
        double rate = NEUTRAL_RATE_PER_H * (waterPh < ACID_PH ? ACID_FACTOR : 1);
        erosion = Math.min(MAX_EROSION, erosion + dtPhysS / 3600 * rate);
    }

    public double erosion() {
        return erosion;
    }

    /** Шаг кавитационной эрозии при интенсивности κ за {@code dtPhysS} физических секунд. */
    public void stepCavitation(double dtPhysS, double kappa) {
        if (!(dtPhysS > 0)) return;
        cavitation = Math.min(1, cavitation + PumpCavitation.erosionRate(kappa) * dtPhysS);
    }

    /** Кавитационный износ колеса W, 0–1. */
    public double cavitation() {
        return cavitation;
    }

    /** Колесо пробито кавитацией. */
    public boolean holed() {
        return cavitation >= 1;
    }

    public void setCavitation(double w) {
        if (!(w >= 0 && w <= 1)) throw new IllegalArgumentException("cavitation: " + w);
        cavitation = w;
    }

    /** Множитель характеристики H(Q) для {@code MineSump.PumpSpec}. */
    public double headFactor(boolean reversed) {
        if (holed()) return 0;
        return (reversed ? REVERSED_HEAD_FACTOR : 1) * (1 - erosion) * (1 - PumpCavitation.WEAR_HEAD_LOSS * cavitation);
    }

    /** Износ задан (сценарии, тесты). */
    public void setErosion(double e) {
        if (!(e >= 0 && e <= MAX_EROSION)) throw new IllegalArgumentException("erosion: " + e);
        erosion = e;
    }

    public void save(StateWriter out) {
        out.putDouble("erosion", erosion);
        out.putDouble("cavitation", cavitation);
    }

    public void load(StateReader in) {
        erosion = in.getDouble("erosion");
        cavitation = in.has("cavitation") ? Math.max(0, Math.min(1, in.getDouble("cavitation"))) : 0;
    }

    public void replace() {
        erosion = 0;
        cavitation = 0;
    }

    @Override
    public MaintenanceFaultKind kind() {
        return MaintenanceFaultKind.IMPELLER_EROSION;
    }

    @Override
    public double value() {
        return Math.max(erosion, cavitation);
    }

    @Override
    public void repair() {
        replace();
    }
}
