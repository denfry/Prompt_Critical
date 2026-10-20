package dev.denfry.promptcritical.sim.metallurgy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.EnumMap;
import java.util.Map;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

/** Марки стали, термообработка, прокатка T8.12, трубы T8.13: B31G, гидроиспытание, контроль, Пэрис, шов. */
class RollingAndPipesTest {
    private static final SteelmakingConfig CFG = SteelmakingConfig.defaults();

    private static Map<AlloyElement, Double> chem(Object... kv) {
        Map<AlloyElement, Double> m = new EnumMap<>(AlloyElement.class);
        for (int i = 0; i < kv.length; i += 2) m.put((AlloyElement) kv[i], (Double) kv[i + 1] / 100);
        return m;
    }

    private static final SteelGrade ST3 = SteelGrade.classify(
            chem(AlloyElement.C, 0.18, AlloyElement.MN, 0.5, AlloyElement.SI, 0.2, AlloyElement.S, 0.03),
            HeatTreatState.AS_ROLLED);

    private static StockState billet(double side, double tempK) {
        return new StockState(StockShape.BILLET, side, side, 6, tempK, ST3, false, false);
    }

    @Test
    void gradesClassifyByChemistry() {
        assertThat(ST3.family()).isEqualTo(SteelFamily.CARBON);
        assertThat(ST3.yieldPa()).isEqualTo(245e6);
        assertThat(ST3.meetsSpec()).isTrue();
        SteelGrade g09 = SteelGrade.classify(chem(AlloyElement.C, 0.12, AlloyElement.MN, 1.5, AlloyElement.SI, 0.6),
                HeatTreatState.AS_ROLLED);
        assertThat(g09.family()).isEqualTo(SteelFamily.LOW_ALLOY);
        assertThat(g09.carbonEq()).isCloseTo(0.12 + 1.5 / 6, within(1e-6));
        SteelGrade ss = SteelGrade.classify(chem(AlloyElement.C, 0.06, AlloyElement.CR, 18.04, AlloyElement.NI, 9.6,
                AlloyElement.MN, 1.2, AlloyElement.SI, 0.5, AlloyElement.TI, 0.3), HeatTreatState.SOLUTION_ANNEALED);
        assertThat(ss.family()).isEqualTo(SteelFamily.STAINLESS_304);
        assertThat(ss.chemPct()).as("не больше 4 легирующих").doesNotContainKey("Ti").containsEntry("Cr", 18.0f);
        SteelGrade x70 = SteelGrade.classify(chem(AlloyElement.C, 0.1, AlloyElement.MN, 1.5, AlloyElement.NB, 0.1),
                HeatTreatState.AS_ROLLED);
        assertThat(x70.yieldPa()).isEqualTo(485e6);

        SteelGrade dirty = SteelGrade.classify(chem(AlloyElement.C, 0.18, AlloyElement.S, 0.10),
                HeatTreatState.AS_ROLLED);
        assertThat(dirty.meetsSpec()).isFalse();
        assertThat(dirty.yieldPa()).isCloseTo(245e6 * (1 - 0.1 * (0.10 / 0.05 - 1)), within(1.0));
        SteelGrade lean = SteelGrade.of(SteelFamily.STAINLESS_304, "08Х18Н10",
                Map.of("C", 0.06f, "Cr", 18f, "Ni", 4f), HeatTreatState.SOLUTION_ANNEALED);
        assertThat(lean.yieldPa()).isCloseTo(205e6 * 0.75, within(1e3));
    }

