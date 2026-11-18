package dev.denfry.promptcritical.sim.pipeline;

/**
 * Коррозия и катодная защита подземной стали (09 §9.8.3). Голая сталь в грунте корродирует 0,1–0,3 мм/год; защита
 * сдвигает потенциал «выкл» отрицательнее −0,85 В по медно-сульфатному электроду (NACE SP0169 / ГОСТ Р 51164) — тогда
 * скорость ×0,02. Потребный ток I = j·A·(1 − η_изол), j = 10–30 мА/м² для голой стали. Перезащита (потенциал ниже
 * −1,2 В) отслаивает изоляцию. Протектор: ток (E_трубы − E_анода)/R_земли, срок m·Q/I при Q = 1100 А·ч/кг (магний, КПД
 * 0,5).
 * <p>Поляризация упрощена линейно [моё]: потенциал «выкл» E = E_ест − 0,2 В·I/I_потр, то есть ровно потребный ток даёт
 * критерий −0,85 В; потенциал «вкл» больше по модулю на падение IR в грунте.
 */
public final class CathodicProtection {
    /** Середина диапазона 0,1–0,3 мм/год, м/год [моё]. */
    public static final double BARE_RATE_M_YR = 0.2e-3;
    public static final double PROTECTED_FACTOR = 0.02;
    /** Середина 10–30 мА/м² [моё], А/м². */
    public static final double BARE_CURRENT_A_M2 = 0.020;
    public static final double COATING_EFFICIENCY = 0.99;
    /** Естественный потенциал стали в грунте, В (МСЭ) [сверить]; критерий защиты; перезащита. */
    public static final double NATURAL_V = -0.65, CRITERION_V = -0.85, OVERPROTECTION_V = -1.2;
    /** Магниевый протектор, В (МСЭ) [сверить]; токоотдача с КПД 0,5, А·ч/кг. */
    public static final double MAGNESIUM_V = -1.55, MAGNESIUM_AH_KG = 1100;
    public static final double HOURS_PER_YEAR = 8766;

    private CathodicProtection() {
    }

    /** Потребный ток защиты участка площадью {@code areaM2}, А. */
    public static double requiredCurrentA(double areaM2, double coatingEfficiency) {
        return BARE_CURRENT_A_M2 * areaM2 * (1 - Math.clamp(coatingEfficiency, 0, 1));
    }

    /** Потенциал «выкл» (без падения IR), В. */
    public static double offPotentialV(double currentA, double requiredA) {
        if (requiredA <= 0) return currentA > 0 ? OVERPROTECTION_V - 0.1 : NATURAL_V;
        return NATURAL_V + (CRITERION_V - NATURAL_V) * Math.max(0, currentA) / requiredA;
    }

    /** Потенциал «вкл»: с падением напряжения в грунте, В. */
    public static double onPotentialV(double offV, double currentA, double earthOhm) {
        return offV - Math.max(0, currentA) * earthOhm;
    }

    public static boolean protectedAt(double offV) {
        return offV <= CRITERION_V;
    }

    public static boolean overprotected(double offV) {
        return offV < OVERPROTECTION_V;
    }

    /** Скорость утонения стенки при потенциале «выкл» {@code offV}, м/год. */
    public static double corrosionRateMYr(double offV) {
        return protectedAt(offV) ? BARE_RATE_M_YR * PROTECTED_FACTOR : BARE_RATE_M_YR;
    }

    /** Ток протектора на трубу с потенциалом {@code pipeV}, А. */
    public static double anodeCurrentA(double pipeV, double earthOhm) {
        return earthOhm <= 0 ? 0 : Math.max(0, (pipeV - MAGNESIUM_V) / earthOhm);
    }

    /** Срок службы протектора массой {@code kg} при токе {@code currentA}, лет. */
    public static double anodeLifeYears(double kg, double currentA) {
        return currentA <= 0 ? Double.POSITIVE_INFINITY : kg * MAGNESIUM_AH_KG / currentA / HOURS_PER_YEAR;
    }
}
