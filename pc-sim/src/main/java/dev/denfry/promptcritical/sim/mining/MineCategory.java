package dev.denfry.promptcritical.sim.mining;

/** Категория шахты по относительной метанообильности (08 §8.2.4, ПБ в угольных шахтах). */
public enum MineCategory {
    /** &lt; 5 м³/т */
    I,
    /** 5–10 м³/т */
    II,
    /** 10–15 м³/т */
    III,
    /** &gt; 15 м³/т */
    SUPER;

    /** @param relativeM3PerT метановыделение на тонну добычи, м³/т */
    public static MineCategory classify(double relativeM3PerT) {
        if (relativeM3PerT < 5) return I;
        if (relativeM3PerT < 10) return II;
        if (relativeM3PerT <= 15) return III;
        return SUPER;
    }
}
