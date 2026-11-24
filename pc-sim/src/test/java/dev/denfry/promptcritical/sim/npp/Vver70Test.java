package dev.denfry.promptcritical.sim.npp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.denfry.promptcritical.sim.core.MutableStateTree;
import dev.denfry.promptcritical.sim.core.TreeStateReader;
import dev.denfry.promptcritical.sim.core.TreeStateWriter;
import dev.denfry.promptcritical.sim.fuel.FuelDesign;
import dev.denfry.promptcritical.sim.reactor.pwr.SecondarySide;
import org.junit.jupiter.api.Test;

/** В-2 (ВВЭР-70, Райнсберг): 265 МВт(т), 10 МПа, 250/267 °C, три петли, 132 кассеты, газовый КД. */
class Vver70Test {
    static final PressurizedWaterReactor.Design D = PressurizedWaterReactor.VVER70;
    static final double DT = PressurizedWaterReactor.DT;
    /** Питательная вода ПГ ВВЭР-70 [est]. */
    static final double FEED_K = 433.15;

    static final class Unit {
        final PressurizedWaterReactor r = nominal();
        final SecondarySide sg = new SecondarySide(Pwrs.W, D.nominalW(), 20_000 * D.nominalW() / 100e6, D.secondaryPa(), FEED_K);
        double maxP, minP = 1e9;

        void run(double seconds) {
            for (int i = 0; i < Math.round(seconds / DT); i++) {
                r.setSecondary(sg.temperatureK(), sg.level(), D.loops());
                r.step(0);
                sg.step(DT, r.steamGeneratorW());
                maxP = Math.max(maxP, r.pressure());
                minP = Math.min(minP, r.pressure());
            }
        }
    }

    static PressurizedWaterReactor nominal() {
        PressurizedWaterReactor r = new PressurizedWaterReactor(D);
        double u235 = 0.03;
        double kg = FuelDesign.VVER_440.uKgPerAssembly(0.96, u235);
        for (int i = 0; i < D.positions(); i++) assertTrue(r.load(i, FuelDesign.VVER_440, kg, u235, 0.0003), "load " + i);
        for (int l = 0; l < D.loops(); l++) r.setPumpDrive(l, 1);
        r.setSecondary(Pwrs.W.saturationP(D.secondaryPa()).tSat(), 0.5, D.loops());
        double rodM = r.core().rodLengthM();
        double fuel = (D.inletK() + D.outletK()) / 2 + D.nominalW() / (rodM * 1000 / PressurizedWaterReactor.FUEL_R_K_PER_KW_M);
        r.setThermalState(fuel, D.outletK(), D.inletK());
        r.setRods(0, Pwrs.RG_WORKING);
        double lo = 0, hi = 30;
        for (int i = 0; i < 50; i++) {
            double m = (lo + hi) / 2;
            r.setBoronGkg(m);
            if (r.evaluateReactivity() > 0) lo = m;
            else hi = m;
        }
        r.setBoronGkg((lo + hi) / 2);
        r.equilibrate(1);
        return r;
    }

    @Test
    void designMatchesTheRheinsbergSheet() {
        assertEquals(265e6, D.nominalW());
        assertEquals(132, D.positions());
        assertEquals(3, D.loops());
        assertEquals(10.0e6, D.pNominal());
        assertEquals(250, D.inletK() - 273.15, 1e-9);
        assertEquals(267, D.outletK() - 273.15, 1e-9);
        assertTrue(D.gasPressurizer());
        assertEquals(0, D.heaterPropW() + D.heaterBackupW() + D.sprayMaxKgS());
        // 265 МВт при ΔT 17 К: ≈ 3 т/с через зону, ≈ 4,7 тыс. м³/ч на петлю (лист насоса)
        double perLoopM3h = D.nominalFlowKgS() / D.loops() / 790 * 3600;
        assertEquals(4700, perLoopM3h, 500);
    }

    @Test
    void defaultDesignKeepsTheStartBlockConstants() {
        PressurizedWaterReactor.Design d = PressurizedWaterReactor.DEFAULT;
        assertEquals(PressurizedWaterReactor.NOMINAL_W, d.nominalW());
        assertEquals(PressurizedWaterReactor.POSITIONS, d.positions());
        assertEquals(PressurizedWaterReactor.LOOPS, d.loops());
        assertEquals(PressurizedWaterReactor.NOMINAL_FLOW_KG_S, d.nominalFlowKgS(), 1e-9);
        assertEquals(PressurizedWaterReactor.UA_SG, d.uaSg(), 1e-6);
        assertEquals(PressurizedWaterReactor.RUPTURE_PA, d.rupturePa(), 1e-6);
        assertFalse(new PressurizedWaterReactor().pressurizer().gas());
    }

    @Test
    void reachesRatedPowerAndTheGasPressurizerHoldsPressure() {
        Unit u = new Unit();
        u.run(180);
        PressurizedWaterReactor r = u.r;
        assertEquals(1.0, r.power(), 0.03);
        assertEquals(265e6, r.steamGeneratorW(), 265e6 * 0.05);
        assertEquals(10.0e6, r.pressure(), 0.15e6);
        assertEquals(267, r.hotLegK() - 273.15, 2.0);
        assertEquals(250, r.coldLegK() - 273.15, 2.0);
        assertTrue(r.pressurizer().gas());
        assertTrue(r.pressurizer().nitrogenKg() > 100, "N2 " + r.pressurizer().nitrogenKg());
        assertEquals(0, r.heaterW(), 1e-9);
        assertFalse(r.protection().isTripped(), "no trip: " + r.protection().firstOut());
        assertTrue(r.mdnbr() > 1.3, "MDNBR " + r.mdnbr());
    }

    @Test
    void loadRejectionCompressesTheGasAndTheGasSystemBringsItBack() {
        Unit u = new Unit();
        u.run(60);
        u.maxP = 0;
        u.sg.setValve(0.85);
        u.run(30);
        assertTrue(u.maxP > 10.0e6, "pressure rises on insurge " + u.maxP);
        assertTrue(u.maxP < D.reliefPa(), "relief not reached " + u.maxP);
        u.run(300);
        assertEquals(10.0e6, u.r.pressure(), 0.15e6);
    }

    @Test
    void gasPressurizerStateRoundTrips() {
        Unit u = new Unit();
        u.run(20);
        MutableStateTree tree = new MutableStateTree();
        u.r.save(new TreeStateWriter(tree));
        PressurizedWaterReactor b = new PressurizedWaterReactor(D);
        b.load(new TreeStateReader(tree));
        assertEquals(u.r.pressure(), b.pressure(), 1e3);
        assertEquals(u.r.pressurizer().nitrogenKg(), b.pressurizer().nitrogenKg(), 1e-9);
        assertEquals(u.r.pressurizerLevel(), b.pressurizerLevel(), 1e-6);
    }
}
