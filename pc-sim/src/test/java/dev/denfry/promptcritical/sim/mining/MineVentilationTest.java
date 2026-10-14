package dev.denfry.promptcritical.sim.mining;

import static dev.denfry.promptcritical.sim.mining.MineTopology.ATMOSPHERE;
import static dev.denfry.promptcritical.sim.mining.Mines.branchR;
import static dev.denfry.promptcritical.sim.mining.Mines.node;
import static dev.denfry.promptcritical.sim.mining.Mines.settle;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.denfry.promptcritical.sim.combustion.Chimney;
import org.junit.jupiter.api.Test;

/** Вентиляционная сеть (08 §8.2.7): Аткинсон, Харди Кросс, вентилятор, двери, естественная тяга. */
class MineVentilationTest {
    /** T8.5: α 0,012, P 12 м, L 100 м, S 9 м², Q 20 м³/с → R = 0,0198 Н·с²/м⁸, h = 7,9 Па ±1 %. */
    @Test
    void t8_5_atkinsonResistanceAndDepression() {
        double r = MineTopology.atkinsonResistance(0.012, 12, 100, 9);
        assertThat(r).isCloseTo(0.0198, within(0.01 * 0.0198));
        assertThat(r * 20 * 20).isCloseTo(7.9, within(0.079));

        // та же выработка в сети: вентилятор подобран на 20 м³/с
        MineTopology.Builder b = MineTopology.builder(0, Mines.T);
        int n = node(b, 1, 1000);
        int drift = b.branch(1, ATMOSPHERE, n, 100, 9, 12, 0.012, BranchKind.DRIFT);
        int ret = branchR(b, 2, n, ATMOSPHERE, 0.1, BranchKind.SHAFT);
        double rTot = r + 0.1, rf = 0.5;
        int fan = b.fan(ret, FanSpec.of(400 * (rTot + rf), Math.sqrt(400 * (rTot + rf) / rf), 500, 0.7));
        MineVentilation v = new MineVentilation();
        v.rebuild(b.build());
        v.setFanSpeed(fan, 500);
        settle(v);
        assertThat(v.flow(drift)).isCloseTo(20, within(1e-6));
        assertThat(v.depression(drift)).isCloseTo(7.9, within(0.079));
    }

    /** Пример 08 §8.2.7: 25 м³/с при 1,2 кПа и η 0,7 → 43 кВт = 43 SU. */
    @Test
    void mainFanOperatingPointAndPower() {
        MineTopology.Builder b = MineTopology.builder(0, Mines.T);
        int n1 = node(b, 1, 2000), n2 = node(b, 2, 2000);
        branchR(b, 1, ATMOSPHERE, n1, 0.5, BranchKind.SHAFT);
        branchR(b, 2, n1, n2, 1.12, BranchKind.DRIFT);
        int ret = branchR(b, 3, n2, ATMOSPHERE, 0.3, BranchKind.SHAFT);
        int fan = b.fan(ret, FanSpec.of(1600, 50, 750, 0.7)); // h₀[1 − (25/50)²] = 1,2 кПа
        MineVentilation v = new MineVentilation();
        v.rebuild(b.build());
        v.setFanSpeed(fan, 750);
        v.solveSlow(Mines.K_SLOW, 10);
        assertThat(v.iterations()).as("из покоя за один шаг").isLessThanOrEqualTo(10);
        assertThat(v.fanFlowM3s(fan)).isCloseTo(25, within(1e-4));
        assertThat(v.fanPressurePa(fan)).isCloseTo(1200, within(0.01));
        assertThat(v.fanPowerW(fan)).isCloseTo(42_857, within(5.0));
        assertThat(v.depression(0) + v.depression(1) + v.depression(2)).isCloseTo(1200, within(0.01));

        // подобие: половина оборотов — половина подачи, четверть давления
        v.setFanSpeed(fan, 375);
        settle(v);
        assertThat(v.fanFlowM3s(fan)).isCloseTo(12.5, within(1e-4));
        assertThat(v.fanPressurePa(fan)).isCloseTo(300, within(0.01));

        // реверс: 60 % подачи в обратную сторону
        v.setFanSpeed(fan, -750);
        settle(v);
        assertThat(v.fanFlowM3s(fan)).isCloseTo(-15, within(1e-4));
        assertThat(v.flow(0)).isCloseTo(-15, within(1e-4));
    }