    @Test
    void heatTreatmentWindows() {
        SteelGrade c30 = SteelGrade.classify(chem(AlloyElement.C, 0.3, AlloyElement.MN, 0.6), HeatTreatState.AS_ROLLED);
        assertThat(HeatTreatment.quench(c30, 1073.15)).as("ниже A_c3 + 30").isSameAs(c30);
        SteelGrade q = HeatTreatment.quench(c30, 1153.15);
        assertThat(q.state()).isEqualTo(HeatTreatState.QUENCHED);
        assertThat(q.hardnessHrc()).isCloseTo(20 + 60 * Math.sqrt(0.3), within(1e-6));
        assertThat(q.sourCapable()).isFalse();
        SteelGrade qt = HeatTreatment.temper(q, 873.15);
        assertThat(qt.state()).isEqualTo(HeatTreatState.QUENCHED_TEMPERED);
        assertThat(qt.yieldPa() / c30.yieldPa()).isCloseTo(1.45, within(1e-9));
        assertThat(HeatTreatment.temper(c30, 893.15).state()).isEqualTo(HeatTreatState.STRESS_RELIEVED);
        assertThat(HeatTreatment.normalize(c30, HeatTreatment.ac3K(c30) + 40).state())
                .isEqualTo(HeatTreatState.NORMALIZED);

        SteelGrade l80 = SteelGrade.classify(chem(AlloyElement.C, 0.25, AlloyElement.MN, 1.2, AlloyElement.S, 0.003),
                HeatTreatState.QUENCHED_TEMPERED);
        assertThat(l80.family()).isEqualTo(SteelFamily.SOUR_SERVICE);
        assertThat(l80.hardnessHrc()).isLessThanOrEqualTo(22);
        assertThat(l80.meetsSpec()).isTrue();
        assertThat(l80.sourCapable()).isTrue();

        SteelGrade mar = SteelGrade.classify(chem(AlloyElement.NI, 18.0, AlloyElement.CO, 9.0, AlloyElement.MO, 5.0,
                AlloyElement.TI, 0.7, AlloyElement.C, 0.01), HeatTreatState.SOLUTION_ANNEALED);
        assertThat(mar.family()).isEqualTo(SteelFamily.MARAGING);
        SteelGrade aged = HeatTreatment.age(mar, 753.15);
        assertThat(aged.state()).isEqualTo(HeatTreatState.AGED);
        assertThat(aged.yieldPa()).isEqualTo(1900e6);
        assertThat(aged.hardnessHrc()).isCloseTo(52, within(1.0));
    }

    @Test
    void t8_12_tselikovForceAndPower() {
        double f = RollingMill.force(120e6, 0.15, 0.3, 0.03, 1.1);
        double m = RollingMill.torque(f, 0.5, 0.3, 0.03);
        assertThat(f).isCloseTo(1.88e6, within(0.02 * 1.88e6));
        assertThat(m).isCloseTo(178e3, within(0.02 * 178e3));
        assertThat(m * 2 * Math.PI).isCloseTo(1.12e6, within(0.02 * 1.12e6));

        RollingMill mill = new RollingMill(MillRollKind.FLAT, 0.3, 0.35, 1e6);
        PassResult r = mill.pass(billet(0.15, 1373.15), 0.12, 2 * Math.PI);
        assertThat(r.outcome()).isEqualTo(PassResult.Outcome.ROLLED);
        assertThat(r.forceN()).isCloseTo(1.88e6, within(0.02 * 1.88e6));
        assertThat(r.powerW()).isCloseTo(1.12e6, within(0.02 * 1.12e6));
        assertThat(r.out().massKg()).isCloseTo(billet(0.15, 1373.15).massKg(), within(1e-6));
        assertThat(r.out().widthM()).isGreaterThan(0.15);
        assertThat(r.out().cracked()).isFalse();
    }

    @Test
    void biteShearPinAndColdRolling() {
        RollingMill mill = new RollingMill(MillRollKind.FLAT, 0.3, 0.35, 1e6);
        assertThat(mill.pass(billet(0.15, 1373.15), 0.10, 6).outcome()).isEqualTo(PassResult.Outcome.NOT_BITTEN);
        RollingMill weak = new RollingMill(MillRollKind.FLAT, 0.3, 0.35, 1e5);
        PassResult pin = weak.pass(billet(0.15, 1373.15), 0.12, 6);
        assertThat(pin.outcome()).isEqualTo(PassResult.Outcome.SHEAR_PIN);
        assertThat(pin.out()).isEqualTo(billet(0.15, 1373.15));

        assertThat(RollingMill.flowStressPa(billet(0.15, 1273.15))).isCloseTo(240e6, within(1.0));
        assertThat(RollingMill.flowStressPa(billet(0.15, 293.15))).isCloseTo(1.8 * 245e6, within(1.0));
        PassResult warm = mill.pass(billet(0.15, 1073.15), 0.14, 6);
        assertThat(warm.out().cracked()).as("ниже Ar₃").isTrue();
        PassResult cold = mill.pass(billet(0.004, 293.15), 0.0035, 6);
        assertThat(cold.out().cracked()).isFalse();

        StockState s = billet(0.05, 1473.15);
        for (int k = 0; k < 6; k++) s = mill.pass(s, s.thicknessM() * 0.8, 6).out().withTemp(1473.15);
        assertThat(s.shape()).isEqualTo(StockShape.PLATE);
        RollingMill section = new RollingMill(MillRollKind.SECTION, 0.2, 0.35, 1e6);
        StockState rod = section.pass(billet(0.032, 1473.15), 0.026, 6).out();
        assertThat(rod.shape()).isEqualTo(StockShape.ROD);
    }

