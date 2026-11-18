package dev.denfry.promptcritical.sim.refining;

/**
 * Установка Клауса: сероводород аминовой регенерации → элементная сера (GPSA Engineering Data Book, гл. 22; Kohl,
 * Nielsen, «Gas Purification», гл. 8).
 *
 * <p><b>Термическая ступень</b> (1000–1300 °C): треть H₂S сгорает, H₂S + 1,5 O₂ → SO₂ + H₂O (ΔH = −518 кДж/моль),
 * и в пламени же идёт реакция Клауса 2 H₂S + SO₂ ⇌ 3/x Sₓ + 2 H₂O — в серу переходит {@link #THERMAL_CONVERSION} = 0,67
 * серы сырья (типично 60–70 %, GPSA). Тепло горения снимает котёл-утилизатор.
 *
 * <p><b>Каталитические ступени</b> (активный Al₂O₃, 200–330 °C): реакция Клауса экзотермична, равновесие тем лучше,
 * чем холоднее слой; в ступени j доля оставшейся серы r_j = r_{j−1}·(1 − a·X_eq(T_j)), X_eq = 0,50 при 300 °C, 0,60
 * при 240 °C, 0,65 при 210 °C, между точками — линейно (GDD 09 §9.9.7 [BALANCE]; дают типичные 94–97 % на 2–3
 * ступени). Пример: 300/240/240 °C → r = 0,33·0,5·0,4·0,4 = 0,0264, извлечение 97,4 %.
 *
 * <p><b>Точка росы серы.</b> Слой должен быть на 10 К горячее точки росы серы на этой ступени, иначе сера
 * конденсируется в порах; точка росы падает от ступени к ступени, потому что серы в газе всё меньше:
 * {@link #DEW_POINT_C} = 210, 190, 175 °C [BALANCE: типичные значения для богатого кислого газа, GPSA рис. 22-7].
 * Холоднее — активность ступени падает экспоненциально ({@link #ageStage}).
 *
 * <p><b>Соотношение H₂S : SO₂ = 2 : 1.</b> Ошибка подачи воздуха на долю ε снижает извлечение на 3ε² [BALANCE] (10 %
 * — минус 3 %). Вся несконденсированная сера дожигается в SO₂ и уходит в трубу.
 */
public final class ClausUnit {
    public static final double THERMAL_CONVERSION = 0.67, AIR_ERROR_PENALTY = 3, DEW_MARGIN_K = 10;
    public static final double M_H2S = 34.08, M_S = 32.06, M_SO2 = 64.07, H2S_COMBUSTION_J_MOL = 518e3;
    public static final double[] DEW_POINT_C = {210, 190, 175};
    public static final int MAX_STAGES = 3;
    /** Конденсация серы в порах: e-кратное падение активности за сутки физ. работы ниже точки росы + 10 К. */
    public static final double PORE_FILL_PER_S = 1.0 / 86400;
    /** Узлы X_eq(T): °C по убыванию. */
    static final double[] EQ_T_C = {300, 240, 210}, EQ_X = {0.50, 0.60, 0.65};

    /**
     * @param recovery       извлечение серы
     * @param sulfurKgS      сера, кг/с
     * @param so2KgS         SO₂ в трубу после дожига, кг/с
     * @param boilerW        тепло котла-утилизатора термической ступени, Вт
     * @param remaining      доля серы, оставшейся в газе после каждой ступени (0 — термическая)
     * @param wetStage       номер первой ступени ниже точки росы + 10 К (−1 — нет)
     */
    public record Result(double recovery, double sulfurKgS, double so2KgS, double boilerW, double[] remaining, int wetStage) {
    }

    private final double[] activity = {1, 1, 1};

    /** Равновесная конверсия каталитической ступени при температуре слоя. */
    public static double equilibriumConversion(double bedC) {
        if (bedC >= EQ_T_C[0]) return EQ_X[0];
        if (bedC <= EQ_T_C[EQ_T_C.length - 1]) return EQ_X[EQ_X.length - 1];
        for (int i = 1; i < EQ_T_C.length; i++)
            if (bedC >= EQ_T_C[i]) return EQ_X[i] + (EQ_X[i - 1] - EQ_X[i]) * (bedC - EQ_T_C[i]) / (EQ_T_C[i - 1] - EQ_T_C[i]);
        return EQ_X[EQ_X.length - 1];
    }

    /**
     * @param h2sKgS   сероводород кислого газа, кг/с
     * @param bedC     температуры слоёв каталитических ступеней, °C (1–3 ступени)
     * @param airError ошибка подачи воздуха, доля (0 — ровно треть H₂S в SO₂)
     */
    public Result run(double h2sKgS, double[] bedC, double airError) {
        int n = Math.min(MAX_STAGES, bedC.length);
        double[] rem = new double[n + 1];
        rem[0] = 1 - THERMAL_CONVERSION;
        int wet = -1;
        for (int j = 0; j < n; j++) {
            if (wet < 0 && bedC[j] < DEW_POINT_C[j] + DEW_MARGIN_K) wet = j + 1;
            rem[j + 1] = rem[j] * (1 - activity[j] * equilibriumConversion(bedC[j]));
        }
        double recovery = Math.max(0, 1 - rem[n] - AIR_ERROR_PENALTY * airError * airError);
        double sMolS = Math.max(0, h2sKgS) * 1000 / M_H2S;
        double burned = sMolS / 3 * (1 + airError);
        return new Result(recovery, sMolS * recovery * M_S / 1000, sMolS * (1 - recovery) * M_SO2 / 1000,
                burned * H2S_COMBUSTION_J_MOL, rem, wet);
    }

    /** Ступени ниже точки росы + 10 К теряют активность (сера в порах), выше — медленно отпариваются обратно. */
    public void ageStage(int stage, double bedC, double dtS) {
        if (stage < 0 || stage >= MAX_STAGES) return;
        double margin = bedC - (DEW_POINT_C[stage] + DEW_MARGIN_K);
        if (margin < 0) activity[stage] *= Math.exp(-PORE_FILL_PER_S * dtS * Math.min(1, -margin / 10));
        else activity[stage] = Math.min(1, activity[stage] + PORE_FILL_PER_S * dtS * 0.25);
    }

    public double activity(int stage) {
        return activity[stage];
    }

    public void setActivity(int stage, double a) {
        activity[stage] = Math.clamp(a, 0, 1);
    }
}
