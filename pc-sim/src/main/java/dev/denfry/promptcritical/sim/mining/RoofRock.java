package dev.denfry.promptcritical.sim.mining;

/** Порода непосредственной кровли (08 §8.2.2): допустимый пролёт — в {@link MiningConfig#roofSpan}. */
public enum RoofRock {
    /** песчаник — устойчивая: обрушение через 30 мин физ. после превышения пролёта */
    SANDSTONE(1800),
    /** аргиллит — средней устойчивости: 5 мин физ. */
    MUDSTONE(300),
    /** углистый сланец — неустойчивая: 30 с физ. */
    CARBONACEOUS_SHALE(30);

    /** Задержка обрушения после превышения пролёта, с физ. (таймер SLOW). */
    public final double collapseDelayS;

    RoofRock(double collapseDelayS) {
        this.collapseDelayS = collapseDelayS;
    }
}
