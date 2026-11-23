package dev.denfry.promptcritical.sim.manufacturing;

import static dev.denfry.promptcritical.sim.manufacturing.ManufacturingFixtures.bar45;
import static dev.denfry.promptcritical.sim.manufacturing.ManufacturingFixtures.grade45;
import static dev.denfry.promptcritical.sim.manufacturing.ManufacturingFixtures.gradeShKh15;
import static dev.denfry.promptcritical.sim.manufacturing.ManufacturingFixtures.wroughtIron;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import dev.denfry.promptcritical.sim.metallurgy.SteelFamily;
import org.junit.jupiter.api.Test;

class ForgingTest {
    @Test void flowStressOf45AtForgingHeats() {        // [сверить] Полухин 1983
        var g = grade45();
        assertThat(Forging.flowStressPa(g, 1273.15, 1)).isCloseTo(110e6, within(1e3));
        assertThat(Forging.flowStressPa(g, 1473.15, 1)).isBetween(45e6, 65e6);
        assertThat(Forging.flowStressPa(g, 1073.15, 1)).isBetween(190e6, 250e6);
    }

    @Test void flowStressReferenceByFamily() {
        assertThat(Forging.flowStressPa(wroughtIron(), 1273.15, 1)).isCloseTo(70e6, within(1e3));
        assertThat(Forging.flowStressPa(gradeShKh15(), 1273.15, 1)).isCloseTo(140e6, within(1e3));
    }

    @Test void rangeByFamily() {
        assertThat(Forging.range(SteelFamily.CARBON)).isEqualTo(new Forging.Range(1573, 1023));
        assertThat(Forging.range(SteelFamily.BEARING)).isEqualTo(new Forging.Range(1423, 1073));
        assertThat(Forging.range(SteelFamily.WROUGHT_IRON)).isEqualTo(new Forging.Range(1573, 1073));
        assertThat(Forging.range(SteelFamily.INCONEL)).isEqualTo(new Forging.Range(1473, 1123));
    }

    @Test void drawConservesVolumeAndThinsTheBar() {
        var w = bar45(0.040, 1473.15);
        var r = Forging.series(w, Forging.Op.DRAW, 60, 8, 10, 5, 293.15);
        assertThat(r.piece().volumeM3()).isCloseTo(w.volumeM3(), within(1e-12));
        assertThat(r.piece().aM()).isLessThan(w.aM());
        assertThat(r.events()).containsExactly(ForgeEvent.OK);
    }

    @Test void strainFollowsEnergyBalance() {
        var w = bar45(0.040, 1273.15);
        var r = Forging.series(w, Forging.Op.DRAW, 60, 8, 1, 0, 293.15);
        double de = 0.7 * 60 * 8 / (110e6 * w.volumeM3());
        assertThat(r.piece().areaM2()).isCloseTo(w.areaM2() * Math.exp(-de), within(1e-12));
    }

    @Test void longBarBucklesOnUpset() {
        var r = Forging.series(bar45(0.020, 1473.15), Forging.Op.UPSET, 60, 8, 10, 5, 293.15); // L/D = 15
        assertThat(r.events()).contains(ForgeEvent.BUCKLED);
        assertThat(r.piece().severity(PartDefect.BUCKLED)).isEqualTo(0.5);
        assertThat(r.piece().aM()).isEqualTo(0.020);
    }

    @Test void hugeUpsetEnergyIsTooThinAndKeepsGeometry() {
        var w = Workpiece.bar(grade45(), Workpiece.Section.ROUND, 0.040, 0.040, 0.080, 1473.15);
        var r = Forging.series(w, Forging.Op.UPSET, 1e12, 8, 10, 5, 293.15);
        assertThat(r.events()).containsExactly(ForgeEvent.TOO_THIN);
        assertThat(r.piece().lengthM()).isEqualTo(w.lengthM());
        assertThat(r.piece().aM()).isEqualTo(w.aM());
        assertThat(r.piece().tempK()).isLessThan(w.tempK());
    }

    @Test void shortBarUpsetsShorterAndFatter() {
        var w = Workpiece.bar(grade45(), Workpiece.Section.ROUND, 0.040, 0.040, 0.080, 1473.15);
        var r = Forging.series(w, Forging.Op.UPSET, 200, 8, 10, 0, 293.15);
        assertThat(r.events()).containsExactly(ForgeEvent.OK);
        assertThat(r.piece().lengthM()).isLessThan(w.lengthM());
        assertThat(r.piece().aM()).isGreaterThan(w.aM());
        assertThat(r.piece().bM()).isEqualTo(r.piece().aM());
        assertThat(r.piece().volumeM3()).isCloseTo(w.volumeM3(), within(1e-12));
    }

    @Test void flatKeepsAspectRatioOnDrawAndUpset() {
        var w = Workpiece.bar(grade45(), Workpiece.Section.FLAT, 0.010, 0.040, 0.020, 1473.15);
        for (var op : new Forging.Op[] {Forging.Op.DRAW, Forging.Op.UPSET}) {
            var p = Forging.series(w, op, 100, 8, 10, 0, 293.15).piece();
            assertThat(p.bM() / p.aM()).isCloseTo(4.0, within(1e-9));
            assertThat(p.volumeM3()).isCloseTo(w.volumeM3(), within(1e-12));
        }
    }

    @Test void bendLeavesGeometryAlone() {
        var w = bar45(0.040, 1473.15);
        var r = Forging.series(w, Forging.Op.BEND, 60, 8, 10, 0, 293.15);
        assertThat(r.piece().aM()).isEqualTo(w.aM());
        assertThat(r.piece().lengthM()).isEqualTo(w.lengthM());
        assertThat(r.events()).containsExactly(ForgeEvent.OK);
    }

    @Test void coldStrikeCracksAndStaysFinite() {      // Review Focus 3
        var r = Forging.series(bar45(0.040, 293.15), Forging.Op.DRAW, 60, 8, 10, 5, 293.15);
        assertThat(r.events()).contains(ForgeEvent.CRACKED);
        assertThat(r.piece().severity(PartDefect.CRACK)).isEqualTo(0.6);
        assertThat(r.piece().aM()).isPositive().isLessThanOrEqualTo(0.040);
    }

    @Test void slightlyColdStrikeCracksLess() {
        var r = Forging.series(bar45(0.040, 1023.15), Forging.Op.DRAW, 60, 8, 10, 0, 293.15);
        assertThat(r.events()).containsExactly(ForgeEvent.CRACKED);
        assertThat(r.piece().severity(PartDefect.CRACK)).isEqualTo(0.25);
    }

    @Test void overDrawnBarStopsAtMinimumSection() {   // Review Focus 2
        var w = Workpiece.bar(grade45(), Workpiece.Section.ROUND, 0.0031, 0.0031, 0.3, 1473.15);
        var r = Forging.series(w, Forging.Op.DRAW, 1000, 8, 10, 0, 293.15);
        assertThat(r.events()).containsExactly(ForgeEvent.TOO_THIN);
        assertThat(r.piece().aM()).isEqualTo(w.aM());
    }

    @Test void rejectsNonPositiveStrainRate() {
        assertThatThrownBy(() -> Forging.series(bar45(0.04, 1473.15), Forging.Op.DRAW, 60, 8, 0, 0, 293.15))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
