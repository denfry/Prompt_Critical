package dev.denfry.promptcritical.sim.isotopes;

/**
 * Тепловыделение и температура капсулы в канале (12 §12.1.4): распад продуктов плюс γ-нагрев зоны
 * q̇_γ = k_γ·P/P_ном (5 Вт/г стали на номинале PWR — {@code isotopes.gamma_heating_w_per_g}). Температура
 * квазистационарная T = T_охл + P·R_th; выше предела материала оболочки — разгерметизация.
 */
public final class CapsuleThermal {
    private CapsuleThermal() {
    }

    /** Мощность в капсуле, Вт. */
    public static double powerW(TargetMaterial m, double[] atoms, double massKg, double gammaWPerG, double powerFraction) {
        return m.decayHeatW(atoms) + massKg * 1000 * gammaWPerG * Math.max(0, powerFraction);
    }

    /** Температура капсулы, К. */
    public static double tempK(double coolantK, double powerW, double rThKPerW) {
        return coolantK + Math.max(0, powerW) * rThKPerW;
    }
}
