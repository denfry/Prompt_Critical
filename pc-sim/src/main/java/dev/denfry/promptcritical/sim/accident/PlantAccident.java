package dev.denfry.promptcritical.sim.accident;

import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;

/**
 * Accident layer of a light-water reactor in the world: the coolant inventory, core cooling and containment once the
 * primary is no longer a closed, powered loop. The reactor's own simulator ({@code PressurizedWaterReactor},
 * {@code BoilingWaterReactor}) keeps the neutronics, the decay heat and the normal hydraulics; this layer takes the
 * core power from it, the heat its steam generators or turbine still remove, and its pressure while the loop is
 * intact, and answers how much water is left over the fuel, how hot the cladding gets, how much hydrogen the
 * zirconium makes and what the containment does with it. The reactor blocks feed the answers back: a scram, a
 * pressure and a level on the panel, failed cladding, a hydrogen burn or a containment breach through the
 * consequence router.
 *
 * <p><b>Initiating events.</b> A break of area A (a pipe or pump torn out of the loop, or the drill button on the
 * panel) or a station blackout: no AC on the safety buses, so pumped ECCS and normal makeup stop. The layer stays
 * idle — following the reactor's pressure with a full inventory — until one of them happens.
 *
 * <p><b>Break.</b> The same lumped blowdown as {@link LossOfCoolantTransient}: an instant subcooled decompression to
 * the hot-leg saturation pressure, then homogeneous critical flow {@code G = Cd·A·√(2ρ̄Δp)} with ρ̄ = M/V and
 * Cd = 0.35, and the pressure following the inventory {@code p = p_c + (p_sat − p_c)·(M/M₀)^1.3}, never rising again.
 * Near containment pressure the water above the break nozzle spills out. A large break in blowdown sweeps 70 % of
 * the injected water out of the break (ECC bypass).
 *
 * <p><b>Intact primary.</b> Core heat the heat sink does not take lifts the pressure to the relief setpoint and then
 * boils water off through the valves, {@code dM/dt = −(Q_core − Q_sink)/h_fg} — the {@link StationBlackout} clock.
 * With AC the normal makeup (charging pumps, feedwater) keeps the inventory up.
 *
 * <p><b>ECCS.</b> Each pumped train is a high-head and a low-head stage, each with a parabolic pump curve
 * {@code H = H₀·(1 − (q/q_max)²)} read backwards: {@code q = q_max·√(1 − p/p_shutoff)}; both need AC. Accumulators
 * ({@link Accumulator}) need nothing but a primary below their nitrogen pressure. A BWR has the automatic
 * depressurisation system instead: after 120 s at low level, with a low-pressure pump running, the relief valves
 * blow the vessel down so the low-head pumps can inject (GE BWR/4 logic: low level, a low-pressure pump
 * running, a 120 s timer — USNRC GE BWR/4 Technology Manual).
 *
 * <p><b>Core.</b> Two nodes as in {@link LossOfCoolantTransient}, scaled to the plant: fuel and cladding exchange
 * heat through a gap conductance that carries full power over the nominal 580 K fuel-to-clad drop; the cladding is
 * cooled by nucleate boiling over the covered height and by steam above it; Baker–Just oxidation
 * ({@link ZircaloyOxidation}) adds heat and hydrogen. The hot rod runs 1.6× the core-average rise. Cladding bursts
 * from about 800 °C (balloon-and-burst data, NUREG-0630), so between 800 °C and the 1204 °C limit of 10 CFR 50.46
 * the breached share of the core grows linearly — a stand-in for the radial power spread.
 *
 * <p><b>Containment.</b> Break mass and energy go into {@link ContainmentAtmosphere} with the zirconium's hydrogen;
 * a pressure-suppression containment (BWR) condenses the steam in its pool and keeps only the gas, and is inerted.
 * The relief-valve discharge of a PWR reaches the containment through the relief tank, a BWR's goes into the pool.
 * Without a closed containment the hydrogen and the steam go to the open air.
 *
 * <p>Simplifications: one volume, no loop seal, no counter-current flow limit, no steam binding, no reflood
 * quench front, no in-vessel melt progression past the cladding melting point.
 */
