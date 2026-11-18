package dev.denfry.promptcritical.sim.heavywater;

/**
 * Вода со смесью изотопов водорода: x — атомная доля дейтерия среди атомов водорода (протий + дейтерий). Тритий
 * учитывается отдельно ({@link HeavyWaterInventory}) — по массе его доли ничтожны.
 *
 * <p>Природная вода — стандарт VSMOW, D/H = 155,76·10⁻⁶ (Hagemann, Nief, Roth, Tellus 22 (1970) 712), то есть одна
 * молекула HDO на ~3200 молекул. Реакторная чистота — 99,75 ат. % D (Rouben, «CANDU Fuel Management», 1999; AECL,
 * «CANDU 6 Technical Summary», 2005). Плотность при 20 °C: H₂O 998,2, D₂O 1105,3 кг/м³ (CRC Handbook of Chemistry
 * and Physics, 97th ed., 2016), между ними — линейно по x: мольные объёмы изотопологов отличаются меньше чем на 0,5 %.
 *
 * <p>Коэффициенты разделения:
 * <ul>
 *   <li>изотопный обмен H₂O(ж) + HDS(г) ⇄ HDO(ж) + H₂S(г) — α ≈ 2,32 при 30 °C и ≈ 1,80 при 130 °C (Benedict, Pigford,
 *       Levi, «Nuclear Chemical Engineering», 2nd ed., 1981, гл. 13; Miller, «Heavy Water: A Manufacturer's Guide for the
 *       Hydrogen Century», Canadian Nuclear Society Bulletin 22 (2001) 1); между точками — по Вант-Гоффу, ln α = a + b/T;</li>
 *   <li>перегонка воды — α = √(p_H₂O/p_D₂O) по правилу среднего геометрического для HDO (Benedict гл. 13); отношение
 *       давлений паров 1,153 при 25 °C и 1,054 при 100 °C (D₂O кипит при 101,42 °C), то есть α ≈ 1,05 при 60 °C —
 *       поэтому колонны дистилляции работают под вакуумом: чем холоднее, тем больше α.</li>
 * </ul>
 */
public final class HeavyWater {
    /** Природная вода VSMOW: x = R/(1 + R), R = 155,76·10⁻⁶. */
    public static final double NATURAL_X = 155.76e-6 / (1 + 155.76e-6);
    /** Реакторная чистота D₂O, ат. доля D. */
    public static final double REACTOR_GRADE_X = 0.9975;
    /** Атомные массы H, D и O, г/моль (IUPAC 2013). */
    public static final double M_H = 1.007825, M_D = 2.014102, M_O = 15.9994;
    /** Плотность при 20 °C, кг/м³ (CRC). */
    public static final double RHO_H2O_20 = 998.2, RHO_D2O_20 = 1105.3;
    /** Опорные точки α обмена вода — сероводород: (T, α). */
    static final double GS_T1 = 303.15, GS_A1 = 2.32, GS_T2 = 403.15, GS_A2 = 1.80;
    /** Опорные точки отношения давлений паров p_H₂O/p_D₂O: (T, отношение). */
    static final double VP_T1 = 298.15, VP_R1 = 1.153, VP_T2 = 373.15, VP_R2 = 1.054;
    public static final double AVOGADRO = 6.02214076e23;

    private HeavyWater() {
    }

    /** Молярная масса воды с долей D {@code x}, кг/моль. */
    public static double molarMassKg(double x) {
        double cx = clamp(x);
        return (2 * (cx * M_D + (1 - cx) * M_H) + M_O) / 1000;
    }

    /** Плотность при 20 °C, кг/м³. */
    public static double density20(double x) {
        return RHO_H2O_20 + (RHO_D2O_20 - RHO_H2O_20) * clamp(x);
    }

    /** Моли воды в {@code kg}. */
    public static double moles(double kg, double x) {
        return kg / molarMassKg(x);
    }

    /** Масса {@code mol} молей воды, кг. */
    public static double kg(double mol, double x) {
        return mol * molarMassKg(x);
    }

    /** Литры (= мБ) при 20 °C в {@code kg}. */
    public static double litres(double kg, double x) {
        return kg / density20(x) * 1000;
    }

    /** Масса {@code litres} литров при 20 °C, кг. */
    public static double kgFromLitres(double litres, double x) {
        return litres * density20(x) / 1000;
    }

    /** Отношение изотопов D/H. */
    public static double ratio(double x) {
        double cx = clamp(x);
        return cx / Math.max(1 - cx, 1e-15);
    }

    public static double fromRatio(double r) {
        return r / (1 + r);
    }

    /** α обмена вода — сероводород при {@code tempK}: вода обогащается дейтерием сильнее на холоде. */
    public static double gsAlpha(double tempK) {
        return vantHoff(tempK, GS_T1, Math.log(GS_A1), GS_T2, Math.log(GS_A2));
    }

    /** α перегонки воды при {@code tempK}: D₂O менее летуча, кубовый остаток обогащается. */
    public static double distillationAlpha(double tempK) {
        return vantHoff(tempK, VP_T1, 0.5 * Math.log(VP_R1), VP_T2, 0.5 * Math.log(VP_R2));
    }

    private static double vantHoff(double t, double t1, double ln1, double t2, double ln2) {
        double b = (ln1 - ln2) / (1 / t1 - 1 / t2), a = ln1 - b / t1;
        return Math.exp(a + b / t);
    }

    static double clamp(double x) {
        return Math.max(0, Math.min(1, x));
    }
}
