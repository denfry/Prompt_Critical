package dev.denfry.promptcritical.sim.production;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.denfry.promptcritical.sim.drilling.FormationFluid;
import dev.denfry.promptcritical.sim.drilling.NaturalGas;
import dev.denfry.promptcritical.sim.drilling.Trap;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Газлифт 09 §9.6.4. */
class GasLiftTest {
    private static final double SEP = 0.6e6;

    private static ProducingReservoir deadField() {
        Trap t = new Trap(0, 0, 100, 900, 80, 960, 20, 0.22, 300, FormationFluid.OIL, 8_000);
        return ProducingReservoir.perforate(t, 900);
    }

    @Test
    void injectedGasLightensTheColumn() {
        ProducingReservoir r = deadField();
        double q = r.inflow().maxRateM3s() * 0.2;
        double plain = Tubing.pressureAtPa(SEP, q, r.gasOilRatio(), r.waterCut(), r.datumM(), r.datumM(), r.bubblePointPa(), 0, 0);
        double lifted = Tubing.pressureAtPa(SEP, q, r.gasOilRatio(), r.waterCut(), r.datumM(), r.datumM(), r.bubblePointPa(), 0.3, 700);
        assertThat(lifted).isLessThan(plain);
        // без закачки — прежняя кривая лифта
        assertThat(Tubing.bottomholePa(SEP, q, r.gasOilRatio(), r.waterCut(), r.datumM(), r.bubblePointPa())).isCloseTo(plain, within(1e-6));
    }

    @Test
    void gasColumnAddsPressureWithDepth() {
        double p = GasLift.casingAtDepthPa(8e6, 1000, 1000);
        double rho = NaturalGas.densityKgM3(8e6 + NaturalGas.STD_PRESSURE_PA, 300);
        assertThat(p - 8e6).isCloseTo(rho * Tubing.G * 1000, within(0.15 * rho * Tubing.G * 1000));
    }

    @Test
    void deadWellFlowsOnGasLiftAndMoreGasGivesMoreOilUpToAPoint() {
        ProducingReservoir r = deadField();
        Inflow ipr = r.inflow();
        double gor = r.gasOilRatio(), wc = r.waterCut(), d = r.datumM();
        assertThat(Tubing.kicksOff(ipr, gor, wc, d, SEP)).as("the dead well does not kick off by itself").isFalse();
        List<GasLift.Valve> one = List.of(new GasLift.Valve(800, 6e6));
        double[] inj = {0.02, 0.05, 0.1, 0.2, 0.4, 0.8, 1.6};
        double best = 0;
        for (double g : inj) {
            GasLift.Point p = GasLift.operate(ipr, gor, wc, d, 64, SEP, g, one);
            assertThat(p.oilM3s()).as("lifts at %s nm3/s", g).isGreaterThan(0);
            assertThat(p.injDepthM()).isEqualTo(800);
            best = Math.max(best, p.oilM3s());
        }
        // кривая «дебит от закачки» имеет максимум: при большой закачке берёт верх трение газа
        assertThat(GasLift.operate(ipr, gor, wc, d, 64, SEP, 1.6, one).oilM3s()).isLessThan(best);
        assertThat(GasLift.operate(ipr, gor, wc, d, 32, SEP, 0, one)).isEqualTo(GasLift.Point.NONE);
    }

    @Test
    void unloadingValveWithTooLowAnOpeningPressureKeepsTheWellOnTheTopValve() {
        ProducingReservoir r = deadField();
        Inflow ipr = r.inflow();
        double gor = r.gasOilRatio(), wc = r.waterCut(), d = r.datumM();
        GasLift.Point stuck = GasLift.operate(ipr, gor, wc, d, 32, SEP, 0.3,
                List.of(new GasLift.Valve(300, 2e6), new GasLift.Valve(800, 6e6)));
        GasLift.Point good = GasLift.operate(ipr, gor, wc, d, 32, SEP, 0.3,
                List.of(new GasLift.Valve(300, 7.9e6), new GasLift.Valve(800, 6e6)));
        assertThat(stuck.stuckHigh()).isTrue();
        assertThat(stuck.injDepthM()).isEqualTo(300);
        assertThat(good.stuckHigh()).isFalse();
        assertThat(good.injDepthM()).isEqualTo(800);
        assertThat(good.oilM3s()).isGreaterThan(stuck.oilM3s());
        assertThat(good.casingSurfacePa()).isLessThanOrEqualTo(GasLift.KICKOFF_PA);
    }

    @Test
    void productionWellRunsOnGasLiftAndReturnsTheInjectedGas() {
        ProductionWell w = new ProductionWell(deadField());
        w.setChoke64(32);
        w.step(60, true, 0, SEP);
        assertThat(w.mode()).isEqualTo(ProductionWell.Mode.DEAD);
        w.setGasLiftValves(List.of(new GasLift.Valve(600, 6e6)));
        w.setGasInjectionStdM3s(0.3);
        w.step(60, true, 0, SEP);
        assertThat(w.mode()).isEqualTo(ProductionWell.Mode.GAS_LIFT);
        assertThat(w.oilM3s()).isGreaterThan(0);
        assertThat(w.gasStdM3s()).isGreaterThan(0.3);
        w.setGasInjectionStdM3s(0);
        w.step(60, true, 0, SEP);
        assertThat(w.mode()).isNotEqualTo(ProductionWell.Mode.GAS_LIFT);
    }

    @Test
    void valvesBelowThePackerAreIgnored() {
        assertThat(GasLift.usable(List.of(new GasLift.Valve(925, 6e6), new GasLift.Valve(500, 6e6)), 930)).hasSize(1);
    }
}