public final class PlantAccident {
    /** Hot-rod to core-average rise above coolant (F_q-class peaking on the lumped node). */
    public static final double PEAKING = 1.6;
    /** Zircaloy balloon-and-burst onset, K (≈ 800 °C, NUREG-0630). */
    public static final double BURST_K = 800 + 273.15;
    public static final double ATMOSPHERE_PA = 101_325;
    /** ADS timer after the low-level signal, s. */
    public static final double ADS_DELAY_S = 120;
    private static final double CD = 0.35;
    private static final double BYPASS_PA = 1.5e6;
    private static final double MAX_SUBSTEP_S = 0.25;

    /**
     * Plant data. Masses and powers are those of the 100 MWt game plants; flows and areas are the full-size values
     * scaled by the power ratio.
     *
     * @param hotSatPa         saturation pressure at the hot-leg temperature (subcooled decompression stops here)
     * @param reliefPa         relief / safety valve setpoint
     * @param inventoryKg      primary water at normal level
     * @param volumeM3         primary (vessel and loops) volume
     * @param lowerPlenumKg    water below the bottom of the active fuel
     * @param coreWaterKg      water between the bottom and the top of the active fuel
     * @param breakNozzleKg    inventory up to the break nozzle: above it the water spills out near containment pressure
     * @param makeupKgS        normal makeup with AC (charging or feedwater)
     * @param hpiKgS           high-head stage run-out flow per ECCS train
     * @param hpiShutoffPa     high-head stage shutoff pressure
     * @param lpiKgS           low-head stage run-out flow per train
     * @param lpiShutoffPa     low-head stage shutoff pressure
     * @param accTankM3        accumulator tank volume (0: the plant has none)
     * @param accWaterM3       accumulator water volume
     * @param accPa            accumulator nitrogen pressure
     * @param accLineM2        accumulator injection line area
     * @param adsM2            ADS valve area (0: no ADS)
     * @param suppressionPool  steam into containment condenses in a pool (pressure suppression, inerted)
     * @param smallBreakM2     the drill's small break
     * @param largeBreakM2     double-ended guillotine break of the main coolant pipe
     * @param saturated        the primary runs at saturation (BWR): a break under water does not depressurise it,
     *                         the decay heat keeps it at pressure until steam reaches the break or the ADS opens
     */
    public record Design(String id, double nominalW, double hotSatPa, double reliefPa, double inventoryKg, double volumeM3,
            double lowerPlenumKg, double coreWaterKg, double breakNozzleKg, double zrKg, double cladM2, double fuelJK,
            double cladJK, double makeupKgS, double hpiKgS, double hpiShutoffPa, double lpiKgS, double lpiShutoffPa,
            double accTankM3, double accWaterM3, double accPa, double accLineM2, double adsM2, boolean suppressionPool,
            double containmentFailurePa, double smallBreakM2, double largeBreakM2, boolean saturated) {

        public Design {
            if (!(nominalW > 0) || !(inventoryKg > lowerPlenumKg + coreWaterKg) || !(volumeM3 > 0) || !(zrKg > 0) || !(cladM2 > 0))
                throw new IllegalArgumentException("bad plant design " + id);
        }

        public boolean hasAccumulators() {
            return accTankM3 > 0;
        }
    }

    /**
     * VVER-type T2a, 100 MWt, 15.7 MPa, 290 → 320 °C (Denisov &amp; Dragunov, "Reaktornye ustanovki VVER", 2002).
     * Inventory: 3 + 5 m³ of loops and 60 % of a 3 m³ pressurizer. Core: 19 VVER-440 assemblies, 126 rods
     * Ø9.1 × 0.65 mm, 2.42 m — 680 kg Zr, 166 m² of cladding, 2.3 t UO₂. ECCS train: the 15 kg/s high-head SI of
     * the reactor against up to 14 MPa and a low-head pump of 20 kg/s against 1.4 MPa. Hydroaccumulators as on
     * VVER-1000 (60 m³ tanks, 50 m³ of water, 5.9 MPa) scaled 1:30. Break: DN850 double-ended (2 × 0.57 m²) / 30.
     */
    public static final Design PWR_T2A = new Design("pwr", 100e6, 11.3e6, 17.0e6, 6_800, 11, 800, 350, 1_550,
            680, 166, 2_300 * 300, 680 * 330, 6, 15, 14e6, 20, 1.4e6, 2.0, 1.67, 5.9e6, 0.0024, 0, false,
            1.0e6, 1e-4, 0.038, false);

