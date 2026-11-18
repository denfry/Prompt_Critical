package dev.denfry.promptcritical.sim.refining;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class CatalystStateTest {

    /** §9.9.11: итоговая активность — произведение a_c·a_S·a_perm. */
    @Test
    void activityIsTheProduct() {
        CatalystState s = new CatalystState(0.8, 0.5, 0.9, 0.01, 0, 0, 0, 0, 10, CatalystState.Stage.ACTIVE);
        assertThat(s.activity()).isCloseTo(0.36, within(1e-12));
        assertThat(CatalystState.FRESH.activity()).isEqualTo(1);
    }

    /** Равновесный FCC: кокс на регенерированном 0,2 % — a_c = e⁻¹; ниже половины — отработанный. */
    @Test
    void fccEquilibrium() {
        CatalystState s = CatalystState.fccEquilibrium(0.7, 0.002, 100);
        assertThat(s.activityCoke()).isCloseTo(Math.exp(-1), within(1e-12));
        assertThat(s.state()).isEqualTo(CatalystState.Stage.SPENT);
        assertThat(CatalystState.fccEquilibrium(0.7, 0, 100).state()).isEqualTo(CatalystState.Stage.ACTIVE);
    }
}
