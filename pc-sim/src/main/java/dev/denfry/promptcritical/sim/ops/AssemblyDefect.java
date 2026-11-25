package dev.denfry.promptcritical.sim.ops;

import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;

/**
 * Дефект сборки из паспорта машины (18 §18.8.3): начальный дисбаланс, несоосность, течь уплотнения или прокладки,
 * сопротивление изоляции обмотки. Значение не меняется само; ремонт (балансировка, центровка, замена уплотнения,
 * перемотка) возвращает его к значению исправной машины ({@link MaintenanceFaultKind#normal()}).
 */
public final class AssemblyDefect implements FaultSource {
    private final MaintenanceFaultKind kind;
    private double value;

    public AssemblyDefect(MaintenanceFaultKind kind, double value) {
        if (kind == null || !(value >= 0) || !Double.isFinite(value)) throw new IllegalArgumentException("defect");
        this.kind = kind;
        this.value = value;
    }

    @Override
    public MaintenanceFaultKind kind() {
        return kind;
    }

    @Override
    public double value() {
        return value;
    }

    @Override
    public void repair() {
        value = kind.normal();
    }

    void save(StateWriter out) {
        out.putDouble("v", value);
    }

    void load(StateReader in) {
        value = in.getDouble("v");
    }
}
