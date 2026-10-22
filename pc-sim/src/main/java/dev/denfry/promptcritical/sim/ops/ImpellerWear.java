package dev.denfry.promptcritical.sim.ops;

import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;

/**
 * Коррозия с эрозией рабочего колеса {@code mine_drainage_pump} (13 §13.6.7): потеря напора e растёт с наработкой,
 * в кислой {@code mine_water} (pH &lt; 5) втрое быстрее. Напор насоса — H·(1 − e), при обратном вращении ещё ×0,4
 * (13 §13.6.6). Скорость [BALANCE, моё]: 50 % потери за 20 000 ч в нейтральной воде.
 */
public final class ImpellerWear implements FaultSource {
    public static final double NEUTRAL_RATE_PER_H = 1.0 / 40_000;
    public static final double ACID_FACTOR = 3;
    public static final double ACID_PH = 5;
    public static final double REVERSED_HEAD_FACTOR = 0.4;
    /** Колесо, съеденное до ступицы, всё же что-то качает. */
    public static final double MAX_EROSION = 0.95;

    private double erosion;

    public void step(double dtPhysS, double waterPh) {
        if (!(dtPhysS > 0)) return;
        double rate = NEUTRAL_RATE_PER_H * (waterPh < ACID_PH ? ACID_FACTOR : 1);
        erosion = Math.min(MAX_EROSION, erosion + dtPhysS / 3600 * rate);
    }

    public double erosion() {
        return erosion;
    }

    /** Множитель характеристики H(Q) для {@code MineSump.PumpSpec}. */
    public double headFactor(boolean reversed) {
        return (reversed ? REVERSED_HEAD_FACTOR : 1) * (1 - erosion);
    }

    /** Износ задан (сценарии, тесты). */
    public void setErosion(double e) {
        if (!(e >= 0 && e <= MAX_EROSION)) throw new IllegalArgumentException("erosion: " + e);
        erosion = e;
    }

    public void save(StateWriter out) {
        out.putDouble("erosion", erosion);
    }

    public void load(StateReader in) {
        erosion = in.getDouble("erosion");
    }

    public void replace() {
        erosion = 0;
    }

    @Override
    public MaintenanceFaultKind kind() {
        return MaintenanceFaultKind.IMPELLER_EROSION;
    }

    @Override
    public double value() {
        return erosion;
    }

    @Override
    public void repair() {
        replace();
    }
}
