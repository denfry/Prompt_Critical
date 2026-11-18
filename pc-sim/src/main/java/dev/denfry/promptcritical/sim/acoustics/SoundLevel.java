package dev.denfry.promptcritical.sim.acoustics;

/**
 * Уровни шума машин (04a §4a.2.1, §4a.4.8, §4a.7.4). Все уровни — А-взвешенные, дБ(А): эмпирика мощности шума машин
 * задана по корректированному уровню звуковой мощности L_WA.
 *
 * <ul>
 *   <li>Уровень у слушателя для точечного источника в свободном поле: L_p = L_W − 20·lg(r/1 м) − 11 − ΣTL, где
 *       11 дБ = 10·lg(4π) — сферическое расхождение (ISO 9613-2:1996, A_div = 20·lg(d) + 11). Ближе 1 м формула
 *       точечного источника неприменима — расстояние прижимается к 1 м.</li>
 *   <li>Сложение некогерентных источников: L = 10·lg Σ 10^(L_i/10) (энергетическое; два одинаковых — +3 дБ).</li>
 *   <li>Звуковая мощность машины: L_W = 70 + 10·lg(P/1 кВт) [BALANCE, 04a §4a.4.8 — порядок 10 дБ на декаду
 *       мощности для центробежных машин]; дефект подшипника добавляет слой L_W − 20 + 25·h₃ + 10·h₄.</li>
 * </ul>
 */
public final class SoundLevel {
    public static final double LW0_DB = 70;
    /** Ниже 100 Вт машина считается стоящей тихо. */
    public static final double MIN_POWER_W = 100;
    /** Потолок звукоизоляции луча (обходные пути через стыки), дБ (04a §4a.2.2). */
    public static final double MAX_OCCLUSION_DB = 45;
    /** 10·lg(4π). */
    public static final double SPHERICAL_DB = 10 * Math.log10(4 * Math.PI);

    private SoundLevel() {
    }

    /** Энергетическая сумма уровней, дБ; пустая — −∞. */
    public static double sum(double... levelsDb) {
        double e = 0;
        for (double l : levelsDb) if (Double.isFinite(l)) e += Math.pow(10, l / 10);
        return e > 0 ? 10 * Math.log10(e) : Double.NEGATIVE_INFINITY;
    }

    /**
     * Уровень звукового давления у слушателя, дБ(А).
     *
     * @param lwDb        уровень звуковой мощности источника
     * @param distanceM   расстояние, м
     * @param occlusionDb суммарная звукоизоляция блоков на луче (обрезается до {@link #MAX_OCCLUSION_DB})
     */
    public static double atDistance(double lwDb, double distanceM, double occlusionDb) {
        double r = Math.max(1, distanceM);
        return lwDb - 20 * Math.log10(r) - SPHERICAL_DB - Math.min(Math.max(0, occlusionDb), MAX_OCCLUSION_DB);
    }

    /** L_WA работающей машины по мощности на валу, дБ(А). */
    public static double machineLw(double shaftPowerW) {
        return LW0_DB + 10 * Math.log10(Math.max(MIN_POWER_W, shaftPowerW) / 1000);
    }

    /** Машина с дефектом подшипника: основной шум плюс слой подшипника (стадии 3–4 слышны, 1–2 — нет). */
    public static double machineLw(double shaftPowerW, RollingBearing bearing) {
        double run = machineLw(shaftPowerW);
        return sum(run, run - 20 + 25 * bearing.h(3) + 10 * bearing.h(4));
    }
}
