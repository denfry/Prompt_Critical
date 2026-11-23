package dev.denfry.promptcritical.sim.manufacturing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import dev.denfry.promptcritical.sim.metallurgy.AlloyElement;
import dev.denfry.promptcritical.sim.metallurgy.HeatTreatState;
import dev.denfry.promptcritical.sim.metallurgy.SteelFamily;
import dev.denfry.promptcritical.sim.metallurgy.SteelGrade;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** MF4: электротехническая сталь, потери по Штейнмецу, вырубка, шихтовка, обмоточный провод, обмотка статора. */
class ElectricalStageTest {
    private static final LaminationShape STATOR = LaminationShape.STATOR_112;

    private static ElectricalSheet annealed3Si() {
        return new ElectricalSheet(0.0005, 0.03, 0, SheetCoating.NONE);
    }

    /** Хорошая технология: отожжённый лист, вырубка, отжиг пластин, лак после вырубки. */
    private static CoreStack goodCore() {
        var p = Punching.punch(annealed3Si(), STATOR, Punching.CLEARANCE_OPT, 0, 250e3);
        Lamination l = p.lamination().anneal().coat(SheetCoating.PHENOLIC);
        return new CoreStack(l, CoreStack.countFor(l, Winding.STACK_LENGTH_M));
    }

    private static Wire annealedCopper(double dM) {
        return new Wire(Wire.Metal.COPPER, dM, 300, 0);
    }

    @Test void siliconMeltClassifiesAs2212AndHighCarbonDoesNot() {
        var e = SteelGrade.classify(Map.of(AlloyElement.SI, 0.03, AlloyElement.C, 0.0004), HeatTreatState.AS_ROLLED);
        assertThat(e.family()).isEqualTo(SteelFamily.ELECTRICAL);
        var spring = SteelGrade.classify(Map.of(AlloyElement.SI, 0.02, AlloyElement.C, 0.0045), HeatTreatState.AS_ROLLED);
        assertThat(spring.family()).isEqualTo(SteelFamily.MEDIUM_CARBON);
        assertThat(SteelFamily.ELECTRICAL.ordinal()).isEqualTo(SteelFamily.values().length - 1);
    }

    @Test void steinmetzLossMatchesGrade2212AndPlainIronIsTwiceWorse() {
        // 2212 / М470-50А: W15/50 ≤ 4,7 Вт/кг; нелегированная 0,5 мм — 8–10
        assertThat(annealed3Si().lossWPerKg(1.5, 50)).isBetween(3.5, 4.7);
        double iron = new ElectricalSheet(0.0005, 0, 0, SheetCoating.NONE).lossWPerKg(1.5, 50);
        assertThat(iron).isBetween(8.0, 11.0);
        // вихревая часть ~ d²: тоньше лист — меньше потери
        assertThat(new ElectricalSheet(0.00035, 0.03, 0, SheetCoating.NONE).lossWPerKg(1.5, 50))
                .isLessThan(annealed3Si().lossWPerKg(1.5, 50));
        // нет поля — нет потерь
        assertThat(annealed3Si().lossWPerKg(0, 50)).isZero();
    }

    @Test void coldRollingOf3SiNeedsAnIntermediateAnneal() {
        var s = ElectricalSheet.hotBand(0.002, 0.03);
        int passes = 0;
        ElectricalSheet.Pass p;
        while ((p = s.roll(0.0005)).outcome() == ElectricalSheet.Outcome.OK) {
            assertThat(p.sheet().thicknessM()).isGreaterThanOrEqualTo(s.thicknessM() * (1 - ElectricalSheet.MAX_PASS_REDUCTION) - 1e-12);
            assertThat(p.workJ()).isPositive();
            s = p.sheet();
            passes++;
        }
        assertThat(p.outcome()).isEqualTo(ElectricalSheet.Outcome.NEEDS_ANNEAL);
        assertThat(s.thicknessM()).isGreaterThan(0.0005);
        assertThat(passes).isPositive();
        int anneals = 0;
        while (p.outcome() != ElectricalSheet.Outcome.DONE) {
            if (p.outcome() == ElectricalSheet.Outcome.NEEDS_ANNEAL) {
                s = s.anneal();
                anneals++;
            } else s = p.sheet();
            assertThat(anneals).isLessThan(5);
            p = s.roll(0.0005);
        }
        assertThat(anneals).isBetween(1, 3);
        assertThat(s.thicknessM()).isCloseTo(0.0005, within(1e-9));
        // после прокатки лист нагартован: потери выше, чем после отжига
        assertThat(s.strain()).isPositive();
        assertThat(s.lossWPerKg(1.5, 50)).isGreaterThan(s.anneal().lossWPerKg(1.5, 50));
    }

