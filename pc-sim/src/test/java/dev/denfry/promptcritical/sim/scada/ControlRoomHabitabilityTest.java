package dev.denfry.promptcritical.sim.scada;

import static org.assertj.core.api.Assertions.assertThat;

import dev.denfry.promptcritical.sim.scada.ControlRoomHabitability.Cause;
import dev.denfry.promptcritical.sim.scada.ControlRoomHabitability.State;
import org.junit.jupiter.api.Test;

/** Обитаемость БЩУ 03c §3c.9.1, тест-кейс T-3c-13. */
class ControlRoomHabitabilityTest {
    @Test
    void t3c13DoseThresholds() {
        assertThat(ControlRoomHabitability.assess(1e-9, Double.NaN, 0, Double.NaN).state()).isEqualTo(State.NORMAL);
        assertThat(ControlRoomHabitability.assess(2e-8, Double.NaN, 0, Double.NaN)).isEqualTo(new ControlRoomHabitability.Result(State.DEGRADED, Cause.DOSE));
        assertThat(ControlRoomHabitability.assess(3e-6, Double.NaN, 0, Double.NaN).state()).isEqualTo(State.UNINHABITABLE);
    }

    @Test
    void degradedThresholdIsFiftyMillisievertsOverThirtyDays() {
        assertThat(0.050 / (30 * 24 * 3600.0)).isBetween(1.92e-8, 1.94e-8);
    }

    @Test
    void worstParameterWins() {
        assertThat(ControlRoomHabitability.assess(2e-8, 335, 0, 0)).isEqualTo(new ControlRoomHabitability.Result(State.UNINHABITABLE, Cause.TEMPERATURE));
        assertThat(ControlRoomHabitability.assess(0, 300, 1, 0).cause()).isEqualTo(Cause.SMOKE);
        assertThat(ControlRoomHabitability.assess(0, 300, 4, 0).state()).isEqualTo(State.UNINHABITABLE);
        assertThat(ControlRoomHabitability.assess(0, 300, 0, 0.5).state()).isEqualTo(State.DEGRADED);
        assertThat(ControlRoomHabitability.assess(0, 300, 0, 1).state()).isEqualTo(State.UNINHABITABLE);
        assertThat(ControlRoomHabitability.assess(0, 324, 0, 0).state()).isEqualTo(State.DEGRADED);
    }
}
