package dev.denfry.promptcritical.sim.hazard;

/**
 * Струйный пожар — горящий фонтан газа или двухфазной смеси (09 §9.11.5): Q = ṁ·LHV, длина факела (API 521)
 * L ≈ 0,00326·Q^{0,478} м (Q в Вт; 100 МВт — около 22 м), излучение — точечный источник с χr = 0,2. Струя, бьющая в
 * соседний аппарат, греет его стенку потоком 100–250 кВт/м² — главный механизм «эффекта домино».
 */
public final class JetFire {
    public static final double RADIANT_FRACTION = 0.2;
    /** Поток на стенку аппарата в факеле: середина диапазона 100–250 кВт/м² (§9.11.5) [моё]. */
    public static final double IMPINGEMENT_KW_M2 = 175;

    private JetFire() {
    }

    public static double heatReleaseW(double kgS, Flammable gas) {
        return Math.max(0, kgS) * gas.lhvJkg;
    }

    public static double lengthM(double qW) {
        return qW <= 0 ? 0 : 0.00326 * Math.pow(qW, 0.478);
    }

    /** Плотность потока на расстоянии {@code rM} от середины факела, кВт/м². */
    public static double fluxKwM2(double qW, double rM) {
        return ThermalRadiation.pointSourceKwM2(RADIANT_FRACTION, qW, rM);
    }
}
