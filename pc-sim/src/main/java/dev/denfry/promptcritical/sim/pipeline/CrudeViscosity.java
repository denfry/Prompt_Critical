package dev.denfry.promptcritical.sim.pipeline;

/**
 * Вязкость дегазированной нефти по Беггсу — Робинсону (Beggs &amp; Robinson, 1975; 09 §9.3.6):
 * μ_od = 10^x − 1 сП, x = T_F^−1,163 · 10^(3,0324 − 0,02023·API). Корреляция построена на нефтях 16–58 °API при
 * 70–295 °F; за этими пределами — экстраполяция.
 */
public final class CrudeViscosity {
    private CrudeViscosity() {
    }

    /** Плотность в градусах API по плотности при 15 °C, кг/м³ (ρ воды 999,0). */
    public static double api(double densityKgM3) {
        return 141.5 / (densityKgM3 / 999.0) - 131.5;
    }

    /** Динамическая вязкость дегазированной нефти, сП (мПа·с), при {@code tempC}. */
    public static double deadOilCp(double densityKgM3, double tempC) {
        double tF = Math.max(32, tempC * 9 / 5 + 32);
        double x = Math.pow(tF, -1.163) * Math.pow(10, 3.0324 - 0.02023 * api(densityKgM3));
        return Math.pow(10, x) - 1;
    }
}
