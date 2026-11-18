package dev.denfry.promptcritical.sim.acoustics;

/**
 * Класс машины по ISO 10816-1:1995, приложение B (общий уровень СКЗ виброскорости в полосе 10–1000 Гц, м/с), 04a §4a.6.4.
 * Те же границы зон приняты в ГОСТ ИСО 10816-1-97; ISO 20816-1:2016 их сохраняет как ориентир для машин без своей части
 * стандарта.
 */
public enum MachineClass {
    /** Малые машины до 15 кВт. */
    I(0.71e-3, 1.8e-3, 4.5e-3),
    /** Средние 15–75 кВт (и до 300 кВт на особом фундаменте). */
    II(1.12e-3, 2.8e-3, 7.1e-3),
    /** Крупные на жёстком фундаменте. */
    III(1.8e-3, 4.5e-3, 11.2e-3),
    /** Крупные на податливом фундаменте: турбоагрегаты, генераторы. */
    IV(2.8e-3, 7.1e-3, 18.0e-3);

    public static final double SMALL_MAX_W = 15e3, MEDIUM_MAX_W = 75e3;

    /** Границы зон A/B, B/C, C/D, м/с СКЗ. */
    public final double ab, bc, cd;

    MachineClass(double ab, double bc, double cd) {
        this.ab = ab;
        this.bc = bc;
        this.cd = cd;
    }

    public IsoZone zoneOf(double vRms) {
        return vRms < ab ? IsoZone.A : vRms < bc ? IsoZone.B : vRms < cd ? IsoZone.C : IsoZone.D;
    }

    /**
     * Класс по мощности на валу (04a §4a.6.4: «задаётся по мощности автоматически»).
     *
     * @param flexible податливый фундамент: генератор, турбина
     */
    public static MachineClass forPower(double shaftPowerW, boolean flexible) {
        if (shaftPowerW <= SMALL_MAX_W) return I;
        if (shaftPowerW <= MEDIUM_MAX_W) return II;
        return flexible ? IV : III;
    }
}
