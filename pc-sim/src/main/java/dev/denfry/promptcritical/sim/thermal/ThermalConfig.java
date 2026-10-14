package dev.denfry.promptcritical.sim.thermal;

/**
 * Ключи {@code thermal.*} (01 §1.15), которые читают тепловые структуры.
 *
 * @param fuelRadialNodes           {@code fuel_radial_nodes} — кольца таблетки по умолчанию (3..5)
 * @param maxCouplingSubsteps       {@code max_coupling_substeps}
 * @param filmRadEmissivity         {@code film_rad_emissivity} [BALANCE]
 * @param gapJumpDistance           {@code gap_jump_distance_m}, м [BALANCE]
 * @param gapRelocationFraction     {@code gap_relocation_fraction} [BALANCE]
 * @param fgrSlopePer100K           {@code fgr_slope_per_100K} [BALANCE]
 * @param noncondensableDegradation {@code noncondensable_degradation} [BALANCE]
 */
public record ThermalConfig(int fuelRadialNodes, int maxCouplingSubsteps, double filmRadEmissivity,
        double gapJumpDistance, double gapRelocationFraction, double fgrSlopePer100K,
        double noncondensableDegradation) {
    public ThermalConfig {
        if (fuelRadialNodes < 3 || fuelRadialNodes > 5) throw new IllegalArgumentException("fuelRadialNodes");
        if (maxCouplingSubsteps < 1) throw new IllegalArgumentException("maxCouplingSubsteps");
        if (!(filmRadEmissivity >= 0 && filmRadEmissivity <= 1)) throw new IllegalArgumentException("filmRadEmissivity");
        if (!(gapJumpDistance >= 0)) throw new IllegalArgumentException("gapJumpDistance");
        if (!(gapRelocationFraction >= 0 && gapRelocationFraction < 1)) {
            throw new IllegalArgumentException("gapRelocationFraction");
        }
        if (!(fgrSlopePer100K >= 0)) throw new IllegalArgumentException("fgrSlopePer100K");
        if (!(noncondensableDegradation >= 0 && noncondensableDegradation < 1)) {
            throw new IllegalArgumentException("noncondensableDegradation");
        }
    }

    public static ThermalConfig defaults() {
        return new ThermalConfig(4, 8, 0.7, 1.0e-5, 0.3, 0.05, 0.7);
    }
}
