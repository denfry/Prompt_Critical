package dev.denfry.promptcritical.sim.scada;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import dev.denfry.promptcritical.sim.scada.WatchdogTimer.State;
import org.junit.jupiter.api.Test;

/** Сторожевое реле 03c §3c.7.2–§3c.7.3: T-3c-10, T-3c-12 и перехват владения резервом. */
class WatchdogTimerTest {
    static final double DT = 0.05;

    @Test
    void t3c10TripsExactlyAtTheTimeoutAfterTheLastKick() {
        WatchdogTimer w = new WatchdogTimer();
        w.arm(1, 0);
        double trippedAt = Double.NaN;
        for (int k = 1; k <= 100 && Double.isNaN(trippedAt); k++) if (w.step(k * DT)) trippedAt = k * DT;
        assertThat(trippedAt).isCloseTo(2.0, within(DT + 1e-9));
        assertThat(w.state()).isEqualTo(State.TRIPPED);
        assertThat(w.trips()).isEqualTo(1);
    }

    @Test
    void kicksKeepItArmed() {
        WatchdogTimer w = new WatchdogTimer();
        w.arm(1, 0);
        for (int k = 1; k <= 400; k++) {
            double t = k * DT;
            if (k % 10 == 0) w.kick(1, t);
            assertThat(w.step(t)).isFalse();
        }
        assertThat(w.state()).isEqualTo(State.ARMED);
    }

    @Test
    void t3c12NonOwnerIsRefused() {
        WatchdogTimer w = new WatchdogTimer();
        w.arm(1, 0);
        assertThatThrownBy(() -> w.kick(2, 0.1)).hasMessageContaining("E_ACCESS: NOT_MASTER");
        assertThatThrownBy(() -> w.arm(2, 0.1)).hasMessageContaining("NOT_MASTER");
        assertThatThrownBy(() -> w.reset(2)).hasMessageContaining("NOT_MASTER");
    }

    @Test
    void standbyTakesOverOnlyAfterTheTrip() {
        WatchdogTimer w = new WatchdogTimer();
        w.arm(1, 0);
        assertThatThrownBy(() -> w.takeover(2)).hasMessageContaining("E_STATE");
        w.step(2.0);
        w.takeover(2);
        w.reset(2);
        w.arm(2, 2.05);
        w.kick(2, 2.3);
        assertThat(w.owner()).isEqualTo(2);
        assertThatThrownBy(() -> w.kick(1, 2.4)).hasMessageContaining("NOT_MASTER");
        // переключение: max(1,5 с, T_wd) + 1 тик ≈ 2,05 с ≤ 2,1 с (T-3c-11)
        assertThat(Math.max(1.5, w.timeoutS()) + DT).isLessThanOrEqualTo(2.1);
    }

    @Test
    void timeoutRangeIsChecked() {
        WatchdogTimer w = new WatchdogTimer();
        assertThatThrownBy(() -> w.configure(0.4)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> w.configure(61)).isInstanceOf(IllegalArgumentException.class);
        w.configure(10);
        w.arm(1, 0);
        assertThat(w.step(9.9)).isFalse();
        assertThat(w.step(10)).isTrue();
        assertThatThrownBy(() -> w.arm(1, 11)).hasMessageContaining("E_STATE");
    }
}