    /** Две параллельные выработки делят расход как √(R₂/R₁); закрытая дверь гонит воздух в соседнюю. */
    @Test
    void parallelSplitAndDoor() {
        MineTopology.Builder b = MineTopology.builder(0, Mines.T);
        int a = node(b, 1, 500), c = node(b, 2, 500);
        branchR(b, 1, ATMOSPHERE, a, 0.1, BranchKind.SHAFT);
        int p1 = branchR(b, 2, a, c, 0.4, BranchKind.DRIFT);
        int p2 = branchR(b, 3, a, c, 0.1, BranchKind.DRIFT);
        int ret = branchR(b, 4, c, ATMOSPHERE, 0.1, BranchKind.SHAFT);
        int door = b.door(p2, DoorKind.DOOR);
        int fan = b.fan(ret, FanSpec.of(2000, 60, 600, 0.75));
        MineTopology topo = b.build();
        assertThat(topo.loopCount()).isEqualTo(2);
        MineVentilation v = new MineVentilation();
        v.rebuild(topo);
        v.setFanSpeed(fan, 600);
        v.setDoorClosed(door, false);
        settle(v);
        assertThat(v.flow(p1) / v.flow(p2)).isCloseTo(0.5, within(1e-6));
        double q = v.flow(0);
        assertThat(v.flow(p1) + v.flow(p2)).isCloseTo(q, within(1e-9));
        assertThat(v.depression(p1)).isCloseTo(v.depression(p2), within(1e-3));

        v.setDoorClosed(door, true);
        settle(v);
        double leak = v.flow(p2) / v.flow(0);
        assertThat(leak).as("утечка через дверь 50 Н·с²/м⁸").isCloseTo(Math.sqrt(0.4 / 50.1) / (1 + Math.sqrt(0.4 / 50.1)),
                within(1e-6));
        assertThat(v.flow(0)).isLessThan(q);
    }

    /** Регулятор: окно меньше — расход в ветви меньше; полностью открытое окно не сопротивляется. */
    @Test
    void regulatorThrottlesBranch() {
        assertThat(MineVentilation.regulatorResistance(1.2, 9, 9)).isZero();
        assertThat(MineVentilation.regulatorResistance(1.2, 9, 1)).isGreaterThan(
                MineVentilation.regulatorResistance(1.2, 9, 2));
        MineTopology.Builder b = MineTopology.builder(0, Mines.T);
        int a = node(b, 1, 500), c = node(b, 2, 500);
        branchR(b, 1, ATMOSPHERE, a, 0.1, BranchKind.SHAFT);
        int p1 = branchR(b, 2, a, c, 0.2, BranchKind.FACE);
        int p2 = branchR(b, 3, a, c, 0.2, BranchKind.FACE);
        int ret = branchR(b, 4, c, ATMOSPHERE, 0.1, BranchKind.SHAFT);
        int reg = b.door(p1, DoorKind.REGULATOR);
        int fan = b.fan(ret, FanSpec.of(2000, 60, 600, 0.75));
        MineVentilation v = new MineVentilation();
        v.rebuild(b.build());
        v.setFanSpeed(fan, 600);
        settle(v);
        assertThat(v.flow(p1)).isCloseTo(v.flow(p2), within(1e-6));
        v.setRegulatorArea(reg, 1.0);
        settle(v);
        assertThat(v.flow(p1) / v.flow(p2)).isLessThan(0.5);
    }

    /** Естественная тяга: зимой холодный ствол опускает воздух, тёплый поднимает; Q = √(Σh_e/ΣR). */
    @Test
    void naturalDraftDrivesLoopWithoutFan() {
        MineTopology.Builder b = MineTopology.builder(64, 263.15); // −10 °C
        int bottomIn = b.node(1, 1000, -136, 9, 12, 283.15);
        int bottomOut = b.node(2, 1000, -136, 9, 12, 303.15);
        int in = branchR(b, 1, ATMOSPHERE, bottomIn, 0.2, BranchKind.SHAFT);
        branchR(b, 2, bottomIn, bottomOut, 0.5, BranchKind.DRIFT);
        branchR(b, 3, bottomOut, ATMOSPHERE, 0.2, BranchKind.SHAFT);
        MineVentilation v = new MineVentilation();
        v.rebuild(b.build());
        settle(v);
        double he = v.naturalDraft(0) + v.naturalDraft(1) + v.naturalDraft(2);
        double rhoIn = MineVentilation.AIR_PRESSURE * Chimney.AIR_MOLAR_MASS / (Chimney.GAS_CONSTANT * 273.15);
        double rhoOut = MineVentilation.AIR_PRESSURE * Chimney.AIR_MOLAR_MASS / (Chimney.GAS_CONSTANT * 283.15);
        assertThat(he).isCloseTo(Chimney.GRAVITY * 200 * (rhoIn - rhoOut), within(1e-9));
        assertThat(v.flow(in)).isPositive().isCloseTo(Math.sqrt(he / 0.9), within(1e-5));
    }

