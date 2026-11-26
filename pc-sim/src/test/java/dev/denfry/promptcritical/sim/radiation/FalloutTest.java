package dev.denfry.promptcritical.sim.radiation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.denfry.promptcritical.sim.consequence.GasDispersion;
import org.junit.jupiter.api.Test;

class FalloutTest {
    private static final GasDispersion.Stability D = GasDispersion.Stability.D;

    /**
     * 04b §4b.9 napkin check, class D, u = 5 m/s, x = 500 m on the axis: σy = 39.0 m, σz = 22.7 m; a ground release of
     * 10¹² Bq/s of Cs-137 gives C = 7.19·10⁷ Bq/m³ (the GDD's 3.0·10⁷ is the same plume from a 30 m stack) and a dry
     * flux v_d·C = 7.19·10⁴ Bq/(m²·s), a few per cent less after the plume has lost what it dropped on the way.
     */
    @Test
    void dryDepositionOnTheAxisAtFiveHundredMetres() {
        assertEquals(39.0, D.sigmaY(500), 0.05);
        assertEquals(22.7, D.sigmaZ(500), 0.05);
        double vd = Fallout.depositionVelocity(Emitter.CS137);
        assertEquals(1e-3, vd);
        double undepleted = vd * 1e12 / (Math.PI * 5 * D.sigmaY(500) * D.sigmaZ(500));
        assertEquals(7.19e4, undepleted, 0.01e4);
        double dep = Fallout.depletion(vd, 0, 5, D, 500);
        assertTrue(dep > 0.95 && dep < 1, "depletion " + dep);
        assertEquals(undepleted * dep, Fallout.fluxBqM2S(1e12, 5, D, 500, 0, vd, 0), 1e-6 * undepleted);
        // 100 s of the release leave about 7 MBq/m² under the axis
        assertEquals(7.0e6, Fallout.fluxBqM2S(1e12, 5, D, 500, 0, vd, 0) * 100, 0.3e6);
    }

    /** Rain washes the plume out: Λ·Q/(√(2π)·u·σy) on the axis, far more than the dry part, and the plume thins faster. */
    @Test
    void rainWashesThePlumeOut() {
        double vd = 1e-3, lam = Fallout.washout(true, false);
        assertEquals(1e-4, lam);
        assertEquals(3e-4, Fallout.washout(true, true));
        assertEquals(0, Fallout.washout(false, false));
        double dry = Fallout.fluxBqM2S(1e12, 5, D, 500, 0, vd, 0);
        double wet = Fallout.fluxBqM2S(1e12, 5, D, 500, 0, vd, lam);
        double washedUndepleted = lam * 1e12 / (Math.sqrt(2 * Math.PI) * 5 * D.sigmaY(500));
        assertTrue(wet > dry + 0.5 * washedUndepleted, "wet " + wet + " dry " + dry);
        assertTrue(Fallout.depletion(vd, lam, 5, D, 500) < Fallout.depletion(vd, 0, 5, D, 500), "rain depletes the plume");
    }

    /** Iodine settles ten times faster than caesium; noble gases and upwind points get nothing; the trace narrows off-axis. */
    @Test
    void whatSettlesAndWhere() {
        double cs = Fallout.fluxBqM2S(1e10, 3, D, 100, 0, Fallout.depositionVelocity(Emitter.CS137), 0);
        double io = Fallout.fluxBqM2S(1e10, 3, D, 100, 0, Fallout.depositionVelocity(Emitter.I131), 0);
        // ten times the velocity, a little less than ten times the flux: iodine also strips its own plume faster
        double depCs = Fallout.depletion(1e-3, 0, 3, D, 100), depIo = Fallout.depletion(1e-2, 0, 3, D, 100);
        assertEquals(10 * depIo / depCs, io / cs, 1e-9);
        assertTrue(io > 5 * cs && io < 10 * cs, "I-131 " + io + " vs Cs-137 " + cs);
        assertEquals(0, Fallout.fluxBqM2S(1e10, 3, D, 100, 0, Fallout.depositionVelocity(Emitter.KR85), 0));
        assertEquals(0, Fallout.fluxBqM2S(1e10, 3, D, 100, 0, Fallout.depositionVelocity(Emitter.KR85), 3e-4), "rain does not wash out a noble gas");
        assertEquals(0, Fallout.fluxBqM2S(1e10, 3, D, -50, 0, 1e-3, 1e-4), "upwind");
        assertEquals(0, Fallout.fluxBqM2S(1e10, 3, D, Fallout.MAX_DISTANCE_M + 1, 0, 1e-3, 1e-4), "beyond the trace");
        double sy = D.sigmaY(100);
        assertEquals(Math.exp(-0.5), Fallout.fluxBqM2S(1e10, 3, D, 100, sy, 1e-3, 0) / cs, 1e-9);
    }
}