    /**
     * BWR T2b, 100 MWt, 7 MPa saturated, 25 t of water in the vessel. Core: 19 8×8 assemblies (62 rods Ø12.3 mm,
     * 3.7 m, plus channel boxes) — 1.6 t Zr, 200 m², 3.4 t UO₂. ECCS train: high-pressure core spray 12 kg/s
     * against 8.5 MPa and LPCI 40 kg/s against 2 MPa (BWR/4 values scaled 1:33). No accumulators; ADS of seven
     * relief valves scaled to 0.003 m². Mark I/II-type pressure-suppression containment, nitrogen-inerted. Break:
     * recirculation suction, 0.6 m double-ended / 33.
     */
    public static final Design BWR_T2B = new Design("bwr", 100e6, 7.0e6, 7.9e6, 25_000, 40, 3_000, 700, 6_000,
            1_600, 200, 3_400 * 300, 1_600 * 330, 60, 12, 8.5e6, 40, 2.0e6, 0, 0, 0, 0, 0.003, true,
            1.0e6, 1e-4, 0.017, true);

    private final Design d;
    private final ZircaloyOxidation zr;
    private final double gapWK, hCoveredWK, hSteamWK, hFilmWK;
    private Accumulator[] accumulators = new Accumulator[0];
    private ContainmentAtmosphere containment;

    // inputs
    private double breakM2;
    private boolean ac = true;
    private int trains, recombiners, containmentM3;

    // state
    private boolean active, breakWasOpen, adsOpen, breached, uncovered;
    private double t, mass, pressure, tFuel = 600, tClad = 590, pct = 590, adsTimer;
    private double releasedKg;

    // per-step outputs and events
    private double breakKgS, eccsKgS, accKgS, boilKgS, lastDt;
    private boolean burnEvent, breachEvent, uncoveryEvent;
    private double burnJ;

    public PlantAccident(Design design) {
        this.d = design;
        this.zr = new ZircaloyOxidation(design.cladM2, design.zrKg);
        this.mass = design.inventoryKg;
        this.pressure = design.hotSatPa;
        double nominal = design.nominalW;
        // LossOfCoolantTransient's coefficients (3411 MWt plant) scaled by power
        this.gapWK = nominal / (1200 - 620);
        this.hCoveredWK = nominal / 170;
        this.hSteamWK = nominal * 5e4 / 3.411e9;
        this.hFilmWK = nominal * 3e6 / 3.411e9;
    }

    public Design design() {
        return d;
    }

    // ------------------------------------------------------------------ inputs

    /** Total break area, m² (0 closes the break). */
    public void setBreakArea(double m2) {
        breakM2 = Math.max(0, m2);
    }

    public double breakArea() {
        return breakM2;
    }

    /** AC on the safety buses: pumped ECCS and normal makeup run only with it. */
    public void setAcPower(boolean on) {
        ac = on;
    }

    public boolean acPower() {
        return ac;
    }

    /** Pumped ECCS trains built and connected. */
    public void setEccsTrains(int n) {
        trains = Math.max(0, n);
    }

    public int eccsTrains() {
        return trains;
    }

    /** Accumulators built; a plant without them ignores the count. Existing tanks keep what they have injected. */
    public void setAccumulators(int n) {
        n = d.hasAccumulators() ? Math.max(0, n) : 0;
        if (n == accumulators.length) return;
        Accumulator[] next = new Accumulator[n];
        for (int i = 0; i < n; i++)
            next[i] = i < accumulators.length ? accumulators[i] : new Accumulator(d.accTankM3, d.accWaterM3, d.accPa, d.accLineM2);
        accumulators = next;
    }

    public int accumulatorCount() {
        return accumulators.length;
    }

