package dev.denfry.promptcritical.sim.grid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

/** Диаграмма мощности синхронной машины (07 §7.3.4): пределы статора, ротора и ограничителя недовозбуждения. */
class MachineCapabilityTest {
    private final MachineParams tg = MachineParams.turbogenerator(100e6, 10.5e3);

    @Test
    void ratedPointLiesOnTheRotorAndStatorLimits() {
        // номинал cos φ = 0,8 (§7.3.1): ток возбуждения номинальный, Q = 0,6 о.е. — и предел ротора, и статора
        assertThat(tg.qMaxPu(0.8, 1)).isCloseTo(0.6, within(0.01));
    }

    @Test
    void atNoLoadTheRotorLimitsTheReactivePower() {
        // (V·E_q,max − V²)/x_d = (2,53 − 1)/1,8 ≈ 0,85 < 1 статора
        assertThat(tg.qMaxPu(0, 1)).isCloseTo(0.85, within(0.01));
    }

    @Test
    void underexcitationLimitRisesWithActivePower() {
        assertThat(tg.qMinPu(0, 1)).isCloseTo(-1 / 1.8, within(1e-9));
        assertThat(tg.qMinPu(0.8, 1)).isCloseTo(-1 / 1.8 + MachineParams.K_UEL * 0.8, within(1e-9));
        // при низком напряжении запас по недовозбуждению меньше: −V²/x_d
        assertThat(tg.qMinPu(0, 0.9)).isGreaterThan(tg.qMinPu(0, 1));
    }

    @Test
    void outsideTheStatorCircleThereIsNoOperatingPoint() {
        assertThat(tg.qMaxPu(1.1, 1)).isNaN();
        assertThat(tg.qMinPu(1.1, 1)).isNaN();
    }
}
