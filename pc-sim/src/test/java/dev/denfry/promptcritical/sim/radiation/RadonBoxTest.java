package dev.denfry.promptcritical.sim.radiation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import org.junit.jupiter.api.Test;

/** Радоновый бокс: примеры 04b §4b.13.3 и 02 §2.1.6, устойчивость точного шага, защита от неверного ввода. */
class RadonBoxTest {
    @Test void houseExampleOf04b() {   // 100 м³, 40 м² грунтового пола, 0,5 обмена в час → 45 Бк/м³, 0,16 мкЗв/ч
        double q = 0.5 * 100 / 3600;
        double c = RadonBox.steady(0.016 * 40, 100, q);
        assertThat(c).isCloseTo(45, within(1.0));
        assertThat(RadonBox.doseUSvH(c, RadonBox.Setting.BUILDING)).isCloseTo(0.16, within(0.01));
    }

    @Test void driftExampleOf02() {    // 02 §2.1.6: 90 м³, 6 м² руды 1 %, Q 1 м³/с → 672 Бк/м³
        double j = RadonBox.oreExhalation(1.047e5);
        assertThat(j).isCloseTo(112, within(1.0));
        assertThat(RadonBox.steady(j * 6, 90, 1.0)).isCloseTo(672, within(5.0));
    }

    @Test void stepConvergesToSteadyAndIsStableForHugeSteps() {
        double c = 0;
        for (int i = 0; i < 10_000; i++) c = RadonBox.step(c, 0.64, 100, 0.0139, 10);
        assertThat(c).isCloseTo(RadonBox.steady(0.64, 100, 0.0139), within(0.5));
        assertThat(RadonBox.step(500, 0, 100, 0.0139, 1e9)).isCloseTo(0, within(1e-9));
    }

    @Test void stepMatchesSteadyAtHugeDtFromAnyStart() {
        double steady = RadonBox.steady(0.64, 100, 0.0139);
        assertThat(RadonBox.step(0, 0.64, 100, 0.0139, 1e12)).isCloseTo(steady, within(1e-9));
        assertThat(RadonBox.step(1e6, 0.64, 100, 0.0139, 1e12)).isCloseTo(steady, within(1e-9));
    }

    @Test void stepWithoutVentilationDecaysByRadonHalfLife() {
        double halfLife = Math.log(2) / RadonBox.LAMBDA_RN;
        assertThat(RadonBox.step(100, 0, 50, 0, halfLife)).isCloseTo(50, within(1e-9));
    }

    @Test void zeroDtKeepsConcentration() {
        assertThat(RadonBox.step(123, 1, 10, 0.1, 0)).isEqualTo(123);
    }

    @Test void surfacesAndSettingsCarryTheirTableValues() {
        assertThat(RadonBox.Surface.SOIL.bqM2S).isEqualTo(0.016);
        assertThat(RadonBox.Surface.GRANITE.bqM2S).isEqualTo(0.01);
        assertThat(RadonBox.Surface.NONE.bqM2S).isZero();
        assertThat(RadonBox.Setting.MINE_VENTILATED.equilibrium).isEqualTo(0.3);
        assertThat(RadonBox.Setting.MINE_STILL.equilibrium).isEqualTo(0.6);
    }

    @Test void invalidInputIsRejected() {
        assertThatThrownBy(() -> RadonBox.step(0, 1, 0, 0, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RadonBox.step(0, 1, -5, 0, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RadonBox.step(0, 1, 10, 0, -1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RadonBox.step(Double.NaN, 1, 10, 0, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RadonBox.step(0, 1, 10, Double.POSITIVE_INFINITY, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RadonBox.steady(1, 0, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RadonBox.steady(1, 10, Double.NaN)).isInstanceOf(IllegalArgumentException.class);
    }
}
