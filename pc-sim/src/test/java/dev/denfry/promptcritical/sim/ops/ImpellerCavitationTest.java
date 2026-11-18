package dev.denfry.promptcritical.sim.ops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.denfry.promptcritical.sim.core.MutableStateTree;
import dev.denfry.promptcritical.sim.core.TreeStateReader;
import dev.denfry.promptcritical.sim.core.TreeStateWriter;
import org.junit.jupiter.api.Test;

/** Кавитационная эрозия колеса (04a §4a.3.3): напор −25 % на износ, пробитое колесо не качает, замена лечит. */
class ImpellerCavitationTest {
    @Test
    void cavitationEatsTheImpellerUntilItIsHoled() {
        ImpellerWear w = new ImpellerWear();
        w.stepCavitation(100 * 3600, 1);
        assertThat(w.cavitation()).isCloseTo(0.5, within(1e-9));
        assertThat(w.headFactor(false)).isCloseTo(1 - 0.25 * 0.5, within(1e-9));
        assertThat(w.value()).isCloseTo(0.5, within(1e-9));
        w.stepCavitation(100 * 3600, 0);
        assertThat(w.cavitation()).isCloseTo(0.5, within(1e-9)); // тихо — колесо не ест
        w.stepCavitation(200 * 3600, 1);
        assertThat(w.holed()).isTrue();
        assertThat(w.headFactor(false)).isZero();
        w.replace();
        assertThat(w.cavitation()).isZero();
        assertThat(w.headFactor(false)).isEqualTo(1);
    }

    @Test
    void cavitationWearIsSaved() {
        ImpellerWear w = new ImpellerWear();
        w.setCavitation(0.3);
        MutableStateTree t = new MutableStateTree();
        w.save(new TreeStateWriter(t));
        ImpellerWear q = new ImpellerWear();
        q.load(new TreeStateReader(t));
        assertThat(q.cavitation()).isEqualTo(0.3);
    }
}
