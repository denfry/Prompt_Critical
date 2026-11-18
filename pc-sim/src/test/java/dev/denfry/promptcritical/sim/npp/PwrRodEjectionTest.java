package dev.denfry.promptcritical.sim.npp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.denfry.promptcritical.sim.accident.ReactivityInitiatedAccident;
import dev.denfry.promptcritical.sim.protection.TripReason;
import org.junit.jupiter.api.Test;

/** Rod ejection on the T2a VVER (04c §4c.5.1–§4c.5.3). */
class PwrRodEjectionTest {
    /** Hot zero power: 290 °C everywhere, scram bank cocked, control group fully in, boron on criticality. */
    static PressurizedWaterReactor hotZeroPower() {
        PressurizedWaterReactor r = Pwrs.loaded(0.044);
        for (int l = 0; l < PressurizedWaterReactor.LOOPS; l++) r.setPumpDrive(l, 1);
        r.setSecondary(Pwrs.T_SEC, 0.5, 2);
        r.setThermalState(563.15, 563.15, 563.15);
        r.setRods(0, 1);
        double lo = 0, hi = 30;
        for (int i = 0; i < 50; i++) {
            double m = (lo + hi) / 2;
            r.setBoronGkg(m);
            if (r.evaluateReactivity() > 0) lo = m;
            else hi = m;
        }
        r.setBoronGkg((lo + hi) / 2);
        r.equilibrate(1e-4);
        return r;
    }

    /**
     * Ejecting a cluster of the fully inserted control group at hot zero power is about 1.3β: a mild prompt burst that
     * the Doppler turns over, trips the reactor on high flux and leaves fresh fuel intact (§4c.5.2: ~10 cal/g on average).
     */
    @Test
    void hotZeroPowerEjectionIsAMildPromptBurst() {
        PressurizedWaterReactor r = hotZeroPower();
        double tf = r.fuelK();
        ReactivityInitiatedAccident.Pulse p = r.ejectRodCluster();
        assertTrue(p.prompt(), "prompt critical: " + p.dollars() + " $");
        assertEquals(1.3, p.dollars(), 0.25);
        assertTrue(r.rodClusterEjected());
        assertTrue(r.fuelK() > tf + 20, "burst energy in the fuel: " + tf + " → " + r.fuelK());
        double rhoAfter = r.evaluateReactivity();
        r.step(0); // the trip request is latched on the next protection step
        assertTrue(r.protection().isTripped());
        assertEquals(TripReason.HIGH_FLUX, r.protection().firstOut());
        ReactivityInitiatedAccident.Damage d = r.lastRiaDamage();
        assertTrue(d.peakCalG() > 20 && d.peakCalG() < ReactivityInitiatedAccident.PCMI_BASE_CAL_G, "hot spot " + d.peakCalG());
        assertEquals(0, r.failedAssemblies());
        // the Doppler leaves the core below prompt critical
        assertTrue(rhoAfter < r.betaEff(), "after the burst " + rhoAfter / r.betaEff() + " $");
        assertNull(r.ejectRodCluster(), "one cluster out at a time");
        Pwrs.run(r, 5);
        assertTrue(r.reseatRodCluster(), "rods down: the drill is reset");
    }

    /** At power the control group is nearly out: the cluster is worth about 0.2β, a delayed excursion, no burst. */
    @Test
    void ejectionAtPowerIsADelayedExcursion() {
        PressurizedWaterReactor r = Pwrs.nominal();
        ReactivityInitiatedAccident.Pulse p = r.ejectRodCluster();
        assertFalse(p.prompt());
        assertEquals(0.2, p.dollars(), 0.1);
        assertFalse(r.reseatRodCluster(), "not on a running reactor");
        double peak = 0;
        for (int i = 0; i < 600 && !r.protection().isTripped(); i++) {
            r.step(0);
            peak = Math.max(peak, r.power());
        }
        assertTrue(peak > 1.0, "power rises: " + peak);
        assertTrue(r.protection().isTripped(), "the excursion trips the reactor");
    }

    /** A single cluster for the whole group (the §4c.5.1 design mistake): several β, failed and fragmented fuel. */
    @Test
    void singleClusterGroupIsASevereRia() {
        PressurizedWaterReactor r = hotZeroPower();
        ReactivityInitiatedAccident.Pulse p = r.ejectRodCluster(1);
        assertTrue(p.dollars() > 3, "several β: " + p.dollars());
        ReactivityInitiatedAccident.Damage d = r.lastRiaDamage();
        assertTrue(d.peakCalG() > ReactivityInitiatedAccident.FRAGMENTATION_CAL_G, "hot spot " + d.peakCalG());
        assertTrue(d.fragmentedFraction() > 0);
        assertTrue(r.failedAssemblies() > 0, "failed cladding");
        assertTrue(r.fuelK() <= PressurizedWaterReactor.UO2_MELT_K);
    }
}
