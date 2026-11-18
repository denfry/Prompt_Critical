package dev.denfry.promptcritical.sim.reactor;

/**
 * Счётчик непрерывной работы реактора для достижения {@code hundred_days} (11 §11.2.2: «100 суток SLOW без АЗ, кроме
 * ручных»). Копит физическое время на мощности; автоматическая АЗ (по любому параметру или внешняя ПАЗ) обнуляет счёт,
 * ручная — нет. Время стоянки не идёт в счёт, но и не обнуляет его.
 */
public final class ContinuousRun {
    /** Цель: 100 физических суток, с. */
    public static final double TARGET_S = 100 * 86_400.0;
    /** Реактор «на мощности» от этой доли номинала. */
    public static final double AT_POWER = 0.1;

    private double runS;
    private boolean reported;

    /**
     * @param dtPhysS       физический шаг, с (в SLOW — с ускорением мира)
     * @param power         мощность, доли номинала
     * @param automaticTrip сработала автоматическая АЗ (не ручная)
     * @return {@code true} в шаге, когда набраны 100 суток
     */
    public boolean update(double dtPhysS, double power, boolean automaticTrip) {
        if (automaticTrip) {
            runS = 0;
            reported = false;
            return false;
        }
        if (power >= AT_POWER) runS += dtPhysS;
        if (!reported && runS >= TARGET_S) {
            reported = true;
            return true;
        }
        return false;
    }

    /** Набранное время работы без автоматической АЗ, сутки. */
    public double days() {
        return runS / 86_400.0;
    }

    public double seconds() {
        return runS;
    }

    /** Восстановление из сохранения (NBT блока). */
    public void restore(double seconds) {
        runS = Math.max(0, seconds);
        reported = runS >= TARGET_S;
    }
}
