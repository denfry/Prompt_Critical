package dev.denfry.promptcritical.sim.manufacturing;

import static org.assertj.core.api.Assertions.assertThat;

import dev.denfry.promptcritical.sim.metallurgy.HeatTreatState;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Опиловка кованого вала (18 §18.7, §18.15 п. 4). */
class HandFilingTest {
    private static PartSpec forged(int hrc) {
        return PartSpec.of("shaft", "medium_carbon", PartRoute.FORGED, HeatTreatState.NORMALIZED, hrc, 0, 25,
                Map.of(PartDefect.DECARB, 0.2)).inspect();
    }

    @Test
    void filingGivesIt13AndAFinerSurfaceAndClearsTheInspection() {
        PartSpec f = HandFiling.file(forged(20));
        assertThat(f.itGrade()).isEqualTo(13);
        assertThat(f.raUm()).isEqualTo(6.3);
        assertThat(f.inspected()).isFalse();
        assertThat(f.defects()).isEqualTo(forged(20).defects());
    }

    @Test
    void hardenedOrAlreadyTurnedPartsCannotBeFiled() {
        assertThat(HandFiling.canFile(forged(50))).isFalse();
        PartSpec turned = new PartSpec("shaft", "medium_carbon", PartRoute.FORGED, HeatTreatState.NORMALIZED, 20, 9, 1.6,
                Map.of(), false);
        assertThat(HandFiling.canFile(turned)).isFalse();
        assertThat(HandFiling.canFile(HandFiling.file(forged(20)))).isFalse();
    }
}
