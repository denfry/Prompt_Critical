package dev.denfry.promptcritical.sim.accident;

import dev.denfry.promptcritical.sim.neutronics.DecayHeat;

/**
 * Large-break LOCA in a lumped PWR primary (one control volume, two-node fuel/cladding).
 *
 * <p>Blowdown: homogeneous critical mass flux {@code G = Cd·√(2·ρ̄·Δp)} with ρ̄ = M/V (the HEM
 * mixture density of the whole primary, Cd = 0.35 standing in for the Moody saturated two-phase multiplier). The
 * pressure follows the inventory, {@code p = p_c + (p_sat − p_c)·(M/M₀)^1.3} (monotone: refill does not repressurise),, after an instant
 * subcooled decompression to the hot-leg saturation pressure. ECCS: accumulators (passive,
 * ~4.2 MPa), HPI below 11 MPa and LPI below 1.1 MPa (after a 25 s diesel load sequence) when AC power is available; while the
 * primary is above 1.5 MPa most of the injected water is swept out the break (ECC bypass).
 *
 * <p>Core: scrammed by voiding at t = 0, decay heat from {@link DecayHeat}. Fuel and cladding
 * exchange heat through a gap conductance chosen so the nominal ΔT carries full power; the
 * cladding is cooled by a coverage-weighted reflood/steam coefficient. Zr–steam oxidation
 * ({@link ZircaloyOxidation}) adds heat and hydrogen. PCT is the hot-rod cladding temperature:
 * the core-average rise above coolant times the peaking factor.
 *
 * <p>Simplifications (documented for the handbook): one volume, no loop seal or downcomer
 * boiling, no steam binding, a fixed bypass fraction, no ballooning/rupture of the cladding.
 */
public final class LossOfCoolantTransient {
    /** 2200 °F, the 10 CFR 50.46 peak-cladding-temperature limit. */
    public static final double PCT_LIMIT_K = 1204 + 273.15;

    // Reference 4-loop plant (3411 MWt), values rounded from the Westinghouse FSAR class.
    private static final double POWER_W = 3.411e9;
    private static final double M0 = 2.5e5;
    private static final double VOLUME_M3 = M0 / 700;
    private static final double P_HOT_SAT = 10e6;
    private static final double LOWER_PLENUM_KG = 30e3;
    private static final double CORE_WATER_KG = 20e3;
    private static final double SPILL_KG = LOWER_PLENUM_KG + CORE_WATER_KG + 15e3;
    private static final double FUEL_CAP = 9.0e4 * 300;
    private static final double CLAD_CAP = 2.0e4 * 330;
    private static final double GAP_W_K = POWER_W / (1200 - 620);
    private static final double H_REFLOOD_W_K = 2e7;
    private static final double H_STEAM_W_K = 5e4;
    private static final double H_FILM_W_K = 3e6;
    private static final double LPI_DELAY_S = 25;
    private static final double CD = 0.35;
    private static final double PEAKING = 1.6;
    private static final double HFG = 2.0e6;
    /** Zircaloy melts (≈2125 K): past this the lumped model stops meaning anything — core damage. */
    public static final double CLAD_MELT_K = 2125;

    private final double breakAreaM2;
    private final double containmentPa;
    private final boolean acPower;
    private final Accumulator[] accumulators;
    private final ZircaloyOxidation zr = new ZircaloyOxidation(5560, 2.0e4);

    private double t;
    private double mass = M0;
    private double pressure = 15.5e6;
    private double tFuel = 1200;
    private double tClad = 620;
    private double pct = 620;
    private double pctTime;
    private double releasedKg;
    private double releasedJ;
    private double lpiStartS = Double.NaN;

    /**
     * @param breakAreaM2  total flow area (0.77 m² for a double-ended 0.7 m cold leg)
     * @param accumulators number of available accumulators (four on a 4-loop plant)
     * @param acPower      whether pumped ECCS (HPI/LPI) runs
     */
    public LossOfCoolantTransient(double breakAreaM2, double containmentPa, int accumulators, boolean acPower) {
        if (!(breakAreaM2 > 0) || !(containmentPa > 0) || accumulators < 0)
            throw new IllegalArgumentException("bad LOCA parameters");
        this.breakAreaM2 = breakAreaM2;
        this.containmentPa = containmentPa;
        this.acPower = acPower;
        this.accumulators = new Accumulator[accumulators];
        for (int i = 0; i < accumulators; i++) this.accumulators[i] = Accumulator.westinghouse();
    }

