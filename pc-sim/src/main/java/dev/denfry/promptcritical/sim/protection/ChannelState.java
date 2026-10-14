package dev.denfry.promptcritical.sim.protection;

/** Состояние измерительного канала (01 §1.11.2). {@code FAULT} голосует «да» (fail-safe). */
public enum ChannelState { OK, TRIP, FAULT, BYPASS }
