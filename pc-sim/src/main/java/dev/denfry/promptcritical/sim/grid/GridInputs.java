package dev.denfry.promptcritical.sim.grid;

/** Входы тика сети от соседних моделей (07 §7.19.1). Вызываются в горячем цикле — без аллокаций. */
public interface GridInputs {
    GridInputs NONE = new GridInputs() {};

    /**
     * Механическая мощность на валу {@code shaft_generator} от сети Create, Вт;
     * NaN — держать заданную {@link SynchronousMachine#setLoadTarget}.
     */
    default double createShaftPowerW(int machine) {
        return Double.NaN;
    }
}
