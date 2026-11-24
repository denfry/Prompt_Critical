package dev.denfry.promptcritical.sim.manufacturing.assembly;

/**
 * Допуски ISO 286-1 (ГОСТ 25346) для размеров до 500 мм (18 §18.6.11): единица допуска по среднему геометрическому
 * интервала размеров и ширина квалитетов IT6…IT14.
 */
public final class IsoFits {
    /** Границы интервалов размеров ISO 286-1, мм. */
    private static final double[] STEPS = {0, 3, 6, 10, 18, 30, 50, 80, 120, 180, 250, 315, 400, 500};
    /**
     * Табличные ширины IT6…IT10, мкм, по интервалам {@link #STEPS} (ISO 286-1, табл. 1). Стандарт округляет их по
     * своим правилам, поэтому берём таблицу, а не формулу; IT11…IT14 = 10 · IT(n−5).
     */
    private static final int[][] TABLE = {
            {6, 8, 9, 11, 13, 16, 19, 22, 25, 29, 32, 36, 40},
            {10, 12, 15, 18, 21, 25, 30, 35, 40, 46, 52, 57, 63},
            {14, 18, 22, 27, 33, 39, 46, 54, 63, 72, 81, 89, 97},
            {25, 30, 36, 43, 52, 62, 74, 87, 100, 115, 130, 140, 155},
            {40, 48, 58, 70, 84, 100, 120, 140, 160, 185, 210, 230, 250}};
    public static final int IT_MIN = 6, IT_MAX = 14;
    /** Квалитет необработанной (кованой, литой) поверхности: {@code itGrade == 0} в паспорте детали. */
    public static final int IT_UNMACHINED = IT_MAX;

    private IsoFits() {
    }

    /** Единица допуска i, мкм: 0.45·∛D + 0.001·D, D — среднее геометрическое интервала. */
    public static double toleranceUnitUm(double diameterMm) {
        if (!(diameterMm > 0) || diameterMm > 500) throw new IllegalArgumentException("diameter " + diameterMm);
        int k = 1;
        while (STEPS[k] < diameterMm) k++;
        double d = STEPS[k - 1] == 0 ? Math.sqrt(1 * STEPS[k]) : Math.sqrt(STEPS[k - 1] * STEPS[k]);
        return 0.45 * Math.cbrt(d) + 0.001 * d;
    }

    /** Ширина поля допуска квалитета, мкм (ISO 286-1, табл. 1). */
    public static int widthUm(int it, double diameterMm) {
        if (it < IT_MIN || it > IT_MAX) throw new IllegalArgumentException("IT" + it);
        if (!(diameterMm > 0) || diameterMm > 500) throw new IllegalArgumentException("diameter " + diameterMm);
        if (it > 10) return 10 * widthUm(it - 5, diameterMm);
        int k = 1;
        while (STEPS[k] < diameterMm) k++;
        return TABLE[it - IT_MIN][k - 1];
    }

    /** Квалитет из паспорта детали: 0 — не обработана, вне таблицы — ближайший край. */
    public static int effective(int itGrade) {
        if (itGrade == 0) return IT_UNMACHINED;
        return Math.clamp(itGrade, IT_MIN, IT_MAX);
    }
}
