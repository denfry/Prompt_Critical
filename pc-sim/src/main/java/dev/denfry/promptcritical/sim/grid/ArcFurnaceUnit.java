package dev.denfry.promptcritical.sim.grid;

import dev.denfry.promptcritical.sim.core.SimId;
import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;

/**
 * Дуговая печь на шине 6 кВ (07 §7.6.5): нагрузка постоянной мощности, которую печь меняет каждый тик —
 * дуга неустойчива, и вместе с ней скачут P и Q ({@link dev.denfry.promptcritical.sim.metallurgy.ArcFurnace}).
 */
public final class ArcFurnaceUnit extends GridUnit {
    LoadId load;
    private double demandW, demandVar;

    ArcFurnaceUnit(SimId id) {
        super(id, 1);
    }

    @Override
    VoltageClass terminalClass(int terminal) {
        return VoltageClass.MV_6_3;
    }

    /** Мощность печи на этот тик, Вт и вар. */
    public void setDemand(double pW, double qVar) {
        demandW = Math.max(0, pW);
        demandVar = Math.max(0, qVar);
        if (load != null && owner.ready()) owner.grid().setLoadPower(load, demandW, demandVar);
    }

    /** Напряжение шины печи, о.е. (0 — без напряжения). */
    public double voltagePu() {
        if (load == null || !owner.ready() || node(0) == NO_NODE) return 0;
        return owner.voltageV(node(0)) / VoltageClass.MV_6_3.volts;
    }

    public double demandW() {
        return demandW;
    }

    @Override
    void build(GridSubsystem.Build b) {
        if (node(0) == NO_NODE) return;
        load = b.grid.addLoad(LoadModel.constantPower(demandW, demandVar, LoadKind.ARC_FURNACE).withDamping(0), b.bus(node(0)));
    }

    @Override
    void unbind() {
        load = null;
    }

    @Override
    void save(StateWriter out) {
        saveCommon(out);
    }

    @Override
    void load(StateReader in) {
        loadCommon(in);
    }
}
