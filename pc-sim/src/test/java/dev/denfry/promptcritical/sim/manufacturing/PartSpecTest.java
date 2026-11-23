package dev.denfry.promptcritical.sim.manufacturing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.denfry.promptcritical.sim.metallurgy.HeatTreatState;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PartSpecTest {
    @Test void constructorSnapsToTheCanonicalForm() {
        var p = new PartSpec("shaft", "45", PartRoute.FORGED, HeatTreatState.NORMALIZED, 52, 0, 2.0, Map.of(), false);
        assertThat(p.hardnessHrc()).isEqualTo(50);
        assertThat(p.raUm()).isEqualTo(3.2);
    }

    @Test void quantization() {
        assertThat(PartSpec.quantizeHrc(52.4)).isEqualTo(50);
        assertThat(PartSpec.quantizeHrc(52.6)).isEqualTo(55);
        assertThat(PartSpec.quantizeHrc(-4)).isZero();
        assertThat(PartSpec.quantizeHrc(99)).isEqualTo(70);
        assertThat(PartSpec.quantizeRa(2.0)).isEqualTo(3.2);
        assertThat(PartSpec.quantizeRa(1.6)).isEqualTo(1.6);
        assertThat(PartSpec.quantizeRa(500)).isEqualTo(100);
        assertThat(PartSpec.defectLevel(0.04)).isZero();
        assertThat(PartSpec.defectLevel(0.30)).isEqualTo(1);
        assertThat(PartSpec.defectLevel(0.50)).isEqualTo(2);
        assertThat(PartSpec.defectLevel(1.00)).isEqualTo(3);
    }

    @Test void nearlyEqualPartsAreEqualAfterQuantization() {
        var a = PartSpec.of("shaft", "45", PartRoute.FORGED, HeatTreatState.QUENCHED_TEMPERED, 41.0, 0, 40, Map.of());
        var b = PartSpec.of("shaft", "45", PartRoute.FORGED, HeatTreatState.QUENCHED_TEMPERED, 39.0, 0, 33, Map.of());
        assertThat(a).isEqualTo(b);
    }

    @Test void crackedPartFailsTheGateButPorousOnePasses() {
        assertThat(PartSpec.of("shaft", "45", PartRoute.FORGED, HeatTreatState.NORMALIZED, 20, 0, 50,
                Map.of(PartDefect.CRACK, 0.3)).passesGate()).isFalse();
        assertThat(PartSpec.of("casing", "СЧ20", PartRoute.CAST, HeatTreatState.AS_ROLLED, 0, 0, 50,
                Map.of(PartDefect.POROSITY, 0.3)).passesGate()).isTrue();
    }

    @Test void inspectionDoesNotChangeValues() {
        var a = PartSpec.of("shaft", "45", PartRoute.FORGED, HeatTreatState.NORMALIZED, 20, 0, 50, Map.of());
        assertThat(a.inspect()).isEqualTo(new PartSpec(a.kind(), a.material(), a.route(), a.heatTreat(),
                a.hardnessHrc(), a.itGrade(), a.raUm(), a.defects(), true));
        assertThat(a.inspect().inspected()).isTrue();
    }

    @Test void defectsAreLevelsOnlyAndImmutable() {
        var p = PartSpec.of("shaft", "45", PartRoute.FORGED, HeatTreatState.NORMALIZED, 20, 0, 50,
                Map.of(PartDefect.POROSITY, 0.04, PartDefect.DECARB, 1.0));
        assertThat(p.defects()).containsExactlyEntriesOf(Map.of(PartDefect.DECARB, 3));
        assertThatThrownBy(() -> p.defects().put(PartDefect.CRACK, 1)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test void invalidInputsAreRejected() {
        var ht = HeatTreatState.NORMALIZED;
        assertThatThrownBy(() -> PartSpec.of("s", "45", PartRoute.CAST, ht, Double.NaN, 0, 50, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PartSpec.of("s", "45", PartRoute.CAST, ht, 20, 0, Double.POSITIVE_INFINITY, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PartSpec("s", "45", PartRoute.CAST, ht, 71, 0, 50, Map.of(), false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PartSpec("s", "45", PartRoute.CAST, ht, 20, -1, 50, Map.of(), false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PartSpec("s", "45", PartRoute.CAST, ht, 20, 0, 0, Map.of(), false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PartSpec("s", "45", PartRoute.CAST, ht, 20, 0, 50,
                Map.of(PartDefect.CRACK, 4), false)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PartSpec("s", "45", PartRoute.CAST, ht, 20, 0, 50,
                Map.of(PartDefect.CRACK, 0), false)).isInstanceOf(IllegalArgumentException.class);
    }
}
