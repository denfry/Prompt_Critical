package dev.denfry.promptcritical.sim.mining;

import static dev.denfry.promptcritical.sim.mining.MineTopology.ATMOSPHERE;
import static dev.denfry.promptcritical.sim.mining.Mines.K_SLOW;
import static dev.denfry.promptcritical.sim.mining.Mines.block;
import static dev.denfry.promptcritical.sim.mining.Mines.branchR;
import static dev.denfry.promptcritical.sim.mining.Mines.node;
import static dev.denfry.promptcritical.sim.mining.Mines.settle;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

/** Газы шахты (08 §8.2.4): узловой баланс, десорбция, окисление, дегазация, слоевые скопления. */
class MethaneFieldTest {
    /** Шахта: поверхность → узел(ы) → вентилятор → поверхность. */
    private static final class Line {
        final MineVentilation v = new MineVentilation();
        final MethaneField f = new MethaneField(MiningConfig.defaults());
        final int[] nodes;
        final int fan;

        Line(double... volumes) {
            MineTopology.Builder b = MineTopology.builder(0, Mines.T);
            nodes = new int[volumes.length];
            for (int i = 0; i < volumes.length; i++) nodes[i] = node(b, i + 1, volumes[i]);
            branchR(b, 1, ATMOSPHERE, nodes[0], 0.3, BranchKind.SHAFT);
            for (int i = 1; i < nodes.length; i++) branchR(b, i + 1, nodes[i - 1], nodes[i], 0.3, BranchKind.DRIFT);
            int ret = branchR(b, 100, nodes[nodes.length - 1], ATMOSPHERE, 0.3, BranchKind.SHAFT);
            fan = b.fan(ret, FanSpec.of(1600, 50, 600, 0.7));
            for (int n : nodes) for (int i = 0; i < 10; i++) b.assignBlock(block(n, i), n);
            MineTopology t = b.build();
            v.rebuild(t);
            f.rebuild(t);
        }

        /** Реальных секунд (шагов SLOW) до достижения доли CH₄ в узле. */
        int secondsTo(int node, double frac) {
            for (int s = 1; s < 100_000; s++) {
                v.solveSlow(K_SLOW, 10);
                f.stepSlow(K_SLOW, v);
                if (f.concentration(node, MineGas.CH4) >= frac) return s;
            }
            throw new AssertionError("not reached");
        }
    }

    /**
     * T8.4: вентилятор встал; сеть 10 000 м³, 0,3 м³/мин физ., C₀ = 0 → 1 % CH₄ через 4,6 мин реальных ±5 %.
     * И пример 08 §8.2.7: тупиковая лава 800 м³ при 0,2 м³/мин — 33 с.
     */
    @Test
    void t8_4_fanStopFillsNetworkWithMethane() {
        Line m = new Line(10_000);
        m.v.setFanSpeed(m.fan, 600);
        settle(m.v);
        assertThat(m.v.fanFlowM3s(m.fan)).isGreaterThan(20);
        m.v.setFanSpeed(m.fan, 0);
        m.f.setSource(m.nodes[0], MineGas.CH4, 0.3 / 60);
        int s = m.secondsTo(m.nodes[0], 0.01);
        assertThat(s / 60.0).isCloseTo(4.6, within(0.05 * 4.6));
        assertThat(m.v.fanFlowM3s(m.fan)).isCloseTo(0, within(1e-4));

        Line face = new Line(800);
        face.f.setSource(face.nodes[0], MineGas.CH4, 0.2 / 60);
        assertThat(face.secondsTo(face.nodes[0], 0.01)).isBetween(32, 35);
    }

    /** Проветриваемая цепочка: на исходящей C = I/Q; поступающая чистая. */
    @Test
    void ventilatedSteadyStateIsEmissionOverFlow() {
        Line m = new Line(500, 500, 500);
        m.v.setFanSpeed(m.fan, 600);
        settle(m.v);
        double q = m.v.fanFlowM3s(m.fan), i = 0.05;
        m.f.setSource(m.nodes[1], MineGas.CH4, i);
        for (int k = 0; k < 50; k++) m.f.stepSlow(K_SLOW, m.v);
        assertThat(m.f.concentration(m.nodes[0], MineGas.CH4)).isZero();
        assertThat(m.f.concentration(m.nodes[2], MineGas.CH4)).isCloseTo(i / (q + i), within(1e-9));
        assertThat(m.f.concentration(m.nodes[2], MineGas.O2)).isCloseTo(0.2095 * q / (q + i), within(1e-7));
    }

