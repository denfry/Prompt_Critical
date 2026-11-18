package dev.denfry.promptcritical.sim.electronics;

/**
 * Пластины из слитка (15 §15.6): резка внутренней кромкой и полировка.
 *
 * <p>Пластина 2 дюйма (50 мм, начало 1970-х) толщиной 280 мкм, пропил диска с внутренней алмазной кромкой — 300 мкм.
 * Полировка снимает нарушенный резкой слой ≈ 20 мкм; скорость по Престону MRR = K_p·p·v, K_p ≈ 3·10⁻¹³ Па⁻¹ для
 * кремния со щелочной суспензией кремнезёма (Preston 1927; Steigerwald, «Chemical Mechanical Planarization», 1997):
 * при p = 30 кПа и v = 1 м/с — 0,5 мкм/мин.
 */
public final class Wafering {
    public static final double WAFER_MM = 50, THICKNESS_UM = 280, KERF_UM = 300, DAMAGE_UM = 20, PRESTON_PA = 3e-13, PRESSURE_PA = 30e3;

    private Wafering() {
    }

    /** Пластин из слитка длиной {@code lengthMm}. */
    public static int wafersPerIngot(double lengthMm) {
        return (int) Math.floor(lengthMm * 1000 / (THICKNESS_UM + KERF_UM));
    }

    /** Скорость съёма по Престону, мкм/мин, при скорости полировальника {@code vMs}. */
    public static double removalUmMin(double vMs) {
        return PRESTON_PA * PRESSURE_PA * Math.max(0, vMs) * 1e6 * 60;
    }

    /** Время полировки одной пластины, мин. */
    public static double polishMinutes(double vMs) {
        double r = removalUmMin(vMs);
        return r > 0 ? DAMAGE_UM / r : Double.POSITIVE_INFINITY;
    }
}
