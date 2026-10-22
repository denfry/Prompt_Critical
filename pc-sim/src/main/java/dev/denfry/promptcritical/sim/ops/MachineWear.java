package dev.denfry.promptcritical.sim.ops;

import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Износ машины среза I0 (13 §13.6.7): подшипник, смазка, сальник (если есть шток или вал с набивкой) и рабочее
 * колесо (у насоса). Шаг SLOW только при вращении.
 */
public final class MachineWear {
    /** Что слышно упрощённым стетоскопом (13 §13.6.7): одна строка по наивысшей стадии. */
    public enum StethoscopeSound { HUM, RUSTLE, SCRAPE, KNOCK, HISS }

    private final PlainBearing bearing;
    private final Lubrication lubrication;
    private final GlandPacking packing;
    private final ImpellerWear impeller;
    private final double wearMultiplier;

    public MachineWear(PlainBearing bearing, Lubrication lubrication, GlandPacking packing, ImpellerWear impeller,
            double wearMultiplier) {
        if (bearing == null || lubrication == null || !(wearMultiplier > 0)) throw new IllegalArgumentException("wear");
        this.bearing = bearing;
        this.lubrication = lubrication;
        this.packing = packing;
        this.impeller = impeller;
        this.wearMultiplier = wearMultiplier;
    }

    /** Новая машина I0 на заводской набивке {@code tallow}. */
    public static MachineWear newI0(OpsConfig cfg, SplittableRandom rnd, boolean packed, boolean pump) {
        return new MachineWear(PlainBearing.install(PlainBearing.L10H_I0, rnd),
                new Lubrication(cfg.tallowIntervalH(), Lubricant.TALLOW), packed ? new GlandPacking() : null,
                pump ? new ImpellerWear() : null, cfg.bearingWearMultiplier());
    }

    /**
     * @param dryRunning насос без заливки ({@code DRY_PUMP})
     * @param waterPh    pH перекачиваемой воды; NaN — не насос
     * @return true, если подшипник заплавился на этом шаге
     */
    public boolean stepSlow(double dtPhysS, boolean running, boolean dryRunning, double waterPh) {
        if (!running || bearing.seized()) return false;
        double aLub = lubrication.factor(dryRunning);
        lubrication.step(dtPhysS);
        if (packing != null) packing.step(dtPhysS, dryRunning);
        if (impeller != null && !Double.isNaN(waterPh)) impeller.step(dtPhysS, waterPh);
        return bearing.step(dtPhysS, wearMultiplier, aLub);
    }

    public PlainBearing bearing() {
        return bearing;
    }

    public Lubrication lubrication() {
        return lubrication;
    }

    public GlandPacking packing() {
        return packing;
    }

    public ImpellerWear impeller() {
        return impeller;
    }

    public StethoscopeSound stethoscope() {
        int stage = bearing.stage();
        if (stage >= 3) return StethoscopeSound.KNOCK;
        if (stage == 2) return StethoscopeSound.SCRAPE;
        if (stage == 1) return StethoscopeSound.RUSTLE;
        if (packing != null && MaintenanceFaultKind.PACKING_LEAK.severity(packing.wear()) > 0)
            return StethoscopeSound.HISS;
        return StethoscopeSound.HUM;
    }

    public void save(StateWriter out) {
        out.beginObject("bearing");
        bearing.save(out);
        out.endObject();
        out.beginObject("lube");
        lubrication.save(out);
        out.endObject();
        if (packing != null) {
            out.beginObject("packing");
            packing.save(out);
            out.endObject();
        }
        if (impeller != null) {
            out.beginObject("impeller");
            impeller.save(out);
            out.endObject();
        }
    }

    public void load(StateReader in) {
        in.beginObject("bearing");
        bearing.load(in);
        in.endObject();
        in.beginObject("lube");
        lubrication.load(in);
        in.endObject();
        if (packing != null && in.has("packing")) {
            in.beginObject("packing");
            packing.load(in);
            in.endObject();
        }
        if (impeller != null && in.has("impeller")) {
            in.beginObject("impeller");
            impeller.load(in);
            in.endObject();
        }
    }

    /** Источники для {@link MaintenancePanel}. */
    public List<FaultSource> sources() {
        List<FaultSource> out = new ArrayList<>(List.of(bearing, lubrication));
        if (packing != null) out.add(packing);
        if (impeller != null) out.add(impeller);
        return out;
    }
}
