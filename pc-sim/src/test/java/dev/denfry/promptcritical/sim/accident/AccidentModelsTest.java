package dev.denfry.promptcritical.sim.accident;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.denfry.promptcritical.sim.consequence.ConsequenceRouter;
import dev.denfry.promptcritical.sim.consequence.ConsequenceRouter.Event;
import dev.denfry.promptcritical.sim.consequence.ConsequenceRouter.ReleasePath;
import dev.denfry.promptcritical.sim.consequence.DamageKind;
import dev.denfry.promptcritical.sim.core.GridPos;
import dev.denfry.promptcritical.sim.core.SimId;
import org.junit.jupiter.api.Test;

class AccidentModelsTest {

    /** Stoichiometry Zr + 2H₂O → ZrO₂ + 2H₂: 4.032 / 91.224 = 0.0442 kg H₂ per kg Zr. */
    @Test
    void bakerJustHydrogenYieldPerKgZr() {
        assertEquals(0.0442, ZircaloyOxidation.H2_PER_KG_ZR, 0.0002);
        ZircaloyOxidation zr = new ZircaloyOxidation(1, 1000);
        zr.step(1500, 100);
        assertEquals(zr.reactedKg() * 0.0442, zr.hydrogenKg(), 1e-4);
        // Baker–Just at 1500 K: w² = 33.3e6·exp(−45500/(1.987·1500)) ≈ 7.6 (mg/cm²)²/s
        assertEquals(7.6, ZircaloyOxidation.rateConstant(1500), 0.4);
    }

    /**
     * Double-ended cold-leg break: accumulators (4.2 MPa) open early in blowdown — FSAR-class
     * analyses put it at ~5–15 s — and LPI follows once the primary is near containment pressure.
     */
    @Test
    void accumulatorsInjectDuringBlowdown() {
        LossOfCoolantTransient loca = LossOfCoolantTransient.doubleEndedColdLeg(4, true);
        while (loca.timeS() < 120) loca.step(0.05);
        double acc = loca.accumulatorStartS();
        assertTrue(acc > 3 && acc < 20, "accumulator start " + acc);
        assertTrue(loca.lpiStartS() > acc, "LPI after the accumulators");
    }

    /** PCT trend: full ECCS stays under the 1204 °C criterion; accumulators alone without AC melt the core. */
    @Test
    void peakCladdingTemperatureTrend() {
        LossOfCoolantTransient full = LossOfCoolantTransient.doubleEndedColdLeg(4, true);
        LossOfCoolantTransient noAcc = LossOfCoolantTransient.doubleEndedColdLeg(0, true);
        LossOfCoolantTransient noAc = LossOfCoolantTransient.doubleEndedColdLeg(4, false);
        for (int i = 0; i < 20_000; i++) {
            full.step(0.05);
            noAcc.step(0.05);
            noAc.step(0.05);
        }
        assertFalse(full.pctLimitExceeded(), "full ECCS PCT " + full.peakCladK());
        assertTrue(full.peakCladK() > 900, "blowdown peak exists");
        assertTrue(noAcc.peakCladK() >= full.peakCladK());
        assertTrue(noAc.pctLimitExceeded(), "no pumped ECCS PCT " + noAc.peakCladK());
        assertTrue(noAc.hydrogenKg() > 100, "oxidation makes hydrogen");
        assertEquals(0, full.hydrogenKg(), 1);
    }

    @Test
    void blackoutUncoversCoreUnlessDieselRestores() {
        StationBlackout none = new StationBlackout(3.4e9, 150e3, Double.POSITIVE_INFINITY);
        StationBlackout dg = new StationBlackout(3.4e9, 150e3, 30);
        for (int i = 0; i < 20_000; i++) {
            none.step(1);
            dg.step(1);
        }
        assertTrue(none.coreUncovered());
        // 150 t of primary water alone (no steam-generator heat sink) boils off in tens of minutes
        assertTrue(none.uncoveredAtS() > 900 && none.uncoveredAtS() < 2 * 3600, "uncovery " + none.uncoveredAtS());
        assertFalse(dg.coreUncovered());
    }

    @Test
    void containmentHydrogenBurnsAboveEightPercentUnlessRecombined() {
        ContainmentAtmosphere bare = ContainmentAtmosphere.largeDry();
        ContainmentAtmosphere par = ContainmentAtmosphere.largeDry();
        par.setRecombiners(60);
        boolean burned = false, parBurned = false;
        for (int s = 0; s < 6 * 3600; s++) {
            bare.addHydrogen(0.03);
            par.addHydrogen(0.03);
            burned |= bare.step(1);
            parBurned |= par.step(1);
        }
        assertTrue(burned, "650 kg in 70 000 m³ passes 8 %");
        assertFalse(parBurned, "recombiners hold it below 8 %: " + par.hydrogenFraction());
    }

    @Test
    void routerKeepsReleaseInsideIntactContainment() {
        GridPos o = new GridPos(0, 64, 0);
        SimId id = SimId.of(SimId.Kind.REACTOR, 1);
        var inside = ConsequenceRouter.route(Event.VESSEL_RUPTURE, 1, id, null, o, 3e8, true, true);
        assertEquals(ReleasePath.INSIDE_CONTAINMENT, inside.release());
        assertEquals(DamageKind.BLAST, inside.damage().kind());
        var breach = ConsequenceRouter.route(Event.CONTAINMENT_BREACH, 2, id, null, o, 1e8, true, true);
        assertEquals(ReleasePath.ENVIRONMENT, breach.release());
        assertEquals(DamageKind.OVERPRESSURE_BREACH, breach.damage().kind());
        assertNull(ConsequenceRouter.route(Event.HYDROGEN_BURN, 3, id, null, o, 0, false, false).damage());
    }

    @Test
    void aDeflagrationBreaksWithATenthOfItsEnergy() {
        GridPos o = new GridPos(0, 64, 0);
        SimId id = SimId.of(SimId.Kind.REACTOR, 1);
        assertEquals(1.3e8, ConsequenceRouter.route(Event.HYDROGEN_BURN, 1, id, null, o, 1.3e9, true, true).damage().energyJ(), 1);
        assertEquals(1.3e9, ConsequenceRouter.route(Event.HYDROGEN_DETONATION, 2, id, null, o, 1.3e9, true, true).damage().energyJ(), 1);
        assertEquals(3e8, ConsequenceRouter.route(Event.VESSEL_RUPTURE, 3, id, null, o, 3e8, true, true).damage().energyJ(), 1);
    }
}
