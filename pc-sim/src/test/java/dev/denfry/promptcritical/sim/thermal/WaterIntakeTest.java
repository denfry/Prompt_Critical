package dev.denfry.promptcritical.sim.thermal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class WaterIntakeTest {

    /** 07 §7.13.4: река сечением 30 м² при 0,5 м/с — 15 м³/с, забор не больше половины; океан — без предела. */
    @Test
    void bodyLimits() {
        assertThat(WaterIntake.bodyLimitM3s(WaterIntake.Body.RIVER, 30, 0)).isCloseTo(7.5, within(1e-9));
        assertThat(WaterIntake.bodyLimitM3s(WaterIntake.Body.OCEAN, 0, 0)).isInfinite();
        assertThat(WaterIntake.bodyLimitM3s(WaterIntake.Body.LAKE, 0, 864_000)).isCloseTo(1, within(1e-9));
        assertThat(new WaterIntake(0).capacityM3s(WaterIntake.Body.OCEAN, 0, 0)).isEqualTo(WaterIntake.SCREEN_M3S);
        assertThat(new WaterIntake(0).capacityM3s(WaterIntake.Body.RIVER, 0.4, 0)).isCloseTo(0.1, within(1e-9));
    }

    /** Засор: за θ при 25 °C — 63 %, в тёплой воде быстрее; стоящий водозабор не засоряется; очистка — с нуля. */
    @Test
    void screenClogging() {
        WaterIntake w = new WaterIntake(0);
        w.step(WaterIntake.SCREEN_THETA0_S, 25, true);
        assertThat(w.clog()).isCloseTo(1 - Math.exp(-1), within(1e-9));
        assertThat(w.capacityM3s(WaterIntake.Body.OCEAN, 0, 0)).isCloseTo(WaterIntake.SCREEN_M3S * (1 - 0.9 * (1 - Math.exp(-1))), within(1e-9));
        assertThat(WaterIntake.theta(35)).isCloseTo(WaterIntake.SCREEN_THETA0_S / 2, within(1e-6));
        double c = w.clog();
        w.step(1e6, 25, false);
        assertThat(w.clog()).isEqualTo(c);
        w.clean();
        assertThat(w.clog()).isZero();
    }
}