    /** Десорбция обнажения: всего κ·x·ρ_c·V, за τ_d уходит 1 − 1/e; отбитый уголь отдаёт остаток за час. */
    @Test
    void desorptionAndBrokenCoal() {
        Line m = new Line(1000);
        long key = block(m.nodes[0], 0);
        m.f.onExposed(key, 15, 0);
        double total = MethaneField.KAPPA * 15 * 1.3;
        assertThat(m.f.remainingGasM3(key)).isCloseTo(total, within(1e-12));
        double tau = MiningConfig.defaults().desorptionTauS();
        int steps = (int) Math.round(tau / K_SLOW);
        for (int k = 0; k < steps; k++) m.f.stepSlow(K_SLOW, m.v);
        assertThat(m.f.remainingGasM3(key)).isCloseTo(total * Math.exp(-1), within(1e-9));

        m.f.onMined(key, 15, 0.3);
        for (int k = 0; k < 3600 / K_SLOW; k++) m.f.stepSlow(K_SLOW, m.v);
        assertThat(m.f.remainingGasM3(key)).isLessThan(0.05 * total * Math.exp(-1));
        for (int k = 0; k < 600; k++) m.f.stepSlow(K_SLOW, m.v);
        assertThat(m.f.exposureCount()).as("истощённый отбитый уголь снят").isZero();
        // при отбойке на узел легла пыль 0,5 кг/т
        double surface = Mines.P * 1000 / Mines.S;
        assertThat(m.f.meanCoalDust(m.nodes[0])).isCloseTo(0.5 * 1.3 * 1000 / surface, within(1e-9));
    }

    /** Газоносность x₀(1 − e^{−(72−y)/40}). */
    @Test
    void gasContentGrowsWithDepth() {
        assertThat(MethaneField.gasContent(20, 72)).isZero();
        assertThat(MethaneField.gasContent(20, 100)).isZero();
        assertThat(MethaneField.gasContent(20, 32)).isCloseTo(20 * (1 - Math.exp(-1)), within(1e-12));
        assertThat(MethaneField.gasContent(20, -64)).isCloseTo(19.33, within(0.01));
    }

    /** Глухая выработка: уголь поглощает O₂, растут CO₂ и CO, сумма долей не больше единицы. */
    @Test
    void sealedWorkingLosesOxygen() {
        Line m = new Line(200);
        for (int i = 0; i < 10; i++) m.f.onExposed(block(m.nodes[0], i), 0, 0);
        for (int k = 0; k < 30 * 86_400 / K_SLOW; k++) m.f.stepSlow(K_SLOW, m.v);
        int n = m.nodes[0];
        double o2 = m.f.concentration(n, MineGas.O2);
        assertThat(o2).isLessThan(0.20);
        assertThat(m.f.concentration(n, MineGas.CO2)).isGreaterThan(0.002);
        assertThat(m.f.concentration(n, MineGas.CO)).isPositive();
        double sum = 0;
        for (MineGas g : MineGas.values()) sum += m.f.concentration(n, g);
        assertThat(sum).isLessThanOrEqualTo(1);
    }

    /** Дегазация уводит долю газовыделения в скважины; слоевое скопление удваивает CH₄ у кровли. */
    @Test
    void degasificationAndLayering() {
        Line m = new Line(1000);
        int n = m.nodes[0];
        for (int i = 0; i < 10; i++) m.f.onExposed(block(n, i), 20, 0);
        m.f.setDegasification(n, 0.4);
        m.f.stepSlow(K_SLOW, m.v);
        double emitted = m.f.emissionM3s(n, MineGas.CH4), captured = m.f.degasifiedM3s(n);
        assertThat(captured / (captured + emitted)).isCloseTo(0.4, within(1e-12));

        m.f.setConcentration(n, MineGas.CH4, 0.012);
        assertThat(m.f.nodeVelocity(n)).isLessThan(MethaneField.LAYERING_VELOCITY);
        assertThat(m.f.roofMethane(n)).isCloseTo(0.024, within(1e-12));
        m.v.setFanSpeed(m.fan, 600);
        settle(m.v);
        m.f.stepSlow(K_SLOW, m.v);
        assertThat(m.f.nodeVelocity(n)).isGreaterThan(0.5);
        assertThat(m.f.roofMethane(n)).isEqualTo(m.f.concentration(n, MineGas.CH4));
    }

    /** Перестройка графа сохраняет доли газов по ключам узлов. */
    @Test
    void rebuildCarriesConcentrations() {
        Line m = new Line(1000, 1000);
        m.f.setConcentration(m.nodes[1], MineGas.CH4, 0.02);
        m.f.rebuild(m.f.topology());
        assertThat(m.f.concentration(m.nodes[1], MineGas.CH4)).isEqualTo(0.02);
        assertThat(m.f.concentration(m.nodes[0], MineGas.O2)).isEqualTo(0.2095);
    }

    @Test
    void mineCategoryByRelativeMethane() {
        assertThat(MineCategory.classify(3)).isEqualTo(MineCategory.I);
        assertThat(MineCategory.classify(7)).isEqualTo(MineCategory.II);
        assertThat(MineCategory.classify(12)).isEqualTo(MineCategory.III);
        assertThat(MineCategory.classify(18)).isEqualTo(MineCategory.SUPER);
    }

    /** Горячий цикл эталонной шахты 08 §8.18 не аллоцирует. */
    @Test
    void referenceMineHotLoopDoesNotAllocate() {
        Mines.Reference m = new Mines.Reference();
        com.sun.management.ThreadMXBean mx =
                (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
        long thread = Thread.currentThread().threadId();
        for (int k = 0; k < 5_000; k++) m.tick();
        long before = mx.getThreadAllocatedBytes(thread);
        for (int k = 0; k < 20_000; k++) m.tick();
        long allocated = mx.getThreadAllocatedBytes(thread) - before;
        assertThat(allocated / 20_000).as("bytes per tick (total %d B)", allocated).isZero();
        assertThat(m.field.concentration(m.ret[0], MineGas.CH4)).isBetween(0.0, 0.01);
    }
}
