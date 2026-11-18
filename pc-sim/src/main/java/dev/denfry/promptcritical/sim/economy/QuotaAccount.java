package dev.denfry.promptcritical.sim.economy;

import java.util.UUID;

/**
 * Счёт квот CO₂ команды (14 §14.7.1, §14.11), тонны CO₂-экв.: выделено в периоде, на руках (выделение + перенесённый
 * излишек + купленное), долг прошлого периода, номер периода n.
 */
public record QuotaAccount(UUID team, double allocatedT, double heldT, double carriedDebtT, int period) {}