    /**
     * Closed containment around the reactor: free volume in m³ (0 — none or open) and passive recombiners in it.
     * The atmosphere is rebuilt when the volume changes outside an accident; during one the building is what it was.
     */
    public void setContainment(int volumeM3, int recombiners) {
        this.recombiners = Math.max(0, recombiners);
        int v = Math.max(0, volumeM3);
        if (v == containmentM3 && (v == 0) == (containment == null)) return;
        if (active && (breached || containment != null && v > 0)) return;
        containmentM3 = v;
        if (v == 0) {
            containment = null;
            return;
        }
        ContainmentAtmosphere prev = containment;
        containment = new ContainmentAtmosphere(v, d.containmentFailurePa);
        containment.setInerted(d.suppressionPool);
        if (prev != null) containment.addHydrogen(prev.hydrogenKg());
    }

    // ------------------------------------------------------------------ step

    /**
     * @param coreW     heat released in the fuel (fission plus decay) from the reactor simulator
     * @param sinkW     heat the steam generators or the turbine still take from the primary
     * @param reactorPa primary pressure of the reactor simulator, followed while the loop is intact
     */
    public void step(double dt, double coreW, double sinkW, double reactorPa) {
        burnEvent = breachEvent = uncoveryEvent = false;
        burnJ = 0;
        breakKgS = eccsKgS = accKgS = boilKgS = 0;
        lastDt = dt;
        if (!(dt > 0)) return;
        coreW = Math.max(0, coreW);
        sinkW = Math.max(0, sinkW);
        boolean brk = breakM2 > 0;
        double net = coreW - sinkW;
        if (!active && (brk || !ac && net > 0.005 * d.nominalW)) {
            active = true;
            pressure = reactorPa;
        }
        if (!active) {
            idle(dt, coreW, reactorPa);
            return;
        }
        int n = (int) Math.ceil(dt / MAX_SUBSTEP_S);
        double h = dt / n;
        for (int i = 0; i < n; i++) advance(h, coreW, sinkW, reactorPa);
        breakKgS /= dt;
        eccsKgS /= dt;
        accKgS /= dt;
        boilKgS /= dt;
        // recovered: break closed, AC back, the vessel full again and nothing left to boil away
        if (!brk && ac && mass >= 0.999 * d.inventoryKg && net <= 0.005 * d.nominalW) {
            active = false;
            adsOpen = false;
            adsTimer = 0;
            breakWasOpen = false;
            breached = false;
            uncovered = false;
        }
    }

    /** Normal operation: full inventory, the reactor's pressure, quasi-steady cladding. */
    private void idle(double dt, double coreW, double reactorPa) {
        mass = d.inventoryKg;
        pressure = reactorPa;
        double tCool = LossOfCoolantTransient.saturationK(Math.max(ATMOSPHERE_PA, Math.min(reactorPa, 21e6)));
        tClad = tCool + coreW / hCoveredWK;
        tFuel = tClad + coreW / gapWK;
        containmentStep(dt, 0, 0, 0);
    }

