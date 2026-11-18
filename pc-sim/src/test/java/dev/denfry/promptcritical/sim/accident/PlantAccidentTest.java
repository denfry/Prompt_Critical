package dev.denfry.promptcritical.sim.accident;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.denfry.promptcritical.sim.core.MutableStateTree;
import dev.denfry.promptcritical.sim.core.TreeStateReader;
import dev.denfry.promptcritical.sim.core.TreeStateWriter;
import dev.denfry.promptcritical.sim.neutronics.DecayHeat;
import org.junit.jupiter.api.Test;

class PlantAccidentTest {
    private static final double DT = 0.5;

    /** Scrammed at t = 0 after long operation: core power is the decay heat alone. */
    private static double decayW(PlantAccident a, double t) {
        return a.design().nominalW() * DecayHeat.afterInfiniteOperation(t);
    }

    /** Runs {@code seconds} of accident with no heat sink and the given reactor pressure while intact. */
    private static double run(PlantAccident a, double seconds, double sinkW, double reactorPa) {
        double t0 = a.timeS();
        double t = 0;
        while (t < seconds) {
            a.step(DT, decayW(a, t0 + t), sinkW, reactorPa);
            t += DT;
        }
        return t;
    }

    @Test
    void idleFollowsTheReactorWithAFullVessel() {
        PlantAccident a = new PlantAccident(PlantAccident.PWR_T2A);
        a.setEccsTrains(1);
        for (int i = 0; i < 100; i++) a.step(0.05, 100e6, 100e6, 15.7e6);
        assertFalse(a.active());
        assertEquals(15.7e6, a.pressurePa(), 1);
        assertEquals(1, a.coverage(), 1e-9);
        assertEquals(0, a.eccsFlowKgS(), 0);
    }

    /** Pump curve q = q_max·√(1 − p/p_shutoff): run-out at zero pressure, nothing at shutoff. */
    @Test
    void eccsPumpCurve() {
        PlantAccident a = new PlantAccident(PlantAccident.PWR_T2A);
        assertEquals(35, a.pumpFlow(0), 1e-9);
        assertEquals(0, a.pumpFlow(14e6), 1e-9);
        assertEquals(15 * Math.sqrt(0.5), a.pumpFlow(7e6), 1e-9);
    }

    /** Watson h_fg: 2257 kJ/kg at 0.1 MPa, ≈ 1400 kJ/kg at 10 MPa, ≈ 900 kJ/kg at 17 MPa (IAPWS-IF97 tables). */
    @Test
    void latentHeatAgainstTheSteamTables() {
        assertEquals(2.257e6, PlantAccident.latentHeat(101_325), 0.03e6);
        assertEquals(1.32e6, PlantAccident.latentHeat(10e6), 0.12e6);
        assertEquals(0.9e6, PlantAccident.latentHeat(17e6), 0.15e6);
    }

    /** A 1 cm² break on a VVER with one ECCS train: high-head injection holds the core under water. */
    @Test
    void smallBreakHandledByEccs() {
        PlantAccident a = new PlantAccident(PlantAccident.PWR_T2A);
        a.setEccsTrains(1);
        a.setBreakArea(PlantAccident.PWR_T2A.smallBreakM2());
        run(a, 1800, 0, 15.7e6);
        assertTrue(a.active());
        assertFalse(a.coreUncovered(), "level " + a.levelFraction());
        assertTrue(a.eccsFlowKgS() > 1, "ECCS injecting " + a.eccsFlowKgS());
        assertTrue(a.breakFlowKgS() > 0.5, "break still flowing " + a.breakFlowKgS());
        assertFalse(a.pctLimitExceeded(), "PCT " + a.peakCladK());
        assertEquals(0, a.claddingFailedFraction(), 0);
    }

