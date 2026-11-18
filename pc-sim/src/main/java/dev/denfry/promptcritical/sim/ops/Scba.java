package dev.denfry.promptcritical.sim.ops;

/**
 * Изолирующий дыхательный аппарат на сжатом воздухе {@code scba} (13 §13.7): баллон 6,8 л × 30 МПа ≈
 * {@value #AIR_L} л воздуха, расход {@value #CONSUMPTION_LPM} л/мин — около 50 мин; при остатке
 * {@value #WHISTLE_FRACTION} — сигнальный свисток. Кратность {@value Ppe#SCBA_FACTOR}, работает при любом O₂;
 * пустой аппарат не защищает. [BALANCE] {@code ops.scba_air_l}, {@code ops.scba_consumption_lpm}.
 */
public final class Scba {
    public static final double AIR_L = 2000, CONSUMPTION_LPM = 40, WHISTLE_FRACTION = 0.2;

    private double airL;

    public Scba(double airL) {
        this.airL = Math.clamp(airL, 0, AIR_L);
    }

    public double airL() {
        return airL;
    }

    public boolean empty() {
        return airL <= 0;
    }

    /** Остаток ниже порога свистка. */
    public boolean whistle() {
        return airL < AIR_L * WHISTLE_FRACTION;
    }

    /** Минут дыхания осталось. */
    public double minutesLeft() {
        return airL / CONSUMPTION_LPM;
    }

    /**
     * {@code dtS} секунд дыхания из аппарата.
     *
     * @return аппарат подавал воздух весь шаг (изолирует)
     */
    public boolean breathe(double dtS) {
        if (empty()) return false;
        airL = Math.max(0, airL - CONSUMPTION_LPM * dtS / 60);
        return true;
    }

    /** Концентрация под маской работающего аппарата. */
    public static double inhaled(double concentration) {
        return concentration / Ppe.SCBA_FACTOR;
    }
}
