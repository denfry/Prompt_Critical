package dev.denfry.promptcritical.sim.grid;

import dev.denfry.promptcritical.sim.core.SimId;
import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;

/**
 * Щиток освещения {@code lighting_board} на шине 0,4 кВ: одна нагрузка {@code LIGHTING} на группу светильников.
 * Мощность группы — сумма светильников, которые питает щиток; меняется без пересборки сети. Светильники горят,
 * пока включён групповой автомат и напряжение на вводе не ниже {@value #LAMP_MIN_PU} номинала (лампы ДРЛ и
 * люминесцентные гаснут при глубокой просадке).
 */
public final class LightingBoardUnit extends GridUnit {
    /** Ниже этого напряжения, о.е., разрядные лампы гаснут. */
    public static final double LAMP_MIN_PU = 0.7;

    double demandW;
    boolean switchedOn = true;
    LoadId load;

    LightingBoardUnit(SimId id) {
        super(id, 1);
    }

    @Override
    VoltageClass terminalClass(int terminal) {
        return VoltageClass.LV_0_4;
    }

    /** Установленная мощность светильников группы, Вт. */
    public double demandW() {
        return demandW;
    }

    /** Мощность группы, Вт; меняет нагрузку в действующей сети без пересборки. */
    public void setDemandW(double watts) {
        double w = Math.max(0, watts);
        if (Math.abs(w - demandW) < 1e-6) return;
        demandW = w;
        if (load != null && owner != null && owner.ready()) owner.grid().setLoadPowerW(load, w);
    }

    public boolean switchedOn() {
        return switchedOn;
    }

    /** Групповой автомат щитка. */
    public void setSwitchedOn(boolean on) {
        switchedOn = on;
        if (load != null && owner != null && owner.ready()) owner.grid().setLoadConnected(load, on);
    }

    /** Напряжение на вводе, о.е. 0,4 кВ (0 — шины нет или она без источника). */
    public double inputPu() {
        return node(0) == NO_NODE || owner == null || !owner.ready() ? 0 : owner.voltageV(node(0)) / VoltageClass.LV_0_4.volts;
    }

    /** Светильники группы горят. */
    public boolean lampsLit() {
        return switchedOn && load != null && inputPu() >= LAMP_MIN_PU;
    }

    /** Мощность, которую забирает группа, Вт. */
    public double powerW() {
        return load != null && owner.ready() && switchedOn ? owner.grid().loadPowerW(load) : 0;
    }

    @Override
    void build(GridSubsystem.Build b) {
        if (node(0) == NO_NODE) return;
        load = b.grid.addLoad(LoadModel.typical(LoadKind.LIGHTING, demandW), b.bus(node(0)));
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
