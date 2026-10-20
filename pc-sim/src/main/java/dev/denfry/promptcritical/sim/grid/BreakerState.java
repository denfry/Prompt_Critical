package dev.denfry.promptcritical.sim.grid;

/** Состояние выключателя — автомат 07 §7.4.7. */
public enum BreakerState { OPEN, CLOSING, CLOSED, OPENING, FAILED_TO_INTERRUPT, FAULTED }
