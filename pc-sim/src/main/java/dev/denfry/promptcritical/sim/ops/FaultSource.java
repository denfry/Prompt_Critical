package dev.denfry.promptcritical.sim.ops;

import java.util.function.DoubleSupplier;

/**
 * Переменная износа модели-владельца для {@code maintenance_panel} (13 §13.6.5): панель только читает её, ремонт
 * сбрасывает её у владельца.
 */
public interface FaultSource {
    MaintenanceFaultKind kind();

    /** Текущее значение в единицах каталога {@link MaintenanceFaultKind}. */
    double value();

    /** Сброс переменной у владельца после ремонта ({@code MAINTENANCE_DONE}). */
    void repair();

    /** Источник поверх чужой модели (накипь котла, канат подъёма). */
    static FaultSource of(MaintenanceFaultKind kind, DoubleSupplier value, Runnable repair) {
        return new FaultSource() {
            @Override public MaintenanceFaultKind kind() { return kind; }
            @Override public double value() { return value.getAsDouble(); }
            @Override public void repair() { repair.run(); }
        };
    }
}
