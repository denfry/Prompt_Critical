package dev.denfry.promptcritical.sim.ops;

import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;

/**
 * Дефект сборки из паспорта машины (18 §18.8.3): начальный дисбаланс, несоосность, течь уплотнения или прокладки.
 * Значение не растёт само; ремонт (балансировка, центровка, замена уплотнения) сбрасывает его в ноль.
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
        value = 0;
    }

    void save(StateWriter out) {
        out.putDouble("v", value);
    }

    void load(StateReader in) {
        value = in.getDouble("v");
    }
}