    @Test void siliconAboveTheLimitIsTooBrittleToRoll() {
        var p = ElectricalSheet.hotBand(0.002, 0.05).roll(0.0005);
        assertThat(p.outcome()).isEqualTo(ElectricalSheet.Outcome.TOO_BRITTLE);
        assertThat(p.sheet().thicknessM()).isEqualTo(0.002);
    }

    @Test void annealBurnsTheCoatingAndRollingStripsIt() {
        var c = annealed3Si().coat(SheetCoating.PHENOLIC);
        assertThat(c.anneal().coating()).isEqualTo(SheetCoating.NONE);
        assertThat(ElectricalSheet.hotBand(0.001, 0.03).coat(SheetCoating.PAPER).roll(0.0005).sheet().coating())
                .isEqualTo(SheetCoating.NONE);
    }

    @Test void punchingForceIsPerimeterTimesThicknessTimesShear() {
        double f = Punching.forceN(STATOR.cutLengthM(), 0.0005, annealed3Si().utsPa());
        assertThat(f).isCloseTo(STATOR.cutLengthM() * 0.0005 * 0.8 * annealed3Si().utsPa(), within(1e-6));
        // ≈ 46 т на всю пластину
        assertThat(f).isBetween(400e3, 520e3);
        var big = Punching.punch(annealed3Si(), STATOR, Punching.CLEARANCE_OPT, 0, 600e3);
        assertThat(big.mode()).isEqualTo(Punching.Mode.COMPOUND);
        assertThat(big.strokes()).isEqualTo(1);
        var small = Punching.punch(annealed3Si(), STATOR, Punching.CLEARANCE_OPT, 0, 250e3);
        assertThat(small.mode()).isEqualTo(Punching.Mode.NOTCHING);
        assertThat(small.strokes()).isEqualTo(STATOR.slots() + 2);
        assertThat(small.peakForceN()).isLessThanOrEqualTo(250e3);
        var weak = Punching.punch(annealed3Si(), STATOR, Punching.CLEARANCE_OPT, 0, 50e3);
        assertThat(weak.mode()).isEqualTo(Punching.Mode.TOO_WEAK);
        assertThat(weak.lamination()).isNull();
    }

    @Test void burrGrowsWithDieWearAndWrongClearance() {
        double fresh = Punching.burrM(0.0005, Punching.CLEARANCE_OPT, 0);
        assertThat(fresh).isCloseTo(15e-6, within(1e-9));
        assertThat(Punching.burrM(0.0005, Punching.CLEARANCE_OPT + 0.01, 0)).isEqualTo(fresh);
        assertThat(Punching.burrM(0.0005, 0.15, 0)).isGreaterThan(fresh);
        assertThat(Punching.burrM(0.0005, Punching.CLEARANCE_OPT, Punching.DIE_LIFE_STROKES))
                .isCloseTo(fresh + Punching.BURR_WEAR_M, within(1e-12));
        assertThat(Punching.burrM(0.0005, Punching.CLEARANCE_OPT, -5)).isEqualTo(fresh);
    }

    @Test void burrShortsLaminationsThroughThinVarnishUnlessVarnishedAfterPunching() {
        var p = Punching.punch(annealed3Si().coat(SheetCoating.PHENOLIC), STATOR, Punching.CLEARANCE_OPT, 0, 250e3);
        var before = new CoreStack(p.lamination(), 240);
        assertThat(before.shortedFraction()).isPositive();
        var after = new CoreStack(p.lamination().coat(SheetCoating.PHENOLIC), 240);
        assertThat(after.shortedFraction()).isZero();
        var paper = new CoreStack(Punching.punch(annealed3Si().coat(SheetCoating.PAPER), STATOR, Punching.CLEARANCE_OPT, 0,
                250e3).lamination(), 240);
        assertThat(paper.shortedFraction()).isZero();
        // бумага толще лака: пакет той же длины держит меньше стали
        assertThat(paper.stackingFactor()).isLessThan(after.stackingFactor());
        assertThat(after.stackingFactor()).isBetween(0.95, 0.99);
        assertThat(before.lossWPerKg(1.5, 50)).isGreaterThan(after.lossWPerKg(1.5, 50));
    }

    @Test void edgeStressFromPunchingIsRelievedByAnnealing() {
        var l = Punching.punch(annealed3Si(), STATOR, Punching.CLEARANCE_OPT, 0, 250e3).lamination();
        var raw = new CoreStack(l, 240);
        var relieved = new CoreStack(l.anneal(), 240);
        assertThat(raw.hysteresisFactor()).isGreaterThan(1.05);
        assertThat(relieved.hysteresisFactor()).isEqualTo(1.0);
        assertThat(raw.coreLossW(1.5, 50)).isGreaterThan(relieved.coreLossW(1.5, 50));
    }

