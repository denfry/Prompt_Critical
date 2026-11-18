package dev.denfry.promptcritical.sim.grid;

import dev.denfry.promptcritical.sim.core.SimId;
import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;

/**
 * {@code rectifier_unit}: выпрямительный агрегат электролиза 6,3 кВ — трансформатор с РПН и кремниевый выпрямитель
 * (в 1950-е — ртутные вентили). Нагрузка {@link LoadKind#RECTIFIER} постоянной мощности, cos φ 0,92; КПД
 * преобразования {@value #EFFICIENCY} (O'Brien et al., т. I, гл. 5 «Power supply»: кремниевые 96–98 %, ртутные
 * 93–95 %). Мир задаёт мощность постоянного тока, которую берёт серия ванн; при просадке сети РПН держит ток до
 * 0,9 о.е., ниже ток снижается пропорционально напряжению, ниже 0,7 о.е. агрегат отключается.
 */
public final class RectifierUnit extends GridUnit {
    public static final double EFFICIENCY = 0.965;
    /** Предельная мощность постоянного тока, Вт [BALANCE: серия 20 ванн на 40 кА]. */
    public static final double RATED_DC_W = 3.2e6;

    double dcDemandW;
    boolean switchedOn = true;
    LoadId load;

    RectifierUnit(SimId id) {
        super(id, 1);
    }

    @Override
    VoltageClass terminalClass(int terminal) {
        return VoltageClass.MV_6_3;
    }

    public boolean switchedOn() {
        return switchedOn;
    }

    public void setSwitchedOn(boolean on) {
        switchedOn = on;
        if (load != null && owner != null && owner.ready()) owner.grid().setLoadConnected(load, on);
    }

    /** Мощность постоянного тока, которую просит серия, Вт; меняет нагрузку в действующей сети без пересборки. */
    public void setDcDemandW(double watts) {
        double w = Math.clamp(watts, 0, RATED_DC_W);
        if (Math.abs(w - dcDemandW) < 1) return;
        dcDemandW = w;
        if (load != null && owner != null && owner.ready()) owner.grid().setLoadPowerW(load, w / EFFICIENCY);
    }

    public double dcDemandW() {
        return dcDemandW;
    }

    /** Напряжение на вводе, о.е. 6,3 кВ; 0 — нет сети или агрегат отключён. */
    public double supplyPu() {
        if (!switchedOn || load == null || node(0) == NO_NODE || owner == null || !owner.ready()) return 0;
        return owner.voltageV(node(0)) / VoltageClass.MV_6_3.volts;
    }

    /** Доля заданного тока, которую агрегат может дать при нынешнем напряжении сети. */
    public double currentFraction() {
        double v = supplyPu();
        if (v < 0.7) return 0;
        return Math.min(1, v / 0.9);
    }

    /** Мощность из сети, Вт. */
    public double powerW() {
        return load != null && owner.ready() && switchedOn ? owner.grid().loadPowerW(load) : 0;
    }

    @Override
    void build(GridSubsystem.Build b) {
        if (node(0) == NO_NODE) return;
        load = b.grid.addLoad(LoadModel.typical(LoadKind.RECTIFIER, dcDemandW / EFFICIENCY), b.bus(node(0)));
        b.grid.setLoadConnected(load, switchedOn);
    }

    @Override
    void unbind() {
        load = null;
    }

    @Override
    void save(StateWriter out) {
        saveCommon(out);
        out.putDouble("dc", dcDemandW);
        out.putBoolean("on", switchedOn);
    }

    @Override
    void load(StateReader in) {
        loadCommon(in);
        dcDemandW = in.optDouble("dc", 0);
        switchedOn = in.optInt("on", 1) != 0;
    }
}
