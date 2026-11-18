package dev.denfry.promptcritical.sim.accident;

import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;

/**
 * Lumped containment atmosphere: air, steam and hydrogen in one well-mixed volume.
 *
 * <p>Mass–energy release from the primary heats the gas and the structures (lumped heat
 * capacity per m³ of free volume); steam above saturation condenses on the walls, so the
 * steam partial pressure is capped by p_sat(T). Passive autocatalytic recombiners remove H₂ at
 * a rate proportional to its concentration (≈ 1.5 kg/h per unit at 4 vol-%, AREVA FR90-class),
 * using the oxygen and leaving water.
 *
 * <p><b>Combustion</b> (04c §4c.7.3–§4c.7.5). The mixture is classified by {@link HydrogenCombustion}: more than 55 %
 * steam or less than 5 % oxygen does not burn (a nitrogen-inerted BWR containment holds 3.5 % O₂), whatever the
 * hydrogen. Ignition: a global burn once the hydrogen reaches {@link #DEFLAGRATION_FRACTION} — a large building always
 * has a spark somewhere by then — or as soon as the mixture is flammable when a hot surface is in it (corium out of the
 * vessel, melt droplets of direct heating; above 858 K, §4c.7.4). A slow deflagration below 8 % burns only part of the
 * hydrogen. The burn heat goes into the gas at constant c_v = 25 J/(mol·K) on the moles left after
 * {@code H₂ + ½O₂ → H₂O} (AICC, T-4c-12); a detonation loads the walls with twice that pressure (Chapman–Jouguet).
 * Pressure above the failure pressure breaches the containment.
 *
 * <p>Not modelled: random sparks, deliberate igniters and local (non-global) hydrogen pockets.
 */
public final class ContainmentAtmosphere {
    public static final double R = 8.314;
    /** Lower limit of upward flame propagation is 4 %; a global burn needs ~8 % (NUREG/CR-5662). */
    public static final double DEFLAGRATION_FRACTION = HydrogenCombustion.COMPLETE;
    public static final double H2_LHV_J_KG = 120e6;
    /** Constant c_v of the burnt gas, J/(mol·K) (04c §4c.7.5, T-4c-12). */
    public static final double CV_J_MOLK = 25;
    /** Oxygen in air, and in a nitrogen-inerted containment (10 CFR 50.44 keeps it under 4 %). */
    public static final double O2_AIR = 0.21, O2_INERTED = 0.035;
    private static final double M_H2O = 0.018, M_H2 = 0.002016;
    /** Gas plus participating concrete/steel heat capacity, J/(K·m³ free volume). */
    private static final double STRUCTURE_J_K_M3 = 25e3;
    private static final double PAR_KG_S_AT_4PCT = 1.5 / 3600;

    private final double volumeM3;
    private final double failurePa;
    /** Nitrogen and argon of the air (never react). */
    private double inertMol;
    private double o2Mol;
    private double steamMol;
    private double h2Mol;
    private double tK = 313;
    private int recombiners;
    private boolean failed, inerted, hotSurface;
    private double lastBurnJ, pendingHeatJ, lastPeakPa;
    private HydrogenCombustion.Regime lastRegime = HydrogenCombustion.Regime.NONE;

    /** Large dry PWR containment: 70 000 m³, fails at ~1.0 MPa (≈ 2.5× design). */
    public static ContainmentAtmosphere largeDry() {
        return new ContainmentAtmosphere(70_000, 1.0e6);
    }

    public ContainmentAtmosphere(double volumeM3, double failurePa) {
        if (!(volumeM3 > 0) || !(failurePa > 101_325)) throw new IllegalArgumentException("bad containment");
        this.volumeM3 = volumeM3;
        this.failurePa = failurePa;
        double airMol = 101_325 * volumeM3 / (R * tK);
        this.inertMol = airMol * (1 - O2_AIR);
        this.o2Mol = airMol * O2_AIR;
    }

    public void setRecombiners(int n) {
        recombiners = Math.max(0, n);
    }

    /**
     * Nitrogen-inerted atmosphere (BWR Mark I/II, required by 10 CFR 50.44): oxygen is held below the 5 vol-%
     * needed for combustion, so hydrogen accumulates but never burns.
     */
    public void setInerted(boolean on) {
        if (on == inerted) return;
        inerted = on;
        // nitrogen replaces the oxygen: the moles of the air stay
        double air = inertMol + o2Mol;
        o2Mol = air * (on ? O2_INERTED : O2_AIR);
        inertMol = air - o2Mol;
    }