    @Test void referenceStatorMatchesThe4A112M4Motor() {
        CoreStack core = goodCore();
        assertThat(core.lengthM()).isCloseTo(Winding.STACK_LENGTH_M, within(0.002));
        assertThat(core.massKg()).isBetween(8.0, 14.0);
        assertThat(Winding.turnsPerPhase(STATOR)).isEqualTo(174);
        assertThat(Winding.wireNeededM(core)).isBetween(250.0, 320.0);
        Stator st = Winding.wind(core, annealedCopper(0.0014), WireInsulation.ENAMEL, true);
        assertThat(st.insulationClass()).isEqualTo(InsulationClass.B);
        assertThat(st.flawSeverity()).isZero();
        assertThat(st.insulationStartMOhm()).isEqualTo(1000.0);
        // каталог 4А112М4: η = 85,5 %
        assertThat(st.efficiency()).isBetween(0.84, 0.88);
        assertThat(st.coreLossW()).isBetween(50.0, 150.0);
        assertThat(st.copperLossW()).isBetween(350.0, 600.0);
    }

    @Test void carelessCoreCostsEfficiency() {
        var sheet = new ElectricalSheet(0.0005, 0.001, 1.0, SheetCoating.NONE);
        var l = Punching.punch(sheet, STATOR, Punching.CLEARANCE_OPT, 0, 250e3).lamination();
        var bad = new CoreStack(l, CoreStack.countFor(l, Winding.STACK_LENGTH_M));
        var wire = annealedCopper(0.0014);
        double good = Winding.wind(goodCore(), wire, WireInsulation.ENAMEL, true).efficiency();
        double worse = Winding.wind(bad, wire, WireInsulation.ENAMEL, true).efficiency();
        assertThat(good - worse).isGreaterThan(0.02);
    }

    @Test void overfilledSlotsAndHardWireDamageTheInsulation() {
        CoreStack core = goodCore();
        // бумага толще эмали: тот же провод не лезет в паз
        Stator paper = Winding.wind(core, annealedCopper(0.0016), WireInsulation.PAPER, true);
        assertThat(paper.fillFactor()).isGreaterThan(Winding.FILL_LIMIT_MACHINE);
        assertThat(paper.flawSeverity()).isPositive();
        assertThat(paper.insulationStartMOhm()).isLessThan(InsulationClass.A.nominalMOhm);
        // руками заполнение хуже, чем станком
        var w = annealedCopper(0.0016);
        assertThat(Winding.wind(core, w, WireInsulation.ENAMEL, false).flawSeverity())
                .isGreaterThan(Winding.wind(core, w, WireInsulation.ENAMEL, true).flawSeverity());
        // эмаль на нагартованной меди трескается, отжиг перед эмалированием это снимает
        Wire hard = new Wire(Wire.Metal.COPPER, 0.0014, 300, 2.0);
        assertThat(Winding.wind(core, hard, WireInsulation.ENAMEL, true).flawSeverity()).isPositive();
        // тоньше провод — сопротивление и потери в меди выше
        assertThat(Winding.wind(core, annealedCopper(0.0012), WireInsulation.ENAMEL, true).copperLossW())
                .isGreaterThan(Winding.wind(core, annealedCopper(0.0014), WireInsulation.ENAMEL, true).copperLossW());
    }

    @Test void insulationStartValueIsFiniteAndOrderedBySeverity() {
        var core = goodCore();
        double prev = Double.MAX_VALUE;
        for (double sev = 0; sev <= 1.0; sev += 0.25) {
            var st = new Stator(core, InsulationClass.F, 0.4, 1.0, sev);
            assertThat(st.insulationStartMOhm()).isFinite().isPositive().isLessThanOrEqualTo(prev);
            prev = st.insulationStartMOhm();
        }
        assertThat(prev).isCloseTo(10.0, within(1e-9));
        assertThat(InsulationClass.F.agingFactor(InsulationClass.F.ratedK + 10)).isCloseTo(2.0, within(1e-12));
        assertThat(InsulationClass.B.agingFactor(InsulationClass.F.ratedK)).isGreaterThan(1);
    }

    @Test void bareOrNonCopperWireIsRejected() {
        var core = goodCore();
        assertThatThrownBy(() -> Winding.wind(core, annealedCopper(0.0014), WireInsulation.NONE, true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Winding.wind(core, new Wire(Wire.Metal.MILD_STEEL, 0.0014, 300, 0), WireInsulation.ENAMEL, true))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
