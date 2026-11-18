package dev.denfry.promptcritical.sim.environment;

/**
 * Загрязнение воздуха (14 §14.1–14.4): подъём дымового факела, приземная концентрация от трубы, кислотность дождя,
 * индекс кислотной нагрузки и пороги эффектов.
 *
 * <p><b>Подъём факела</b> — Бриггс (1975, как в EPA ISC3): F = g·v·d²·(T_s − T_a)/(4·T_s),
 * Δh = 21,425·F^{3/4}/u при F &lt; 55 м⁴/с³, иначе 38,71·F^{3/5}/u; u не ниже 1 м/с.
 *
 * <p><b>Приземная концентрация</b> — Паскуилл–Гиффорд с отражением от земли (Turner 1970), нейтральный класс D для
 * открытой местности (Briggs 1973): σ_y = 0,08x(1 + 10⁻⁴x)^{−1/2}, σ_z = 0,06x(1 + 1,5·10⁻³x)^{−1/2}.
 *
 * <p><b>Дождь</b>: [H⁺] = 10^{−5,6} + a_S·c̄_SO₂ + 0,696·a_S·c̄_NO₂, a_S = 6,06·10⁻⁷ моль/л на мкг/м³ — 100 мкг/м³ SO₂
 * дают pH 4,2 ({@code env.rain_acid_coeff}). <b>Индекс нагрузки</b> Φ = (2D_S/64 + D_N/46)/D_crit, D в мг/м², D_crit =
 * 0,1 мэкв/м² (UNECE, масштаб условный).
 */
public final class Pollution {
    public static final double G = 9.80665, RAIN_ACID_COEFF = 6.06e-7, D_CRIT_MEQ = 0.1;

    /** Вещества слоёв загрязнения; CO₂ и CH₄ идут только в учёт квот. */
    public enum Species { SO2, NOX, PM }

    /** Уровни эффектов §14.4.1. */
    public enum Level { CLEAN, HAZE, SMOG, WITHERING, COUGH, WEAKNESS, ACID_RAIN }

    private Pollution() {
    }

    /** Параметр плавучести, м⁴/с³. */
    public static double buoyancyFlux(double exitVelocityMs, double diameterM, double exitTempK, double ambientK) {
        if (exitTempK <= ambientK) return 0;
        return G * exitVelocityMs * diameterM * diameterM * (exitTempK - ambientK) / (4 * exitTempK);
    }

    /** Окончательный подъём факела по Бриггсу, м. */
    public static double plumeRise(double flux, double windMs) {
        double u = Math.max(1, windMs);
        if (flux <= 0) return 0;
        return flux < 55 ? 21.425 * Math.pow(flux, 0.75) / u : 38.71 * Math.pow(flux, 0.6) / u;
    }

    public static double sigmaY(double xM) {
        return 0.08 * xM / Math.sqrt(1 + 1e-4 * xM);
    }

    public static double sigmaZ(double xM) {
        return 0.06 * xM / Math.sqrt(1 + 1.5e-3 * xM);
    }

    /**
     * Приземная концентрация на расстоянии {@code xM} по ветру и {@code yM} поперёк, кг/м³.
     *
     * @param qKgS     выброс, кг/с
     * @param effHeightM эффективная высота H_e
     */
    public static double groundConcentration(double qKgS, double windMs, double effHeightM, double xM, double yM) {
        if (xM <= 1 || qKgS <= 0) return 0;
        double u = Math.max(1, windMs), sy = sigmaY(xM), sz = sigmaZ(xM);
        return qKgS / (Math.PI * u * sy * sz) * Math.exp(-yM * yM / (2 * sy * sy)) * Math.exp(-effHeightM * effHeightM / (2 * sz * sz));
    }

    /** pH дождя по средним концентрациям SO₂ и NO₂, мкг/м³. */
    public static double rainPh(double so2UgM3, double no2UgM3) {
        double h = Math.pow(10, -5.6) + RAIN_ACID_COEFF * Math.max(0, so2UgM3) + 0.696 * RAIN_ACID_COEFF * Math.max(0, no2UgM3);
        return -Math.log10(h);
    }

    /** Индекс кислотной нагрузки по осаждённым S (как SO₂) и N (как NO₂), мг/м². */
    public static double acidIndex(double sMgM2, double nMgM2) {
        return (2 * sMgM2 / 64 + nMgM2 / 46) / D_CRIT_MEQ;
    }

    /**
     * Самый тяжёлый уровень эффекта.
     *
     * @param pmUg    PM₁₀ сейчас, мкг/м³
     * @param so2Ug   SO₂ сейчас, мкг/м³
     * @param so2DayUg SO₂ в среднем за сутки, мкг/м³
     * @param phi     индекс кислотной нагрузки
     * @param raining идёт ли дождь
     * @param rainPh  pH дождя
     */
    public static Level level(double pmUg, double so2Ug, double so2DayUg, double phi, boolean raining, double rainPh) {
        if (raining && rainPh <= 4.5) return Level.ACID_RAIN;
        if (so2Ug >= 2620 || pmUg >= 500) return Level.WEAKNESS;
        if (so2Ug >= 500 || pmUg >= 150) return Level.COUGH;
        if (phi >= 1 || so2DayUg >= 30) return Level.WITHERING;
        if (pmUg >= 150 || so2Ug >= 350) return Level.SMOG;
        if (pmUg >= 45) return Level.HAZE;
        return Level.CLEAN;
    }
}
