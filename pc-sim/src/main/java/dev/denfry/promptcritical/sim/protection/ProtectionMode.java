package dev.denfry.promptcritical.sim.protection;

/** Состояние автомата АЗ (01 §1.11.5). */
public enum ProtectionMode { NORMAL, TRIP_PENDING, TRIPPED, RESET_ARMED, FAILED_ARMED }
