package dev.denfry.promptcritical.sim.npp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.denfry.promptcritical.sim.accident.SteamGeneratorTubeRupture;
import dev.denfry.promptcritical.sim.core.MutableStateTree;
import dev.denfry.promptcritical.sim.core.TreeStateReader;
import dev.denfry.promptcritical.sim.core.TreeStateWriter;
import dev.denfry.promptcritical.sim.reactor.pwr.SecondarySide;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

/** Steam-generator tube rupture on the T2a VVER (04c §4c.6). */
class PwrTubeRuptureTest {
    /** Couples the generator model to the reactor for {@code seconds}. */
    private static void run(PressurizedWaterReactor r, SteamGeneratorTubeRupture sg, double seconds, SplittableRandom rnd) {
        int n = (int) Math.round(seconds / PressurizedWaterReactor.DT);
        for (int i = 0; i < n; i++) {
            r.setTubeRupture(sg.breakAreaM2(), sg.sgPressurePa());
            r.step(0);
            sg.step(PressurizedWaterReactor.DT, r.tubeLeakKgS(), r.coldLegK(), r.power(), rnd);
        }
    }

    /** T-4c-09: one ⌀16×1.5 tube is 2.65 cm² and leaks ≈ 17 kg/s at nominal; the N-16 alarm comes within 10 s. */
    @Test
    void oneTubeLeaksSeventeenKilogramsASecond() {
        assertEquals(2.65e-4, SteamGeneratorTubeRupture.TUBE_BREAK_M2, 0.01e-4);
        PressurizedWaterReactor r = Pwrs.nominal();
        SteamGeneratorTubeRupture sg = new SteamGeneratorTubeRupture(SecondarySide.P_NOMINAL);
        sg.rupture(true);
        SplittableRandom rnd = new SplittableRandom(1);
        run(r, sg, 0.5, rnd);
        assertEquals(17, r.tubeLeakKgS(), 1.7, "leak at nominal");
        run(r, sg, 9.5, rnd);
        assertTrue(sg.n16Alarm(), "N-16 alarm");
        assertTrue(sg.alarmDelayS() > SteamGeneratorTubeRupture.TRANSIT_S && sg.alarmDelayS() < 10, "alarm at " + sg.alarmDelayS() + " s");
        assertTrue(sg.toCondenserKg() > 100, "leak goes to the condenser with the steam: " + sg.toCondenserKg());
        assertEquals(0, sg.toAtmosphereKg());
    }

    /** The 6 kg/s makeup cannot keep up: the primary loses pressure and trips on low pressure, then SI holds it. */
    @Test
    void makeupCannotKeepUpAndThePrimaryTrips() {
        PressurizedWaterReactor r = Pwrs.nominal();
        SteamGeneratorTubeRupture sg = new SteamGeneratorTubeRupture(SecondarySide.P_NOMINAL);
        sg.rupture(true);
        SplittableRandom rnd = new SplittableRandom(1);
        double p0 = r.pressure();
        for (int i = 0; i < 600 * 20 && !r.protection().isTripped(); i++) run(r, sg, PressurizedWaterReactor.DT, rnd);
        assertTrue(r.protection().isTripped(), "tripped");
        assertTrue(r.pressure() < p0, "pressure falls: " + r.pressure());
        run(r, sg, 120, rnd);
        assertTrue(r.safetyInjection(), "SI on");
        assertTrue(r.tubeLeakKgS() > 5, "leak continues: " + r.tubeLeakKgS());
    }