    @Test
    void reheatingScaleAndBurning() {
        StockState b = billet(0.15, 293.15);
        assertThat(1 - b.reheated(1423.15).lengthM() / b.lengthM()).isCloseTo(0.01, within(1e-9));
        assertThat(1 - b.reheated(1523.15).lengthM() / b.lengthM()).isCloseTo(0.02, within(1e-9));
        assertThat(b.reheated(1523.15).cracked()).isFalse();
        assertThat(b.reheated(1600).cracked()).isTrue();
        SteelIngot ingot = new SteelIngot(1_000, 150, Map.of(AlloyElement.C, 0.002), true, false);
        StockState is = StockState.fromIngot(ingot, ST3, 1473.15);
        assertThat(is.massKg()).isCloseTo(1_000, within(1e-6));
        assertThat(is.porous()).isTrue();
    }

    private static PipeSpec x52(PipeDefect d) {
        return new PipeSpec("X52", 0.219, 0.008, 359e6, 23.6e6, true, false, false, 0, PipeKind.LINE, 455e6, 0, d, 1);
    }

    @Test
    void t8_13_b31gAndHydrotest() {
        PipeSpec clean = x52(PipeDefect.NONE);
        assertThat(clean.yieldPressurePa() / 1e6).isCloseTo(26.2, within(0.262));
        assertThat(x52(new PipeDefect(DefectKind.HOOK_CRACK, 0.5, 0.05)).failurePressurePa() / 1e6)
                .isCloseTo(24.9, within(0.02 * 24.9));
        PipeSpec bad = x52(new PipeDefect(DefectKind.LACK_OF_FUSION, 0.8, 0.1));
        assertThat(bad.failurePressurePa() / 1e6).isCloseTo(17.4, within(0.02 * 17.4));

        assertThat(bad.defectFound()).isFalse();
        PipeSpec.HydrotestResult r = bad.hydrotest();
        assertThat(r.burst()).isTrue();
        assertThat(r.pipe().inspectedMask() & PipeSpec.HYDRO).isEqualTo(PipeSpec.HYDRO);
        assertThat(r.pipe().defectFound()).isTrue();

        PipeSpec shallow = x52(new PipeDefect(DefectKind.POROSITY, 0.5, 0.05));
        PipeSpec.HydrotestResult ok = shallow.hydrotest();
        assertThat(ok.burst()).isFalse();
        assertThat(ok.pipe().defectFound()).isFalse();
        assertThat(ok.pipe().inspected(PipeSpec.UT).defectFound()).as("поры УЗК не видит").isFalse();
        assertThat(ok.pipe().inspected(PipeSpec.RT).defectFound()).isTrue();
        assertThat(x52(new PipeDefect(DefectKind.LAMINATION, 0.3, 0.2)).inspected(PipeSpec.UT).defectFound())
                .isTrue();
        assertThat(shallow.rupturesAt(25e6)).isTrue();
        assertThat(clean.rupturesAt(25e6)).isFalse();
        assertThat(clean.failurePressurePa()).isCloseTo(1.1 * clean.yieldPressurePa(), within(1.0));
        assertThat(PipeSpec.b31gPa(359e6, 0.219, 0.008, 0.5, 1.0) / 1e6)
                .as("длинный дефект: 1 − d/t").isCloseTo(1.1 * 26.23 * 0.5, within(0.05));
    }

    @Test
    void parisGrowthOfPlanarDefects() {
        PipeSpec crack = x52(new PipeDefect(DefectKind.HOOK_CRACK, 0.3, 0.05));
        PipeSpec grown = crack.pressureCycles(10e6, 100_000, CFG.parisC());
        assertThat(grown.defect().depthFrac()).isGreaterThan(0.3);
        assertThat(grown.failurePressurePa()).isLessThan(crack.failurePressurePa());
        double a0 = 0.3 * 0.008, ds = 10 * 0.219 / 0.016;
        double rate = CFG.parisC() * Math.pow(1.12 * ds * Math.sqrt(Math.PI * a0), 3);
        PipeSpec few = crack.pressureCycles(10e6, 1_000, CFG.parisC());
        assertThat((few.defect().depthFrac() - 0.3) * 0.008).isCloseTo(rate * 1_000, within(0.02 * rate * 1_000));
        assertThat(crack.pressureCycles(10e6, 100_000_000L, CFG.parisC()).defect().depthFrac()).isEqualTo(1);
        PipeSpec pores = x52(new PipeDefect(DefectKind.POROSITY, 0.3, 0.05));
        assertThat(pores.pressureCycles(10e6, 100_000, CFG.parisC())).isSameAs(pores);
    }

