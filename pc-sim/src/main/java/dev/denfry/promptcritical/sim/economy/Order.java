package dev.denfry.promptcritical.sim.economy;

/** Заявка (14 §14.11): {@code qty} > 0 — покупка у NPC, < 0 — продажа NPC; лимит средней цены в центах. */
public record Order(String commodityId, double qty, long limitAvgCents) {}
