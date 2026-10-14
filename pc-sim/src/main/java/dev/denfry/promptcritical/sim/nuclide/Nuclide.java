package dev.denfry.promptcritical.sim.nuclide;

/**
 * Хранимые нуклиды топлива (01 §1.9.3): тяжёлые металлы, нейтронные яды, сосредоточенный
 * псевдо-ПД {@code LFP} и радиологические ПД.
 */
public enum Nuclide {
    U235(235, true), U236(236, true), U238(238, true), NP237(237, true), NP239(239, true),
    PU238(238, true), PU239(239, true), PU240(240, true), PU241(241, true), PU242(242, true),
    AM241(241, true), AM243(243, true), CM242(242, true), CM244(244, true),
    I135(135, false), XE135(135, false), PM149(149, false), SM149(149, false),
    /** Пара осколков деления, усреднённая (§1.9.3). */
    LFP(117.5, false),
    KR85(85, false), KR88(88, false), XE133(133, false), I131(131, false), I133(133, false),
    CS134(134, false), CS137(137, false), SR90(90, false), BA140(140, false), TE132(132, false),
    RU106(106, false), ZR95(95, false), CE144(144, false), H3(3, false);

    private final double molarMass;
    private final boolean heavyMetal;

    Nuclide(double massNumber, boolean heavyMetal) {
        this.molarMass = massNumber; // г/моль; дефект массы ниже точности модели
        this.heavyMetal = heavyMetal;
    }

    /** Молярная масса, г/моль (по массовому числу). */
    public double molarMass() {
        return molarMass;
    }

    public boolean heavyMetal() {
        return heavyMetal;
    }
}
