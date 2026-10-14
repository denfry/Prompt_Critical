package dev.denfry.promptcritical.sim.mining;

import dev.denfry.promptcritical.sim.combustion.FuelPhase;
import dev.denfry.promptcritical.sim.combustion.FuelSpec;

/**
 * Технический и элементный анализ угля или кокса (08 §8.2.1) — компонент {@code coal_assay} предметов
 * {@code raw_coal}, {@code washed_coal}, {@code coal_middlings}, {@code coke} и ванильного угля; он же описание
 * топлива для {@code Combustor} (07 §7.8.1).
 *
 * @param moistureFrac     W^r — рабочая влага
 * @param ashDryFrac       A^d — зольность на сухую массу
 * @param sulfurDryFrac    S_t^d — общая сера на сухую массу
 * @param pyriticShare     доля пиритной серы (0,3–0,7); уходит в тяжёлую фракцию при обогащении
 * @param volatileDaf      V^daf — выход летучих
 * @param plasticLayerMm   y — толщина пластического слоя, мм
 * @param cDaf             C^daf
 * @param hDaf             H^daf
 * @param oDaf             O^daf
 * @param nDaf             N^daf
 * @param densityFractions массовые доли четырёх плотностных фракций с зольностью {@link #FRACTION_ASH}
 * @param strengthM25      прочность M₂₅ (только кокс), NaN у угля
 * @param rank             марка
 */
