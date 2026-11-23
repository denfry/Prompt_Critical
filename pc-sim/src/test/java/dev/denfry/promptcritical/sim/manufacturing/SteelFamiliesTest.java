package dev.denfry.promptcritical.sim.manufacturing;

import static org.assertj.core.api.Assertions.assertThat;

import dev.denfry.promptcritical.sim.metallurgy.AlloyElement;
import dev.denfry.promptcritical.sim.metallurgy.HeatTreatState;
import dev.denfry.promptcritical.sim.metallurgy.SteelFamily;
import dev.denfry.promptcritical.sim.metallurgy.SteelGrade;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SteelFamiliesTest {
    @Test
    void newFamiliesAreAppendedAfterRailWire() {
        assertThat(SteelFamily.MEDIUM_CARBON.ordinal()).isEqualTo(SteelFamily.RAIL_WIRE.ordinal() + 1);
        assertThat(SteelFamily.WROUGHT_IRON.ordinal()).isEqualTo(SteelFamily.values().length - 1);
    }

    @Test
    void melt45ClassifiesAsMediumCarbon() {
        var g = SteelGrade.classify(Map.of(AlloyElement.C, 0.0045, AlloyElement.MN, 0.0065), HeatTreatState.NORMALIZED);
        assertThat(g.family()).isEqualTo(SteelFamily.MEDIUM_CARBON);
    }

    @Test
    void bearingMeltClassifiesAsShKh15() {
        var g = SteelGrade.classify(Map.of(AlloyElement.C, 0.010, AlloyElement.CR, 0.015), HeatTreatState.AS_ROLLED);
        assertThat(g.family()).isEqualTo(SteelFamily.BEARING);
    }

    @Test
    void lowCarbonMeltStaysCarbon() {
        var g = SteelGrade.classify(Map.of(AlloyElement.C, 0.0018), HeatTreatState.AS_ROLLED);
        assertThat(g.family()).isEqualTo(SteelFamily.CARBON);
    }
}