    /** Требуемый расход по газу и пределы скорости. */
    @Test
    void requiredFlowAndVelocityLimits() {
        // 2 м³/мин CH₄, k_н 1,4, 1 % на исходящей, 0,1 % на поступающей → 311 м³/мин
        assertThat(MineVentilation.requiredFlowM3s(2.0 / 60, 1.4, 0.01, 0.001) * 60).isCloseTo(311.1, within(0.1));
        Mines.Reference m = new Mines.Reference();
        Mines.settle(m.ventilation);
        MiningConfig cfg = MiningConfig.defaults();
        int deadEnd = m.topology.branchCount() - 1;
        assertThat(m.ventilation.flow(deadEnd)).isCloseTo(0, within(1e-6));
        assertThat(m.ventilation.velocityViolation(deadEnd, cfg)).as("тупик не проветривается").isTrue();
        assertThat(m.ventilation.velocityViolation(0, cfg)).isFalse();
    }

    /** Тёплый старт: перестройка с тем же графом сохраняет расходы, сходимость за 1–2 итерации. */
    @Test
    void rebuildCarriesFlowsByKey() {
        Mines.Reference m = new Mines.Reference();
        settle(m.ventilation);
        double q = m.ventilation.fanFlowM3s(m.fan);
        assertThat(q).isPositive();
        m.ventilation.rebuild(m.topology);
        m.ventilation.solveSlow(Mines.K_SLOW, 10);
        assertThat(m.ventilation.iterations()).isLessThanOrEqualTo(2);
        assertThat(m.ventilation.fanFlowM3s(m.fan)).isCloseTo(q, within(1e-4));
        assertThat(m.ventilation.fanSpeed(m.fan)).isEqualTo(600);
    }

    /** Больше {@value MineVentilation#DENSE_LOOP_LIMIT} контуров — сопряжённые градиенты; 300 равных параллельных ветвей делят расход поровну. */
    @Test
    void largeNetworkSolvedByConjugateGradients() {
        MineTopology.Builder b = MineTopology.builder(0, Mines.T);
        int a = node(b, 1, 500), c = node(b, 2, 500);
        branchR(b, 1, ATMOSPHERE, a, 0.01, BranchKind.SHAFT);
        int ret = branchR(b, 2, c, ATMOSPHERE, 0.01, BranchKind.SHAFT);
        for (int k = 0; k < 300; k++) branchR(b, 10 + k, a, c, 2.0, BranchKind.DRIFT);
        int fan = b.fan(ret, FanSpec.of(2000, 60, 600, 0.75));
        MineTopology topo = b.build();
        assertThat(topo.loopCount()).isGreaterThan(MineVentilation.DENSE_LOOP_LIMIT);
        MineVentilation v = new MineVentilation();
        v.rebuild(topo);
        v.setFanSpeed(fan, 600);
        v.solveSlow(Mines.K_SLOW, 10);
        assertThat(v.lastCorrection()).isLessThan(MineVentilation.TOLERANCE);
        double q = v.flow(0);
        // ΣR: 0,02 + 2/300² + внутреннее сопротивление вентилятора
        assertThat(q).isCloseTo(Math.sqrt(2000 / (0.02 + 2.0 / 90_000 + 2000.0 / 3600)), within(1e-6));
        for (int k = 0; k < 300; k++) assertThat(v.flow(2 + k)).isCloseTo(q / 300, within(1e-7));
    }

    /** Контуры по остовному дереву: каждая хорда — контур, суммы депрессий по контурам нулевые. */
    @Test
    void loopsCloseOnReferenceMine() {
        Mines.Reference m = new Mines.Reference();
        MineTopology t = m.topology;
        assertThat(t.nodeCount()).isEqualTo(45);
        assertThat(t.loopCount()).isEqualTo(t.branchCount() - t.nodeCount() + 1);
        settle(m.ventilation);
        for (int k = 0; k < t.loopCount(); k++) {
            double sum = 0;
            for (int i = t.loopStart[k]; i < t.loopStart[k + 1]; i++) {
                int j = t.loopBranch[i];
                int f = t.fanOf[j];
                double h = m.ventilation.depression(j) - m.ventilation.naturalDraft(j)
                        - (f >= 0 ? m.ventilation.fanPressurePa(f) : 0);
                sum += t.loopSign[i] * h;
            }
            assertThat(sum).as("контур %d", k).isCloseTo(0, within(1e-3));
        }
        // неразрывность в каждом узле
        for (int n = 1; n < t.nodeCount(); n++) {
            double bal = 0;
            for (int j = 0; j < t.branchCount(); j++) {
                if (t.to(j) == n) bal += m.ventilation.flow(j);
                if (t.from(j) == n) bal -= m.ventilation.flow(j);
            }
            assertThat(bal).isCloseTo(0, within(1e-9));
        }
    }
}
