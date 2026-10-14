package dev.denfry.promptcritical.sim.neutronics;

/**
 * Шесть групп Кипина (Keepin, «Physics of Nuclear Kinetics», 1965; 01 §1.5.2), смешение по
 * долям делений и фотонейтронные группы (§1.5.6).
 *
 * <p>Порядок делящихся нуклидов во всех массивах долей — {@code [U-235, U-238, Pu-239, Pu-241]},
 * как в {@code NuclideInventory.fissionFractions()}.
 */
public final class DelayedNeutronData {
    public static final int U235 = 0, U238 = 1, PU239 = 2, PU241 = 3, FISSILE_COUNT = 4;
    public static final int KEEPIN_GROUPS = 6;

    /** Полный выход запаздывающих нейтронов β по нуклидам. */
    private static final double[] BETA = {0.0065, 0.0148, 0.0021, 0.0054};
    private static final double[][] LAMBDA = {
            {0.0124, 0.0305, 0.111, 0.301, 1.14, 3.01},
            {0.0132, 0.0321, 0.139, 0.358, 1.41, 4.02},
            {0.0128, 0.0301, 0.124, 0.325, 1.12, 2.69},
            {0.0128, 0.0297, 0.124, 0.352, 1.61, 3.47},
    };
    /** Относительные выходы a_i = β_i/β. */
    private static final double[][] YIELD = {
            {0.033, 0.219, 0.196, 0.395, 0.115, 0.042},
            {0.013, 0.137, 0.162, 0.388, 0.225, 0.075},
            {0.035, 0.298, 0.211, 0.326, 0.086, 0.044},
            {0.010, 0.229, 0.173, 0.390, 0.182, 0.016},
    };

    /** Фотонейтронные группы: T½ = 2,5 мин, 1,1 ч, 12,8 сут; доли 0,6/0,3/0,1. */
    private static final double[] PHOTO_HALF_LIFE = {150, 1.1 * 3600, 12.8 * 86400};
    private static final double[] PHOTO_SPLIT = {0.6, 0.3, 0.1};

    private DelayedNeutronData() {}

    /** β нуклида (0..3). */
    public static double beta(int nuclide) {
        return BETA[nuclide];
    }

    /** Группы одного нуклида без поправки на ценность. */
    public static DelayedGroups keepin(int nuclide) {
        double[] b = new double[KEEPIN_GROUPS];
        for (int i = 0; i < KEEPIN_GROUPS; i++) b[i] = BETA[nuclide] * YIELD[nuclide][i];
        return new DelayedGroups(b, LAMBDA[nuclide]);
    }

    /** Группы U-235 с суммарной долей {@code betaTotal} — эталон тестов §1.5.4–1.5.5. */
    public static DelayedGroups u235(double betaTotal) {
        return keepin(U235).scaledTo(betaTotal);
    }

    /**
     * Смешение по долям делений F_k: β_i^mix = Σ F_k β_k a_{k,i},
     * λ_i^mix = Σ F_k β_k a_{k,i} λ_{k,i} / β_i^mix; затем β_eff,i = γ_d β_i^mix.
     */
    public static DelayedGroups mix(double[] fissionFractions, double importance) {
        if (fissionFractions.length != FISSILE_COUNT) throw new IllegalArgumentException("fissionFractions");
        double[] b = new double[KEEPIN_GROUPS], l = new double[KEEPIN_GROUPS];
        for (int i = 0; i < KEEPIN_GROUPS; i++) {
            double s = 0, sl = 0;
            for (int k = 0; k < FISSILE_COUNT; k++) {
                double bi = fissionFractions[k] * BETA[k] * YIELD[k][i];
                s += bi;
                sl += bi * LAMBDA[k][i];
            }
            b[i] = importance * s;
            l[i] = s > 0 ? sl / s : LAMBDA[U235][i];
        }
        return new DelayedGroups(b, l);
    }

    /** β^mix = Σ F_k β_k. */
    public static double mixedBeta(double[] fissionFractions) {
        double s = 0;
        for (int k = 0; k < FISSILE_COUNT; k++) s += fissionFractions[k] * BETA[k];
        return s;
    }

    /**
     * Фактор ценности запаздывающих нейтронов γ_d ≈ exp(B²(τ_p − τ_d))/ε: они рождаются мягче
     * (меньше утекают), но не делят U-238 на быстрых нейтронах.
     */
    public static double importanceFactor(double buckling2, double agePrompt, double ageDelayed, double epsilon) {
        return Math.exp(buckling2 * (agePrompt - ageDelayed)) / epsilon;
    }

    /** Добавляет три фотонейтронные группы с суммарной долей {@code betaPhoto} (D₂O, Be). */
    public static DelayedGroups withPhotoneutrons(DelayedGroups groups, double betaPhoto, int maxGroups) {
        if (betaPhoto <= 0) return groups;
        int g = groups.count();
        if (g + PHOTO_SPLIT.length > maxGroups) throw new IllegalArgumentException("maxGroups");
        double[] b = new double[g + PHOTO_SPLIT.length], l = new double[b.length];
        for (int i = 0; i < g; i++) {
            b[i] = groups.beta(i);
            l[i] = groups.lambda(i);
        }
        for (int j = 0; j < PHOTO_SPLIT.length; j++) {
            b[g + j] = betaPhoto * PHOTO_SPLIT[j];
            l[g + j] = Math.log(2) / PHOTO_HALF_LIFE[j];
        }
        return new DelayedGroups(b, l);
    }
}
