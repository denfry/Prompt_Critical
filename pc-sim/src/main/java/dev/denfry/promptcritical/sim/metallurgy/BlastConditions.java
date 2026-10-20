package dev.denfry.promptcritical.sim.metallurgy;

/**
 * Дутьё домны на шаг SLOW.
 *
 * @param blastNm3S     расход сухого дутья, нм³/с физ.
 * @param blastTempK    температура горячего дутья, К
 * @param oxygenFrac    O₂ в дутье (0,21; обогащение — больше)
 * @param moistureKgNm3 влага дутья, кг/нм³ (≈ 0,01)
 * @param cooling       вода холодильников и фурм подаётся
 */
public record BlastConditions(double blastNm3S, double blastTempK, double oxygenFrac, double moistureKgNm3,
        boolean cooling) {
    public static final double AIR_O2 = 0.21, TYPICAL_MOISTURE = 0.010;

    public BlastConditions {
        if (!(blastNm3S >= 0 && blastTempK > 200 && oxygenFrac >= AIR_O2 - 1e-9 && oxygenFrac <= 0.40
                && moistureKgNm3 >= 0 && moistureKgNm3 < 0.1)) throw new IllegalArgumentException("blast");
    }

    /** Воздух с типичной влагой, охлаждение в норме. */
    public static BlastConditions air(double blastNm3S, double blastTempK) {
        return new BlastConditions(blastNm3S, blastTempK, AIR_O2, TYPICAL_MOISTURE, true);
    }
}