    public static LossOfCoolantTransient doubleEndedColdLeg(int accumulators, boolean acPower) {
        return new LossOfCoolantTransient(0.77, 0.2e6, accumulators, acPower);
    }

    public void step(double dt) {
        t += dt;
        // ── blowdown ────────────────────────────────────────────────────────────
        double dp = pressure - containmentPa;
        double out = 0;
        if (dp > 0 && pressure > 0.35e6) { // near containment pressure the break vents steam; liquid only spills (see below)
            double rho = mass / VOLUME_M3;
            out = Math.min(mass, CD * breakAreaM2 * Math.sqrt(2 * rho * dp) * dt);
        }
        // ── ECCS ────────────────────────────────────────────────────────────────
        double in = 0;
        for (Accumulator a : accumulators) in += a.step(pressure, dt, t);
        if (acPower && pressure < 11e6) in += 30 * dt;
        if (acPower && pressure < 1.1e6 && t >= LPI_DELAY_S) {
            if (Double.isNaN(lpiStartS)) lpiStartS = t;
            in += 500 * dt;
        }
        if (pressure > 1.5e6) in *= 0.3;
        // ── core ────────────────────────────────────────────────────────────────
        double decay = POWER_W * DecayHeat.afterInfiniteOperation(t);
        double cov = coverage();
        double tCool = saturationK(pressure);
        double gap = GAP_W_K * (tFuel - tClad);
        // above 1.5 MPa the flashing core is past DNB: film boiling; below it the collapsed level decides
        double hA = pressure > 1.5e6 ? H_FILM_W_K : cov * H_REFLOOD_W_K + (1 - cov) * H_STEAM_W_K;
        double cool = hA * Math.max(0, tClad - tCool);
        double zrKg = zr.step(tClad, dt);
        double oxW = zrKg * ZircaloyOxidation.HEAT_J_PER_KG_ZR / dt;
        tFuel += (decay - gap) * dt / FUEL_CAP;
        tClad = Math.min(CLAD_MELT_K, tClad + (gap + oxW - cool) * dt / CLAD_CAP);
        tFuel = Math.min(3100, tFuel);
        double boil = cov * Math.min(decay, cool) * dt / HFG;

        mass = Math.max(0, mass - out - boil + in);
        if (pressure <= 0.35e6 && mass > SPILL_KG) mass = SPILL_KG; // spills out the cold-leg break
        releasedKg += out + boil;
        releasedJ += out * 1.3e6 + boil * 2.7e6;
        // once vented, refilling with cold ECC water does not repressurise: pressure only falls
        double f = Math.min(1, mass / M0);
        pressure = Math.min(pressure, containmentPa + (P_HOT_SAT - containmentPa) * Math.pow(f, 1.3));
        double hot = Math.min(CLAD_MELT_K, tCool + PEAKING * (tClad - tCool));
        if (hot > pct) {
            pct = hot;
            pctTime = t;
        }
    }

    /** Fraction of the core height covered by the collapsed liquid level. */
    public double coverage() {
        return Math.max(0, Math.min(1, (mass - LOWER_PLENUM_KG) / CORE_WATER_KG));
    }

    /** Clausius–Clapeyron with h_fg/R = 4890 K, good to ~15 K over 0.1–16 MPa. */
    static double saturationK(double pa) {
        return 1 / (1 / 373.15 - Math.log(pa / 101_325) / 4890);
    }

    public double timeS() { return t; }
    public double pressurePa() { return pressure; }
    public double primaryMassKg() { return mass; }
    public double cladK() { return tClad; }
    public double peakCladK() { return pct; }
    public double peakCladTimeS() { return pctTime; }
    public boolean pctLimitExceeded() { return pct > PCT_LIMIT_K; }
    public double hydrogenKg() { return zr.hydrogenKg(); }
    public double oxidizedFraction() { return zr.reactedFraction(); }
    /** Mass and energy blown into containment so far (kg, J). */
    public double releasedMassKg() { return releasedKg; }
    public double releasedEnergyJ() { return releasedJ; }
    public double lpiStartS() { return lpiStartS; }

    /** Earliest accumulator injection, s (NaN if none injected). */
    public double accumulatorStartS() {
        double first = Double.NaN;
        for (Accumulator a : accumulators) {
            double s = a.injectionStartS();
            if (!Double.isNaN(s) && (Double.isNaN(first) || s < first)) first = s;
        }
        return first;
    }
}
