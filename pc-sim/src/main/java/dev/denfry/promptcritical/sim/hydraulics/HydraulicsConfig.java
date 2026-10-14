package dev.denfry.promptcritical.sim.hydraulics;

import java.util.Objects;

/**
 * Ключи {@code hydraulics.*} (01 §1.15), которые читает решатель. Модель критического
 * расхода выбирает пакет среды ({@code WaterPackage.of}).
 *
 * @param reliefBlowdown        {@code relief_blowdown} [BALANCE]
 * @param twoPhaseMultiplier    {@code two_phase_multiplier}
 * @param moistureCarryover     {@code moisture_carryover} [BALANCE]
 * @param maxLinearizationError {@code max_linearization_error}, доля p
 * @param breakDischargeCoeff   {@code break_discharge_coeff} [BALANCE]
 * @param maxSubsteps           {@code max_substeps}
 */
public record HydraulicsConfig(double reliefBlowdown, Friction.TwoPhaseMultiplier twoPhaseMultiplier,
        double moistureCarryover, double maxLinearizationError, double breakDischargeCoeff, int maxSubsteps) {
    public HydraulicsConfig {
        Objects.requireNonNull(twoPhaseMultiplier, "twoPhaseMultiplier");
        if (!(reliefBlowdown >= 0 && reliefBlowdown < 1)) throw new IllegalArgumentException("reliefBlowdown");
        if (!(moistureCarryover >= 0 && moistureCarryover < 1)) throw new IllegalArgumentException("moistureCarryover");
        if (!(maxLinearizationError > 0)) throw new IllegalArgumentException("maxLinearizationError");
        if (!(breakDischargeCoeff > 0)) throw new IllegalArgumentException("breakDischargeCoeff");
        if (maxSubsteps < 1) throw new IllegalArgumentException("maxSubsteps");
    }

    public static HydraulicsConfig defaults() {
        return new HydraulicsConfig(0.05, Friction.TwoPhaseMultiplier.HEM, 0.002, 0.02, 1.0, 64);
    }
}
