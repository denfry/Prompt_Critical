package dev.denfry.promptcritical.sim.grid;

/**
 * Состояние асинхронного двигателя (07 §7.6.3): {@code OFF} — без напряжения или контактор отключён
 * (выбег); за критическим скольжением — {@code STARTING}, пока момент двигателя больше момента
 * нагрузки, иначе {@code STALLED} (опрокинулся).
 */
public enum MotorState { OFF, STARTING, RUNNING, STALLED }
