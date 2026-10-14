package dev.denfry.promptcritical.sim.protection;

/** Кто инициировал АЗ (04d §4d.7.4); дополняет {@link TripReason}, не заменяет её. */
public enum TripSource { AUTOMATIC, OPERATOR_PANEL, LUA, WATCHDOG, SERVER_POLICY, ADMIN_COMMAND }
