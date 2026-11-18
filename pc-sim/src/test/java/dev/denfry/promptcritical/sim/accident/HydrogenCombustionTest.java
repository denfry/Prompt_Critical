package dev.denfry.promptcritical.sim.accident;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.denfry.promptcritical.sim.accident.HydrogenCombustion.Regime;
import org.junit.jupiter.api.Test;

class HydrogenCombustionTest {
    /** T-4c-12: dry air at 1 bar and 300 K, c_v = 25: 4 % → 2.3, 10 % → 4.2, 15 % → 5.8 bar (3 %). */
    @Test
    void aiccPressureAgainstT4c12() {
        assertEquals(2.3, HydrogenCombustion.aiccRatio(0.04, 1, 300), 0.03 * 2.3);
        assertEquals(4.2, HydrogenCombustion.aiccRatio(0.10, 1, 300), 0.03 * 4.2);
        assertEquals(5.8, HydrogenCombustion.aiccRatio(0.15, 1, 300), 0.03 * 5.8);
    }

    /** §4c.7.3 regimes: limits with steam and oxygen, deflagration bands, detonation by composition and by size. */
    @Test
    void regimesFollowTheDiagram() {
        assertEquals(Regime.NONE, HydrogenCombustion.classify(0.03, 0, 0.2, 20));
        assertEquals(Regime.NONE, HydrogenCombustion.classify(0.05, 0.2, 0.15, 20), "LFL 6.2 % at 20 % steam");
        assertEquals(Regime.NONE, HydrogenCombustion.classify(0.20, 0.60, 0.04, 20), "steam-inert");
        assertEquals(Regime.NONE, HydrogenCombustion.classify(0.20, 0, 0.04, 20), "no oxygen");
        assertEquals(Regime.SLOW_DEFLAGRATION, HydrogenCombustion.classify(0.06, 0, 0.2, 20));
        assertEquals(Regime.COMPLETE_DEFLAGRATION, HydrogenCombustion.classify(0.09, 0, 0.19, 20));
        assertEquals(Regime.FAST_DEFLAGRATION, HydrogenCombustion.classify(0.11, 0, 0.19, 20));
        assertEquals(Regime.FAST_DEFLAGRATION, HydrogenCombustion.classify(0.14, 0, 0.18, 2), "a small room: no DDT at 14 %");
        assertEquals(Regime.DETONATION, HydrogenCombustion.classify(0.14, 0, 0.18, 20), "DDT in a large containment");
        assertEquals(Regime.DETONATION, HydrogenCombustion.classify(0.296, 0, 0.15, 0.1), "stoichiometric");
        assertEquals(0.5, HydrogenCombustion.completeness(Regime.SLOW_DEFLAGRATION, 0.06, 0), 1e-12);
        assertEquals(1.0, HydrogenCombustion.cellWidthM(0.13), 1e-12);
        assertEquals(0.05, HydrogenCombustion.cellWidthM(0.20), 1e-12);
    }

    /** More than 55 % steam keeps 10 % hydrogen from burning even with a hot surface in it. */
    @Test
    void steamInertsTheContainment() {
        ContainmentAtmosphere c = new ContainmentAtmosphere(1000, 1e6);
        c.addSteam(3000, 1.1e10); // ≈ 150 °C: the steam stays above 55 % after condensation
        c.addHydrogen(36);
        c.setHotSurface(true);
        assertFalse(c.step(1), "steam-inerted mixture burned");
        assertTrue(c.steamFraction() > HydrogenCombustion.STEAM_INERT, "steam " + c.steamFraction());
        assertTrue(c.hydrogenFraction() > ContainmentAtmosphere.DEFLAGRATION_FRACTION, "H2 " + c.hydrogenFraction());
    }

    /** A hot surface lights a 5 % mixture: a slow, partial burn; without one the hydrogen waits for 8 %. */
    @Test
    void hotSurfaceLightsALeanMixture() {
        ContainmentAtmosphere lit = new ContainmentAtmosphere(5000, 1e6), dark = new ContainmentAtmosphere(5000, 1e6);
        double kg = 0.05 * 5000 * 101_325 / (8.314 * 313) * 0.002016 / 0.95;
        lit.addHydrogen(kg);
        dark.addHydrogen(kg);
        lit.setHotSurface(true);
        assertFalse(dark.step(1));
        assertTrue(lit.step(1));
        assertEquals(Regime.SLOW_DEFLAGRATION, lit.lastRegime());
        assertTrue(lit.hydrogenKg() > 0.5 * kg, "a slow deflagration leaves most of it: " + lit.hydrogenKg());
    }

    /** 14 % in a 20 000 m³ building detonates and loads the walls with twice the AICC pressure. */
    @Test
    void largeContainmentDetonates() {
        ContainmentAtmosphere c = new ContainmentAtmosphere(20_000, 3e6);
        double air = 101_325 * 20_000 / (8.314 * 313);
        c.addHydrogen(air * 0.14 / 0.86 * 0.002016);
        assertEquals(Regime.DETONATION, c.regime());
        assertTrue(c.step(1));
        assertEquals(Regime.DETONATION, c.lastRegime());
        assertTrue(c.lastPeakPa() > 1.8 * 0.5e6, "CJ load " + c.lastPeakPa());
    }

    /** Nitrogen inerting holds oxygen at 3.5 %: no regime at any hydrogen. */
    @Test
    void inertedContainmentHasNoRegime() {
        ContainmentAtmosphere c = new ContainmentAtmosphere(1000, 1e6);
        c.setInerted(true);
        c.addHydrogen(20);
        assertEquals(Regime.NONE, c.regime());
        c.setHotSurface(true);
        assertFalse(c.step(1));
    }
}
