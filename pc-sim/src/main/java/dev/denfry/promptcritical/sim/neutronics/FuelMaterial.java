package dev.denfry.promptcritical.sim.neutronics;

/**
 * Материал топлива: плотность, кислород на атом ТМ и параметры резонансного интеграла U-238
 * по Хеллстранду (01 §1.6.2): I(300 К) = A + B√(S/M), β_I = a + b·S/M.
 */
public enum FuelMaterial {
    /** UO₂, 95 % ТП. */
    UO2(10.41, 2, 4.45, 26.6, 6.1e-3, 4.7e-3),
    U_METAL(18.9, 0, 2.95, 25.8, 4.8e-3, 6.4e-3),
    MOX(10.41, 2, 4.45, 26.6, 6.1e-3, 4.7e-3);

    private final double density, oxygenPerHeavyAtom, resonanceA, resonanceB, dopplerA, dopplerB;

    FuelMaterial(double density, double oxygenPerHeavyAtom, double resonanceA, double resonanceB, double dopplerA,
            double dopplerB) {
        this.density = density;
        this.oxygenPerHeavyAtom = oxygenPerHeavyAtom;
        this.resonanceA = resonanceA;
        this.resonanceB = resonanceB;
        this.dopplerA = dopplerA;
        this.dopplerB = dopplerB;
    }

    /** г/см³. */
    public double density() {
        return density;
    }

    public double oxygenPerHeavyAtom() {
        return oxygenPerHeavyAtom;
    }

    /** I_eff(300 К), б; {@code surfaceToMass} — S/M, см²/г, уже с поправкой Данкова. */
    public double resonanceIntegral300(double surfaceToMass) {
        return resonanceA + resonanceB * Math.sqrt(surfaceToMass);
    }

    /** β_I, К^{-1/2}. */
    public double dopplerCoefficient(double surfaceToMass) {
        return dopplerA + dopplerB * surfaceToMass;
    }
}