    private void advance(double dt, double coreW, double sinkW, double reactorPa) {
        t += dt;
        boolean brk = breakM2 > 0;
        if (brk && !breakWasOpen) pressure = Math.min(pressure, d.hotSatPa);
        breakWasOpen = brk;
        double pc = containment != null ? containment.pressurePa() : ATMOSPHERE_PA;
        double cov = coverage();

        // ── ADS (BWR): low level for 120 s with a low-pressure pump running ──
        if (d.adsM2 > 0 && !adsOpen) {
            boolean lowLevel = mass < d.lowerPlenumKg + 1.3 * d.coreWaterKg;
            adsTimer = lowLevel ? adsTimer + dt : 0;
            if (adsTimer >= ADS_DELAY_S && ac && trains > 0) adsOpen = true;
        }

        // ── outflow: the break and the ADS ──
        double out = 0, toContainment = 0, energy = 0, liquid = 0;
        // the break nozzle under water passes the two-phase mixture (ρ̄ = M/V), above it only steam
        boolean underWater = mass > d.breakNozzleKg;
        if (brk && pressure - pc > 0.05e6) {
            double rho = underWater ? mass / d.volumeM3 : steamDensity(pressure);
            out = Math.min(mass, CD * breakM2 * Math.sqrt(2 * rho * (pressure - pc)) * dt);
            liquid = out;
            toContainment += out;
            energy += out * 1.3e6; // saturated mixture leaving the break, ≈ h_f at 10 MPa
        }
        if (adsOpen && pressure - pc > 0.05e6) {
            double rhoSteam = steamDensity(pressure);
            double vent = Math.min(mass - out, 0.6 * d.adsM2 * Math.sqrt(2 * rhoSteam * (pressure - pc)) * dt);
            out += Math.max(0, vent); // into the suppression pool
            // blowdown of the steam dome: the vessel falls to the low-head range within a few minutes
            pressure += (pc - pressure) * Math.min(1, dt / 60);
        }

        // ── injection ──
        double acc = 0;
        for (Accumulator a : accumulators) acc += a.step(pressure, dt, t);
        double pumped = 0;
        if (ac && trains > 0) pumped = trains * pumpFlow(pressure) * dt;
        double makeup = 0;
        if (ac && !brk && !adsOpen) makeup = Math.min(d.makeupKgS * dt, Math.max(0, d.inventoryKg - mass));
        double in = acc + pumped;
        if (brk && breakM2 >= 0.1 * d.largeBreakM2 && pressure > BYPASS_PA) in *= 0.3;
        accKgS += acc;
        eccsKgS += pumped;

        // ── core ──
        double tCool = LossOfCoolantTransient.saturationK(Math.max(ATMOSPHERE_PA, pressure));
        boolean blowdown = brk && breakM2 >= 0.1 * d.largeBreakM2 && pressure > BYPASS_PA;
        double hA = blowdown ? hFilmWK : cov * hCoveredWK + (1 - cov) * hSteamWK;
        double gap = gapWK * (tFuel - tClad);
        double zrKg = zr.step(tClad, dt);
        double oxW = zrKg * ZircaloyOxidation.HEAT_J_PER_KG_ZR / dt;
        tFuel = Math.min(3100, tFuel + (coreW - gap) * dt / d.fuelJK);
        // cladding implicit in its cooling term: stable for any covered-core conductance
        tClad = (tClad + dt / d.cladJK * (gap + oxW + hA * tCool)) / (1 + dt * hA / d.cladJK);
        tClad = Math.min(LossOfCoolantTransient.CLAD_MELT_K, Math.max(tCool, tClad));
        double cool = hA * Math.max(0, tClad - tCool);

        // ── boiling: the heat the secondary does not take leaves as steam ──
        double boil = 0;
        double hfg = latentHeat(pressure);
        boolean steamOut;
        if (brk || adsOpen) {
            boil = Math.max(0, cov * cool - sinkW) * dt / hfg;
            steamOut = brk; // ADS steam goes to the pool
        } else {
            // closed loop: unremoved heat pressurises it to the relief setpoint, then boils water off through the valves
            double excess = Math.max(0, coreW - sinkW);
            double target = excess > 0 ? d.reliefPa : reactorPa;
            pressure += (target - pressure) * Math.min(1, dt / 30);
            if (excess > 0 && pressure >= 0.99 * d.reliefPa) boil = excess * dt / hfg;
            steamOut = true; // a PWR's relief tank vents to the containment, a BWR's valves to the pool
        }
        boil = Math.min(boil, Math.max(0, mass - out));
        if (steamOut) {
            toContainment += boil;
            energy += boil * 2.7e6;
        }
        boilKgS += boil;
        breakKgS += liquid;

        mass = Math.max(0, Math.min(d.inventoryKg, mass - out - boil + in + makeup));
        double spilled = 0;
        if (brk && pressure - pc < 0.25e6 && mass > d.breakNozzleKg) {
            spilled = mass - d.breakNozzleKg;
            mass = d.breakNozzleKg;
        }
        if (brk) {
            if (mass > d.breakNozzleKg) {
                // subcooled primary: the flashing blowdown follows the inventory; a saturated one holds its pressure
                if (!d.saturated) {
                    double f = mass / d.inventoryKg;
                    pressure = Math.min(pressure, pc + (d.hotSatPa - pc) * Math.pow(f, 1.3));
                }
            } else {
                // steam at the break: the vessel vents as a gas volume, τ = V / (Cd·A·c) with c ≈ 450 m/s in
                // saturated steam — seconds for the guillotine break, tens of minutes for a small one
                pressure = pc + (pressure - pc) * Math.exp(-dt * CD * breakM2 * 450 / d.volumeM3);
            }
        }
        pressure = Math.max(pc, pressure);
        releasedKg += out + boil + spilled;
        toContainment += spilled;
        energy += spilled * 4e5;

        double hot = Math.min(LossOfCoolantTransient.CLAD_MELT_K, tCool + PEAKING * (tClad - tCool));
        pct = Math.max(pct, hot);
        if (!uncovered && coverage() < 1) {
            uncovered = true;
            uncoveryEvent = true;
        }
        containmentStep(dt, d.suppressionPool ? 0 : toContainment, d.suppressionPool ? 0 : energy,
                zrKg * ZircaloyOxidation.H2_PER_KG_ZR);
    }

