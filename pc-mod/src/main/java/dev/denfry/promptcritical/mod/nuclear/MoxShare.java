package dev.denfry.promptcritical.mod.nuclear;

import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;
import dev.denfry.promptcritical.sim.enrichment.Criticality;

/**
 * Плутоний свежего МОКС-топлива в цехе, отнесённый к урану: {@code puPerU} кг Pu на кг U и изотопия Pu. Пресс,
 * печь, станок твэлов и стенд ТВС ведут уран по кг U — плутоний идёт с ним в этой пропорции и попадает в поля
 * {@code pu}, {@code pu239…pu242} партии ({@link FuelLot}). {@link #NONE} — урановое топливо.
 */
public record MoxShare(double puPerU, double pu239, double pu240, double pu241, double pu242) {
    public static final MoxShare NONE = new MoxShare(0, 0, 0, 0, 0);

    /** Доля плутония у свежей партии (у облучённой — {@link #NONE}: её Pu наработан, это не МОКС). */
    public static MoxShare of(FuelLot l) {
        if (l.pu() <= 0 || l.uKg() <= 0 || l.burnup() > 0) return NONE;
        return new MoxShare(l.pu() / l.uKg(), l.pu239(), l.pu240(), l.pu241(), l.pu242());
    }

    public boolean isMox() {
        return puPerU > 0;
    }

    /** Та же партия плутония. */
    public boolean same(MoxShare o) {
        return Math.abs(puPerU - o.puPerU) < 1e-9 && Math.abs(pu240 - o.pu240) < 1e-6;
    }

    /** Партия с плутонием этой доли на её уран. */
    public FuelLot apply(FuelLot l) {
        return new FuelLot(l.uKg(), l.u235(), l.u234(), l.density(), l.count(), l.design(), l.gas(), l.burnup(), l.u236(),
                l.uKg() * puPerU, pu239, pu240, pu241, pu242);
    }

    /** Смесь двух порций по кг урана (изотопия — по массе Pu). */
    public static MoxShare mix(MoxShare a, double kgA, MoxShare b, double kgB) {
        double u = kgA + kgB;
        if (u <= 0) return b;
        double pa = a.puPerU * kgA, pb = b.puPerU * kgB, pu = pa + pb;
        if (pu <= 0) return NONE;
        return new MoxShare(pu / u, (a.pu239 * pa + b.pu239 * pb) / pu, (a.pu240 * pa + b.pu240 * pb) / pu,
                (a.pu241 * pa + b.pu241 * pb) / pu, (a.pu242 * pa + b.pu242 * pb) / pu);
    }

    /** Безопасная масса урана такой смеси по плутонию ({@link Criticality#safePuKg}), кг U; без Pu — бесконечность. */
    public double safeUKg() {
        return isMox() ? Criticality.safePuKg(pu240) / puPerU : Double.POSITIVE_INFINITY;
    }

    public void save(StateWriter out, String p) {
        out.putDouble(p + "pu", puPerU);
        out.putDouble(p + "pu239", pu239);
        out.putDouble(p + "pu240", pu240);
        out.putDouble(p + "pu241", pu241);
        out.putDouble(p + "pu242", pu242);
    }

    public static MoxShare load(StateReader in, String p) {
        return new MoxShare(in.optDouble(p + "pu", 0), in.optDouble(p + "pu239", 0), in.optDouble(p + "pu240", 0),
                in.optDouble(p + "pu241", 0), in.optDouble(p + "pu242", 0));
    }
}
