package dev.denfry.promptcritical.sim.automation.twin;

/** Действие сценария возмущений или инструктора (03d §3d.9.1 {@code schedule}, 11 §11.3.3). */
public sealed interface ScenarioAction {
    /** Ввести отказ. */
    record Inject(Fault fault) implements ScenarioAction {
    }

    /** Возмущение со стороны объекта: команда механизму (например, поставщик прибавил приём). */
    record Set(String actuator, double value) implements ScenarioAction {
    }

    /** Снять все отказы. */
    record ClearFaults() implements ScenarioAction {
    }
}
