package dev.denfry.promptcritical.sim.fuel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.denfry.promptcritical.sim.core.MutableStateTree;
import dev.denfry.promptcritical.sim.core.TreeStateReader;
import dev.denfry.promptcritical.sim.core.TreeStateWriter;
import dev.denfry.promptcritical.sim.enrichment.Criticality;
import org.junit.jupiter.api.Test;

/** Фабрикация топлива: порошок UO₂, прессование, спекание, конструкции твэлов и ТВС, учёт U-235. */
class FuelFabricationTest {
    /** Теоретическая плотность UO₂ 10,96 г/см³, урана в нём 88,15 % (Olander, табл. 11.1). */
    @Test
    void uraniumDioxideConstants() {
        assertEquals(10_960, FuelDesign.Uo2.theoreticalDensity(0.00711), 3);
        assertEquals(0.8815, FuelDesign.Uo2.uraniumShare(0.00711), 0.0005);
        assertTrue(FuelDesign.Uo2.theoreticalDensity(0.05) < FuelDesign.Uo2.theoreticalDensity(0.00711));
    }

    /** Сырая таблетка при 300 МПа — 50–53 % теоретической (5,5–5,8 г/см³); брак растёт вне 150–450 МПа. */
    @Test
    void heckelPressing() {
        double g = PelletProcess.greenDensity(300);
        assertTrue(g > 0.50 && g < 0.53, "green density " + g);
        assertTrue(PelletProcess.greenDensity(450) > g);
        assertEquals(0.01, PelletProcess.pressingDefects(300), 1e-12);
        assertTrue(PelletProcess.pressingDefects(600) > 0.2 && PelletProcess.pressingDefects(80) > 0.1);
    }

    /**
     * Спекание в водороде: 1700 °C, 4 ч — 95–96 % теоретической и зерно 8–12 мкм (Olander, гл. 14; ТУ ВВЭР
     * 10,4–10,7 г/см³); холоднее и короче — рыхлее; закрытая пористость не даёт пройти 98,5 %.
     */
    @Test
    void sinteringInHydrogen() {
        PelletProcess.Sintered s = PelletProcess.sinter(0.52, 1973.15, 4, 1);
        assertTrue(s.density() > 0.95 && s.density() < 0.96, "density " + s.density());
        assertTrue(s.grainUm() > 8 && s.grainUm() < 12, "grain " + s.grainUm());
        assertEquals(2.0, s.oxygenToU(), 1e-12);
        double t1600 = PelletProcess.sinter(0.52, 1873.15, 4, 1).density(), t1500 = PelletProcess.sinter(0.52, 1773.15, 4, 1).density();
        assertTrue(t1500 < t1600 && t1600 < s.density(), t1500 + " < " + t1600);
        assertTrue(t1600 > 0.91 && t1600 < 0.95, "1600 C " + t1600);
        assertTrue(PelletProcess.sinter(0.52, 1973.15, 8, 1).density() > s.density());
        assertTrue(PelletProcess.sinter(0.52, 2073.15, 40, 1).density() < PelletProcess.D_MAX);
        assertTrue(PelletProcess.accepted(FuelDesign.VVER_1000, s));
        assertFalse(PelletProcess.accepted(FuelDesign.VVER_1000, PelletProcess.sinter(0.52, 1773.15, 4, 1)), "underdense");
        assertFalse(PelletProcess.accepted(FuelDesign.VVER_1000, PelletProcess.sinter(0.52, 1973.15, 4, 0)), "no hydrogen: O/U 2.03");
    }

    /** Уран в ТВС: ВВЭР-1000 ≈ 400–500 кг, PWR 17×17 ≈ 440–490 кг, ВВЭР-440 ≈ 110–130 кг. */
    @Test
    void assemblyUraniumLoadings() {
        double vver1000 = FuelDesign.VVER_1000.uKgPerAssembly(0.955, 0.044), pwr = FuelDesign.PWR_17X17.uKgPerAssembly(0.955, 0.044),
                vver440 = FuelDesign.VVER_440.uKgPerAssembly(0.955, 0.036);
        assertTrue(vver1000 > 400 && vver1000 < 500, "VVER-1000 " + vver1000);
        assertTrue(pwr > 440 && pwr < 490, "PWR " + pwr);
        assertTrue(vver440 > 110 && vver440 < 130, "VVER-440 " + vver440);
        assertEquals(353, FuelDesign.VVER_1000.pelletsPerRod());
        assertTrue(FuelDesign.VVER_1000.hexagonal && !FuelDesign.PWR_17X17.hexagonal);
    }

    @Test
    void conversionMakesPowderAndKeepsTheBooks() {
        Uo2Conversion c = new Uo2Conversion();
        double fed = 0, powder = 0;
        for (int i = 0; i < 200; i++) {
            Uo2Conversion.Result r = c.step(3.6, 100, 0.044, 100, 100, 1000);
            fed += r.feedKgU();
            powder += r.powderKgU();
        }
        assertTrue(fed > 10, "fed " + fed);
        assertEquals(fed * 0.996 * 0.9995, powder, 0.01 * fed);
        MaterialBalance b = c.balance();
        assertEquals(b.u235().inKg(), b.u235().outKg() + b.u235().lossKg() + b.u235().holdupKg(), 1e-12);
        assertEquals(0.044, b.u235().outKg() / b.uranium().outKg(), 1e-9);
        assertTrue(b.uranium().holdupKg() < 0.1, "nothing stuck: " + b.uranium().holdupKg());
    }

    /** Без водорода ДУА копится; на 20 % запас U-235 упирается в безопасную массу, и питание срезают. */
    @Test
    void conversionHoldsTheSafeMass() {
        Uo2Conversion c = new Uo2Conversion();
        boolean limited = false;
        for (int i = 0; i < 2000 && !limited; i++) limited = c.step(3.6, 100, 0.20, 100, 0, 1000).limitedBySafety();
        assertTrue(limited, "safe mass interlock");
        assertTrue(c.balance().holdupU235Kg() <= Criticality.safeU235Kg(0.20) + 1e-9);
        assertTrue(new Uo2Conversion().step(3.6, 100, 0.36, 100, 100, 1000).refused(), "HEU refused");
    }

    @Test
    void conversionStateRoundTrips() {
        Uo2Conversion a = new Uo2Conversion();
        for (int i = 0; i < 20; i++) a.step(3.6, 100, 0.044, 100, 0, 1000);
        MutableStateTree tree = new MutableStateTree();
        a.save(new TreeStateWriter(tree));
        Uo2Conversion b = new Uo2Conversion();
        b.load(new TreeStateReader(tree));
        assertEquals(a.cakeKg(), b.cakeKg(), 1e-12);
        assertEquals(a.balance().holdupU235Kg(), b.balance().holdupU235Kg(), 1e-12);
    }
}
