package dev.denfry.promptcritical.sim.mining;

import static dev.denfry.promptcritical.sim.mining.MineTopology.ATMOSPHERE;
import static dev.denfry.promptcritical.sim.mining.Mines.branchR;
import static dev.denfry.promptcritical.sim.mining.Mines.node;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.denfry.promptcritical.sim.core.StateHash;
import dev.denfry.promptcritical.sim.core.TreeStateReader;
import dev.denfry.promptcritical.sim.core.TreeStateWriter;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Шахта как подсистема мира (этап 7 M2): перестройка, пламя, фронт для привязки, сохранение. */
class MineNetworkTest {
    static final double V = 64;

    /** Штрек из 4 узлов, выход на поверхность у первого; блоки 0…2 — воздух узла, 100 — обнажённый уголь у n4. */
    private static MineTopology drift(double barrierKg) {
        MineTopology.Builder b = MineTopology.builder(0, Mines.T);
        int n1 = node(b, 11, V), n2 = node(b, 12, V), n3 = node(b, 13, V), n4 = node(b, 14, V);
        branchR(b, 1, ATMOSPHERE, n1, 0.1, BranchKind.SHAFT);
        branchR(b, 2, n1, n2, 0.1, BranchKind.DRIFT);
        int j = branchR(b, 3, n2, n3, 0.1, BranchKind.DRIFT);
        branchR(b, 4, n3, n4, 0.1, BranchKind.DRIFT);
        if (barrierKg > 0) b.barrier(j, BarrierKind.STONE, barrierKg);
        for (int n = 1; n <= 4; n++) for (int i = 0; i < 3; i++) b.assignBlock(Mines.block(n, i), n);
        b.assignBlock(100, n4);
        return b.build();
    }

    private static void rebuild(MineNetwork m, MineTopology t, boolean flameAtN1) {
        long[] origins = new long[t.nodeCount()];
        for (int n = 1; n < origins.length; n++) origins[n] = Mines.block(n, 0);
        boolean[] flames = new boolean[t.nodeCount()];
        flames[1] = flameAtN1;
        m.rebuild(t, origins, flames, new long[] {100}, new double[] {12}, 1);
    }

    @Test
    void openFlameIgnitesExplosiveNodeAndFrontReportsBurnedNodes() {
        MineNetwork m = new MineNetwork(1, MiningConfig.defaults());
        rebuild(m, drift(0), true);
        for (int n = 1; n <= 4; n++) m.field().setConcentration(n, MineGas.CH4, 0.09);
        m.stepSlow(Mines.K_SLOW);
        assertThat(m.exploding()).isTrue();
        List<Integer> burned = new ArrayList<>();
        List<Long> origins = new ArrayList<>();
        m.drainBurned((node, origin, e) -> {
            burned.add(node);
            origins.add(origin);
            assertThat(e).isPositive();
        });
        while (m.exploding()) {
            m.stepFront();
            m.drainBurned((node, origin, e) -> {
                burned.add(node);
                origins.add(origin);
            });
        }
        assertThat(burned).containsExactly(1, 2, 3, 4);
        assertThat(origins).containsExactly(Mines.block(1, 0), Mines.block(2, 0), Mines.block(3, 0), Mines.block(4, 0));
        assertThat(m.field().concentration(4, MineGas.CO)).isGreaterThan(0.005);
        // второй фронт от того же пламени не начинается: метан выгорел
        m.stepSlow(Mines.K_SLOW);
        assertThat(m.explosions()).isEqualTo(1);
    }

    @Test
    void flameOutsideExplosiveLimitsDoesNothing() {
        MineNetwork m = new MineNetwork(1, MiningConfig.defaults());
        rebuild(m, drift(0), true);
        m.field().setConcentration(1, MineGas.CH4, 0.03);
        m.stepSlow(Mines.K_SLOW);
        assertThat(m.exploding()).isFalse();
        assertThat(m.explosions()).isZero();
    }

