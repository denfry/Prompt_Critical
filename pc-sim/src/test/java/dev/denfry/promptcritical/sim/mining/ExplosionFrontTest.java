package dev.denfry.promptcritical.sim.mining;

import static dev.denfry.promptcritical.sim.mining.MineTopology.ATMOSPHERE;
import static dev.denfry.promptcritical.sim.mining.Mines.branchR;
import static dev.denfry.promptcritical.sim.mining.Mines.node;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.denfry.promptcritical.sim.combustion.Chimney;
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

        // очаг — метан и пыль, дальше одна пыль; энергия узлов с одинаковой пылью одна и та же
        assertThat(front.energyJ(1)).isPositive();
        assertThat(front.energyJ(0)).isGreaterThan(front.energyJ(1));
        assertThat(front.energyJ(2)).isCloseTo(front.energyJ(1), within(1e-6 * front.energyJ(1)));
        assertThat(front.totalEnergyJ()).isCloseTo(front.energyJ(0) + 2 * front.energyJ(1), within(1.0));

        // рудничный газ в очаге; пыль пройденных узлов сгорела, инертная осталась
        assertThat(g.f.concentration(g.n1, MineGas.CH4)).isZero();
        assertThat(g.f.concentration(g.n1, MineGas.CO)).isBetween(0.01, 0.08);
        assertThat(g.f.concentration(g.n1, MineGas.O2)).isLessThan(0.10);
        assertThat(g.f.concentration(g.n2, MineGas.CO)).isPositive();
        assertThat(g.f.meanCoalDust(g.n2)).isZero();
        assertThat(g.f.inertFraction(g.n2)).isEqualTo(1);
        assertThat(g.f.meanCoalDust(g.n5)).isPositive();
    }

    /**
     * Энергия взрыва ограничена кислородом. Теплота на моль смеси — по стадиям H → H₂O, C → CO, CO → CO₂
     * (энтальпии NIST, ≥ 10 % углерода в CO), посчитана вручную; кислород воздуха (1 − x)·20,95 %:
     * 6 % CH₄ — всё сгорает, 46,44 кДж/моль; 9,5 % — 73,53; 15 % — O₂ хватает на водород и часть
     * углерода в CO, 67,53. Пыль 720 г/м³ с V^daf 30 % — кислород кончается на водороде, 0,2095·408,79.
     */
    @Test
    void explosionEnergyIsLimitedByOxygen() {
        double f = MiningConfig.defaults().explosionBlastFraction();
        double molesPerM3 = MineVentilation.AIR_PRESSURE / (Chimney.GAS_CONSTANT * Mines.T);
        double e6 = methaneEnergy(0.06), e95 = methaneEnergy(0.095), e15 = methaneEnergy(0.15);
        assertThat(e6).isCloseTo(f * V * molesPerM3 * 46.44e3, within(1e-3 * e6));
        assertThat(e95).isCloseTo(f * V * molesPerM3 * 73.53e3, within(1e-3 * e95));
        assertThat(e15).isCloseTo(f * V * molesPerM3 * 67.53e3, within(1e-3 * e15));
        assertThat(e15).isLessThan(e95); // пик — у стехиометрии, а не на верхнем пределе

        MineTopology.Builder b = MineTopology.builder(0, Mines.T);
        int n = node(b, 1, V);
        branchR(b, 1, ATMOSPHERE, n, 0.1, BranchKind.SHAFT);
        MethaneField field = new MethaneField(MiningConfig.defaults());
        field.rebuild(b.build());
        field.depositCoalDust(n, 720 * Mines.S / Mines.P, 0.30);
        assertThat(field.raisedDustGm3(n)).isCloseTo(720, within(1e-6));
        field.setConcentration(n, MineGas.O2, 0.2095);
        ExplosionFront dust = field.ignite(n, IgnitionSource.BLASTING);
        assertThat(dust.ignited()).isTrue();
        double eDust = dust.energyJ(0);
        assertThat(eDust).isCloseTo(f * V * molesPerM3 * 0.2095 * 408.79e3, within(1e-3 * eDust));
        assertThat(field.concentration(n, MineGas.O2)).isZero();
    }

    /** Энергия очага одного узла с метаном x в воздухе, без пыли. */
    private static double methaneEnergy(double x) {
        MineTopology.Builder b = MineTopology.builder(0, Mines.T);
        int n = node(b, 1, V);
        branchR(b, 1, ATMOSPHERE, n, 0.1, BranchKind.SHAFT);
        MethaneField field = new MethaneField(MiningConfig.defaults());
        field.rebuild(b.build());
        field.setConcentration(n, MineGas.CH4, x);
        field.setConcentration(n, MineGas.O2, (1 - x) * 0.2095);
        ExplosionFront front = field.ignite(n, IgnitionSource.SPARK);
        assertThat(front.ignited()).isTrue();
        return front.energyJ(0);
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
