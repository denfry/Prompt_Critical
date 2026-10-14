package dev.denfry.promptcritical.sim.thermo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.denfry.promptcritical.sim.thermo.CriticalFlowTable.Model;
import dev.denfry.promptcritical.sim.thermo.if97.CriticalFlowGenerator;
import dev.denfry.promptcritical.sim.thermo.if97.CriticalFlowGenerator.Choke;
import dev.denfry.promptcritical.sim.thermo.if97.If97;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Таблица критического расхода против эталонного расчёта по IF97 (01 §1.3.9, §1.16). */
class CriticalFlowTableTest {
    private static final WaterProperties W = WaterProperties.tables();
    private static final CriticalFlowTable HEM = CriticalFlowTable.of(Model.HEM);
    private static final CriticalFlowTable MOODY = CriticalFlowTable.of(Model.MOODY);

    private static double rel(double a, double e) {
        return Math.abs(a - e) / Math.abs(e);
    }

    @Test
    @Tag("T-1.3-05")
    void hemControlValues() {
        SaturationState s7 = W.saturationP(7e6);
        assertThat(rel(HEM.criticalMassFlux(15.5e6, W.enthalpyPT(15.5e6, 573.15)), 1.02e5)).isLessThan(0.03);
        assertThat(rel(HEM.criticalMassFlux(7e6, s7.hF()), 2.65e4)).isLessThan(0.03);
        assertThat(rel(HEM.criticalMassFlux(7e6, s7.hG()), 1.00e4)).isLessThan(0.03);
    }

    /** Таблица 01 §1.3.9: G_c и давление в горле эталонного HEM-расчёта. */
    @Test
    void referenceThroatConditions() {
        double[][] cases = {
            {15.5e6, If97.pt(15.5e6, 573.15).h(), 1.02e5, 8.2e6},
            {15.5e6, If97.pt(15.5e6, 593.15).h(), 7.8e4, 10.9e6},
            {7e6, If97.satLiquid(7e6).h(), 2.6e4, 5.5e6},
            {7e6, If97.satVapor(7e6).h(), 1.0e4, 4.1e6},
            {15.5e6, If97.satVapor(15.5e6).h(), 2.4e4, 9.4e6},
        };
        for (double[] c : cases) {
            Choke r = CriticalFlowGenerator.hem(c[0], c[1]);
            assertThat(rel(r.massFlux(), c[2])).as("G_c at p0=%s h0=%s", c[0], c[1]).isLessThan(0.03);
            assertThat(r.throatPressure()).as("p_throat at p0=%s h0=%s", c[0], c[1]).isCloseTo(c[3], within(0.05e6));
        }
    }

    /** Сильно недогретая жидкость: HEM близка к Бернулли до p_s(T₀) (01 §1.3.9, 2 %). */
    @Test
    void subcooledLiquidMatchesBernoulli() {
        double p0 = 15.5e6, t0 = 573.15;
        double bernoulli = Math.sqrt(2 * If97.pt(p0, t0).rho() * (p0 - If97.psat(t0)));
        assertThat(rel(HEM.criticalMassFlux(p0, W.enthalpyPT(p0, t0)), bernoulli)).isLessThan(0.02);
    }

    /** Случайные (p₀, h₀) по всей сетке: интерполяция против эталона, обе модели. */
    @Test
    void interpolationAccuracy() {
        SplittableRandom rnd = new SplittableRandom(1965);
        for (int n = 0; n < 300; n++) {
            double p0 = Math.exp(rnd.nextDouble(Math.log(0.15e6), Math.log(22e6)));
            double h0 = rnd.nextDouble(400e3, 2900e3);
            double hem = CriticalFlowGenerator.hem(p0, h0).massFlux();
            double moody = CriticalFlowGenerator.moody(p0, h0).massFlux();
            assertThat(rel(HEM.criticalMassFlux(p0, h0), hem)).as("HEM p0=%s h0=%s", p0, h0).isLessThan(0.02);
            assertThat(rel(MOODY.criticalMassFlux(p0, h0), moody)).as("Moody p0=%s h0=%s", p0, h0).isLessThan(0.02);
        }
    }

    /**
     * Муди не меньше HEM; для насыщенной смеси 01 §1.3.9 и 04c §4c.2.3 дают +20…50 %,
     * расчёт при 7 МПа: +47 % (x = 0), +56 % (x = 0,1), +27 % (x = 0,5).
     */
    @Test
    void moodyIsConservative() {
        SaturationState s = W.saturationP(7e6);
        for (double x : new double[] {0.0, 0.1, 0.3, 0.5}) {
            double h0 = s.hF() + x * s.latentHeat();
            double ratio = MOODY.criticalMassFlux(7e6, h0) / HEM.criticalMassFlux(7e6, h0);
            assertThat(ratio).as("x=%s", x).isBetween(1.2, 1.6);
        }
        SplittableRandom rnd = new SplittableRandom(7);
        for (int n = 0; n < 2000; n++) {
            double p0 = Math.exp(rnd.nextDouble(Math.log(0.15e6), Math.log(22e6)));
            double h0 = rnd.nextDouble(400e3, 2900e3);
            assertThat(MOODY.criticalMassFlux(p0, h0)).isGreaterThanOrEqualTo(HEM.criticalMassFlux(p0, h0) * 0.999);
        }
        assertThat(HEM.model()).isEqualTo(Model.HEM);
        assertThat(MOODY.model()).isEqualTo(Model.MOODY);
    }

    @Test
    void outsideGridIsBoundedAndMonotoneInPressure() {
        double h0 = 2.8e6;
        double edge = HEM.criticalMassFlux(0.15e6, h0);
        assertThat(HEM.criticalMassFlux(0.075e6, h0)).isCloseTo(edge / 2, within(edge * 1e-9));
        assertThat(HEM.criticalMassFlux(0.0, h0)).isZero();
        assertThat(HEM.criticalMassFlux(30e6, h0)).isEqualTo(HEM.criticalMassFlux(22e6, h0));
        assertThat(HEM.criticalMassFlux(7e6, 100e3)).isEqualTo(HEM.criticalMassFlux(7e6, 400e3));
        assertThat(HEM.criticalMassFlux(7e6, 4e6)).isEqualTo(HEM.criticalMassFlux(7e6, 2.9e6));
        double prev = 0;
        for (double p = 0.2e6; p < 22e6; p *= 1.1) {
            double g = HEM.criticalMassFlux(p, h0);
            assertThat(g).isGreaterThan(prev);
            prev = g;
        }
    }
}
