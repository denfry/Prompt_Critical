package dev.denfry.promptcritical.sim.accident;

import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;

/**
 * Lumped containment atmosphere: air, steam and hydrogen in one well-mixed volume.
 *
 * <p>Mass–energy release from the primary heats the gas and the structures (lumped heat
 * capacity per m³ of free volume); steam above saturation condenses on the walls, so the
 * steam partial pressure is capped by p_sat(T). Passive autocatalytic recombiners remove H₂ at
 * a rate proportional to its concentration (≈ 1.5 kg/h per unit at 4 vol-%, AREVA FR90-class).
 * A global deflagration consumes the hydrogen once it reaches {@link #DEFLAGRATION_FRACTION};
 * the burn energy (LHV 120 MJ/kg) lifts the pressure (adiabatic isochoric complete combustion,
 * AICC). Pressure above the failure pressure breaches the containment.
 */
public final class ContainmentAtmosphere {
    public static final double R = 8.314;
    /** Lower limit of upward flame propagation is 4 %; a global burn needs ~8 % (NUREG/CR-5662). */
    public static final double DEFLAGRATION_FRACTION = 0.08;
    public static final double H2_LHV_J_KG = 120e6;
    private static final double M_AIR = 0.029, M_H2O = 0.018, M_H2 = 0.002016;
    /** Gas plus participating concrete/steel heat capacity, J/(K·m³ free volume). */
    private static final double STRUCTURE_J_K_M3 = 25e3;
    private static final double PAR_KG_S_AT_4PCT = 1.5 / 3600;

    private final double volumeM3;
    private final double failurePa;
    private final double airMol;
    private double steamMol;
    private double h2Mol;
    private double tK = 313;
    private int recombiners;
    private boolean failed, inerted;
    private double lastBurnJ, pendingHeatJ;

    /** Large dry PWR containment: 70 000 m³, fails at ~1.0 MPa (≈ 2.5× design). */
    public static ContainmentAtmosphere largeDry() {
        return new ContainmentAtmosphere(70_000, 1.0e6);
    }

    public ContainmentAtmosphere(double volumeM3, double failurePa) {
        if (!(volumeM3 > 0) || !(failurePa > 101_325)) throw new IllegalArgumentException("bad containment");
        this.volumeM3 = volumeM3;
        this.failurePa = failurePa;
        this.airMol = 101_325 * volumeM3 / (R * tK);
    }

    public void setRecombiners(int n) {
        recombiners = Math.max(0, n);
    }

    /**
     * Nitrogen-inerted atmosphere (BWR Mark I/II, required by 10 CFR 50.44): oxygen is held below the 5 vol-%
     * needed for combustion, so hydrogen accumulates but never burns.
     */
    public void setInerted(boolean on) {
        inerted = on;
    }

    /** Steam/water release from the primary (kg at the given specific enthalpy relative to 0 °C). */
    public void addSteam(double kg, double energyJ) {
        if (kg <= 0) return;
        steamMol += kg / M_H2O;
        tK += energyJ / heatCapacity() * 0.25; // most of the enthalpy goes into condensate on the walls
    }

    public void addHydrogen(double kg) {
        if (kg > 0) h2Mol += kg / M_H2;
    }

    /**
     * Heat dumped straight into the gas — direct containment heating by melt droplets blown out of a failing vessel
     * (04c §4c.10.3). Applied on the next {@link #step} like a burn: into the gas alone for the pressure peak, then
     * quenched by the walls.
     */
    public void addHeat(double j) {
        if (j > 0) pendingHeatJ += j;
    }

    /**
     * Advances recombination, condensation and the burn check.
     *
     * @return true when a deflagration happened during this step ({@link #lastBurnJ()} gives its energy)
     */
    public boolean step(double dt) {
        lastBurnJ = 0;
        // condensation: the steam partial pressure cannot exceed saturation at the gas temperature
        double maxSteam = saturationPa(tK) * volumeM3 / (R * tK);
        if (steamMol > maxSteam) steamMol = maxSteam;
        // slow cooldown to the walls/sprays, τ ≈ 1 h
        tK += (313 - tK) * Math.min(1, dt / 3600);
        double x = hydrogenFraction();
        if (recombiners > 0 && x > 0.005) {
            double kg = Math.min(h2Mol * M_H2, recombiners * PAR_KG_S_AT_4PCT * (x / 0.04) * dt);
            h2Mol -= kg / M_H2;
            tK += kg * H2_LHV_J_KG / heatCapacity();
        }
        boolean burn = !inerted && hydrogenFraction() >= DEFLAGRATION_FRACTION;
        if (burn) {
            lastBurnJ = h2Mol * M_H2 * H2_LHV_J_KG;
            steamMol += h2Mol;
            h2Mol = 0;
            // AICC: all the heat into the gas alone before the walls take it
            double gasCp = 21 * (airMol + steamMol);
            tK += lastBurnJ / gasCp;
        }
        boolean heated = pendingHeatJ > 0;
        if (heated) {
            tK += pendingHeatJ / (21 * (airMol + steamMol + h2Mol));
            pendingHeatJ = 0;
        }
        if (pressurePa() > failurePa) failed = true;
        if (burn || heated) tK = Math.min(tK, 313 + (tK - 313) * 0.3); // walls quench the burn within the step
        return burn;
    }

    private double heatCapacity() {
        return STRUCTURE_J_K_M3 * volumeM3;
    }

    /** Antoine (water, 1–374 °C range fit), Pa. */
    static double saturationPa(double tK) {
        double c = tK - 273.15;
        return 133.322 * Math.pow(10, 8.14019 - 1810.94 / (244.485 + c));
    }

    public double pressurePa() {
        return (airMol + steamMol + h2Mol) * R * tK / volumeM3;
    }

    public double hydrogenFraction() {
        return h2Mol / (airMol + steamMol + h2Mol);
    }

    public double volumeM3() { return volumeM3; }
    public double temperatureK() { return tK; }
    public double hydrogenKg() { return h2Mol * M_H2; }
    public boolean failed() { return failed; }
    public double lastBurnJ() { return lastBurnJ; }

    public void save(StateWriter out) {
        out.putDouble("steam", steamMol);
        out.putDouble("h2", h2Mol);
        out.putDouble("t", tK);
        out.putBoolean("failed", failed);
    }

    public void load(StateReader in) {
        steamMol = Math.max(0, in.optDouble("steam", 0));
        h2Mol = Math.max(0, in.optDouble("h2", 0));
        tK = in.optDouble("t", 313);
        failed = in.optInt("failed", 0) != 0;
    }
}
