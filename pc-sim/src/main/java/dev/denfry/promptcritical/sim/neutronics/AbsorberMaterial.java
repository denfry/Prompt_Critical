package dev.denfry.promptcritical.sim.neutronics;

/** Поглотители стержней СУЗ и стеллажей (01 §1.10.1): тепловое Σ_a материала, см⁻¹. */
public enum AbsorberMaterial {
    /** Природный бор, 2,52 г/см³. */
    B4C(81),
    /** Ag-In-Cd 80/15/5 (сильное резонансное поглощение — не учитывается). */
    AG_IN_CD(5),
    HAFNIUM(4),
    /** Только тепловые нейтроны (T0). */
    CADMIUM(120),
    /** Сталь с 1,5 % природного бора — стеллажи хранения ТВС. */
    BORON_STEEL(5.3),
    NONE(0);

    private final double sigmaA;

    AbsorberMaterial(double sigmaA) {
        this.sigmaA = sigmaA;
    }

    public double sigmaA() {
        return sigmaA;
    }
}
