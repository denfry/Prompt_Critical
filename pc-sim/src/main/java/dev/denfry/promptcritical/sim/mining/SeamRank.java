package dev.denfry.promptcritical.sim.mining;

/** Свойство {@code rank} блока {@code coal_seam} (08 §8.1.4): по выходу летучих V^daf. */
public enum SeamRank {
    /** V^daf &gt; 42 % — бурый уголь (Б). */
    LIGNITE,
    /** 8–42 % — каменный (Д…Т). */
    BITUMINOUS,
    /** &lt; 8 % — антрацит. */
    ANTHRACITE;

    /** Ранг по V^daf, %. */
    public static SeamRank of(double volatileDafPct) {
        return volatileDafPct > 42 ? LIGNITE : volatileDafPct < 8 ? ANTHRACITE : BITUMINOUS;
    }
}
