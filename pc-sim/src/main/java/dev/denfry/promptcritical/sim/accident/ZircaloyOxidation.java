package dev.denfry.promptcritical.sim.accident;

import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;

/**
 * Zirconium–steam reaction Zr + 2H₂O → ZrO₂ + 2H₂ with Baker–Just parabolic kinetics
 * (Baker &amp; Just, ANL-6548, 1962; the 10 CFR 50 Appendix K correlation):
 * {@code w² = 33.3e6 · t · exp(−45 500 / (R·T))}, w in mg Zr/cm², R = 1.987 cal/(mol·K).
 *
 * <p>Stoichiometry: 4.032 g H₂ per 91.224 g Zr → 0.0442 kg H₂ per kg Zr. Reaction heat
 * ≈ 586 kJ/mol Zr (Baker–Just) → 6.42 MJ per kg Zr.
 */
public final class ZircaloyOxidation {
    /** kg H₂ released per kg Zr reacted. */
    public static final double H2_PER_KG_ZR = 2 * 2.016 / 91.224;
    /** J released per kg Zr reacted. */
    public static final double HEAT_J_PER_KG_ZR = 586e3 / 0.091224;
    private static final double R_CAL = 1.987;

    private final double surfaceCm2;
    private final double zrMassKg;
    /** Reacted areal mass squared, (mg/cm²)². */
    private double w2;

    /**
     * @param surfaceM2 wetted cladding outer surface
     * @param zrMassKg  total cladding zirconium inventory (caps the reaction); 0 — steel cladding, no Zr–steam reaction
     */
    public ZircaloyOxidation(double surfaceM2, double zrMassKg) {
        if (!(surfaceM2 > 0) || !(zrMassKg >= 0)) throw new IllegalArgumentException("surface must be > 0, mass >= 0");
        this.surfaceCm2 = surfaceM2 * 1e4;
        this.zrMassKg = zrMassKg;
    }

    /** Parabolic rate constant d(w²)/dt at cladding temperature {@code tK}, (mg/cm²)²/s. */
    public static double rateConstant(double tK) {
        return 33.3e6 * Math.exp(-45_500.0 / (R_CAL * tK));
    }

    /**
     * Advances the reaction; steam is assumed available (not steam-starved).
     *
     * @return Zr mass reacted during the step, kg
     */
    public double step(double tK, double dtS) {
        if (tK < 900 || dtS <= 0 || zrMassKg <= 0) return 0; // below ~900 K the rate is negligible on accident time scales
        double before = reactedKg();
        w2 += rateConstant(tK) * dtS;
        double after = reactedKg();
        return after - before;
    }

    /** Zr reacted so far, kg (capped at the inventory). */
    public double reactedKg() {
        return Math.min(zrMassKg, Math.sqrt(w2) * surfaceCm2 * 1e-6);
    }

    public double hydrogenKg() {
        return reactedKg() * H2_PER_KG_ZR;
    }

    /** Equivalent cladding reacted fraction (ECR, the 17 % criterion of 10 CFR 50.46). */
    public double reactedFraction() {
        return zrMassKg > 0 ? reactedKg() / zrMassKg : 0;
    }

    public void save(StateWriter out) {
        out.putDouble("w2", w2);
    }

    public void load(StateReader in) {
        w2 = Math.max(0, in.optDouble("w2", 0));
    }
}
