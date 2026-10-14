package dev.denfry.promptcritical.sim.hydraulics;

/** Тип связи гидросети (01 §1.2.2, §1.2.8). Арматура и насосы — связи, а не узлы (§1.12.1). */
public enum LinkKind { PIPE, PUMP, VALVE_GATE, VALVE_CHECK, VALVE_RELIEF, VALVE_CONTROL, BREAK }
