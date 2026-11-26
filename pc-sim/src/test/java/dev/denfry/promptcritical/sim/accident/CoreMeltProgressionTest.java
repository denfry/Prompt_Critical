package dev.denfry.promptcritical.sim.accident;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.denfry.promptcritical.sim.core.MutableStateTree;
import dev.denfry.promptcritical.sim.core.TreeStateReader;
import dev.denfry.promptcritical.sim.core.TreeStateWriter;
import dev.denfry.promptcritical.sim.neutronics.DecayHeat;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class CoreMeltProgressionTest {
    private static final double DT = 0.5, T_SAT = 373.15;

    /** The VVER core of {@link PlantAccident#PWR_T2A}. */
    private static CoreMeltProgression vver() {
        PlantAccident.Design d = PlantAccident.PWR_T2A;
        return new CoreMeltProgression(CoreMeltProgression.PWR_T2A, d.fuelJK() / 300, d.zrKg(), d.fuelJK() + d.cladJK());
    }

    /** Steps an uncovered, dry core at constant decay heat and pressure; records the stages it passes. */
    private static double runDry(CoreMeltProgression m, double coreW, double pressurePa, double seconds, List<CoreMeltProgression.Stage> seen) {
        SplittableRandom r = new SplittableRandom(5);
        double t = 0;
        for (; t < seconds; t += DT) {
            m.beginStep();
            m.observeCladding(LossOfCoolantTransient.CLAD_MELT_K);
            m.step(DT, coreW, 0, pressurePa, 0, 800, LossOfCoolantTransient.CLAD_MELT_K, 0.1, T_SAT, r);
            if (m.stageEvent() && seen != null) seen.add(m.stage());
            if (m.vesselFailed() && seen == null) break;
        }
        return t;
    }

    /** §4c.10.3: 1300 K above 10 MPa, 1500 K below 1 MPa, linear between. */
    @Test
    void lowerHeadFailureTemperature() {
        assertEquals(1300, CoreMeltProgression.failureK(15e6), 1e-9);
        assertEquals(1500, CoreMeltProgression.failureK(0.5e6), 1e-9);
        assertEquals(1400, CoreMeltProgression.failureK(5.5e6), 1e-9);
    }

    /**
     * A dry core on 2 MW of decay heat goes through the stages of §4c.10.1 in order — candling, pool, lower plenum,
     * head failure, concrete — within a few hours; failing at low pressure, it heats no containment.
     */
    @Test
    void dryCoreMeltsThroughInOrder() {
        CoreMeltProgression m = vver();
        List<CoreMeltProgression.Stage> seen = new ArrayList<>();
        SplittableRandom r = new SplittableRandom(5);
        double dch = 0, t = 0;
        boolean failedAtLowPressure = false;
        for (; t < 6 * 3600 && !m.vesselFailed(); t += DT) {
            m.beginStep();
            m.observeCladding(LossOfCoolantTransient.CLAD_MELT_K);
            m.step(DT, 2e6, 0, 0.3e6, 0, 800, LossOfCoolantTransient.CLAD_MELT_K, 0.1, T_SAT, r);
            if (m.stageEvent()) seen.add(m.stage());
            dch += m.directHeatingJ();
            failedAtLowPressure |= m.failureEvent();
        }
        assertTrue(m.vesselFailed(), "head failed after " + t + " s");
        assertTrue(t > 600 && t < 4 * 3600, "within hours: " + t);
        assertTrue(failedAtLowPressure);
        assertEquals(0, dch, "no direct heating at low pressure");
        int last = -1;
        for (CoreMeltProgression.Stage s : seen) {
            assertTrue(s.ordinal() > last, "stages in order: " + seen);
            last = s.ordinal();
        }
        assertTrue(seen.contains(CoreMeltProgression.Stage.CLAD_MELT_CANDLING));
        assertTrue(seen.contains(CoreMeltProgression.Stage.CERAMIC_MELT_POOL));
        assertTrue(seen.contains(CoreMeltProgression.Stage.LOWER_PLENUM_RELOCATION));
        assertTrue(seen.contains(CoreMeltProgression.Stage.LOWER_HEAD_FAILURE));
        assertTrue(m.exVesselKg() > 0.35 * (PlantAccident.PWR_T2A.fuelJK() / 300 + PlantAccident.PWR_T2A.zrKg()), "the melt is out: " + m.exVesselKg());
        assertTrue(m.gamma() > 0.4, "Γ " + m.gamma());
    }

    /**
     * §4c.10.4: the trigger chance is per melt-entry event. The crust failure is one; the melt that keeps draining
     * behind it, step by step, is a stream into a plenum already full of corium and rolls no new steam explosions.
     */
    @Test
    void onlyTheCrustFailurePourCanTriggerASteamExplosion() {
        for (long seed = 1; seed <= 40; seed++) {
            CoreMeltProgression m = vver();
            SplittableRandom r = new SplittableRandom(seed);
            int explosions = 0, pours = 0;
            for (double t = 0; t < 6 * 3600 && !m.vesselFailed(); t += DT) {
                m.beginStep();
                m.observeCladding(LossOfCoolantTransient.CLAD_MELT_K);
                // the lower plenum stays full of water while the core above melts
                m.step(DT, 2e6, 0, 0.3e6, 800, 800, LossOfCoolantTransient.CLAD_MELT_K, 0.1, T_SAT, r);
                if (m.steamExplosionJ() > 0) explosions++;
                if (m.stage() == CoreMeltProgression.Stage.LOWER_PLENUM_RELOCATION && m.stageEvent()) pours++;
            }
            assertEquals(1, pours, "one relocation, seed " + seed);
            assertTrue(explosions <= 1, "at most one steam explosion per relocation, seed " + seed + ": " + explosions);
        }
    }

    /** At primary pressure the head fails 200 K cooler, and the ejected melt heats the containment directly. */
    @Test
    void highPressureFailureHeatsTheContainment() {
        CoreMeltProgression lo = vver(), hi = vver();
        double tLo = runDry(lo, 2e6, 0.3e6, 6 * 3600, null), tHi = runDry(hi, 2e6, 15e6, 6 * 3600, null);
        assertTrue(hi.vesselFailed() && lo.vesselFailed());
        assertTrue(tHi < tLo, "earlier at pressure: " + tHi + " vs " + tLo);
        assertTrue(hi.directHeatingJ() > 0.2 * 1e9, "DCH 0.2 of the melt's heat: " + hi.directHeatingJ());
    }

    /** A flooded cavity holds the melt in the vessel: the head flux is far below 1.2 MW/m² (§4c.10.3). */
    @Test
    void floodedCavityRetainsTheMeltInTheVessel() {
        CoreMeltProgression m = vver();
        m.setCavityFlooded(true);
        runDry(m, 2e6, 0.3e6, 8 * 3600, new ArrayList<>());
        assertFalse(m.vesselFailed());
        assertTrue(m.lowerPlenumKg() > 0, "corium in the lower plenum");
        assertTrue(m.retainedInVessel());
        assertTrue(m.lowerHeadK() < 600, "head cooled from outside: " + m.lowerHeadK());
    }

    /** Water back over a core that has only started to candle (Γ &lt; 0.1) quenches it; nothing melts. */
    @Test
    void earlyRefloodQuenchesTheCore() {
        CoreMeltProgression m = vver();
        SplittableRandom r = new SplittableRandom(1);
        for (int i = 0; i < 20; i++) {
            m.beginStep();
            m.step(DT, 2e6, 0, 0.3e6, 0, 800, LossOfCoolantTransient.CLAD_MELT_K, 0.5, T_SAT, r);
        }
        assertTrue(m.started());
        assertTrue(m.gamma() < 0.1, "Γ " + m.gamma());
        for (int i = 0; i < 2000; i++) {
            m.beginStep();
            m.step(DT, 2e6, 1, 0.3e6, 6800, 800, LossOfCoolantTransient.CLAD_MELT_K, 0.5, T_SAT, r);
        }
        assertEquals(T_SAT, m.debrisK(), 1);
        assertEquals(0, m.meltKg());
        assertEquals(0, m.lowerPlenumKg());
    }

    /** T-4c-16: 20 t at 1 MW eats a block in ≈ 77 min dry; water over its crust slows it at least twice (here it stops). */
    @Test
    void mcciAblationAgainstT4c16() {
        CoreMeltProgression dry = CoreMeltProgression.onConcrete(20_000), wet = CoreMeltProgression.onConcrete(20_000);
        wet.setCavityFlooded(true);
        SplittableRandom r = new SplittableRandom(1);
        double t = 0;
        while (dry.ablatedM3() < 1 && t < 10 * 3600) {
            dry.beginStep();
            dry.step(1, 1e6, 0, 0.1e6, 0, 800, 3000, 1, T_SAT, r);
            t += 1;
        }
        assertEquals(77 * 60, t, 0.2 * 77 * 60);
        for (int i = 0; i < 2 * 77 * 60; i++) {
            wet.beginStep();
            wet.step(1, 1e6, 0, 0.1e6, 0, 800, 3000, 1, T_SAT, r);
        }
        assertEquals(0, wet.ablatedM3(), "crust under water takes the decay heat");
        assertTrue(dry.mcciHydrogenKg() > 10, "concrete water reduced to hydrogen: " + dry.mcciHydrogenKg());
    }

    /** The accident layer hands over at the cladding melting point and drains the vessel when the head fails. */
    @Test
    void plantAccidentCarriesTheMeltToTheConcrete() {
        PlantAccident a = new PlantAccident(PlantAccident.PWR_T2A);
        assertNotNull(a.melt());
        assertNull(new PlantAccident(PlantAccident.CANDU_T3).melt(), "channel reactors have no lower head");
        a.setBreakArea(PlantAccident.PWR_T2A.largeBreakM2());
        double t = 0;
        while (t < 8 * 3600 && !a.melt().vesselFailed()) {
            a.step(5, 100e6 * DecayHeat.afterInfiniteOperation(t), 0, 15.7e6);
            t += 5;
        }
        assertTrue(a.coreMelt(), "cladding melted");
        assertTrue(a.melt().vesselFailed(), "head failed by " + t + " s, stage " + a.melt().stage());
        assertEquals(0, a.inventoryKg(), "vessel drained into the cavity");
        assertTrue(a.active());
        a.step(5, 1e6, 0, 15.7e6);
        assertTrue(a.active(), "a melted core never goes back to idle");
    }

    @Test
    void stateRoundTrips() {
        CoreMeltProgression m = vver();
        runDry(m, 2e6, 0.3e6, 2000, new ArrayList<>());
        MutableStateTree tree = new MutableStateTree();
        m.save(new TreeStateWriter(tree));
        CoreMeltProgression copy = vver();
        copy.load(new TreeStateReader(tree));
        assertEquals(m.stage(), copy.stage());
        assertEquals(m.gamma(), copy.gamma(), 1e-12);
        assertEquals(m.lowerHeadK(), copy.lowerHeadK(), 1e-12);
        assertEquals(m.debrisK(), copy.debrisK(), 1e-12);
    }
}
