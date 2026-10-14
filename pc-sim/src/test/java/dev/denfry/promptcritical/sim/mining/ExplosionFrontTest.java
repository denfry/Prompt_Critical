package dev.denfry.promptcritical.sim.mining;

import static dev.denfry.promptcritical.sim.mining.MineTopology.ATMOSPHERE;
import static dev.denfry.promptcritical.sim.mining.Mines.branchR;
import static dev.denfry.promptcritical.sim.mining.Mines.node;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

/** Взрыв метана и пыли, осланцевание, заслоны (08 §8.2.6). */
class ExplosionFrontTest {
    static final double V = 500;

    /**
     * Цепочка n1–n2–n3–n4 и ответвление n1–[заслон]–n5–n6. Пыль V^daf 30 %, c_d 60 г/м³ везде; x_ин 0,5, у n4 —
     * 0,8; метан 9 % в n1.
     */
    private static final class Gallery {
        final MethaneField f = new MethaneField(MiningConfig.defaults());
        final int n1, n2, n3, n4, n5, n6, barrierBranch;

        Gallery(double barrierKgPerM2) {
            MineTopology.Builder b = MineTopology.builder(0, Mines.T);
            n1 = node(b, 1, V);
            n2 = node(b, 2, V);
            n3 = node(b, 3, V);
            n4 = node(b, 4, V);
            n5 = node(b, 5, V);
            n6 = node(b, 6, V);
            branchR(b, 1, ATMOSPHERE, n1, 0.1, BranchKind.SHAFT);
            branchR(b, 2, n1, n2, 0.1, BranchKind.DRIFT);
            branchR(b, 3, n2, n3, 0.1, BranchKind.DRIFT);
            branchR(b, 4, n3, n4, 0.1, BranchKind.DRIFT);
            barrierBranch = branchR(b, 5, n1, n5, 0.1, BranchKind.DRIFT);
            branchR(b, 6, n5, n6, 0.1, BranchKind.DRIFT);
            b.barrier(barrierBranch, BarrierKind.STONE, barrierKgPerM2 * Mines.S);
            f.rebuild(b.build());
            double coal = 60 * Mines.S / Mines.P; // c_d = m_d·P/S = 60 г/м³
            for (int n : new int[] {n1, n2, n3, n4, n5, n6}) {
                f.depositCoalDust(n, coal, 0.30);
                f.depositStoneDust(n, n == n4 ? 4 * coal : coal);
            }
            f.setConcentration(n1, MineGas.CH4, 0.09);
        }
    }

    /**
     * T8.8: фронт идёт по узлам с x_ин &lt; 0,75 (по узлу за тик), останавливается на заслоне; энергия — только
     * по пройденным узлам.
     */
    @Test
    void t8_8_frontStopsOnStoneDustAndBarrier() {
        Gallery g = new Gallery(400);
        assertThat(g.f.raisedDustGm3(g.n2)).isCloseTo(60, within(1e-9));
        assertThat(g.f.inertFraction(g.n2)).isCloseTo(0.5, within(1e-12));
        assertThat(g.f.inertFraction(g.n4)).isCloseTo(0.8, within(1e-12));

        ExplosionFront front = g.f.ignite(g.n1, IgnitionSource.SPARK);
        assertThat(front.ignited()).isTrue();
        assertThat(front.advance()).isEqualTo(1); // n2; n5 — за заслоном
        assertThat(front.advance()).isEqualTo(1); // n3
        assertThat(front.advance()).isZero(); // n4 осланцован
        assertThat(front.done()).isTrue();

        assertThat(front.burnedCount()).isEqualTo(3);
        assertThat(front.burned(g.n1) && front.burned(g.n2) && front.burned(g.n3)).isTrue();
        assertThat(front.burned(g.n4) || front.burned(g.n5) || front.burned(g.n6)).isFalse();
        assertThat(front.quenchedByBarriers()).isEqualTo(1);
        assertThat(front.burnedAtTick(1)).isEqualTo(1);
        assertThat(front.burnedAtTick(2)).isEqualTo(2);

        double f = MiningConfig.defaults().explosionBlastFraction();
        double dustE = 60 * V / 1000 * MethaneField.Q_DUST;
        assertThat(front.energyJ(0)).isCloseTo(f * (0.09 * V * MethaneField.Q_CH4 + dustE), within(1.0));
        assertThat(front.energyJ(1)).isCloseTo(f * dustE, within(1.0));
        assertThat(front.totalEnergyJ()).isCloseTo(front.energyJ(0) + 2 * f * dustE, within(1.0));

        // рудничный газ в очаге; пыль пройденных узлов сгорела, инертная осталась
        assertThat(g.f.concentration(g.n1, MineGas.CH4)).isZero();
        assertThat(g.f.concentration(g.n1, MineGas.CO)).isBetween(0.01, 0.08);
        assertThat(g.f.concentration(g.n1, MineGas.O2)).isLessThan(0.10);
        assertThat(g.f.concentration(g.n2, MineGas.CO)).isPositive();
        assertThat(g.f.meanCoalDust(g.n2)).isZero();
        assertThat(g.f.inertFraction(g.n2)).isEqualTo(1);
        assertThat(g.f.meanCoalDust(g.n5)).isPositive();
    }

