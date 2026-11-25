package dev.denfry.promptcritical.sim.manufacturing;

import static org.assertj.core.api.Assertions.assertThat;

import dev.denfry.promptcritical.sim.manufacturing.PartInspection.Check;
import dev.denfry.promptcritical.sim.manufacturing.PartInspection.Tool;
import dev.denfry.promptcritical.sim.metallurgy.HeatTreatState;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Контроль детали (18 §18.6.10): какие замеры нужны и каким инструментом. */
class PartInspectionTest {
    private static PartSpec part(PartRoute route, HeatTreatState ht, int it) {
        return PartSpec.of("shaft", "medium_carbon", route, ht, 40, it, 3.2, Map.of());
    }

    @Test
    void forgedUntreatedShaftNeedsOnlyItsSize() {
        PartSpec p = part(PartRoute.FORGED, HeatTreatState.AS_ROLLED, 0);
        assertThat(PartInspection.required(p)).containsExactly(Check.DIMENSION);
        assertThat(PartInspection.measure(Tool.HARDNESS_TESTER, p)).isNull();
        assertThat(PartInspection.measure(Tool.RADIOGRAPHY, p)).isNull();
        int m = PartInspection.record(0, PartInspection.measure(Tool.CALIPER, p));
        assertThat(PartInspection.complete(p, m)).isTrue();
    }

    @Test
    void castQuenchedPartNeedsSizeHardnessAndRadiography() {
        PartSpec p = part(PartRoute.CAST, HeatTreatState.QUENCHED_TEMPERED, 12);
        assertThat(PartInspection.required(p)).containsExactlyInAnyOrder(Check.DIMENSION, Check.HARDNESS, Check.INTERNAL);
        int m = PartInspection.record(0, Check.DIMENSION);
        m = PartInspection.record(m, Check.HARDNESS);
        assertThat(PartInspection.complete(p, m)).isFalse();
        m = PartInspection.record(m, PartInspection.measure(Tool.RADIOGRAPHY, p));
        assertThat(PartInspection.complete(p, m)).isTrue();
    }

    @Test
    void caliperCannotProveAFineFitButAMicrometerCan() {
        PartSpec fine = part(PartRoute.ROLLED, HeatTreatState.AS_ROLLED, 7);
        assertThat(PartInspection.measure(Tool.CALIPER, fine)).isNull();
        assertThat(PartInspection.measure(Tool.MICROMETER, fine)).isEqualTo(Check.DIMENSION);
        assertThat(PartInspection.measure(Tool.CALIPER, part(PartRoute.ROLLED, HeatTreatState.AS_ROLLED, 12)))
                .isEqualTo(Check.DIMENSION);
        assertThat(PartInspection.measure(Tool.MICROMETER, part(PartRoute.ROLLED, HeatTreatState.AS_ROLLED, 5))).isNull();
    }
}