    /** The same break with nothing to inject: the core uncovers, heats up and oxidises. */
    @Test
    void smallBreakWithoutEccsUncoversTheCore() {
        PlantAccident a = new PlantAccident(PlantAccident.PWR_T2A);
        a.setAcPower(false);
        a.setBreakArea(PlantAccident.PWR_T2A.smallBreakM2());
        run(a, 4 * 3600, 0, 15.7e6);
        assertTrue(a.coreUncovered(), "level " + a.levelFraction());
        assertTrue(a.pctLimitExceeded(), "PCT " + a.peakCladK());
        assertTrue(a.hydrogenKg() > 1, "H2 " + a.hydrogenKg());
        assertEquals(1, a.claddingFailedFraction(), 1e-9);
    }

    /**
     * Double-ended main pipe break with two hydroaccumulators and two pumped trains: the tanks open early in the
     * blowdown, the low-head pumps refill the vessel and the cladding stays below 1204 °C.
     */
    @Test
    void largeBreakWithAccumulatorsAndLowHeadPumps() {
        PlantAccident a = new PlantAccident(PlantAccident.PWR_T2A);
        a.setAccumulators(2);
        a.setEccsTrains(2);
        a.setBreakArea(PlantAccident.PWR_T2A.largeBreakM2());
        double accStart = Double.NaN;
        double t = 0;
        while (t < 900) {
            a.step(0.05, decayW(a, t), 0, 15.7e6);
            if (Double.isNaN(accStart) && a.accumulatorFlowKgS() > 0) accStart = t;
            t += 0.05;
        }
        assertTrue(accStart > 0 && accStart < 30, "accumulators at " + accStart);
        assertTrue(a.pressurePa() < 0.5e6, "blown down " + a.pressurePa());
        assertTrue(a.coverage() > 0.99, "reflooded " + a.coverage());
        assertFalse(a.pctLimitExceeded(), "PCT " + a.peakCladK());
    }

    /** Large break with the accumulators but no AC: once the tanks are empty the boil-off uncovers the core. */
    @Test
    void largeBreakWithoutAcDamagesTheCore() {
        PlantAccident a = new PlantAccident(PlantAccident.PWR_T2A);
        a.setAccumulators(2);
        a.setEccsTrains(2);
        a.setAcPower(false);
        a.setBreakArea(PlantAccident.PWR_T2A.largeBreakM2());
        run(a, 3 * 3600, 0, 15.7e6);
        assertTrue(a.accumulatorWaterKg() < 1, "tanks empty");
        assertTrue(a.coreUncovered());
        assertTrue(a.pctLimitExceeded(), "PCT " + a.peakCladK());
    }

    /**
     * Station blackout on a VVER without a heat sink: the loop rises to the relief setpoint and boils off through
     * it. With ~5.6 t of water above the core, h_fg ≈ 0.9 MJ/kg and 1–2 MW of decay heat the core uncovers after
     * roughly an hour; full-size plants with no feedwater at all take a few hours, as their water-to-power ratio is
     * larger.
     */
    @Test
    void stationBlackoutBoilsOffThroughTheReliefValves() {
        PlantAccident a = new PlantAccident(PlantAccident.PWR_T2A);
        a.setEccsTrains(2);
        a.setAcPower(false);
        double t = 0;
        double uncovered = Double.NaN;
        while (t < 6 * 3600 && Double.isNaN(uncovered)) {
            a.step(DT, decayW(a, t), 0, 15.7e6);
            if (a.coreUncovered()) uncovered = t;
            t += DT;
            if (Math.abs(t - 600) < DT / 2) {
                assertTrue(a.active() && a.stationBlackout());
                assertEquals(PlantAccident.PWR_T2A.reliefPa(), a.pressurePa(), 0.2e6);
                assertTrue(a.boilOffKgS() > 0.5, "boiling off " + a.boilOffKgS());
                assertEquals(0, a.eccsFlowKgS(), 0);
            }
        }
        assertTrue(uncovered > 1200 && uncovered < 4 * 3600, "uncovered at " + uncovered);
    }

