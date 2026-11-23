package dev.denfry.promptcritical.sim.ops;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LicenceRulesTest {
    private static final UUID A = new UUID(0, 1);
    private static final Set<String> I0_KEYS = Set.of("promptcritical:i0/a", "promptcritical:i0/b");

    private static LicenceRegistry allI0Done() {
        LicenceRegistry reg = new LicenceRegistry();
        for (String k : I0_KEYS) reg.taskDone(A, k);
        for (UnitKind k : UnitKind.values()) if (k.era() == PlantEra.I0) reg.accepted(A, k);
        return reg;
    }

    @Test
    void i1NeedsI0KeyTasksAndAllI0KindsAccepted() {
        LicenceVerdict v = LicenceRules.evaluate(allI0Done(), A, PlantEra.I1, I0_KEYS);
        assertThat(v.granted()).isTrue();
        assertThat(v.era()).isEqualTo(PlantEra.I1);
        assertThat(v.missingEra()).isNull();
        assertThat(v.missingTasks()).isEmpty();
        assertThat(v.missingKinds()).isEmpty();
    }

    @Test
    void missingKindIsNamed() {
        LicenceRegistry reg = new LicenceRegistry();
        for (String k : I0_KEYS) reg.taskDone(A, k);
        for (UnitKind k : UnitKind.values()) if (k != UnitKind.MINE_HOIST) reg.accepted(A, k);
        LicenceVerdict v = LicenceRules.evaluate(reg, A, PlantEra.I1, I0_KEYS);
        assertThat(v.granted()).isFalse();
        assertThat(v.missingKinds()).containsExactly(UnitKind.MINE_HOIST);
    }

    @Test
    void missingTaskIsNamed() {
        LicenceRegistry reg = new LicenceRegistry();
        reg.taskDone(A, "promptcritical:i0/a");
        for (UnitKind k : UnitKind.values()) reg.accepted(A, k);
        LicenceVerdict v = LicenceRules.evaluate(reg, A, PlantEra.I1, I0_KEYS);
        assertThat(v.granted()).isFalse();
        assertThat(v.missingTasks()).containsExactly("promptcritical:i0/b");
    }

    @Test
    void previousEraRequired() {
        LicenceRegistry reg = allI0Done();
        reg.grant(A, PlantEra.I1, 0);
        LicenceVerdict v = LicenceRules.evaluate(reg, A, PlantEra.I3, Set.of());
        assertThat(v.granted()).isFalse();
        assertThat(v.missingEra()).isEqualTo(PlantEra.I2);
    }

    @Test
    void i0IsGrantedTrivially() {
        assertThat(LicenceRules.evaluate(new LicenceRegistry(), A, PlantEra.I0, I0_KEYS).granted()).isTrue();
    }
}