    @Test
    void weldDefectProbability() {
        double p0 = CFG.weldDefectBasePerM();
        assertThat(PipeMill.weldDefectPerM(1, 1, 400e3, 400e3, 0.3, p0)).isEqualTo(p0);
        assertThat(PipeMill.weldDefectPerM(1.25, 1, 400e3, 400e3, 0.3, p0)).isCloseTo(2 * p0, within(1e-12));
        assertThat(PipeMill.weldDefectPerM(1, 1, 300e3, 400e3, 0.3, p0)).isCloseTo(2 * p0, within(1e-12));
        assertThat(PipeMill.weldDefectPerM(1, 1, 400e3, 400e3, 0.5, p0)).isCloseTo(3 * p0, within(1e-12));

        SplittableRandom rng = new SplittableRandom(5);
        StockState plate = new StockState(StockShape.PLATE, 0.008, 0.66, 12, 293.15, ST3, false, false);
        int defects = 0, lof = 0;
        for (int k = 0; k < 2_000; k++) {
            PipeSpec p = PipeMill.welded(plate, PipeKind.LINE, 0.219, 20, 1.5, 1, 400e3, 400e3, k, CFG, rng);
            assertThat(p.seamless()).isFalse();
            if (p.defect().present()) {
                defects++;
                if (p.defect().kind() == DefectKind.LACK_OF_FUSION) lof++;
            }
        }
        double expect = 1 - Math.pow(1 - 3 * p0, 20);
        assertThat(defects / 2_000.0).isCloseTo(expect, within(0.03));
        assertThat(lof).isEqualTo(defects);

        PipeSpec lam = PipeMill.welded(new StockState(StockShape.PLATE, 0.008, 0.66, 12, 293.15, ST3, false, true),
                PipeKind.LINE, 0.219, 20, 1, 1, 400e3, 400e3, 1, new SteelmakingConfig(0.85, 0.003, 0.005, 0, 0.9, 5e-12),
                rng);
        assertThat(lam.defect().kind()).isEqualTo(DefectKind.LAMINATION);
    }

    @Test
    void seamlessPiercing() {
        SplittableRandom rng = new SplittableRandom(11);
        assertThat(PipeMill.seamless(billet(0.15, 1373.15), PipeKind.CASING, 0.1778, 0.00919, 1, CFG, rng)).isNull();
        int bad = 0;
        for (int k = 0; k < 500; k++) {
            PipeSpec p = PipeMill.seamless(billet(0.15, 1498.15), PipeKind.CASING, 0.1778, 0.00919, k, CFG, rng);
            assertThat(p.defect().present()).isFalse();
            assertThat(p.odM()).isCloseTo(0.1778, within(0.1778 * 0.0075));
            assertThat(p.seamless()).isTrue();
            assertThat(p.testPressurePa()).isCloseTo(0.9 * p.yieldPressurePa(), within(1.0));
            PipeSpec hot = PipeMill.seamless(billet(0.15, 1560), PipeKind.CASING, 0.1778, 0.00919, k, CFG, rng);
            if (hot.defect().kind() == DefectKind.ECCENTRICITY) bad++;
        }
        assertThat(bad / 500.0).as("вне окна прошивки: P(e > 12,5 %) = 0,375").isCloseTo(0.375, within(0.07));
        StockState porous = new StockState(StockShape.BILLET, 0.15, 0.15, 6, 1498.15, ST3, false, true);
        assertThat(PipeMill.seamless(porous, PipeKind.LINE, 0.219, 0.008, 1, CFG, rng).defect().kind())
                .isEqualTo(DefectKind.INCLUSION_LAP);
    }

    @Test
    void pipeTakesGradeAfterHeatTreatment() {
        SteelGrade l80 = SteelGrade.classify(chem(AlloyElement.C, 0.25, AlloyElement.MN, 1.2, AlloyElement.S, 0.003),
                HeatTreatState.AS_ROLLED);
        PipeSpec p = PipeSpec.of(l80, PipeKind.CASING, 0.1778, 0.00919, true, PipeDefect.NONE, 3, CFG);
        SteelGrade q = HeatTreatment.quench(SteelGrade.of(SteelFamily.SOUR_SERVICE, "L80", l80.chemPct(),
                HeatTreatState.AS_ROLLED), 1153.15);
        PipeSpec quenched = p.withGrade(q);
        assertThat(quenched.sourService()).isFalse();
        PipeSpec qt = quenched.withGrade(HeatTreatment.temper(q, 873.15));
        assertThat(qt.sourService()).isTrue();
        assertThat(qt.smysPa()).isEqualTo(552e6);
        assertThat(qt.grade()).isEqualTo("L80");
    }
}
