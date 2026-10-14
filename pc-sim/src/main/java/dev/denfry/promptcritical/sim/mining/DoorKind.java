package dev.denfry.promptcritical.sim.mining;

/** Исполнение {@code ventilation_door} (08 §8.2.7). */
public enum DoorKind {
    /** глухая дверь: закрытая — R = 50 Н·с²/м⁸ последовательно с ветвью */
    DOOR,
    /** регулятор — дверь с настраиваемым окном */
    REGULATOR
}