    private void containmentStep(double dt, double steamKg, double energyJ, double h2Kg) {
        if (containment == null) return;
        containment.setRecombiners(recombiners);
        containment.addSteam(steamKg, energyJ);
        containment.addHydrogen(h2Kg);
        boolean wasFailed = containment.failed();
        if (containment.step(dt)) {
            burnEvent = true;
            burnJ += containment.lastBurnJ();
        }
        if (containment.failed() && !wasFailed) {
            breached = true;
            breachEvent = true;
            // the torn shell vents: from here on the release goes to the open air
            containment = null;
        }
    }

    /** Saturated steam density, ideal gas at T_sat(p), kg/m³ (≈ 25 % low at 7 MPa, enough for a vent rate). */
    static double steamDensity(double pa) {
        return pa * 0.018 / (8.314 * LossOfCoolantTransient.saturationK(Math.max(ATMOSPHERE_PA, pa)));
    }

    /** Pumped ECCS flow of one train at primary pressure {@code p}, kg/s. */
    public double pumpFlow(double p) {
        return stage(d.hpiKgS, d.hpiShutoffPa, p) + stage(d.lpiKgS, d.lpiShutoffPa, p);
    }

    private static double stage(double qMax, double shutoff, double p) {
        return qMax <= 0 || p >= shutoff ? 0 : qMax * Math.sqrt(1 - p / shutoff);
    }

    /**
     * Latent heat of water, J/kg — Watson's correlation h_fg = 2257·((T_c − T)/(T_c − 373.15))^0.38 kJ/kg with
     * T_c = 647.1 K (Watson, Ind. Eng. Chem. 35, 1943), within ~3 % of the steam tables up to 17 MPa.
     */
    static double latentHeat(double pa) {
        double tK = Math.min(640, LossOfCoolantTransient.saturationK(Math.max(ATMOSPHERE_PA, pa)));
        return Math.max(2e5, 2.257e6 * Math.pow((647.1 - tK) / (647.1 - 373.15), 0.38));
    }

    // ------------------------------------------------------------------ outputs

    /** An initiating event is in progress (the layer owns the inventory). */
    public boolean active() {
        return active;
    }

    /** Station blackout: no AC while the core still makes heat. */
    public boolean stationBlackout() {
        return !ac;
    }

    public double timeS() {
        return t;
    }

    public double pressurePa() {
        return pressure;
    }

    public double inventoryKg() {
        return mass;
    }

    /** Collapsed level as a share of the normal inventory, 0–1. */
    public double levelFraction() {
        return mass / d.inventoryKg;
    }

    /** Fraction of the active fuel height under the collapsed level. */
    public double coverage() {
        return Math.max(0, Math.min(1, (mass - d.lowerPlenumKg) / d.coreWaterKg));
    }

    public boolean coreUncovered() {
        return uncovered;
    }

    public double cladK() {
        return tClad;
    }

    public double fuelK() {
        return tFuel;
    }

    /** Peak hot-rod cladding temperature since the plant was built, K. */
    public double peakCladK() {
        return pct;
    }

    public boolean pctLimitExceeded() {
        return pct > LossOfCoolantTransient.PCT_LIMIT_K;
    }

    /** Share of the core whose cladding has burst (0 below 800 °C, all of it at the 1204 °C limit). */
    public double claddingFailedFraction() {
        return Math.max(0, Math.min(1, (pct - BURST_K) / (LossOfCoolantTransient.PCT_LIMIT_K - BURST_K)));
    }

    /** Cladding reached its melting point: core damage past what the lumped model describes. */
    public boolean coreMelt() {
        return pct >= LossOfCoolantTransient.CLAD_MELT_K - 1;
    }