public record CoalAssay(double moistureFrac, double ashDryFrac, double sulfurDryFrac, double pyriticShare,
        double volatileDaf, double plasticLayerMm, double cDaf, double hDaf, double oDaf, double nDaf,
        float[] densityFractions, double strengthM25, CoalRank rank) {
    /** Зольность плотностных фракций (08 §8.3): 5 / 15 / 35 / 75 %. */
    public static final double[] FRACTION_ASH = {0.05, 0.15, 0.35, 0.75};
    /** Связывание серы в золе r_S при слоевом и пылевом сжигании угля (07 §7.8.2: 0,05–0,10). */
    public static final double SULFUR_RETENTION = 0.05;
    /**
     * Доля топливного азота в NO, η_N. 07 §7.8.4 даёт 0,15–0,25; нижняя граница выбрана так, чтобы уголь Г без
     * подавления давал 700–900 мг/нм³ при 6 % O₂, как там же сказано (с 0,2 выходит ≈ 1070).
     */
    public static final double FUEL_N_CONVERSION = 0.15;
    /** Азот органической массы по умолчанию (каменные угли 1,5–2 %) — допущение, в GDD нет. */
    public static final double N_DAF_DEFAULT = 0.018;

    /** Профили плотностного состава «чистого» и «засорённого» угля, между которыми интерполируется зольность. */
    private static final double[] CLEAN = {0.70, 0.20, 0.07, 0.03}, DIRTY = {0.10, 0.15, 0.25, 0.50};

    public CoalAssay {
        if (!(moistureFrac >= 0 && moistureFrac < 0.7)) throw new IllegalArgumentException("moistureFrac: " + moistureFrac);
        if (!(ashDryFrac >= 0 && ashDryFrac < 0.8)) throw new IllegalArgumentException("ashDryFrac: " + ashDryFrac);
        if (!(sulfurDryFrac >= 0 && sulfurDryFrac < 0.15)) throw new IllegalArgumentException("sulfurDryFrac");
        if (!(pyriticShare >= 0 && pyriticShare <= 1)) throw new IllegalArgumentException("pyriticShare");
        if (!(volatileDaf >= 0 && volatileDaf < 0.7)) throw new IllegalArgumentException("volatileDaf: " + volatileDaf);
        if (!(plasticLayerMm >= 0)) throw new IllegalArgumentException("plasticLayerMm");
        if (!(cDaf >= 0 && hDaf >= 0 && oDaf >= 0 && nDaf >= 0)) throw new IllegalArgumentException("daf composition");
        double sum = cDaf + hDaf + oDaf + nDaf + sulfurDaf(sulfurDryFrac, ashDryFrac);
        if (Math.abs(sum - 1) > 0.01) throw new IllegalArgumentException("daf composition sums to " + sum);
        if (densityFractions == null || densityFractions.length != FRACTION_ASH.length)
            throw new IllegalArgumentException("densityFractions: expected " + FRACTION_ASH.length);
        if (rank == null) throw new IllegalArgumentException("rank");
    }

    /**
     * Уголь по техническому анализу; элементный состав — по аппроксимациям 08 §8.2.1, кислород — по разности.
     *
     * <p>C^daf ≈ 0,97 − 0,40·V^daf вместо 0,97 − 0,53·V^daf из 08 §8.2.1: с коэффициентом 0,53 у угля Г
     * (V^daf 37 %) кислород по разности выходит 13,5 % на daf, а Q_н^р — 22,4 МДж/кг, на 6 % ниже T8.1. С 0,40
     * марки ложатся на справочные C^daf (Д 77–79, Г 80–84, К 87–89, Т 89–91, А 93–96 %), уголь Г даёт C^р 0,61 —
     * как состав 07 §7.8.1 (0,60). H^daf ≈ 0,035 + 0,05·V^daf — по 08.
     */
    public static CoalAssay typical(double volatileDaf, double ashDryFrac, double moistureFrac, double sulfurDryFrac,
            double plasticLayerMm) {
        double h = 0.035 + 0.05 * volatileDaf;
        double n = N_DAF_DEFAULT;
        double s = sulfurDaf(sulfurDryFrac, ashDryFrac);
        double c = 0.97 - 0.40 * volatileDaf;
        double o = 1 - c - h - n - s;
        if (o < 0.01) {
            o = 0.01;
            c = 1 - h - n - s - o;
        }
        return new CoalAssay(moistureFrac, ashDryFrac, sulfurDryFrac, 0.5, volatileDaf, plasticLayerMm, c, h, o, n,
                densityProfile(ashDryFrac), Double.NaN, CoalRank.classify(volatileDaf, plasticLayerMm));
    }

    /**
     * Плотностной состав под заданную зольность: смесь профилей «чистого» (A^d 11 %) и «засорённого» (49 %)
     * угля. Нужен обогащению (08 §8.3); точная форма кривой обогатимости — [BALANCE].
     */
    public static float[] densityProfile(double ashDryFrac) {
        double aClean = mean(CLEAN), aDirty = mean(DIRTY);
        double lambda = Math.max(0, Math.min(1, (ashDryFrac - aClean) / (aDirty - aClean)));
        float[] w = new float[CLEAN.length];
        for (int i = 0; i < w.length; i++) w[i] = (float) (CLEAN[i] + lambda * (DIRTY[i] - CLEAN[i]));
        return w;
    }

    /** A^r = A^d·(1 − W^r). */
    public double ashWorking() {
        return ashDryFrac * (1 - moistureFrac);
    }

    /** Доля органической (daf) массы в рабочей: 1 − W^r − A^r. */
    public double dafWorking() {
        return (1 - moistureFrac) * (1 - ashDryFrac);
    }

    /**
     * Описание топлива для {@code Combustor} (08 §8.2.1 → 07 §7.8.1): X^r = X^daf·(1 − W^r − A^r), сера
     * S^r = S^d·(1 − W^r), кислород — замыкающий (поглощает округления daf-состава).
     */
    public FuelSpec toFuel() {
        double daf = dafWorking();
        double c = cDaf * daf, h = hDaf * daf, n = nDaf * daf, s = sulfurDryFrac * (1 - moistureFrac);
        double ash = ashWorking();
        double o = Math.max(0, 1 - c - h - n - s - ash - moistureFrac);
        return FuelSpec.ofElemental("coal_" + rank.name().toLowerCase(java.util.Locale.ROOT), FuelPhase.SOLID, c, h, o,
                n, s, ash, moistureFrac, SULFUR_RETENTION, FUEL_N_CONVERSION);
    }

    private static double sulfurDaf(double sulfurDry, double ashDry) {
        return sulfurDry / (1 - ashDry);
    }

    private static double mean(double[] w) {
        double a = 0;
        for (int i = 0; i < w.length; i++) a += w[i] * FRACTION_ASH[i];
        return a;
    }
}
