package dev.denfry.promptcritical.sim.manufacturing;

import static dev.denfry.promptcritical.sim.manufacturing.ManufacturingFixtures.bar45;
import static dev.denfry.promptcritical.sim.manufacturing.ManufacturingFixtures.gradeShKh15;
import static dev.denfry.promptcritical.sim.manufacturing.ManufacturingFixtures.wroughtIron;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.denfry.promptcritical.sim.metallurgy.HeatTreatState;
import dev.denfry.promptcritical.sim.metallurgy.SteelFamily;
import org.junit.jupiter.api.Test;

class HeatTreatTest {
    @Test void waterQuenchOfThinSteel45Hardens() {
        var r = Quenching.quench(bar45(0.012, 1123.15), Quenching.Medium.WATER);
        assertThat(r.piece().hardnessHrc()).isCloseTo(20 + 60 * Math.sqrt(0.45), within(0.5));
        assertThat(r.piece().grade().state()).isEqualTo(HeatTreatState.QUENCHED);
        assertThat(r.piece().tempK()).isEqualTo(293.15);
        assertThat(r.events()).containsExactly(ForgeEvent.OK);
    }

    @Test void thickSteel45IsNotThroughHardenedInOil() {
        var r = Quenching.quench(bar45(0.040, 1123.15), Quenching.Medium.OIL);
        assertThat(r.events()).contains(ForgeEvent.NOT_THROUGH_HARDENED);
        double hq = 20 + 60 * Math.sqrt(0.45);
        assertThat(r.piece().hardnessHrc()).isLessThan(hq).isGreaterThan(bar45(0.04, 293.15).hardnessHrc());
    }

    @Test void coldQuenchChangesNothing() {
        var w = bar45(0.012, 293.15);
        var r = Quenching.quench(w, Quenching.Medium.WATER);
        assertThat(r.events()).containsExactly(ForgeEvent.NOT_HARDENED);
        assertThat(r.piece().hardnessHrc()).isEqualTo(w.hardnessHrc());
    }

    @Test void wroughtIronDoesNotHarden() {
        var iron = Workpiece.bar(wroughtIron(), Workpiece.Section.ROUND, 0.012, 0.012, 0.3, 1223.15);
        var r = Quenching.quench(iron, Quenching.Medium.WATER);
        assertThat(r.events()).containsExactly(ForgeEvent.NOT_HARDENED);
        assertThat(r.piece().hardnessHrc()).isEqualTo(iron.hardnessHrc());
    }

    @Test void airDoesNotHardenAndCoolsSlowly() {
        var w = bar45(0.012, 1123.15);
        var r = Quenching.quench(w, Quenching.Medium.AIR);
        assertThat(r.events()).containsExactly(ForgeEvent.NOT_HARDENED);
        assertThat(r.piece().tempK()).isLessThan(w.tempK()).isGreaterThan(293.15);
    }

    @Test void waterCracksBearingSteel() {
        var r = Quenching.quench(Workpiece.bar(gradeShKh15(), Workpiece.Section.ROUND, 0.02, 0.02, 0.2, 1123.15),
                Quenching.Medium.WATER);
        assertThat(r.events()).contains(ForgeEvent.QUENCH_CRACKED).doesNotContain(ForgeEvent.OK);
        assertThat(r.piece().severity(PartDefect.CRACK)).isEqualTo(0.7);
    }

    @Test void oilDoesNotCrackBearingSteel() {
        var r = Quenching.quench(Workpiece.bar(gradeShKh15(), Workpiece.Section.ROUND, 0.02, 0.02, 0.2, 1123.15),
                Quenching.Medium.OIL);
        assertThat(r.events()).containsExactly(ForgeEvent.OK);
    }

    @Test void criticalDiametersPerFamily() {
        assertThat(Quenching.critical(SteelFamily.CARBON)).isEqualTo(new Quenching.Critical(0, 0));
        assertThat(Quenching.critical(SteelFamily.WROUGHT_IRON)).isEqualTo(new Quenching.Critical(0, 0));
        assertThat(Quenching.critical(SteelFamily.LOW_ALLOY)).isEqualTo(new Quenching.Critical(0.010, 0.004));
        assertThat(Quenching.critical(SteelFamily.STAINLESS_304)).isEqualTo(new Quenching.Critical(0, 0));
        assertThat(Quenching.critical(SteelFamily.TOOL_HSS)).isEqualTo(new Quenching.Critical(0, 0));
    }

    @Test void temperingFollowsHollomonJaffe() {
        var q = Quenching.quench(bar45(0.012, 1123.15), Quenching.Medium.WATER).piece();
        assertThat(Tempering.temper(q, 473.15, 3600).hardnessHrc()).isBetween(52.0, 60.0);
        assertThat(Tempering.temper(q, 923.15, 3600).hardnessHrc()).isBetween(15.0, 26.0);
        assertThat(Tempering.temper(q, 923.15, 3600).grade().state()).isEqualTo(HeatTreatState.QUENCHED_TEMPERED);
    }

    @Test void temperOfUnquenchedIsIdentity() {
        var w = bar45(0.012, 293.15);
        assertThat(Tempering.temper(w, 573.15, 3600)).isEqualTo(w);
    }

    @Test void temperWithBadTimeIsIdentity() {
        var q = Quenching.quench(bar45(0.012, 1123.15), Quenching.Medium.WATER).piece();
        assertThat(Tempering.temper(q, 573.15, 0)).isEqualTo(q);
        assertThat(Tempering.temper(q, 573.15, Double.NaN)).isEqualTo(q);
        assertThat(Tempering.temper(q, 573.15, -5)).isEqualTo(q);
    }
}
