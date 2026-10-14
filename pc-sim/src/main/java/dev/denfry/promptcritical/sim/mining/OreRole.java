package dev.denfry.promptcritical.sim.mining;

/**
 * Роль рудного блока в смешанной жиле (08 §8.1.1). Вероятности ролей зависят от относительной высоты блока в теле
 * h = (y − y₀ + c)/2c: верх (h &gt; 0,62) — в основном второстепенная руда (окисленная шляпа, вторичное обогащение),
 * низ (h &lt; 0,38) — основная (первичные сульфиды).
 */
public enum OreRole {
    PRIMARY, SECONDARY, INTERMEDIATE, SPORADIC;

    /** Граница верхней зоны по h. */
    public static final double TOP = 0.62;
    /** Граница нижней зоны по h. */
    public static final double BOTTOM = 0.38;

    private static final OreRole[] ALL = values();
    /** Вероятности (основная, второстепенная, промежуточная, спорадическая) по зонам: верх, середина, низ. */
    private static final double[][] ZONE = {
            {0.10, 0.70, 0.15, 0.05},
            {0.25, 0.15, 0.55, 0.05},
            {0.75, 0.05, 0.15, 0.05}};
    /** Роли в угольном пласте: почти весь пласт — уголь, вкрапления пирита и сфалерита [допущение]. */
    private static final double[] SEAM = {0.92, 0.05, 0.02, 0.01};

    /** Вероятность роли в зоне относительной высоты h. */
    public static double probability(OreRole role, double h) {
        return ZONE[zone(h)][role.ordinal()];
    }

    /** Роль по равномерному u ∈ [0, 1) в зоне h. */
    public static OreRole pick(double h, double u) {
        return pick(ZONE[zone(h)], u);
    }

    /** Роль блока угольного пласта по u ∈ [0, 1). */
    public static OreRole pickSeam(double u) {
        return pick(SEAM, u);
    }

    /** Вероятность роли внутри угольного пласта. */
    public static double seamProbability(OreRole role) {
        return SEAM[role.ordinal()];
    }

    /** Зона: 0 — верх, 1 — середина, 2 — низ. */
    public static int zone(double h) {
        return h > TOP ? 0 : h < BOTTOM ? 2 : 1;
    }

    private static OreRole pick(double[] p, double u) {
        double acc = 0;
        for (int i = 0; i < p.length - 1; i++) {
            acc += p[i];
            if (u < acc) return ALL[i];
        }
        return ALL[p.length - 1];
    }
}
