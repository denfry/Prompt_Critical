package dev.denfry.promptcritical.sim.neutronics;

/** Конструкционные материалы оболочек и труб каналов: плотность и сечения при 2200 м/с (01 §1.6.2). */
public enum CladMaterial {
    ZIRCALOY(6.55, 91.22, 0.185, 6.4),
    /** Zr + 1 % Nb (Э110, Э125). */
    ZR_NB(6.55, 91.40, 0.195, 6.4),
    ALUMINIUM(2.70, 26.98, 0.231, 1.4),
    /**
     * Нержавеющая 08Х18Н10Т (AISI 321): Fe 69, Cr 18, Ni 10, Mn 2, Ti и Si по 0,5 % по массе; σ смеси по атомам
     * (ENDF/B-VII.1: Fe 2,56, Cr 3,05, Ni 4,49, Mn 13,3, Ti 6,09 б). Чистое железо занижало поглощение на 16 %.
     */
    STEEL(7.90, 55.04, 3.05, 10.4);

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
