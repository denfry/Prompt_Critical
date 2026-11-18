package dev.denfry.promptcritical.sim.electronics;

/**
 * Электровакуумный триод уровня A2 (15 §15.6): эмиссия катода и ток пространственного заряда.
 *
 * <p>Ричардсон–Дешман J = A_G·T²·e^{−W/kT}; оксидный катод (BaO–SrO) — W ≈ 1,0 эВ, A_G ≈ 0,01 А/(см²·К²) при 1000–1100 К,
 * торированный вольфрам — W 2,63 эВ, 3 А/(см²·К²) при 1900–2000 К, чистый вольфрам — 4,5 эВ, 60 А/(см²·К²) при 2500 К
 * (Herrmann, Wagener, «The Oxide-Coated Cathode», 1951; Dushman 1930). Анодный ток ограничен пространственным зарядом
 * по Чайлду–Ленгмюру: J = (4ε₀/9)·√(2e/m)·V^{3/2}/d².
 */
public final class VacuumTube {
    public static final double K_EV = 8.617333262e-5, EPS0 = 8.8541878128e-12, E = 1.602176634e-19, ME = 9.1093837015e-31;

    public enum Cathode {
        OXIDE(1.0, 0.01, 1050), THORIATED_TUNGSTEN(2.63, 3, 1950), TUNGSTEN(4.5, 60, 2500);

        public final double workEv, richardsonAcm2K2, operatingK;

        Cathode(double workEv, double richardsonAcm2K2, double operatingK) {
            this.workEv = workEv;
            this.richardsonAcm2K2 = richardsonAcm2K2;
            this.operatingK = operatingK;
        }

        /** Плотность тока эмиссии, А/см². */
        public double emissionAcm2(double tempK) {
            return richardsonAcm2K2 * tempK * tempK * Math.exp(-workEv / (K_EV * tempK));
        }
    }

    /** Плотность тока пространственного заряда плоского диода, А/м². */
    public static double childLangmuirAm2(double volts, double gapM) {
        return 4 * EPS0 / 9 * Math.sqrt(2 * E / ME) * Math.pow(Math.max(0, volts), 1.5) / (gapM * gapM);
    }

    private VacuumTube() {
    }
}