    public double hydrogenKg() {
        return zr.hydrogenKg();
    }

    /** Equivalent cladding reacted (ECR), the 17 % criterion of 10 CFR 50.46. */
    public double oxidizedFraction() {
        return zr.reactedFraction();
    }

    public double breakFlowKgS() {
        return breakKgS;
    }

    public double eccsFlowKgS() {
        return eccsKgS;
    }

    public double accumulatorFlowKgS() {
        return accKgS;
    }

    public double boilOffKgS() {
        return boilKgS;
    }

    public double accumulatorWaterKg() {
        double s = 0;
        for (Accumulator a : accumulators) s += a.waterKg();
        return s;
    }

    public boolean adsOpen() {
        return adsOpen;
    }

    public double releasedKg() {
        return releasedKg;
    }

    public boolean hasContainment() {
        return containment != null;
    }

    public double containmentPa() {
        return containment != null ? containment.pressurePa() : ATMOSPHERE_PA;
    }

    public double containmentHydrogenFraction() {
        return containment != null ? containment.hydrogenFraction() : 0;
    }

    public double containmentHydrogenKg() {
        return containment != null ? containment.hydrogenKg() : 0;
    }

    /** The containment has failed by overpressure (latched, survives a rebuild of the walls). */
    public boolean containmentBreached() {
        return breached;
    }

    /** A hydrogen deflagration happened during the last step; {@link #burnEnergyJ()} gives its energy. */
    public boolean burnEvent() {
        return burnEvent;
    }

    public double burnEnergyJ() {
        return burnJ;
    }

    public boolean breachEvent() {
        return breachEvent;
    }

    public boolean uncoveryEvent() {
        return uncoveryEvent;
    }

    // ------------------------------------------------------------------ state

    public void save(StateWriter out) {
        out.putDouble("break", breakM2);
        out.putBoolean("ac", ac);
        out.putInt("trains", trains);
        out.putBoolean("active", active);
        out.putBoolean("brkOpen", breakWasOpen);
        out.putBoolean("ads", adsOpen);
        out.putBoolean("breached", breached);
        out.putBoolean("uncovered", uncovered);
        out.putDoubles("s", new double[] {t, mass, pressure, tFuel, tClad, pct, adsTimer, releasedKg}, 8);
        out.beginObject("zr");
        zr.save(out);
        out.endObject();
        out.putInt("acc", accumulators.length);
        for (int i = 0; i < accumulators.length; i++) {
            out.beginObject("acc" + i);
            accumulators[i].save(out);
            out.endObject();
        }
        out.putInt("cv", containmentM3);
        out.putInt("pars", recombiners);
        if (containment != null) {
            out.beginObject("ct");
            containment.save(out);
            out.endObject();
        }
    }

    public void load(StateReader in) {
        breakM2 = in.optDouble("break", 0);
        ac = in.optInt("ac", 1) != 0;
        trains = in.optInt("trains", 0);
        active = in.optInt("active", 0) != 0;
        breakWasOpen = in.optInt("brkOpen", 0) != 0;
        adsOpen = in.optInt("ads", 0) != 0;
        breached = in.optInt("breached", 0) != 0;
        uncovered = in.optInt("uncovered", 0) != 0;
        if (in.has("s")) {
            double[] s = in.getDoubles("s");
            if (s.length >= 8) {
                t = s[0];
                mass = s[1];
                pressure = s[2];
                tFuel = s[3];
                tClad = s[4];
                pct = s[5];
                adsTimer = s[6];
                releasedKg = s[7];
            }
        }
        if (in.has("zr")) {
            in.beginObject("zr");
            zr.load(in);
            in.endObject();
        }
        setAccumulators(in.optInt("acc", 0));
        for (int i = 0; i < accumulators.length; i++) {
            if (!in.has("acc" + i)) continue;
            in.beginObject("acc" + i);
            accumulators[i].load(in);
            in.endObject();
        }
        containment = null;
        containmentM3 = 0;
        boolean wasActive = active;
        active = false;
        setContainment(in.optInt("cv", 0), in.optInt("pars", 0));
        active = wasActive;
        if (containment != null && in.has("ct")) {
            in.beginObject("ct");
            containment.load(in);
            in.endObject();
        }
    }
}
