package dev.denfry.promptcritical.sim.grid;

import dev.denfry.promptcritical.sim.core.SimId;
import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;

/**
 * Коннектор провода ВЛ {@code wire_connector_lv}/{@code wire_connector_mv} (07 §7.4.2): точка подвеса пролётов.
 * Его узел — кабели и выводы устройств, которых он касается; пролёты ({@link LineUnit}) соединяют узлы коннекторов.
 */
public final class ConnectorUnit extends GridUnit {
    final boolean mediumVoltage;

    ConnectorUnit(SimId id, boolean mediumVoltage) {
        super(id, 1);
        this.mediumVoltage = mediumVoltage;
    }

    public boolean mediumVoltage() {
        return mediumVoltage;
    }

    @Override
    VoltageClass terminalClass(int terminal) {
        return mediumVoltage ? VoltageClass.MV_6_3 : VoltageClass.LV_0_4;
    }

    /** Наибольший пролёт класса, блоков: 0,4 кВ — 16, 6–10 кВ — 32 (§7.4.2). */
    public int maxSpan() {
        return mediumVoltage ? 32 : 16;
    }

    @Override
    void build(GridSubsystem.Build b) {
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
