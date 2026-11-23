package dev.denfry.promptcritical.sim.chlor;

import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;

/**
 * Печь синтеза хлороводорода с графитовой горелкой и абсорбером (10 §10.3.4; прототип — печи SGL):
 * H₂ + Cl₂ → 2 HCl, −92,3 кДж на моль HCl (2,53 МДж/кг). Водород идёт с избытком 5–10 %: он дожигает весь хлор.
 * Без избытка часть хлора проходит горелку несгоревшей и уходит с хвостовым газом [BALANCE]. Хлороводород
 * поглощается водой до соляной кислоты 31–36 % или сушится и уходит сухим газом. Остаток водорода сгорает в
 * хвостовом газе.
 */
public final class HclSynthesis {
    public static final double M_HCL = 0.036461, M_CL2 = DiaphragmCellRoom.M_CL2;
    /** Теплота образования хлороводорода, Дж на моль HCl. */
    public static final double HEAT_J_PER_MOL_HCL = 92.3e3;
    /** Хлор на горелку, моль/с, на который рассчитана печь (≈ 12 т HCl в сутки) [BALANCE]. */
    public static final double CAPACITY_CL2_MOL_S = 2;
    public static final double MIN_EXCESS = 1.00, MAX_EXCESS = 1.15, MIN_STRENGTH = 0.31, MAX_STRENGTH = 0.36;
    /** Избыток водорода, ниже которого хлор проходит горелку. */
    public static final double SAFE_EXCESS = 1.05;
    /** Доля несгоревшего хлора при стехиометрии (избыток 1,00) [BALANCE]. */
    public static final double SLIP_AT_STOICHIOMETRY = 0.05;

    /** Что делают с хлороводородом за горелкой. */
    public enum Mode { ACID, DRY }

    private boolean on = true;
    private Mode mode = Mode.ACID;
    private double excess = 1.07, strength = 0.33;
    // последний шаг: моль/с и кг/с
    private double cl2Used, h2Used, hclMade, cl2Slip, acidKg, waterKg, heatW;

    public boolean on() {
        return on;
    }

    public void setOn(boolean on) {
        this.on = on;
    }

    public Mode mode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }

    /** Отношение поданного водорода к стехиометрии. */
    public double excess() {
        return excess;
    }

    public void setExcess(double e) {
        excess = Math.clamp(e, MIN_EXCESS, MAX_EXCESS);
    }

    /** Массовая доля HCl в соляной кислоте с абсорбера. */
    public double strength() {
        return strength;
    }

    public void setStrength(double w) {
        strength = Math.clamp(w, MIN_STRENGTH, MAX_STRENGTH);
    }

    /** Доля хлора, прошедшего горелку при заданном избытке водорода. */
    public static double slipFraction(double excess) {
        return SLIP_AT_STOICHIOMETRY * Math.clamp((SAFE_EXCESS - excess) / (SAFE_EXCESS - MIN_EXCESS), 0, 1);
    }

    /** Вода на кг HCl для кислоты крепостью {@code w}, кг. */
    public static double waterPerKgHcl(double w) {
        return (1 - w) / w;
    }

    /**
     * Шаг. Доступно: водород и хлор, моль/с; вода на абсорбцию, кг/с; место под продукт — моль HCl в секунду.
     * Горелка берёт хлора столько, сколько позволяют мощность, водород с избытком, вода (в режиме кислоты) и место.
     */
    public void step(double h2, double cl2, double waterKgS, double roomHclMolS) {
        cl2Used = h2Used = hclMade = cl2Slip = acidKg = waterKg = heatW = 0;
        if (!on) return;
        double slip = slipFraction(excess);
        double hclPerCl2 = 2 * (1 - slip);
        double burn = Math.min(CAPACITY_CL2_MOL_S, Math.min(cl2, h2 / excess));
        burn = Math.min(burn, roomHclMolS / hclPerCl2);
        if (mode == Mode.ACID) burn = Math.min(burn, waterKgS / (hclPerCl2 * M_HCL * waterPerKgHcl(strength)));
        burn = Math.max(0, burn);
        cl2Used = burn;
        h2Used = burn * excess;
        cl2Slip = burn * slip;
        hclMade = burn * hclPerCl2;
        heatW = hclMade * HEAT_J_PER_MOL_HCL;
        if (mode == Mode.ACID) {
            waterKg = hclMade * M_HCL * waterPerKgHcl(strength);
            acidKg = hclMade * M_HCL + waterKg;
        }
    }

    public double cl2UsedMolS() {
        return cl2Used;
    }

    public double h2UsedMolS() {
        return h2Used;
    }

    /** Хлороводород, моль/с: сухим газом в режиме {@link Mode#DRY}, иначе он весь в кислоте. */
    public double hclMolS() {
        return hclMade;
    }

    /** Несгоревший хлор в хвостовом газе, моль/с. */
    public double cl2SlipMolS() {
        return cl2Slip;
    }

    public double acidKgS() {
        return acidKg;
    }

    public double waterUsedKgS() {
        return waterKg;
    }

    /** Теплота горения, Вт (снимается водяной рубашкой горелки). */
    public double heatW() {
        return heatW;
    }

    public void save(StateWriter out) {
        out.putBoolean("on", on);
        out.putInt("mode", mode.ordinal());
        out.putDouble("excess", excess);
        out.putDouble("w", strength);
    }

    public void load(StateReader in) {
        on = in.optInt("on", 1) != 0;
        mode = Mode.values()[Math.clamp(in.optInt("mode", 0), 0, Mode.values().length - 1)];
        setExcess(in.optDouble("excess", 1.07));
        setStrength(in.optDouble("w", 0.33));
    }
}
