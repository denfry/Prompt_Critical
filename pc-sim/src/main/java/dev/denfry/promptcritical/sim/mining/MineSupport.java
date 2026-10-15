package dev.denfry.promptcritical.sim.mining;

/** Крепь {@code mine_support} (08 §8.2.2). */
public enum MineSupport {
    /** деревянная (I0): 50 кН, +2 к пролёту, гниёт во влажной выработке */
    TIMBER(50e3, 2, 0.01),
    /** арочная податливая СВП (I1): 300 кН, +4; коррозия в кислой воде — снаружи модели кровли */
    ARCH(300e3, 4, 0),
    /** распёртая секция механизированной крепи: 5 МН из 4–6, держит лаву; прибавка [BALANCE] */
    POWERED(5e6, 6, 0);

    /** Несущая способность новой крепи, Н. */
    public final double capacityN;
    /** Прибавка к допустимому пролёту, блоков. */
    public final int spanBonus;
    /** Потеря состояния во влажной выработке, доля в сутки физ. */
    public final double wetDecayPerDay;

    MineSupport(double capacityN, int spanBonus, double wetDecayPerDay) {
        this.capacityN = capacityN;
        this.spanBonus = spanBonus;
        this.wetDecayPerDay = wetDecayPerDay;
    }

    /**
     * Состояние крепи после dt физ. (1 — новая).
     *
     * @param wet влажная выработка (капёж, вода на почве)
     */
    public double decay(double condition, double dtPhysS, boolean wet) {
        if (!wet || wetDecayPerDay == 0) return condition;
        return Math.max(0, condition - wetDecayPerDay * dtPhysS / 86_400);
    }

    /** Несущая способность при данном состоянии, Н. */
    public double capacityN(double condition) {
        return capacityN * condition;
    }
}
