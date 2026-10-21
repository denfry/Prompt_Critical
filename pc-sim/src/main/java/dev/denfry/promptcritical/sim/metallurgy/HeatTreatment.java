package dev.denfry.promptcritical.sim.metallurgy;

/**
 * {@code heat_treatment_furnace} + {@code quench_tank} (08 §8.7.5). Режим вне окна температур состояние не меняет
 * (недогрев под закалку — неполная закалка, мир показывает «не принято»).
 * <ul>
 *   <li>нормализация: A_c3 + 30…50 К, воздух;</li>
 *   <li>закалка: от A_c3 + 30 К (860–900 °C у углеродистой); отпуск закалённой 550–650 °C → Q+T;</li>
 *   <li>старение мартенситно-стареющей 480 °C (±20 К);</li>
 *   <li>аустенизация нержавеющей и никелевой 1050 °C (от 1030 °C);</li>
 *   <li>отпуск шва и снятие напряжений 600–650 °C у незакалённой.</li>
 * </ul>
 * A_c3 = 910 − 203√C, °C (Эндрюс без легирующих) [аппрокс.].
 */
public final class HeatTreatment {
    public static final double AC3_MARGIN_K = 30, NORMALIZE_MAX_OVER_K = 80;
    public static final double TEMPER_MIN_K = 823.15, TEMPER_MAX_K = 923.15;
    public static final double STRESS_RELIEF_MIN_K = 873.15, STRESS_RELIEF_MAX_K = 923.15;
    public static final double AGE_K = 753.15, AGE_TOL_K = 20;
    public static final double SOLUTION_MIN_K = 1303.15;

    private HeatTreatment() {}

    /** A_c3, К. */
    public static double ac3K(SteelGrade g) {
        return 273.15 + 910 - 203 * Math.sqrt(g.frac(AlloyElement.C) * 100);
    }

    public static SteelGrade normalize(SteelGrade g, double tempK) {
        double a = ac3K(g);
        if (g.family().hardening != SteelFamily.Hardening.FERRITIC || tempK < a + AC3_MARGIN_K
                || tempK > a + NORMALIZE_MAX_OVER_K + AC3_MARGIN_K) return g;
        return g.withState(HeatTreatState.NORMALIZED);
    }

    public static SteelGrade quench(SteelGrade g, double tempK) {
        SteelFamily.Hardening h = g.family().hardening;
        if (h == SteelFamily.Hardening.AUSTENITIC || h == SteelFamily.Hardening.MARAGING)
            return tempK >= SOLUTION_MIN_K ? g.withState(HeatTreatState.SOLUTION_ANNEALED) : g;
        return tempK >= ac3K(g) + AC3_MARGIN_K ? g.withState(HeatTreatState.QUENCHED) : g;
    }

    /** Отпуск: закалённая → Q+T; незакалённая в окне 600–650 °C → снятие напряжений. */
    public static SteelGrade temper(SteelGrade g, double tempK) {
        if (g.state() == HeatTreatState.QUENCHED)
            return tempK >= TEMPER_MIN_K && tempK <= TEMPER_MAX_K ? g.withState(HeatTreatState.QUENCHED_TEMPERED) : g;
        if (g.family().hardening == SteelFamily.Hardening.FERRITIC && g.state() != HeatTreatState.QUENCHED_TEMPERED
                && tempK >= STRESS_RELIEF_MIN_K && tempK <= STRESS_RELIEF_MAX_K)
            return g.withState(HeatTreatState.STRESS_RELIEVED);
        return g;
    }

    public static SteelGrade age(SteelGrade g, double tempK) {
        if (g.family().hardening != SteelFamily.Hardening.MARAGING || Math.abs(tempK - AGE_K) > AGE_TOL_K) return g;
        return g.withState(HeatTreatState.AGED);
    }

    /**
     * Выгрузка на воздух из печи при {@code tempK}: закалённая — отпуск; мартенситно-стареющая — старение;
     * аустенитная и никелевая — аустенизация (тонкие сечения охлаждаются на воздухе достаточно быстро) [моё];
     * в окне нормализации — нормализация; в окне 600–650 °C — снятие напряжений. Вне окон — без изменений.
     */
    public static SteelGrade airCool(SteelGrade g, double tempK) {
        if (g.state() == HeatTreatState.QUENCHED) return temper(g, tempK);
        SteelFamily.Hardening h = g.family().hardening;
        if (h == SteelFamily.Hardening.MARAGING && g.state() == HeatTreatState.SOLUTION_ANNEALED) return age(g, tempK);
        if (h == SteelFamily.Hardening.AUSTENITIC || h == SteelFamily.Hardening.MARAGING) return quench(g, tempK);
        SteelGrade n = normalize(g, tempK);
        return n != g ? n : temper(g, tempK);
    }
}
