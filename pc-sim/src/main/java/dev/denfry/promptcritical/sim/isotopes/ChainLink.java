package dev.denfry.promptcritical.sim.isotopes;

/**
 * Звено цепочки облучения (12 §12.10): период полураспада (бесконечность — стабильный), тепловые сечения захвата и
 * деления, б, и средняя энергия распада, уходящая в тепло капсулы, МэВ.
 */
public record ChainLink(String nuclide, double halfLifeS, double sigmaCaptureB, double sigmaFissionB, double meanDecayEnergyMeV) {
    public static final double DAY = 86400, YEAR = 365.25 * DAY, MEV_J = 1.602176634e-13;

    public ChainLink {
        if (!(halfLifeS > 0) || sigmaCaptureB < 0 || sigmaFissionB < 0 || meanDecayEnergyMeV < 0) throw new IllegalArgumentException(nuclide);
    }

    /** Постоянная распада, 1/с (0 — стабильный). */
    public double lambda() {
        return Double.isInfinite(halfLifeS) ? 0 : Math.log(2) / halfLifeS;
    }
}
