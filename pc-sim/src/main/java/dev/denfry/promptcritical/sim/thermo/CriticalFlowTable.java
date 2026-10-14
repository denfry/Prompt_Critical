package dev.denfry.promptcritical.sim.thermo;

/**
 * Критический (запертый) массовый поток по параметрам торможения (01 §1.2.8, §1.3.9).
 * Реализация — предвычисленная таблица G_c(ln p₀, h₀); потокобезопасна, без аллокаций.
 */
public interface CriticalFlowTable {
    /** Конфиг {@code hydraulics.critical_flow_model}. */
    enum Model { HEM, MOODY }

    /** Критический массовый поток G_c [кг/(м²·с)] по параметрам торможения p₀ [Па], h₀ [Дж/кг]. */
    double criticalMassFlux(double p0, double h0);

    Model model();

    static CriticalFlowTable of(Model model) {
        return CriticalFlowTables.get(model);
    }
}
