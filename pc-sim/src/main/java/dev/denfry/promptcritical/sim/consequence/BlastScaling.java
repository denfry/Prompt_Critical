package dev.denfry.promptcritical.sim.consequence;

/**
 * Пересчёт энергии в «взрыв MC» по масштабированию Хопкинсона — Кранца (04d §4d.2.4):
 * W = E_mech / 4,184·10⁶ Дж/кг, P_MC = min(4 (W / W_ref)^{1/3}, P_max).
 */
public final class BlastScaling {
    /** Теплота взрыва ТНТ, Дж/кг. */
    public static final double TNT_J_PER_KG = 4.184e6;
    /** Мощность ванильного ТНТ. */
    public static final double VANILLA_TNT_POWER = 4.0;
    /** Радиус разрушения ванильного ТНТ ≈ 5 блоков (§4d.2.4): R ≈ 1,25 P. */
    public static final double RADIUS_PER_POWER = 1.25;

    private BlastScaling() {
    }

    public static double tntEquivalentKg(double mechanicalEnergyJ) {
        return mechanicalEnergyJ / TNT_J_PER_KG;
    }

    public static double power(double mechanicalEnergyJ, double tntReferenceKg, double maxPower) {
        double w = tntEquivalentKg(mechanicalEnergyJ);
        return Math.min(VANILLA_TNT_POWER * Math.cbrt(w / tntReferenceKg), maxPower);
    }

    public static double radiusBlocks(double power) {
        return RADIUS_PER_POWER * power;
    }

    /**
     * Остаточная интенсивность луча на расстоянии {@code d} блоков (упрощение ванильной модели: старт 1,3 P,
     * затухание 0,75 на блок, без затенения соседями).
     */
    public static double residualIntensity(double power, double d) {
        return 1.3 * power - 0.75 * d;
    }

    /** Блок разрушается, если остаточная интенсивность больше его порога (0,3 (R + 0,3), как в MC). */
    public static boolean breaks(double power, double d, float blastResistance) {
        return residualIntensity(power, d) > 0.3 * (blastResistance + 0.3);
    }
}
