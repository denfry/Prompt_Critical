package dev.denfry.promptcritical.sim.refining;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class DelayedCokingTest {

    /** Gary &amp; Handwerk, табл. 5.3: CCR 20 % → кокс 32 %, газ 10,68 %, бензин 18,15 %, газойль — остаток. */
    @Test
    void yieldsFollowConradsonCorrelation() {
        DelayedCoking.Yields y = DelayedCoking.yields(0.20, 0.03, 500);
        assertThat(y.coke()).isCloseTo(0.32, within(1e-9));
        assertThat(y.gas()).isCloseTo(0.1068, within(1e-9));
        assertThat(y.naphtha()).isCloseTo(0.1815, within(1e-9));
        assertThat(y.coke() + y.gas() + y.naphtha() + y.gasOil()).isCloseTo(1, within(1e-12));
        assertThat(y.gasOil()).isBetween(0.38, 0.40);
        assertThat(y.softCoke()).isFalse();
        // 30 % серы в H₂S: 0,03 · 0,3 · 34,08/32,06
        assertThat(y.h2sKgPerKg()).isCloseTo(0.00957, within(1e-4));
    }

    /** Холодная печь — мягкий кокс за счёт жидких; горячая — вдвое быстрее коксуются трубы на каждые 10 K. */
    @Test
    void coilTemperatureWindow() {
        DelayedCoking.Yields cold = DelayedCoking.yields(0.20, 0, 485);
        assertThat(cold.softCoke()).isTrue();
        assertThat(cold.coke()).isCloseTo(0.38, within(1e-9));
        assertThat(DelayedCoking.tubeCokingFactor(500)).isEqualTo(1);
        assertThat(DelayedCoking.tubeCokingFactor(515)).isCloseTo(2, within(1e-12));
    }

    @Test
    void drumCycle() {
        assertThat(DelayedCoking.cycleHours()).isEqualTo(28);
        assertThat(DelayedCoking.phaseAt(10)).isEqualTo(DelayedCoking.Phase.FILL);
        assertThat(DelayedCoking.phaseAt(17)).isEqualTo(DelayedCoking.Phase.STEAM);
        assertThat(DelayedCoking.phaseAt(20)).isEqualTo(DelayedCoking.Phase.QUENCH);
        assertThat(DelayedCoking.phaseAt(25)).isEqualTo(DelayedCoking.Phase.DECOKE);
        assertThat(DelayedCoking.phaseAt(30)).isEqualTo(DelayedCoking.Phase.FILL);
        // 20 кг/с гудрона CCR 20 % за 16 ч: ~433 м³ кокса — камера Ø 8 м × 9 м
        double v = DelayedCoking.cokeVolumePerFill(20, DelayedCoking.yields(0.20, 0, 500));
        assertThat(v).isBetween(400.0, 470.0);
    }
}
