package dev.denfry.promptcritical.sim.accident;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class ReactivityInitiatedAccidentTest {
    /** ПР-300 of 04c §4c.5.2: Λ = 2·10⁻⁵ s, γ = 5.8·10⁻¹² 1/J, β = 680 pcm (1.2β is 136 pcm over β). */
    private static final double LAMBDA = 2e-5, GAMMA = 5.8e-12, BETA = 0.0068, UO2_KG = 14_050;

    private static ReactivityInitiatedAccident.Pulse pr300(double dollars) {
        return ReactivityInitiatedAccident.nordheimFuchs(dollars * BETA, BETA, LAMBDA, GAMMA);
    }

    /** T-4c-07: E = 0.47 / 1.17 / 2.34 / 4.7 GJ and FWHM 52 / 21 / 10 / 5 ms at 1.2 / 1.5 / 2 / 3 β, 15 %. */
    @Test
    void nordheimFuchsAgainstTheTable() {
        double[] dollars = {1.2, 1.5, 2.0, 3.0};
        double[] energy = {0.47e9, 1.17e9, 2.34e9, 4.7e9};
        double[] fwhm = {0.052, 0.021, 0.010, 0.005};
        double[] peak = {8e9, 50e9, 200e9, 800e9};
        for (int i = 0; i < dollars.length; i++) {
            ReactivityInitiatedAccident.Pulse p = pr300(dollars[i]);
            assertTrue(p.prompt());
            assertEquals(energy[i], p.energyJ(), 0.15 * energy[i], "E at " + dollars[i] + "β");
            assertEquals(fwhm[i], p.fwhmS(), 0.15 * fwhm[i], "FWHM at " + dollars[i] + "β");
            assertEquals(peak[i], p.peakW(), 0.15 * peak[i], "P_max at " + dollars[i] + "β");
        }
    }

    /** The sech² shape integrates to E, is half its peak at ±FWHM/2, and leaves the core at β − (ρ₀ − β). */
    @Test
    void burstShapeAndTurnover() {
        ReactivityInitiatedAccident.Pulse p = pr300(1.5);
        double dt = p.fwhmS() / 2000, e = 0;
        for (double t = -40 * p.fwhmS(); t < 40 * p.fwhmS(); t += dt) e += p.powerAt(t) * dt;
        assertEquals(p.energyJ(), e, 0.005 * p.energyJ());
        assertEquals(0.5, p.powerAt(p.fwhmS() / 2) / p.peakW(), 1e-3);
        assertEquals(0.5 * BETA, p.rhoAfter(), 1e-12);
    }

    @Test
    void belowPromptCriticalThereIsNoBurst() {
        ReactivityInitiatedAccident.Pulse p = pr300(0.9);
        assertFalse(p.prompt());
        assertEquals(0, p.energyJ());
        assertEquals(ReactivityInitiatedAccident.Damage.NONE, ReactivityInitiatedAccident.assess(p, UO2_KG, 563, 0));
    }

    /** PCMI threshold max(60, 150 − 1.6·Bu) cal/g. */
    @Test
    void pcmiThresholdFallsWithBurnup() {
        assertEquals(150, ReactivityInitiatedAccident.pcmiThresholdCalG(0), 1e-12);
        assertEquals(70, ReactivityInitiatedAccident.pcmiThresholdCalG(50), 1e-12);
        assertEquals(60, ReactivityInitiatedAccident.pcmiThresholdCalG(80), 1e-12);
    }

    /** HZP start: 18 cal/g at 290 °C (§4c.5.2). */
    @Test
    void hotZeroPowerEnthalpy() {
        assertEquals(18, ReactivityInitiatedAccident.enthalpyCalG(563.15), 1e-9);
    }

    /**
     * Table of §4c.5.2 on ПР-300 from HZP: 1.5β adds 20 cal/g on average, 80 at the hot spot — fresh fuel holds, fuel at
     * 40 MWd/kg fails by PCMI; 3β (80 cal/g on average, 338 at the hot spot) fails a fifth of the core and fragments
     * a few per cent of it.
     */
    @Test
    void fuelDamageFollowsTheEnthalpyLimits() {
        ReactivityInitiatedAccident.Damage fresh = ReactivityInitiatedAccident.assess(pr300(1.5), UO2_KG, 563.15, 0);
        assertEquals(20, fresh.averageRiseCalG(), 1);
        assertEquals(18 + 4 * 20, fresh.peakCalG(), 4);
        assertEquals(ReactivityInitiatedAccident.Failure.NONE, fresh.failure());
        assertEquals(0, fresh.cladFailedFraction());
        ReactivityInitiatedAccident.Damage burnt = ReactivityInitiatedAccident.assess(pr300(1.5), UO2_KG, 563.15, 40);
        assertEquals(ReactivityInitiatedAccident.Failure.RIA_PCMI, burnt.failure());
        assertTrue(burnt.cladFailedFraction() > 0 && burnt.cladFailedFraction() < 0.05, "a few hot rods: " + burnt.cladFailedFraction());
        assertEquals(0, burnt.fragmentedFraction());
        ReactivityInitiatedAccident.Damage severe = ReactivityInitiatedAccident.assess(pr300(3.0), UO2_KG, 563.15, 0);
        // fresh fuel fails by PCMI at 150 cal/g before the 170 cal/g high-temperature limit
        assertEquals(ReactivityInitiatedAccident.Failure.RIA_PCMI, severe.failure());
        assertTrue(severe.peakCalG() > ReactivityInitiatedAccident.FRAGMENTATION_CAL_G);
        assertEquals(0.20, severe.cladFailedFraction(), 0.03);
        assertEquals(0.038, severe.fragmentedFraction(), 0.01);
    }

    /** §4c.10.4: 5 t of melt is 6.5 GJ of heat and 6.5–130 MJ of work; the same seed gives the same explosion. */
    @Test
    void steamExplosionEfficiencyRangeAndDeterminism() {
        double lo = Double.MAX_VALUE, hi = 0;
        SplittableRandom r = new SplittableRandom(42);
        for (int i = 0; i < 2000; i++) {
            double e = ReactivityInitiatedAccident.fciEnergyJ(5_000, r);
            lo = Math.min(lo, e);
            hi = Math.max(hi, e);
        }
        assertTrue(lo >= 6.5e6 && lo < 8e6, "lower end " + lo);
        assertTrue(hi <= 130e6 && hi > 110e6, "upper end " + hi);
        assertEquals(ReactivityInitiatedAccident.fciEnergyJ(5_000, new SplittableRandom(7)),
                ReactivityInitiatedAccident.fciEnergyJ(5_000, new SplittableRandom(7)));
    }
}
