package dev.denfry.promptcritical.sim.grid;

/** Марка провода (07 §7.4.1): сечение, R₂₀, длительно допустимый ток, масса, разрывное усилие. */
public record ConductorSpec(Conductor material, double crossSectionMm2, double r20OhmPerKm, double ampacityA,
        double massKgPerM, double rtsN) {}
