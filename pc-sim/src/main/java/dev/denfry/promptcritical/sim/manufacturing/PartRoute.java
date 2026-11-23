package dev.denfry.promptcritical.sim.manufacturing;

/** Способ получения заготовки детали (GDD 18 §18.4). Сериализуется по {@code name()}: константы не переименовываются. */
public enum PartRoute { CAST, FORGED, ROLLED, DRAWN, WELDED }
