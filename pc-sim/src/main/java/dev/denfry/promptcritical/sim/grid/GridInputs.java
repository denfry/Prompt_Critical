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

    /**
     * Момент нагрузки на валу {@code electric_motor} от сети Create, о.е. на P_ном/ω_s (§7.6.3);
     * NaN — держать заданный {@link PowerGrid#setMotorLoadTorque}.
     */
    default double motorLoadTorquePu(int motor) {
        return Double.NaN;
    }
}
