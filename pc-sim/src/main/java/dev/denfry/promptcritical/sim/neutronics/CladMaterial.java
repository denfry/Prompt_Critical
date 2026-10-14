package dev.denfry.promptcritical.sim.neutronics;

/** Конструкционные материалы оболочек и труб каналов: плотность и сечения при 2200 м/с (01 §1.6.2). */
public enum CladMaterial {
    ZIRCALOY(6.55, 91.22, 0.185, 6.4),
    /** Zr + 1 % Nb (Э110, Э125). */
    ZR_NB(6.55, 91.40, 0.195, 6.4),
    ALUMINIUM(2.70, 26.98, 0.231, 1.4),
    STEEL(7.90, 55.85, 2.56, 11.0);

    private final double density, molarMass, sigmaA, sigmaS;

    CladMaterial(double density, double molarMass, double sigmaA, double sigmaS) {
        this.density = density;
        this.molarMass = molarMass;
        this.sigmaA = sigmaA;
        this.sigmaS = sigmaS;
    }

    /** Ядер в 1 см³, ×10²⁴ (б⁻¹·см⁻¹). */
    public double numberDensity() {
        return density * LatticeModel.AVOGADRO_BARN / molarMass;
    }

    public double sigmaA() {
        return sigmaA;
    }

    public double sigmaS() {
        return sigmaS;
    }

    public double massNumber() {
        return molarMass;
    }
}
