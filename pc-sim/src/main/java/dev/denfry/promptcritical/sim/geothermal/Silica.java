package dev.denfry.promptcritical.sim.geothermal;

/**
 * Кремнезём в геотермальном рассоле (15 §15.6.4). Содержание SiO₂ в пласте задаёт равновесие с кварцем, а отложения
 * в трубах и скважине закачки растут, когда рассол после флеша и охлаждения пересыщен по аморфному кремнезёму.
 * Растворимости — Fournier (1977), «Chemical geothermometers and mixing models», Geothermics 5:
 * <ul>
 *   <li>кварц без потери пара: log₁₀ C = 5,19 − 1309/T;</li>
 *   <li>аморфный кремнезём: log₁₀ C = 4,52 − 731/T;</li>
 * </ul>
 * C — мг/кг, T — К. Пласт 250 °C даёт 487 мг/кг; после флеша до 150 °C концентрация растёт в 1/(1 − x) раз до
 * ≈ 620 мг/кг, что как раз равно растворимости аморфного кремнезёма при 150 °C — поэтому реальные станции не
 * сбрасывают рассол холоднее ≈ 150–160 °C без ингибитора.
 */
public final class Silica {
    private Silica() {}

    /** Равновесие с кварцем при температуре пласта, мг/кг. */
    public static double quartzMgKg(double tK) {
        return Math.pow(10, 5.19 - 1309 / tK);
    }

    /** Растворимость аморфного кремнезёма, мг/кг. */
    public static double amorphousMgKg(double tK) {
        return Math.pow(10, 4.52 - 731 / tK);
    }

    /** Концентрация в рассоле после флеша с долей пара x: пар уносит воду, кремнезём остаётся. */
    public static double afterFlashMgKg(double reservoirMgKg, double steamFraction) {
        return reservoirMgKg / Math.max(1e-6, 1 - Math.max(0, Math.min(0.99, steamFraction)));
    }

    /** Индекс насыщения по аморфному кремнезёму: больше 1 — рассол пересыщен и откладывает SiO₂. */
    public static double saturationIndex(double concentrationMgKg, double tK) {
        return concentrationMgKg / amorphousMgKg(tK);
    }

    /**
     * Самая низкая температура сброса, при которой рассол с концентрацией {@code concentrationMgKg} ещё не
     * пересыщен, К (обращение формулы аморфной растворимости).
     */
    public static double minRejectTempK(double concentrationMgKg) {
        return 731 / (4.52 - Math.log10(Math.max(1e-6, concentrationMgKg)));
    }
}
