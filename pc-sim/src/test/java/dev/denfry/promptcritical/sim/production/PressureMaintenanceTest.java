package dev.denfry.promptcritical.sim.production;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.denfry.promptcritical.sim.drilling.FormationFluid;
import dev.denfry.promptcritical.sim.drilling.Trap;
import org.junit.jupiter.api.Test;

/** ППД 09 §9.4.6: общая залежь, нагнетательная скважина, VRR, трещины и скин. */
class PressureMaintenanceTest {
    private static final Trap TRAP = new Trap(0, 0, 100, 900, 80, 960, 20, 0.22, 300, FormationFluid.OIL, 12_500);

    @Test
    void wellsOnOneTrapShareTheTank() {
        ProducingReservoir.State st = new ProducingReservoir.State();
        ProducingReservoir a = ProducingReservoir.perforate(TRAP, 900, st), b = ProducingReservoir.perforate(TRAP, 905, st);
        double p0 = a.pressurePa();
        a.produce(0.01, 86_400);
        assertThat(b.pressurePa()).isEqualTo(a.pressurePa()).isLessThan(p0);
        ProducingReservoir lone = ProducingReservoir.perforate(TRAP, 900);
        assertThat(lone.pressurePa()).isEqualTo(p0);
    }

    @Test
    void injectionRestoresPressureAndCountsVoidageReplacement() {
        ProducingReservoir.State st = new ProducingReservoir.State();
        ProducingReservoir r = ProducingReservoir.perforate(TRAP, 900, st);
        r.produce(0.01, 10 * 86_400);
        double depleted = r.pressurePa();
        double out = r.producedOilM3() * ProducingReservoir.BO + r.producedWaterM3() * ProducingReservoir.BW;
        r.inject(out / ProducingReservoir.BW / (10 * 86_400), 10 * 86_400, false);
        assertThat(r.voidageReplacementRatio()).isCloseTo(1, within(1e-9));
        assertThat(r.pressurePa()).isGreaterThan(depleted);
        // подвижные запасы растут с закачанным поровым объёмом, но не выше +0,10
        assertThat(r.movableFraction()).isGreaterThan(ProducingReservoir.MOVABLE_FRACTION)
                .isLessThanOrEqualTo(ProducingReservoir.MOVABLE_FRACTION + ProducingReservoir.PPD_EXTRA_FRACTION);
    }

    @Test
    void injectorFollowsDarcyAndFracturesAboveTheFracturePressure() {
        ProducingReservoir.State st = new ProducingReservoir.State();
        ProducingReservoir r = ProducingReservoir.perforate(TRAP, 900, st);
        ProductionWell inj = new ProductionWell(r);
        double rho = 1010, d = r.datumM(), pr = r.pressurePa();
        double whp = 3e6;
        double v = inj.inject(1, true, whp, rho, 2, 1e9);
        double expected = r.injectivity(ProducingReservoir.SKIN) * (whp + rho * Tubing.G * d - pr);
        assertThat(inj.mode()).isEqualTo(ProductionWell.Mode.INJECTING);
        assertThat(v).isCloseTo(expected, within(1e-3 * expected));
        assertThat(inj.fracturing()).isFalse();
        double fracWhp = r.fracturePa() - rho * Tubing.G * d + 1e6;
        double q0 = r.injectivity(ProducingReservoir.SKIN) * (fracWhp + rho * Tubing.G * d - r.pressurePa());
        double qFrac = inj.inject(1, true, fracWhp, rho, 2, 1e9);
        assertThat(inj.fracturing()).isTrue();
        assertThat(qFrac).as("fractures take five times the water").isCloseTo(5 * q0, within(1e-3 * q0));
        // 10 суток закачки с трещинами: обводнённость соседей +0,3
        double wc1 = r.waterCut();
        r.inject(1e-4, 10 * 86_400, true);
        assertThat(r.waterCut() - wc1).isCloseTo(0.3, within(0.01));
        // вода кончилась — закачка ограничена запасом
        assertThat(inj.inject(86_400, true, whp, rho, 2, 0.5)).isCloseTo(0.5, within(1e-9));
    }

    @Test
    void dirtyWaterPlugsTheInjector() {
        ProducingReservoir r = ProducingReservoir.perforate(TRAP, 900);
        ProductionWell clean = new ProductionWell(r), dirty = new ProductionWell(ProducingReservoir.perforate(TRAP, 900));
        for (int i = 0; i < 30; i++) {
            clean.inject(86_400, true, 3e6, 1010, 2, 1e9);
            dirty.inject(86_400, true, 3e6, 1030, 50, 1e9);
        }
        assertThat(clean.injectionSkin()).isEqualTo(ProducingReservoir.SKIN);
        assertThat(dirty.injectionSkin()).isCloseTo(ProducingReservoir.SKIN + 1e-7 * 45 * 30 * 86_400, within(0.5));
    }

    @Test
    void stateRoundTripsIncludingInjection() {
        ProducingReservoir r = ProducingReservoir.perforate(TRAP, 900);
        r.produce(0.01, 86_400);
        r.inject(0.005, 86_400, true);
        ProducingReservoir.State back = ProducingReservoir.State.restore(r.state().export());
        assertThat(back.pressurePa()).isEqualTo(r.pressurePa());
        assertThat(back.injectedM3()).isEqualTo(r.injectedM3());
        // старое сохранение скважины (5 значений) читается
        assertThat(ProducingReservoir.restore(TRAP, r.exportState()).pressurePa()).isEqualTo(r.pressurePa());
    }
}
