package dev.denfry.promptcritical.sim.economy;

/** Котировка (14 §14.11): bid — предельная цена продажи NPC, ask — покупки с учётом спреда. */
public record Quote(long bidCents, long askCents, long midCents, double trend, double sigma,
                    double salesVolume, double depth, double tPhysS) {}
