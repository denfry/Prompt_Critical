package dev.denfry.promptcritical.sim.refining;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.denfry.promptcritical.sim.refining.GasolineBlending.BlendOptimizerInput;
import dev.denfry.promptcritical.sim.refining.GasolineBlending.Properties;
import dev.denfry.promptcritical.sim.refining.GasolineBlending.Solution;
import dev.denfry.promptcritical.sim.refining.LinearProgram.Sense;
import dev.denfry.promptcritical.sim.refining.LinearProgram.Status;
import org.junit.jupiter.api.Test;

/** Симплекс и компаундирование: эталоны 09 §9.9.14 (T9.27–T9.29) и пример 03d §3d.10.3 (T-3d-31). */
class GasolineBlendingTest {

    @Test
    void simplexSolvesATextbookProblemWithDuals() {
        // max 3x + 5y: x ≤ 4, 2y ≤ 12, 3x + 2y ≤ 18 → (2, 6), 36; y* = (0, 1,5, 1)
        LinearProgram.Result r = new LinearProgram(new double[] {3, 5})
                .constraint(new double[] {1, 0}, Sense.LE, 4)
                .constraint(new double[] {0, 2}, Sense.LE, 12)
                .constraint(new double[] {3, 2}, Sense.LE, 18).solve();
        assertThat(r.status()).isEqualTo(Status.OPTIMAL);
        assertThat(r.x()).containsExactly(new double[] {2, 6}, within(1e-9));
        assertThat(r.objective()).isCloseTo(36, within(1e-9));
        assertThat(r.duals()).containsExactly(new double[] {0, 1.5, 1}, within(1e-9));
    }

    @Test
    void simplexReportsInfeasibleAndUnbounded() {
        assertThat(new LinearProgram(new double[] {1}).constraint(new double[] {1}, Sense.GE, 5)
                .constraint(new double[] {1}, Sense.LE, 3).solve().status()).isEqualTo(Status.INFEASIBLE);
        assertThat(new LinearProgram(new double[] {1, 1}).constraint(new double[] {1, -1}, Sense.LE, 1).solve().status())
                .isEqualTo(Status.UNBOUNDED);
    }

    @Test
    void blandsRuleDoesNotCycleOnBealesExample() {
        // пример Била (1955): классический симплекс с наибольшим коэффициентом зацикливается; оптимум 1/20 при x = (1/25, 0, 1, 0)
        LinearProgram.Result r = new LinearProgram(new double[] {0.75, -150, 0.02, -6})
                .constraint(new double[] {0.25, -60, -0.04, 9}, Sense.LE, 0)
                .constraint(new double[] {0.5, -90, -0.02, 3}, Sense.LE, 0)
                .constraint(new double[] {0, 0, 1, 0}, Sense.LE, 1).solve();
        assertThat(r.status()).isEqualTo(Status.OPTIMAL);
        assertThat(r.objective()).isCloseTo(0.05, within(1e-9));
    }

    @Test
    void caseANormal80WithoutAlkylate() {
        Solution s = GasolineBlending.maximizeMargin(new BlendOptimizerInput(GasolineBlending.BASE, GasolineBlending.NORMAL80), 800);
        assertThat(s.feasible()).isTrue();
        assertThat(s.v()).containsExactly(new double[] {2.093, 0.636, 5.000, 0}, within(0.001));
        assertThat(s.objective()).isCloseTo(2151.6, within(0.1));
        Properties p = s.properties();
        assertThat(p.volume()).isCloseTo(7.729, within(0.001));
        assertThat(p.ron()).isCloseTo(85.7, within(0.05));
        assertThat(p.mon()).isCloseTo(77.2, within(0.05));
        assertThat(p.rvpKpa()).isCloseTo(60.0, within(0.01));
        assertThat(p.benzenePct()).isCloseTo(1.0, within(1e-6));
    }

    @Test
    void caseBAi92WithoutAlkylateIsInfeasible() {
        Solution s = GasolineBlending.maximizeMargin(new BlendOptimizerInput(GasolineBlending.BASE, GasolineBlending.AI92), 900);
        assertThat(s.feasible()).isFalse();
    }

    @Test
    void caseCAi92WithAlkylate() {
        Solution s = GasolineBlending.maximizeMargin(new BlendOptimizerInput(GasolineBlending.WITH_ALKYLATE, GasolineBlending.AI92), 900);
        assertThat(s.feasible()).isTrue();
        assertThat(s.v()).containsExactly(new double[] {0.817, 2.627, 5.000, 0.350, 2.000}, within(0.001));
        assertThat(s.objective()).isCloseTo(3454.2, within(0.1));
        Properties p = s.properties();
        assertThat(p.volume()).isCloseTo(10.795, within(0.002));
        assertThat(p.ron()).isCloseTo(92.0, within(1e-6));
        assertThat(p.mon()).isCloseTo(83.9, within(0.05));
        assertThat(p.rvpKpa()).isCloseTo(60.0, within(1e-6));
        assertThat(p.sulfurPpm()).isCloseTo(4.4, within(0.05));
        assertThat(p.onSpec(GasolineBlending.AI92)).isTrue();
    }

    @Test
    void minCostBatchOfThePlaybookExampleAndThePriceOfOctane() {
        // 03d §3d.10.3, T-3d-31: 100 м³, ИОЧ ≥ 95, ДНП ≤ 60 → a 63,03, b 35,63, c 1,34; 58 720; цена октана 4,03
        double r95 = 95;
        double idx = Math.pow(60, 1.25);
        LinearProgram.Result r = new LinearProgram(new double[] {-620, -540, -300})
                .constraint(new double[] {99 - r95, 88 - r95, 93 - r95}, Sense.GE, 0)
                .constraint(new double[] {Math.pow(30, 1.25) - idx, Math.pow(90, 1.25) - idx, Math.pow(400, 1.25) - idx}, Sense.LE, 0)
                .constraint(new double[] {1, 1, 1}, Sense.EQ, 100)
                .upperBound(0, 80).upperBound(1, 50).upperBound(2, 10).solve();
        assertThat(r.x()).containsExactly(new double[] {63.03, 35.63, 1.34}, within(0.01));
        assertThat(-r.objective()).isCloseTo(58_720, within(1.0));
        // поднять «ИОЧ·м³» на единицу стоит 4,03 у.е.
        assertThat(-r.duals()[0]).isCloseTo(4.03, within(0.01));
    }

    @Test
    void actualBlendingMissesTheLinearModelSoTheLpRecipeNeedsCorrection() {
        BlendOptimizerInput in = new BlendOptimizerInput(GasolineBlending.WITH_ALKYLATE, GasolineBlending.AI92);
        Solution s = GasolineBlending.minimizeCost(in, 9);
        assertThat(s.properties().onSpec(GasolineBlending.AI92)).isTrue();
        Properties actual = GasolineBlending.actual(in.components(), s.v());
        assertThat(actual.mon()).isLessThan(83).isGreaterThan(82);
        assertThat(actual.onSpec(GasolineBlending.AI92)).isFalse();
        // запас 1 пункт по ОЧМ в ЛП возвращает смесь в спецификацию дороже оптимума меньше чем на 5 %
        BlendOptimizerInput safe = new BlendOptimizerInput(in.components(), new GasolineBlending.Grade("ai92+", 92, 84, 45, 60,
                10, 1, 35, 18, 720, 775));
        Solution c = GasolineBlending.minimizeCost(safe, 9);
        assertThat(GasolineBlending.actual(in.components(), c.v()).onSpec(GasolineBlending.AI92)).isTrue();
        assertThat(c.objective() / s.objective()).isBetween(1.0, 1.05);
    }
}