    /**
     * Isolated without cooldown: the generator climbs to its relief setpoint, fills with coolant and vents water; with
     * the stuck-open draw it blows down and the leak becomes an unisolable release to the atmosphere.
     */
    @Test
    void isolatedGeneratorOverfillsAndVentsToTheAtmosphere() {
        SteamGeneratorTubeRupture sg = new SteamGeneratorTubeRupture(SecondarySide.P_NOMINAL);
        sg.rupture(true);
        sg.setIsolated(true);
        // a stuck-open seed: the first draw is under 0.3
        SplittableRandom rnd = stuckSeed();
        double coldLeg = 563.15; // P_sat ≈ 7.4 MPa — under the 8.0 MPa setpoint: no lift while there is steam space
        for (int i = 0; i < 200; i++) sg.step(0.5, 10, coldLeg, 0, rnd);
        assertFalse(sg.reliefOpen(), "no lift at " + sg.sgPressurePa());
        assertEquals(1000, sg.excessWaterKg(), 1);
        // the generator fills up and goes solid: its valve lifts and passes water
        for (int i = 0; i < 4000 && !sg.stuckOpen(); i++) sg.step(0.5, 10, coldLeg, 0, rnd);
        assertTrue(sg.waterRelief() || sg.stuckOpen(), "relief passes water");
        assertTrue(sg.stuckOpen(), "stuck open on this seed");
        assertTrue(sg.toAtmosphereKg() > 0);
        for (int i = 0; i < 1200; i++) sg.step(0.5, 10, coldLeg, 0, rnd);
        assertTrue(sg.sgPressurePa() < 1e6, "blown down: " + sg.sgPressurePa());
        assertEquals(10, sg.atmosphereKgS(), 3, "the whole leak goes out");
    }

    /** Cooling the primary below the damaged generator's pressure stops the leak (§4c.6.4). */
    @Test
    void leakStopsAtTheGeneratorPressure() {
        PressurizedWaterReactor r = Pwrs.nominal();
        r.setTubeRupture(SteamGeneratorTubeRupture.TUBE_BREAK_M2, 15.0e6);
        r.step(0);
        assertTrue(r.tubeLeakKgS() > 0 && r.tubeLeakKgS() < 10, "small Δp: " + r.tubeLeakKgS());
        r.setTubeRupture(SteamGeneratorTubeRupture.TUBE_BREAK_M2, 16.0e6);
        r.step(0);
        assertEquals(0, r.tubeLeakKgS(), 1e-9);
    }

    /** The operator's tool for the last step: the pressurizer relief valve held open brings the primary down. */
    @Test
    void porvDepressurisesThePrimary() {
        PressurizedWaterReactor shut = Pwrs.nominal(), vented = Pwrs.nominal();
        shut.scram();
        vented.scram();
        vented.setPorvOpen(true);
        Pwrs.run(shut, 120);
        Pwrs.run(vented, 120);
        assertTrue(vented.pressure() < shut.pressure() - 1e6, "PORV " + vented.pressure() + " vs " + shut.pressure());
        vented.setPorvOpen(false);
        assertFalse(vented.porvOpen());
    }

    @Test
    void stateRoundTrips() {
        SteamGeneratorTubeRupture sg = new SteamGeneratorTubeRupture(SecondarySide.P_NOMINAL);
        sg.rupture(true);
        sg.setIsolated(true);
        SplittableRandom rnd = new SplittableRandom(3);
        for (int i = 0; i < 300; i++) sg.step(0.5, 12, 575, 0, rnd);
        MutableStateTree t = new MutableStateTree();
        sg.save(new TreeStateWriter(t));
        SteamGeneratorTubeRupture copy = new SteamGeneratorTubeRupture(SecondarySide.P_NOMINAL);
        copy.load(new TreeStateReader(t));
        assertEquals(sg.sgPressurePa(), copy.sgPressurePa());
        assertEquals(sg.excessWaterKg(), copy.excessWaterKg());
        assertEquals(sg.isolated(), copy.isolated());
        assertEquals(sg.reliefOpen(), copy.reliefOpen());
    }

    private static SplittableRandom stuckSeed() {
        for (long s = 0; ; s++)
            if (new SplittableRandom(s).nextDouble() < SteamGeneratorTubeRupture.STUCK_OPEN_PROB) return new SplittableRandom(s);
    }
}