    /** Заслон легче 400 кг/м² фронт не гасит; без осланцевания фронт проходит всё. */
    @Test
    void weakBarrierDoesNotQuench() {
        Gallery g = new Gallery(100);
        ExplosionFront front = g.f.ignite(g.n1, IgnitionSource.OPEN_FLAME).runToEnd();
        assertThat(front.burned(g.n5) && front.burned(g.n6)).isTrue();
        assertThat(front.burned(g.n4)).isFalse();
        assertThat(front.quenchedByBarriers()).isZero();
    }

    /** Искра без метана в пределах ничего не воспламеняет; взрывные работы поднимают пыль сами. */
    @Test
    void ignitionNeedsMethaneUnlessBlasting() {
        Gallery g = new Gallery(400);
        g.f.setConcentration(g.n1, MineGas.CH4, 0.03);
        ExplosionFront spark = g.f.ignite(g.n1, IgnitionSource.SPARK);
        assertThat(spark.ignited()).isFalse();
        assertThat(spark.done()).isTrue();
        assertThat(spark.burnedCount()).isZero();
        assertThat(g.f.concentration(g.n1, MineGas.CH4)).isEqualTo(0.03);

        ExplosionFront blast = g.f.ignite(g.n3, IgnitionSource.BLASTING).runToEnd();
        assertThat(blast.ignited()).isTrue();
        assertThat(blast.burned(g.n2) && blast.burned(g.n1)).isTrue();
        assertThat(blast.burned(g.n5)).as("заслон с той стороны").isFalse();
    }

    /** Таблица 08 §8.2.6 и гибридный предел Бартнехта. */
    @Test
    void stoneDustTable() {
        assertThat(StoneDustRule.requiredInertFraction(0.05)).isZero();
        assertThat(StoneDustRule.requiredInertFraction(0.30)).isEqualTo(0.75);
        assertThat(StoneDustRule.requiredInertFraction(0.325)).isCloseTo(0.765, within(1e-12));
        assertThat(StoneDustRule.requiredInertFraction(0.45)).isEqualTo(0.80);
        assertThat(StoneDustRule.lowerLimitGm3(0.30)).isEqualTo(45);
        assertThat(StoneDustRule.lowerLimitGm3(0.12)).isEqualTo(120);
        assertThat(StoneDustRule.lowerLimitGm3(0.05)).isInfinite();
        assertThat(StoneDustRule.hybridLowerLimitGm3(0.30, 0.03)).isCloseTo(45 * 0.16, within(1e-9));
        assertThat(StoneDustRule.dustExplosive(30, 0.5, 0.30, 0)).isFalse();
        assertThat(StoneDustRule.dustExplosive(30, 0.5, 0.30, 0.02)).isTrue();
        assertThat(StoneDustRule.dustExplosive(60, 0.76, 0.30, 0)).isFalse();
        assertThat(StoneDustRule.methaneExplosive(0.095)).isTrue();
        assertThat(StoneDustRule.methaneExplosive(0.2)).isFalse();
    }
}
