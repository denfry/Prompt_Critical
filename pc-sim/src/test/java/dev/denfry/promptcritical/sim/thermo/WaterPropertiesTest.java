package dev.denfry.promptcritical.sim.thermo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.denfry.promptcritical.sim.thermo.if97.If97;
import dev.denfry.promptcritical.sim.thermo.if97.If97Point;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Табличные свойства воды против эталонной IF97 (01 §1.3, §1.16). */
class WaterPropertiesTest {
    private static final WaterProperties W = WaterProperties.tables();
    private static final double P_LO = If97.P_MIN, P_HI = 25e6;

    private static double rel(double a, double e) {
        return Math.abs(a - e) / Math.abs(e);
    }

    /** Случайная точка (p, h) в области §1.3.1, где эталон определён; null — точка вне области. */
    private static If97Point randomReference(SplittableRandom rnd) {
        double p = Math.exp(rnd.nextDouble(Math.log(P_LO), Math.log(P_HI)));
        double h = rnd.nextDouble(0, 5.98e6);
        try {
            If97Point r = If97.ph(p, h);
            return r.t() >= If97.T_MIN && r.t() <= If97.T_MAX ? r : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static boolean nearCritical(If97Point r) {
        return Math.abs(r.p() - If97.P_C) < 0.5e6;
    }

    /**
     * Околокритическая область и псевдокритическая линия, где c_p и λ почти сингулярны.
     * 01 §1.3.2 требует 0,5 % по транспортным «вне 5 К от критической точки», но выше p_c
     * пик c_p смещается с давлением, и на сетке таблицы 0,5 % достигается лишь вне ±25 К.
     */
    private static boolean criticalRegion(If97Point r) {
        return nearCritical(r) || r.p() >= 0.9 * If97.P_C && Math.abs(r.t() - If97.T_C) < 25;
    }

    @Test
    @Tag("T-1.3-01")
    void interpolationAccuracy() {
        SplittableRandom rnd = new SplittableRandom(1997);
        ThermoState s = new ThermoState();
        List<String> bad = new ArrayList<>();
        int n = 0;
        while (n < 100_000) {
            If97Point r = randomReference(rnd);
            if (r == null) continue;
            n++;
            W.stateFromPressureEnthalpy(r.p(), r.h(), s);
            double ev = rel(s.density, r.rho()), et = Math.abs(s.temperature - r.t());
            boolean ok;
            String what;
            if (nearCritical(r)) {
                what = "near-critical";
                ok = ev < 1e-2 && et / r.t() < 1e-2;
            } else if (r.region() == 4) {
                what = "two-phase";
                ok = et < 0.01 && ev < 1e-4 && s.has(ThermoState.TWO_PHASE);
            } else if (r.rho() > If97.RHO_C && r.t() < 0.95 * If97.T_C) {
                what = "liquid";
                ok = ev < 2e-5 && et < 0.05;
            } else if (r.p() < 0.9 * If97.P_C) {
                what = "vapor";
                // у границы регионов 2/5 производные IF97 разрывны (сама формуляция согласована
                // там лишь до ~1e-4), бикубика сглаживает излом
                ok = ev < (Math.abs(r.t() - If97.T_25) < 5 ? 1e-3 : 1e-4) && et < 0.1;
            } else {
                what = "dense near-critical";
                ok = ev < 1e-3 && et < 0.1;
            }
            if (r.region() != 4 && !criticalRegion(r)) {
                ok &= rel(s.cp, r.cp()) < 5e-3 && rel(s.viscosity, r.mu()) < 5e-3 && rel(s.conductivity, r.k()) < 5e-3;
            }
            if (!ok && bad.size() < 30) {
                bad.add(String.format("%s p=%.6g h=%.6g: rho %.4g/%.4g T %.5f/%.5f cp %.4g/%.4g mu %.4g/%.4g k %.4g/%.4g",
                        what, r.p(), r.h(), s.density, r.rho(), s.temperature, r.t(), s.cp, r.cp(),
                        s.viscosity, r.mu(), s.conductivity, r.k()));
            }
        }
        assertThat(bad).isEmpty();
    }

    @Test
    @Tag("T-1.3-02")
    void saturationAt15_5MPa() {
        SaturationState s = W.saturationP(15.5e6);
        assertThat(s.tSat()).isCloseTo(617.94, within(0.05));
        assertThat(s.hF()).isCloseTo(1629.9e3, within(0.2e3));
        assertThat(s.hG()).isCloseTo(2596.2e3, within(0.2e3));
        assertThat(s.rhoF()).isCloseTo(594.4, within(594.4e-3));
        assertThat(s.rhoG()).isCloseTo(101.9, within(101.9e-3));
        assertThat(s.sigma()).isCloseTo(0.0047, within(0.0047e-3 * 30)); // эталон в GDD дан двумя знаками
    }

    @Test
    @Tag("T-1.3-03")
    void saturationAt7MPa() {
        SaturationState s = W.saturationP(7.0e6);
        assertThat(s.tSat()).isCloseTo(558.98, within(0.05));
        assertThat(s.latentHeat()).isCloseTo(1505.1e3, within(0.2e3));
        assertThat(s.rhoF()).isCloseTo(739.7, within(739.7e-3));
        assertThat(s.rhoG()).isCloseTo(36.52, within(36.52e-3));
    }

    @Test
    @Tag("T-1.3-04")
    void subcooledStateFromMassEnergy() {
        ThermoState s = new ThermoState();
        W.stateFromMassEnergy(726.5, 1316.3e3, 10e6, s);
        assertThat(s.pressure).isCloseTo(15.5e6, within(20e3));
        assertThat(s.temperature).isCloseTo(573.15, within(0.05));
        assertThat(s.flags).isZero();
    }

    @Test
    @Tag("T-1.3-06")
    void twoPhaseStateFromMassEnergy() {
        If97Point l = If97.satLiquid(7e6), g = If97.satVapor(7e6);
        double v = 1.0 / 300.0;
        double x = (v - l.v()) / (g.v() - l.v());
        double u = l.u() + x * (g.u() - l.u());
        ThermoState s = new ThermoState();
        W.stateFromMassEnergy(300.0, u, 5e6, s);
        assertThat(s.pressure).isCloseTo(7e6, within(5e3));
        assertThat(s.has(ThermoState.TWO_PHASE)).isTrue();
        assertThat(s.quality).isCloseTo(x, within(1e-4));
    }

    /** (p, h) → (ρ, u) → (p, h) с тёплым стартом, сдвинутым на ±30 %, во всей области. */
    @Test
    void massEnergyInvertsPressureEnthalpy() {
        SplittableRandom rnd = new SplittableRandom(42);
        ThermoState fwd = new ThermoState(), inv = new ThermoState();
        List<String> bad = new ArrayList<>();
        for (int n = 0; n < 50_000; n++) {
            double p = Math.exp(rnd.nextDouble(Math.log(2e3), Math.log(24e6)));
            double h = rnd.nextDouble(20e3, 5.9e6);
            W.stateFromPressureEnthalpy(p, h, fwd);
            if (fwd.temperature < If97.T_MIN || fwd.has(ThermoState.OUT_OF_RANGE)) continue;
            double guess = p * rnd.nextDouble(0.7, 1.3);
            W.stateFromMassEnergy(fwd.density, fwd.specificEnergy, guess, inv);
            // Давление жидкости по (ρ, u) обусловлено плохо: 01 §1.3.2 допускает 15 кПа
            // (ошибка v 2e-5); у насыщения жидкость с x_e → 0 неотличима от смеси с x → 0.
            boolean liquid = !fwd.has(ThermoState.TWO_PHASE) && fwd.density > 500;
            boolean critical = fwd.has(ThermoState.NEAR_CRITICAL);
            // у p_c (∂p/∂ρ)_u → 0: 1 % по ρ из таблицы даёт до 2 % по давлению
            double tolP = critical ? 2e-2 * p : liquid ? 15e3 : 1e-6 * p;
            double tolH = critical ? 1e-2 * h : liquid ? 30.0 : 1.0;
            boolean phaseOk = liquid || critical || inv.has(ThermoState.TWO_PHASE) == fwd.has(ThermoState.TWO_PHASE);
            if (Math.abs(inv.pressure - p) > tolP || Math.abs(inv.enthalpy - h) > tolH || !phaseOk) {
                if (bad.size() < 30) bad.add(String.format("p=%.6g h=%.6g rho=%.6g -> p=%.6g h=%.6g flags %d/%d",
                        p, h, fwd.density, inv.pressure, inv.enthalpy, fwd.flags, inv.flags));
            }
        }
        assertThat(bad).isEmpty();
    }

    @Test
    void auxiliaryFunctions() {
        SplittableRandom rnd = new SplittableRandom(7);
        List<String> bad = new ArrayList<>();
        int n = 0;
        while (n < 20_000) {
            If97Point r = randomReference(rnd);
            if (r == null || r.region() == 4 || nearCritical(r)) continue;
            n++;
            double h = W.enthalpyPT(r.p(), r.t());
            double s = W.entropyPH(r.p(), r.h());
            double hs = W.enthalpyPS(r.p(), r.s());
            double c = W.soundSpeed(r.p(), r.h());
            if (Math.abs(h - r.h()) > 300 || Math.abs(s - r.s()) > 0.05 || Math.abs(hs - r.h()) > 300
                    || rel(c, r.w()) > 5e-3) {
                if (bad.size() < 30) bad.add(String.format("p=%.6g T=%.6g: h %.7g/%.7g s %.6g/%.6g hs %.7g w %.5g/%.5g",
                        r.p(), r.t(), h, r.h(), s, r.s(), hs, c, r.w()));
            }
        }
        assertThat(bad).isEmpty();
    }

    @Test
    void saturationPressureMatchesIf97() {
        for (double t = 280; t < 647; t += 7.3) {
            assertThat(W.saturationPressure(t)).isEqualTo(If97.psat(t));
        }
    }
}
