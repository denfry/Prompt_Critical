package dev.denfry.promptcritical.sim.ops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class FlareSystemTest {
    private static final FlareSystem.Gas GAS = new FlareSystem.Gas(1, 0, 0.75, 50e6, 0.3);

    /** §13.4.3: аварийный сброс 50 кг/с при LHV 50 МДж/кг — Q = 2,5 ГВт, граница 6,31 кВт/м² ≈ 89 м. */
    @Test
    void radiationExample() {
        FlareSystem f = FlareSystem.create();
        FlareSystem.FlareState s = f.step(50, 50 * 0.3, GAS, 3);
        assertThat(s.heatReleaseW() / s.combustionEfficiency()).isCloseTo(2.5e9, within(1e3));
        assertThat(FlareSystem.distanceForFlux(2.5e9, FlareSystem.K_ESCAPE_30S)).isCloseTo(89, within(0.5));
        assertThat(FlareSystem.radiantFluxWm2(2.5e9, 89)).isCloseTo(6310, within(50.0));
        assertThat(FlareSystem.damagePerSecond(7000)).isEqualTo(1);
        assertThat(FlareSystem.damagePerSecond(1500)).isZero();
    }

    /** Без пара — дым, с паром s_req — бездымно, переподача пара роняет полноту и срывает пламя. */
    @Test
    void steamControlsSmokeAndEfficiency() {
        FlareSystem f = FlareSystem.create();
        assertThat(f.step(1, 0, GAS, 3).smokeIndex()).isEqualTo(1);
        FlareSystem.FlareState ok = f.step(1, 0.3, GAS, 3);
        assertThat(ok.smokeIndex()).isZero();
        assertThat(ok.combustionEfficiency()).isEqualTo(FlareSystem.ETA_MAX);
        assertThat(FlareSystem.combustionEfficiency(1.5, 0.3)).isCloseTo(0.78, within(1e-9));
        FlareSystem.FlareState over = f.step(1, 2.5, GAS, 3);
        assertThat(over.flameOut()).isTrue();
        assertThat(over.ch4UnburntKgS()).isCloseTo(1, within(1e-9));
    }

    /** Без дежурных горелок газ уходит несгоревшим; CO₂ и SO₂ по составу. */
    @Test
    void pilotsAndEmissions() {
        FlareSystem f = FlareSystem.create();
        FlareSystem.FlareState out = f.step(2, 0.6, FlareSystem.Gas.ASSOCIATED, 0);
        assertThat(out.flameOut()).isTrue();
        assertThat(out.heatReleaseW()).isZero();
        assertThat(out.ch4UnburntKgS()).isCloseTo(2 * 0.80, within(1e-9));
        FlareSystem.FlareState lit = f.step(2, 0.6, FlareSystem.Gas.ASSOCIATED, 1);
        assertThat(lit.co2KgS()).isCloseTo(0.98 * 2 * 0.72 * 44 / 12, within(1e-9));
        assertThat(lit.so2KgS()).isCloseTo(0.98 * 2 * 0.04 * 64 / 34, within(1e-9));
        assertThat(f.step(0, 0, GAS, 3)).isEqualTo(FlareSystem.FlareState.IDLE);
    }
}
