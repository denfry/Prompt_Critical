package dev.denfry.promptcritical.sim.reactor;

import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;

/**
 * Наблюдатель эпизода «йодная яма» для достижения {@code xenon_pit} (11 §11.2.2): реактор работал на мощности,
 * мощность провалилась, ксенон после провала вырос (яма), и реактор снова выведен на мощность, а оперативный запас
 * реактивности ни разу не опускался ниже минимума и ни один канал не разрушился (04c §4c.9.2–4c.9.3: «не выводить
 * стержни ниже минимального ОЗР»). Нарушение ОЗР или разрушение канала обнуляет эпизод: нужен новый провал с мощности.
 */
public final class XenonPitWatch {
    /** Мощность «на мощности» и «провал», доли номинала (04c §4c.9.2: провал до ~1 %, работа на 50 %). */
    public static final double AT_POWER = 0.5, DIP = 0.1;
    /**
     * Ксенон после провала должен отнять ещё столько реактивности, Δk/k, чтобы провал считался ямой, а не короткой
     * просадкой. [BALANCE] 200 pcm — заметно больше шума регулятора и намного меньше пика ямы АМ-1 (тест
     * {@code iodinePitPeaksAboutTenHoursAfterShutdown}).
     */
    public static final double PIT_DEPTH = 0.002;

    public enum Phase { IDLE, AT_POWER, DIPPED, IN_PIT }

    private Phase phase = Phase.IDLE;
    private double xenonAtDip, minMargin = Double.POSITIVE_INFINITY;
    private int failedAtDip;
    private boolean spoiled;

    /**
     * Шаг наблюдения.
     *
     * @param power      мощность, доли номинала
     * @param marginRods ОЗР, эквивалентных стержней
     * @param xenonRho   реактивность ксенона, Δk/k (отрицательная)
     * @param failed     разрушенных каналов
     * @return {@code true} в шаге, когда реактор вышел из ямы без нарушений
     */
    public boolean update(double power, double marginRods, double xenonRho, int failed) {
        spoiled = false;
        if (phase == Phase.DIPPED || phase == Phase.IN_PIT) {
            minMargin = Math.min(minMargin, marginRods);
            if (marginRods < ChannelReactor.ORM_MIN_RODS || failed > failedAtDip) {
                phase = Phase.IDLE;
                spoiled = true;
                return false;
            }
        }
        switch (phase) {
            case IDLE -> {
                if (power >= AT_POWER) phase = Phase.AT_POWER;
            }
            case AT_POWER -> {
                if (power < DIP) {
                    phase = Phase.DIPPED;
                    xenonAtDip = xenonRho;
                    failedAtDip = failed;
                    minMargin = marginRods;
                }
            }
            case DIPPED -> {
                if (xenonRho <= xenonAtDip - PIT_DEPTH) phase = Phase.IN_PIT;
                else if (power >= AT_POWER) phase = Phase.AT_POWER;
            }
            case IN_PIT -> {
                if (power >= AT_POWER) {
                    phase = Phase.AT_POWER;
                    return true;
                }
            }
        }
        return false;
    }

    public Phase phase() {
        return phase;
    }

    /** Наименьший ОЗР за текущий (или только что законченный) эпизод, эквивалентных стержней. */
    public double minMargin() {
        return minMargin;
    }

    /** Эпизод сорван в последнем шаге (ОЗР ниже минимума или разрушен канал). */
    public boolean spoiled() {
        return spoiled;
    }

    public void save(StateWriter out) {
        out.putString("phase", phase.name());
        out.putDouble("xe", xenonAtDip);
        out.putDouble("orm", Double.isFinite(minMargin) ? minMargin : -1);
        out.putInt("failed", failedAtDip);
    }

    public void load(StateReader in) {
        try {
            phase = Phase.valueOf(in.optString("phase", Phase.IDLE.name()));
        } catch (IllegalArgumentException e) {
            phase = Phase.IDLE;
        }
        xenonAtDip = in.optDouble("xe", 0);
        double m = in.optDouble("orm", -1);
        minMargin = m < 0 ? Double.POSITIVE_INFINITY : m;
        failedAtDip = in.optInt("failed", 0);
    }
}