    @Test
    void barrierQuenchesFrontInTheWorldGraph() {
        MineNetwork m = new MineNetwork(1, MiningConfig.defaults());
        rebuild(m, drift(ExplosionFront.BARRIER_KG_PER_M2 * Mines.S), false);
        for (int n = 1; n <= 4; n++) m.field().setConcentration(n, MineGas.CH4, 0.09);
        assertThat(m.ignite(1, IgnitionSource.LAMP)).isTrue();
        while (m.exploding()) m.stepFront();
        ExplosionFront f = m.lastFront();
        assertThat(f.burned(2)).isTrue();
        assertThat(f.burned(3)).isFalse();
        assertThat(f.quenchedByBarriers()).isEqualTo(1);
    }

    @Test
    void rebuildKeepsGasAndAbortsRunningFront() {
        MineNetwork m = new MineNetwork(1, MiningConfig.defaults());
        rebuild(m, drift(0), false);
        m.field().setConcentration(3, MineGas.CH4, 0.02);
        for (int n = 1; n <= 4; n++) if (n != 3) m.field().setConcentration(n, MineGas.CH4, 0.09);
        m.ignite(1, IgnitionSource.SPARK);
        assertThat(m.exploding()).isTrue();
        rebuild(m, drift(0), false);
        assertThat(m.exploding()).isFalse();
        assertThat(m.field().concentration(3, MineGas.CH4)).isEqualTo(0.02);
        assertThat(m.field().remainingGasM3(100)).isPositive();
    }

    @Test
    void subsystemSaveLoadRoundTrip() {
        MineSubsystem sim = new MineSubsystem(MiningConfig.defaults());
        MineNetwork m = sim.create(new long[] {Mines.block(1, 1)});
        rebuild(m, drift(ExplosionFront.BARRIER_KG_PER_M2 * Mines.S), false);
        m.field().setConcentration(2, MineGas.CH4, 0.031);
        m.field().setConcentration(4, MineGas.CO, 0.004);
        m.field().depositStoneDust(3, 250);
        m.field().depositCoalDust(Mines.block(2, 1), 40, 0.3);
        m.field().onMined(Mines.block(3, 2), 10, 0.3);
        m.markWorkedRoof(777);
        for (int k = 0; k < 3; k++) m.stepSlow(Mines.K_SLOW);
        double ch4 = m.field().concentration(2, MineGas.CH4), co = m.field().concentration(4, MineGas.CO);
        double gas = m.field().remainingGasM3(100), dust = m.field().dustLoading(Mines.block(2, 1));
        double inert = m.field().inertFraction(3), time = m.field().timeS();

        TreeStateWriter w = new TreeStateWriter();
        sim.save(w);
        MineSubsystem loaded = new MineSubsystem(MiningConfig.defaults());
        loaded.load(new TreeStateReader(w.root()));
        assertThat(loaded.networks()).hasSize(1);
        MineNetwork l = loaded.network(m.id());
        assertThat(l.anchors()).containsExactly(Mines.block(1, 1));
        assertThat(l.workedRoof()).containsExactly(777L);
        assertThat(l.ready()).isFalse();
        rebuild(l, drift(ExplosionFront.BARRIER_KG_PER_M2 * Mines.S), false);
        assertThat(l.field().concentration(2, MineGas.CH4)).isEqualTo(ch4);
        assertThat(l.field().concentration(4, MineGas.CO)).isEqualTo(co);
        assertThat(l.field().remainingGasM3(100)).isEqualTo(gas);
        assertThat(l.field().dustLoading(Mines.block(2, 1))).isEqualTo(dust);
        assertThat(l.field().inertFraction(3)).isCloseTo(inert, within(1e-15));
        assertThat(l.field().timeS()).isEqualTo(time);
        // загруженная, но ещё не залитая шахта сохраняется в тот же хеш состояния
        TreeStateWriter again = new TreeStateWriter();
        MineSubsystem fresh = new MineSubsystem(MiningConfig.defaults());
        fresh.load(new TreeStateReader(w.root()));
        fresh.save(again);
        assertThat(StateHash.of(again.root())).isEqualTo(StateHash.of(w.root()));
        // id следующей шахты не повторяется
        assertThat(loaded.create(new long[0]).id()).isGreaterThan(m.id());
    }
}
