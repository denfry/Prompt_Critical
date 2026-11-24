package dev.denfry.promptcritical.sim.ops;

import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;
import dev.denfry.promptcritical.sim.manufacturing.assembly.MachinePassport;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
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
    /** Множитель износа подшипника от посадки при сборке (18 §18.8.3); переживает замену подшипника. */
    private double assemblyWearFactor = 1;
    /** Дефекты сборки из паспорта, в порядке {@link MaintenanceFaultKind}. */
    private final Map<MaintenanceFaultKind, AssemblyDefect> assembly = new EnumMap<>(MaintenanceFaultKind.class);

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
        return bearing.step(dtPhysS, wearMultiplier * assemblyWearFactor, aLub);
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

    /**
     * Начальное состояние из паспорта машины (18 §18.15.7): посадка подшипника и дефекты сборки. Вызывается один раз
     * при установке; номинальный паспорт ничего не меняет.
     *
     * @return новые источники для щита обслуживания
     */
    public List<FaultSource> applyPassport(MachinePassport p) {
        assemblyWearFactor = p.bearingWearFactor();
        List<FaultSource> added = new ArrayList<>();
        addDefect(MaintenanceFaultKind.IMBALANCE, p.imbalanceMmS(), added);
        addDefect(MaintenanceFaultKind.MISALIGNMENT, p.misalignmentMm(), added);
        addDefect(MaintenanceFaultKind.SEAL_LEAK, p.sealLeak(), added);
        addDefect(MaintenanceFaultKind.FLANGE_LEAK, p.flangeLeak(), added);
        return added;
    }

    private void addDefect(MaintenanceFaultKind kind, double value, List<FaultSource> added) {
        if (!(value > 0) || assembly.containsKey(kind)) return;
        AssemblyDefect d = new AssemblyDefect(kind, value);
        assembly.put(kind, d);
        added.add(d);
    }

    public double assemblyWearFactor() {
        return assemblyWearFactor;
    }

    public void save(StateWriter out) {
        if (assemblyWearFactor != 1) out.putDouble("assembly_wear", assemblyWearFactor);
        if (!assembly.isEmpty()) {
            out.beginObject("assembly");
            for (AssemblyDefect d : assembly.values()) {
                out.beginObject(d.kind().name());
                d.save(out);
                out.endObject();
            }
            out.endObject();
        }
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

    /**
     * @return источники дефектов сборки, восстановленные из сохранения (щит добавляет их после базовых, как при
     * установке)
     */
    public List<FaultSource> load(StateReader in) {
        assemblyWearFactor = in.optDouble("assembly_wear", 1);
        assembly.clear();
        List<FaultSource> restored = new ArrayList<>();
        if (in.has("assembly")) {
            in.beginObject("assembly");
            for (MaintenanceFaultKind k : MaintenanceFaultKind.values()) {
                if (!in.has(k.name())) continue;
                in.beginObject(k.name());
                AssemblyDefect d = new AssemblyDefect(k, 0);
                d.load(in);
                in.endObject();
                assembly.put(k, d);
                restored.add(d);
            }
            in.endObject();
        }
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
        return restored;
    }

    /** Источники для {@link MaintenancePanel}. */
    public List<FaultSource> sources() {
        List<FaultSource> out = new ArrayList<>(List.of(bearing, lubrication));
        if (packing != null) out.add(packing);
        if (impeller != null) out.add(impeller);
        out.addAll(assembly.values());
        return out;
    }
}
