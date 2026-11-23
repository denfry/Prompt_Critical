package dev.denfry.promptcritical.sim.manufacturing;

import static dev.denfry.promptcritical.sim.manufacturing.ManufacturingFixtures.grade45;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import dev.denfry.promptcritical.sim.manufacturing.Workpiece.Section;
import org.junit.jupiter.api.Test;

class WorkpieceTest {
    @Test void roundBarGeometry() {
        var w = Workpiece.bar(grade45(), Section.ROUND, 0.040, 0.040, 0.300, 293.15);
        assertThat(w.areaM2()).isCloseTo(Math.PI * 0.02 * 0.02, within(1e-9));
        assertThat(w.massKg()).isCloseTo(7850 * Math.PI * 0.02 * 0.02 * 0.3, within(1e-6));
        assertThat(w.equivalentThicknessM()).isCloseTo(2 * w.volumeM3() / w.surfaceM2(), within(1e-12));
    }

    @Test void defectsClampAndDropZeros() {
        var w = Workpiece.bar(grade45(), Section.ROUND, 0.04, 0.04, 0.3, 293.15)
                .withDefect(PartDefect.CRACK, 0.7).withDefect(PartDefect.CRACK, 0.7).withDefect(PartDefect.BURNT, 0);
        assertThat(w.severity(PartDefect.CRACK)).isEqualTo(1.0);
        assertThat(w.defects()).doesNotContainKey(PartDefect.BURNT);
    }

    @Test void rejectsNonFiniteValues() {
        var g = grade45();
        assertThatThrownBy(() -> Workpiece.bar(g, Section.ROUND, Double.POSITIVE_INFINITY, 0.04, 0.3, 293.15))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Workpiece.bar(g, Section.ROUND, 0.04, 0.04, 0.3, Double.NaN))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Workpiece.bar(g, Section.ROUND, 0.04, 0.04, 0.3, 293.15).withHardness(Double.NaN))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void gateDefects() {
        assertThat(PartDefect.CRACK.gate).isTrue();
        assertThat(PartDefect.BURNT.gate).isTrue();
        assertThat(PartDefect.POROSITY.gate).isFalse();
    }

    @Test void heatColours() {
        assertThat(HeatColors.glow(1000 + 273.15)).isEqualTo(HeatColors.Glow.ORANGE);
        assertThat(HeatColors.glow(1100 + 273.15)).isEqualTo(HeatColors.Glow.YELLOW);
        assertThat(HeatColors.glow(500 + 273.15)).isEqualTo(HeatColors.Glow.NONE);
        assertThat(HeatColors.temper(295 + 273.15)).isEqualTo(HeatColors.Temper.BLUE);
        assertThat(HeatColors.temper(200 + 273.15)).isEqualTo(HeatColors.Temper.NONE);
    }

    @Test void equalContentRecordsAreEqual() {
        var a = Workpiece.bar(grade45(), Section.SQUARE, 0.05, 0.05, 0.3, 300).withDefect(PartDefect.POROSITY, 0.2);
        var b = Workpiece.bar(grade45(), Section.SQUARE, 0.05, 0.05, 0.3, 300).withDefect(PartDefect.POROSITY, 0.2);
        assertThat(a).isEqualTo(b);
    }

    @Test void rejectsNonPositiveGeometry() {
        assertThatThrownBy(() -> Workpiece.bar(grade45(), Section.ROUND, 0, 0, 0.3, 293.15))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
