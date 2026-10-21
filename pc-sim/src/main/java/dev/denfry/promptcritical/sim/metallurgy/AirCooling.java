package dev.denfry.promptcritical.sim.metallurgy;

/**
 * Остывание раската, слитков и труб на воздухе (08 §8.7, FAST — реальные секунды): лумпед по толщине стенки,
 * излучение εσ(T⁴ − T₀⁴) с ε {@value #EMISSIVITY} и конвекция {@value #CONVECTION_W_M2K} Вт/(м²·К), обе стороны,
 * A/V = 2/t [моё]. Заготовка 150 мм с 1200 °C за 20 мин остаётся выше 500 °C, лист 8 мм за 5 мин — ниже 500 °C. Предмет хранит T₀ и тик
 * выдачи, температура считается по прошедшему времени — без тика.
 */
public final class AirCooling {
    public static final double EMISSIVITY = 0.8, SIGMA = 5.670e-8, CONVECTION_W_M2K = 10, STEEL_CP = 650;
    /** Не дольше этого считаем — дальше металл уже комнатный, с. */
    private static final double HORIZON_S = 6 * 3_600;
    private static final int MAX_STEPS = 400;

    private AirCooling() {}

    /** Температура через {@code seconds} после {@code t0K} на воздухе {@code ambientK}, К. */
    public static double tempAfter(double t0K, double ambientK, double thicknessM, double seconds) {
        if (!(thicknessM > 0)) throw new IllegalArgumentException("thickness");
        if (!(seconds > 0) || t0K <= ambientK) return t0K;
        double left = Math.min(seconds, HORIZON_S), t = t0K;
        double k = 2 / thicknessM / (StockState.DENSITY * STEEL_CP);
        // шаг — пятая часть мгновенной постоянной времени, не меньше горизонта / MAX_STEPS
        double minStep = left / MAX_STEPS;
        while (left > 0 && t - ambientK > 0.05) {
            double h = hEff(t, ambientK);
            double dt = Math.min(left, Math.max(minStep, 0.2 / (k * h)));
            double k1 = -k * flux(t, ambientK);
            double tm = t + k1 * dt / 2;
            double k2 = -k * flux(Math.max(ambientK, tm), ambientK);
            t = Math.max(ambientK, t + k2 * dt);
            left -= dt;
        }
        return seconds > HORIZON_S ? ambientK : t;
    }

    private static double flux(double t, double ambient) {
        return EMISSIVITY * SIGMA * (t * t * t * t - ambient * ambient * ambient * ambient) + CONVECTION_W_M2K * (t - ambient);
    }

    private static double hEff(double t, double ambient) {
        return EMISSIVITY * SIGMA * (t * t + ambient * ambient) * (t + ambient) + CONVECTION_W_M2K;
    }
}
