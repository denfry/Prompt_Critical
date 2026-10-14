package dev.denfry.promptcritical.sim.grid;

/** Вид нагрузки (07 §7.6.1); {@code FE_IMPORT} с отрицательной P — источник без инерции (§7.7). */
public enum LoadKind { HEATER, LIGHTING, PROCESS, NPC_CONSUMER, RECTIFIER, ARC_FURNACE, FE_IMPORT }
