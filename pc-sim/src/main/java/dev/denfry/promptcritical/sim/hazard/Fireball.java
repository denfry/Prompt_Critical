package dev.denfry.promptcritical.sim.hazard;

/**
 * Огненный шар BLEVE (09 §9.11.6, CCPS 1994): D_max = 5,8·M^{1/3} м, длительность t = 0,45·M^{1/3} с (M &lt; 30 т;
 * больше — та же формула, оговорки CCPS не моделируются), излучающая способность поверхности 200–350 кВт/м².
 * 10 т пропана: D = 125 м, t = 9,7 с. Поток на расстоянии R от центра — SEP · (D/2)²/R² (коэффициент облучённости
 * сферы; внутри шара — SEP).
 */
public final class Fireball {
    /** Излучающая способность поверхности, середина диапазона 200–350 кВт/м² [моё]. */
    public static final double SURFACE_EMISSIVE_KW_M2 = 275;

    private Fireball() {
    }

    public static double diameterM(double massKg) {
        return 5.8 * Math.cbrt(Math.max(0, massKg));
    }

    public static double durationS(double massKg) {
        return 0.45 * Math.cbrt(Math.max(0, massKg));
    }

    public static double fluxKwM2(double massKg, double rM) {
        double half = diameterM(massKg) / 2;
        if (half <= 0) return 0;
        return rM <= half ? SURFACE_EMISSIVE_KW_M2 : SURFACE_EMISSIVE_KW_M2 * half * half / (rM * rM);
    }
}
