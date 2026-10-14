package dev.denfry.promptcritical.sim.neutronics;

/**
 * Ключи {@code neutronics.*} (01 §1.15).
 *
 * @param maxDelayedGroups           {@code max_delayed_groups} — 6 групп Кипина + до 3 фотонейтронных
 * @param kineticsMaxSubsteps        {@code kinetics_max_substeps}
 * @param photoneutronBetaD2o        {@code photoneutron_beta_d2o} [BALANCE]
 * @param photoneutronBetaBe         {@code photoneutron_beta_be} [BALANCE]
 * @param coolantAdjacencyFactor     {@code coolant_adjacency_factor} [BALANCE]; 1,2 вместо 2,0 из GDD —
 *                                   калибровка на паровой коэффициент T1 ≈ +22 pcm/% (РБМК до 1986 г.)
 * @param fastFissionCoeff           {@code fast_fission_coeff} [BALANCE]; 0,05 вместо 0,075 —
 *                                   калибровка на ε = 1,03 для блочков T0 (оракул самого GDD)
 * @param nodalOuterIterationsPerUpdate {@code nodal_outer_iterations_per_update}
 * @param intranodePeaking           {@code intranode_peaking} [BALANCE]
 * @param pinPeaking                 {@code pin_peaking} [BALANCE]
 */
public record NeutronicsConfig(int maxDelayedGroups, int kineticsMaxSubsteps, double photoneutronBetaD2o,
        double photoneutronBetaBe, double coolantAdjacencyFactor, double fastFissionCoeff,
        int nodalOuterIterationsPerUpdate, double intranodePeaking, double pinPeaking) {
    public NeutronicsConfig {
        if (maxDelayedGroups < DelayedNeutronData.KEEPIN_GROUPS) throw new IllegalArgumentException("maxDelayedGroups");
        if (kineticsMaxSubsteps < 1) throw new IllegalArgumentException("kineticsMaxSubsteps");
        if (!(photoneutronBetaD2o >= 0)) throw new IllegalArgumentException("photoneutronBetaD2o");
        if (!(photoneutronBetaBe >= 0)) throw new IllegalArgumentException("photoneutronBetaBe");
        if (!(coolantAdjacencyFactor >= 1)) throw new IllegalArgumentException("coolantAdjacencyFactor");
        if (!(fastFissionCoeff >= 0)) throw new IllegalArgumentException("fastFissionCoeff");
        if (nodalOuterIterationsPerUpdate < 1) throw new IllegalArgumentException("nodalOuterIterationsPerUpdate");
        if (!(intranodePeaking >= 1)) throw new IllegalArgumentException("intranodePeaking");
        if (!(pinPeaking >= 1)) throw new IllegalArgumentException("pinPeaking");
    }

    public static NeutronicsConfig defaults() {
        return new NeutronicsConfig(9, 4096, 5e-4, 1e-4, 1.2, 0.05, 4, 1.15, 1.08);
    }

    public NeutronicsConfig withCoolantAdjacencyFactor(double g) {
        return new NeutronicsConfig(maxDelayedGroups, kineticsMaxSubsteps, photoneutronBetaD2o, photoneutronBetaBe, g,
                fastFissionCoeff, nodalOuterIterationsPerUpdate, intranodePeaking, pinPeaking);
    }
}
