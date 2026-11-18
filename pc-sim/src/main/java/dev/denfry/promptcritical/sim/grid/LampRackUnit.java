package dev.denfry.promptcritical.sim.grid;

import dev.denfry.promptcritical.sim.core.SimId;
import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;

/**
 * Зарядный стеллаж ламповой {@code lamp_rack} на шине 0,4 кВ: одна нагрузка {@link LoadKind#RECTIFIER} —
 * постоянная мощность выпрямителей, cos φ 0,92. Мощность — сумма зарядных мест, которые сейчас заряжают батареи
 * (мир пересчитывает её каждый тик без пересборки сети); напряжение ввода ограничивает зарядный ток.
 */
public final class LampRackUnit extends GridUnit {
    double demandW;
    boolean switchedOn = true;
    LoadId load;

    LampRackUnit(SimId id) {
        super(id, 1);
    }

    @Override
    VoltageClass terminalClass(int terminal) {
        return VoltageClass.LV_0_4;
    }

    public double demandW() {
        return demandW;
    }

    /** Мощность выпрямителей, Вт; меняет нагрузку в действующей сети без пересборки. */
    public void setDemandW(double watts) {
        double w = Math.max(0, watts);
        if (Math.abs(w - demandW) < 1e-6) return;
        demandW = w;
        if (load != null && owner != null && owner.ready()) owner.grid().setLoadPowerW(load, w);
    }

    public boolean switchedOn() {
        return switchedOn;
    }

    /** Вводной автомат стеллажа. */
    public void setSwitchedOn(boolean on) {
        switchedOn = on;
        if (load != null && owner != null && owner.ready()) owner.grid().setLoadConnected(load, on);
    }

    /** Напряжение на зарядных местах, о.е. 0,4 кВ; 0 — нет сети или автомат отключён. */
    public double supplyPu() {
        if (!switchedOn || load == null || node(0) == NO_NODE || owner == null || !owner.ready()) return 0;
        return owner.voltageV(node(0)) / VoltageClass.LV_0_4.volts;
    }

    /** Мощность из сети, Вт. */
    public double powerW() {
        return load != null && owner.ready() && switchedOn ? owner.grid().loadPowerW(load) : 0;
    }

    @Override
    void build(GridSubsystem.Build b) {
        if (node(0) == NO_NODE) return;
        load = b.grid.addLoad(LoadModel.typical(LoadKind.RECTIFIER, demandW), b.bus(node(0)));
        b.grid.setLoadConnected(load, switchedOn);
    }

    @Override
    void unbind() {
        load = null;
    }

    @Override
    void save(StateWriter out) {
        saveCommon(out);
        out.putDouble("demand", demandW);
        out.putBoolean("on", switchedOn);
    }

    @Override
    void load(StateReader in) {
        loadCommon(in);
        demandW = in.optDouble("demand", 0);
        switchedOn = in.optInt("on", 1) != 0;
    }
}