    /** Blackout with the steam generators still taking all the decay heat (turbine-driven feed): nothing happens. */
    @Test
    void blackoutWithAHeatSinkStaysIdle() {
        PlantAccident a = new PlantAccident(PlantAccident.PWR_T2A);
        a.setAcPower(false);
        double t = 0;
        while (t < 600) {
            double q = decayW(a, t);
            a.step(DT, q, q * 1.1, 15.0e6);
            t += DT;
        }
        assertFalse(a.active());
        assertTrue(a.stationBlackout());
    }

    /**
     * BWR small break with the high-pressure core spray out of service: the saturated vessel stays at pressure while
     * the level falls, ADS opens 120 s after the low-level signal, blows the vessel down to the pool and LPCI
     * refloods before the cladding reaches the 1204 °C limit.
     */
    @Test
    void bwrAdsBlowsDownForLowPressureInjection() {
        PlantAccident.Design b = PlantAccident.BWR_T2B;
        PlantAccident.Design noHpcs = new PlantAccident.Design(b.id(), b.nominalW(), b.hotSatPa(), b.reliefPa(), b.inventoryKg(),
                b.volumeM3(), b.lowerPlenumKg(), b.coreWaterKg(), b.breakNozzleKg(), b.zrKg(), b.cladM2(), b.fuelJK(), b.cladJK(),
                b.makeupKgS(), 0, b.hpiShutoffPa(), b.lpiKgS(), b.lpiShutoffPa(), 0, 0, 0, 0, b.adsM2(), b.suppressionPool(),
                b.containmentFailurePa(), b.smallBreakM2(), b.largeBreakM2(), b.saturated());
        PlantAccident a = new PlantAccident(noHpcs);
        a.setEccsTrains(1);
        a.setBreakArea(b.smallBreakM2());
        double t = 0;
        double adsAt = Double.NaN;
        double pAt1000 = 0;
        while (t < 4 * 3600) {
            a.step(DT, decayW(a, t), 0, 7.0e6);
            if (Double.isNaN(adsAt) && a.adsOpen()) adsAt = t;
            if (Math.abs(t - 1000) < DT / 2) pAt1000 = a.pressurePa();
            t += DT;
        }
        assertTrue(pAt1000 > 6e6, "saturated vessel holds pressure under a small break: " + pAt1000);
        assertTrue(adsAt > PlantAccident.ADS_DELAY_S, "ADS at " + adsAt);
        assertTrue(a.pressurePa() < b.lpiShutoffPa(), "below LPCI shutoff " + a.pressurePa());
        assertTrue(a.coverage() > 0.99, "reflooded " + a.coverage());
        assertFalse(a.pctLimitExceeded(), "PCT " + a.peakCladK());
    }

    /** BWR guillotine break of the recirculation suction: blown down within a couple of minutes, LPCI refloods. */
    @Test
    void bwrLargeBreakHandledByLpci() {
        PlantAccident a = new PlantAccident(PlantAccident.BWR_T2B);
        a.setEccsTrains(2);
        a.setBreakArea(PlantAccident.BWR_T2B.largeBreakM2());
        double t = 0;
        double pAt120 = 0;
        while (t < 900) {
            a.step(0.05, decayW(a, t), 0, 7.0e6);
            if (Math.abs(t - 120) < 0.025) pAt120 = a.pressurePa();
            t += 0.05;
        }
        assertTrue(pAt120 < 2e6, "depressurised " + pAt120);
        assertTrue(a.coverage() > 0.99, "reflooded " + a.coverage());
        assertFalse(a.pctLimitExceeded(), "PCT " + a.peakCladK());
    }

