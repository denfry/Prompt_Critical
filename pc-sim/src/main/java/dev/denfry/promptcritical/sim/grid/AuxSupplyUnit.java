package dev.denfry.promptcritical.sim.grid;

import dev.denfry.promptcritical.sim.core.SimId;
import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;

/**
 * Вход {@code relay_power_supply} от шины 0,4 кВ (03d §3d.13.4, этап 9 M3): выпрямитель — нагрузка сети
 * {@code RECTIFIER} мощности блока при полном токе (КПД 0,8 [BALANCE]); автоматике отдаёт напряжение шины в о.е.
 */
public final class AuxSupplyUnit extends GridUnit {
    public static final double EFFICIENCY = 0.8;

    double powerW = 150;
    LoadId load;

    AuxSupplyUnit(SimId id) {
        super(id, 1);
    }

    @Override
    VoltageClass terminalClass(int terminal) {
        return VoltageClass.LV_0_4;
    }

    /** Мощность выпрямителя при номинальном токе 24 В, Вт. */
    public void setRatedPowerW(double watts) {
        if (Math.abs(watts - powerW) < 1e-9) return;
        powerW = watts;
        changed();
    }

    /** Напряжение на входе, о.е. 0,4 кВ (0 — шины нет или она без источника). */
    public double inputPu() {
        return node(0) == NO_NODE || owner == null ? 0 : owner.voltageV(node(0)) / VoltageClass.LV_0_4.volts;
    }

    @Override
    void build(GridSubsystem.Build b) {
        if (node(0) == NO_NODE) return;
        load = b.grid.addLoad(LoadModel.typical(LoadKind.RECTIFIER, powerW), b.bus(node(0)));
        b.grid.setLoadConnected(load, true);
    }

    @Override
    void unbind() {
        load = null;
    }

    @Override
    void save(StateWriter out) {
        saveCommon(out);
        out.putDouble("power", powerW);
    }

    @Override
    void load(StateReader in) {
        loadCommon(in);
        powerW = in.optDouble("power", 150);
    }
}
