package dev.denfry.promptcritical.sim.metallurgy;

/** Литейная форма (08 §8.9). */
public enum MoldKind {
    /** {@code sand_mold} — разовая песчано-глинистая. */
    SAND(1),
    /** {@code permanent_mold} — кокиль: затвердевание в 5–10 раз быстрее [сверить], мелкое зерно, отбел чугуна. */
    PERMANENT(1.0 / 7);

    /** Множитель постоянной Чворинова к песчаной форме. */
    public final double chvorinovScale;

    MoldKind(double chvorinovScale) {
        this.chvorinovScale = chvorinovScale;
    }

    /**
     * Стойкость кокиля, заливок (08 §8.9: Al 500–5000, чугун 50–300; медь, бронза, сталь — как чугун
     * [допущение]); берётся середина в логарифме. У песчаной формы — 1.
     */
    public int pourLife(CastAlloy alloy) {
        if (this == SAND) return 1;
        return alloy == CastAlloy.ALUMINIUM ? 1580 : 120;
    }
}