    /** A 500 m³ building cannot take a large-break blowdown: overpressure tears it and the release goes outside. */
    @Test
    void undersizedContainmentFails() {
        PlantAccident a = new PlantAccident(PlantAccident.PWR_T2A);
        a.setContainment(500, 0);
        a.setEccsTrains(1);
        a.setBreakArea(PlantAccident.PWR_T2A.largeBreakM2());
        boolean breach = false;
        double t = 0;
        while (t < 120 && !breach) {
            a.step(0.05, decayW(a, t), 0, 15.7e6);
            breach = a.breachEvent();
            t += 0.05;
        }
        assertTrue(breach, "containment " + a.containmentPa());
        assertTrue(a.containmentBreached());
        assertFalse(a.hasContainment());
        // a save and load during the accident does not rebuild the torn shell
        MutableStateTree tree = new MutableStateTree();
        a.save(new TreeStateWriter(tree));
        PlantAccident b = new PlantAccident(PlantAccident.PWR_T2A);
        b.load(new TreeStateReader(tree));
        assertTrue(b.active() && b.containmentBreached());
        assertFalse(b.hasContainment(), "torn containment rebuilt on load");
        b.setContainment(500, 0);
        assertFalse(b.hasContainment(), "the same walls do not close the shell again during the accident");
    }

    /** About 600 m³ per tonne of primary water holds the same blowdown for the first ten minutes (no sprays modelled). */
    @Test
    void adequateContainmentHolds() {
        PlantAccident a = new PlantAccident(PlantAccident.PWR_T2A);
        a.setContainment(4_000, 2);
        a.setAccumulators(2);
        a.setEccsTrains(2);
        a.setBreakArea(PlantAccident.PWR_T2A.largeBreakM2());
        double peak = 0;
        double t = 0;
        while (t < 600) {
            a.step(0.05, decayW(a, t), 0, 15.7e6);
            peak = Math.max(peak, a.containmentPa());
            t += 0.05;
        }
        assertFalse(a.containmentBreached(), "containment " + a.containmentPa());
        assertTrue(peak > 0.15e6 && peak < 0.8e6, "peak containment pressure " + peak);
    }

    /** Nitrogen inerting: 10 % hydrogen does not burn in a BWR-type containment and does in a dry one. */
    @Test
    void inertedContainmentDoesNotBurn() {
        ContainmentAtmosphere dry = new ContainmentAtmosphere(1000, 1e6);
        ContainmentAtmosphere inert = new ContainmentAtmosphere(1000, 1e6);
        inert.setInerted(true);
        dry.addHydrogen(9);
        inert.addHydrogen(9);
        assertTrue(dry.hydrogenFraction() > ContainmentAtmosphere.DEFLAGRATION_FRACTION);
        assertTrue(dry.step(1));
        assertFalse(inert.step(1));
        assertTrue(inert.hydrogenFraction() > ContainmentAtmosphere.DEFLAGRATION_FRACTION);
    }

    @Test
    void stateRoundTrip() {
        PlantAccident a = new PlantAccident(PlantAccident.PWR_T2A);
        a.setAccumulators(2);
        a.setEccsTrains(1);
        a.setContainment(5000, 1);
        a.setBreakArea(PlantAccident.PWR_T2A.largeBreakM2());
        run(a, 60, 0, 15.7e6);
        MutableStateTree tree = new MutableStateTree();
        a.save(new TreeStateWriter(tree));
        PlantAccident b = new PlantAccident(PlantAccident.PWR_T2A);
        b.load(new TreeStateReader(tree));
        assertTrue(b.active());
        assertEquals(a.inventoryKg(), b.inventoryKg(), 1e-9);
        assertEquals(a.pressurePa(), b.pressurePa(), 1e-9);
        assertEquals(a.accumulatorWaterKg(), b.accumulatorWaterKg(), 1e-9);
        assertEquals(a.hydrogenKg(), b.hydrogenKg(), 1e-12);
        assertEquals(a.containmentPa(), b.containmentPa(), 1e-6);
        assertEquals(a.breakArea(), b.breakArea(), 0);
    }
}
