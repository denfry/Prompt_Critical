package dev.denfry.promptcritical.sim.manufacturing;

import static dev.denfry.promptcritical.sim.manufacturing.ManufacturingFixtures.bar45;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.denfry.promptcritical.sim.metallurgy.HeatTreatState;
import dev.denfry.promptcritical.sim.metallurgy.SteelFamily;
import org.junit.jupiter.api.Test;

/** Ручная ступень MF2: окалина кусками, повторный отпуск, сыродутный горн, проковка крицы, id материала. */
class HandStageTest {

    @Test void scaleIsTheSameWhetherTheHeatIsSplitOrNot() {
        var w = bar45(0.040, 1273.15);
        double whole = ForgeHeating.heat(w, 1473.15, 1200).aM();
        var split = w;
        for (int i = 0; i < 1200; i++) split = ForgeHeating.heat(split, 1473.15, 1);
        assertThat(split.aM()).isCloseTo(whole, within(2e-6));
    }

    @Test void strikeAndQuenchKnockTheScaleOff() {
        var hot = ForgeHeating.heat(bar45(0.040, 1273.15), 1473.15, 600);
        assertThat(hot.oxideSq()).isPositive();
        assertThat(Forging.series(hot, Forging.Op.DRAW, 60, 8, 10, 5, 293.15).piece().oxideSq()).isZero();
        assertThat(Quenching.quench(hot, Quenching.Medium.WATER).piece().oxideSq()).isZero();
    }

    @Test void secondHotterTemperGoesFurtherAndColderDoesLittle() {
        var q = Quenching.quench(bar45(0.010, 1123.15), Quenching.Medium.WATER).piece();
        var low = Tempering.temper(q, 473.15, 3600);
        var thenHigh = Tempering.temper(low, 873.15, 3600);
        assertThat(thenHigh.hardnessHrc()).isLessThan(low.hardnessHrc() - 10);
        var high = Tempering.temper(q, 873.15, 3600);
        var thenLow = Tempering.temper(high, 473.15, 3600);
        assertThat(thenLow.hardnessHrc()).isCloseTo(high.hardnessHrc(), within(0.5));
        assertThat(thenLow.temperP()).isGreaterThanOrEqualTo(high.temperP());
    }

    @Test void temperingIsAdditiveInEquivalentTime() {
        var q = Quenching.quench(bar45(0.010, 1123.15), Quenching.Medium.WATER).piece();
        var once = Tempering.temper(q, 773.15, 7200);
        var twice = Tempering.temper(Tempering.temper(q, 773.15, 3600), 773.15, 3600);
        assertThat(twice.hardnessHrc()).isCloseTo(once.hardnessHrc(), within(1e-6));
    }

    @Test void reheatingAboveAc3ForgetsTheTreatment() {
        var q = Quenching.quench(bar45(0.010, 1123.15), Quenching.Medium.WATER).piece();
        var t = Tempering.temper(q, 473.15, 3600);
        var re = ForgeHeating.heat(t, 1473.15, 600);
        assertThat(re.quenchHrc()).isZero();
        assertThat(re.temperP()).isZero();
        assertThat(re.grade().state()).isEqualTo(HeatTreatState.NORMALIZED);
    }

    @Test void bloomeryMakesABloomWithSlag() {
        var b = Bloomery.charged(40, 0.6, 50);
        for (int i = 0; i < 3600 && !b.done(); i++) b = b.step(0.6, 1);
        assertThat(b.done()).isTrue();
        assertThat(b.castIron()).isFalse();
        assertThat(b.ironKg()).isCloseTo(40 * 0.6 * Bloomery.IRON_YIELD, within(0.5));
        var bloom = b.bloom();
        assertThat(bloom.grade().family()).isEqualTo(SteelFamily.WROUGHT_IRON);
        assertThat(bloom.severity(PartDefect.SLAG_INCLUSION)).isCloseTo(0.8, within(1e-9));
        assertThat(bloom.massKg()).isCloseTo(b.bloomKg(), within(1e-6));
    }

    @Test void coldBloomeryReducesNothing() {
        var b = Bloomery.charged(40, 0.6, 50);
        for (int i = 0; i < 600; i++) b = b.step(0, 1);   // без мехов горн 900 °C — ниже 1100 °C восстановления
        assertThat(b.ironKg()).isZero();
    }

    @Test void fullBlastForTooLongMakesCastIron() {
        var b = Bloomery.charged(40, 0.6, 500);
        for (int i = 0; i < 1200; i++) b = b.step(1, 1);
        assertThat(b.castIron()).isTrue();
    }

    @Test void bloomeryWithoutCoalCools() {
        var b = new Bloomery(10, 0.6, 0, 1400, 0, 0, false).step(1, 600);
        assertThat(b.tempK()).isLessThan(500);
    }

    @Test void whiteHeatStrikesSqueezeTheSlagOut() {
        var b = Bloomery.charged(40, 0.6, 50);
        for (int i = 0; i < 3600 && !b.done(); i++) b = b.step(0.6, 1);
        var bloom = b.bloom().withTemp(1523.15);
        var s = ForgingSession.start(bloom);
        int series = 0;
        while (s.piece().severity(PartDefect.SLAG_INCLUSION) > 0 && series < 100) {
            s = s.piece().tempK() < Forging.SLAG_SQUEEZE_MIN_K + 20 ? s.inFire(0.9, 120) : s.strike(Forging.Op.UPSET);
            series++;
        }
        assertThat(s.piece().severity(PartDefect.SLAG_INCLUSION)).isZero();
        assertThat(series).isLessThan(100);
    }

    @Test void coldStrikesLeaveTheSlag() {
        var bloom = Workpiece.bar(ManufacturingFixtures.wroughtIron(), Workpiece.Section.SQUARE, 0.1, 0.1, 0.1, 1273.15)
                .withDefect(PartDefect.SLAG_INCLUSION, 0.8);
        var r = Forging.series(bloom, Forging.Op.DRAW, 60, 8, 10, 5, 293.15);
        assertThat(r.piece().severity(PartDefect.SLAG_INCLUSION)).isEqualTo(0.8);
    }

    @Test void partMaterialIsTheFamilyId() {
        var p = ForgingSession.start(bar45(0.040, 293.15)).toPart("shaft");
        assertThat(p.material()).isEqualTo("medium_carbon");
    }
}
