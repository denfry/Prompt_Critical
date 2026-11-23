package dev.denfry.promptcritical.sim.grid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

/** Исход удара молнии для изоляции линии и заземлитель опоры (07 §7.15.1). */
class LightningStrokeTest {
    private final double cfo = Lightning.lineCfoKv(VoltageClass.MV_6_3);

    @Test
    void aStrokeToThePhaseWireAlwaysFlashesOverAndSendsAChoppedWave() {
        Lightning.Outcome o = Lightning.stroke(5, Lightning.Stroke.PHASE, cfo, Double.POSITIVE_INFINITY, 8, 0);
        assertThat(o.overvoltageKv()).isCloseTo(1000, within(1e-9)); // 5 кА · 400 Ом / 2
        assertThat(o.flashover()).isTrue();
        assertThat(o.surgeKv()).isEqualTo(cfo);
    }

    @Test
    void anEarthedPoleFlashesBackOnlyAboveCfoOverGroundResistance() {
        // R_з = 2 Ом, U₅₀% = 60 кВ: обратное перекрытие при I > 30 кА
        Lightning.Outcome low = Lightning.stroke(20, Lightning.Stroke.TOWER, cfo, 2, 8, 0);
        assertThat(low.flashover()).isFalse();
        assertThat(low.surgeKv()).isZero();
        assertThat(Lightning.stroke(40, Lightning.Stroke.TOWER, cfo, 2, 8, 0).flashover()).isTrue();
        // опора без заземлителя: ток уходит в провод
        assertThat(Lightning.stroke(1, Lightning.Stroke.TOWER, cfo, Double.POSITIVE_INFINITY, 8, 0).flashover()).isTrue();
    }

    @Test
    void aNearbyStrokeInducesAVoltageThatFallsWithDistance() {
        // 30·I·h/d: 31 кА, h = 8 м — на 50 м 149 кВ (перекрытие 6 кВ), на 500 м 15 кВ (волна без перекрытия)
        assertThat(Lightning.stroke(31, Lightning.Stroke.NEARBY, cfo, 0, 8, 50).flashover()).isTrue();
        Lightning.Outcome far = Lightning.stroke(31, Lightning.Stroke.NEARBY, cfo, 0, 8, 500);
        assertThat(far.flashover()).isFalse();
        assertThat(far.surgeKv()).isCloseTo(14.88, within(0.01));
    }

    @Test
    void oneRodInLoamIs33OhmsAndMoreRodsLowerIt() {
        assertThat(Lightning.electrodeOhm(100, 1)).isCloseTo(33.5, within(0.1)); // тест T7.18
        assertThat(Lightning.electrodeOhm(100, 3)).isCloseTo(33.5 / 2.4, within(0.1));
        assertThat(Lightning.electrodeOhm(100, 0)).isInfinite();
    }
}