    /** A surface above 858 K in the gas (corium, melt droplets): it ignites any flammable mixture (§4c.7.4). */
    public void setHotSurface(boolean on) {
        hotSurface = on;
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
     * @return true when a deflagration or detonation happened during this step ({@link #lastBurnJ()} gives its
     *         energy, {@link #lastRegime()} its kind)
     */
    public boolean step(double dt) {
        lastBurnJ = 0;
        lastRegime = HydrogenCombustion.Regime.NONE;
        // condensation: the steam partial pressure cannot exceed saturation at the gas temperature
        double maxSteam = saturationPa(tK) * volumeM3 / (R * tK);
        if (steamMol > maxSteam) steamMol = maxSteam;
        // slow cooldown to the walls/sprays, τ ≈ 1 h
        tK += (313 - tK) * Math.min(1, dt / 3600);
        double x = hydrogenFraction();
        if (recombiners > 0 && x > 0.005 && o2Mol > 0) {
            double kg = Math.min(Math.min(h2Mol, 2 * o2Mol) * M_H2, recombiners * PAR_KG_S_AT_4PCT * (x / 0.04) * dt);
            double mol = kg / M_H2;
            h2Mol -= mol;
            o2Mol -= mol / 2;
            steamMol += mol;
            tK += kg * H2_LHV_J_KG / heatCapacity();
        }
        HydrogenCombustion.Regime regime = regime();
        x = hydrogenFraction();
        boolean ignited = regime != HydrogenCombustion.Regime.NONE && (x >= DEFLAGRATION_FRACTION || hotSurface);
        boolean burn = false;
        if (ignited) {
            double eta = HydrogenCombustion.completeness(regime, x, steamFraction());
            double mol = Math.min(eta * h2Mol, 2 * o2Mol);
            if (mol > 0) {
                burn = true;
                lastRegime = regime;
                lastBurnJ = mol * M_H2 * H2_LHV_J_KG;
                h2Mol -= mol;
                o2Mol -= mol / 2;
                steamMol += mol;
                // AICC: all the heat into the gas alone before the walls take it
                tK += lastBurnJ / (CV_J_MOLK * totalMol());
            }
        }
        boolean heated = pendingHeatJ > 0;
        if (heated) {
            tK += pendingHeatJ / (CV_J_MOLK * totalMol());
            pendingHeatJ = 0;
        }
        lastPeakPa = pressurePa() * (lastRegime == HydrogenCombustion.Regime.DETONATION ? HydrogenCombustion.CJ_OVER_AICC : 1);
        if (lastPeakPa > failurePa) failed = true;
        if (burn || heated) tK = Math.min(tK, 313 + (tK - 313) * 0.3); // walls quench the burn within the step
        return burn;
    }

    private double heatCapacity() {
        return STRUCTURE_J_K_M3 * volumeM3;
    }

    private double totalMol() {
        return inertMol + o2Mol + steamMol + h2Mol;
    }

    /** Antoine (water, 1–374 °C range fit), Pa. */
    static double saturationPa(double tK) {
        double c = tK - 273.15;
        return 133.322 * Math.pow(10, 8.14019 - 1810.94 / (244.485 + c));
    }

    public double pressurePa() {
        return totalMol() * R * tK / volumeM3;
    }

    public double hydrogenFraction() {
        return h2Mol / totalMol();
    }

    public double steamFraction() {
        return steamMol / totalMol();
    }

    public double oxygenFraction() {
        return o2Mol / totalMol();
    }

    /** What the mixture would do if lit now (§4c.7.3); the volume's size sets the deflagration-to-detonation length. */
    public HydrogenCombustion.Regime regime() {
        return HydrogenCombustion.classify(hydrogenFraction(), steamFraction(), oxygenFraction(), Math.cbrt(volumeM3));
    }

    public double volumeM3() { return volumeM3; }
    public double temperatureK() { return tK; }
    public double hydrogenKg() { return h2Mol * M_H2; }
    public boolean failed() { return failed; }
    public double lastBurnJ() { return lastBurnJ; }
    /** Kind of the burn of the last step ({@code NONE} — none). */
    public HydrogenCombustion.Regime lastRegime() { return lastRegime; }
    /** Wall load of the last step: the pressure, twice it for a detonation, Pa. */
    public double lastPeakPa() { return lastPeakPa; }

    public void save(StateWriter out) {
        out.putDouble("steam", steamMol);
        out.putDouble("h2", h2Mol);
        out.putDouble("o2", o2Mol);
        out.putDouble("n2", inertMol);
        out.putDouble("t", tK);
        out.putBoolean("failed", failed);
    }

    public void load(StateReader in) {
        steamMol = Math.max(0, in.optDouble("steam", 0));
        h2Mol = Math.max(0, in.optDouble("h2", 0));
        if (in.has("o2")) o2Mol = Math.max(0, in.optDouble("o2", o2Mol));
        if (in.has("n2")) inertMol = Math.max(0, in.optDouble("n2", inertMol));
        tK = in.optDouble("t", 313);
        failed = in.optInt("failed", 0) != 0;
    }
}
