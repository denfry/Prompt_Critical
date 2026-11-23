package dev.denfry.promptcritical.sim.chlor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.denfry.promptcritical.sim.core.MutableStateTree;
import dev.denfry.promptcritical.sim.core.TreeStateReader;
import dev.denfry.promptcritical.sim.core.TreeStateWriter;
import org.junit.jupiter.api.Test;

/** Печь синтеза HCl (10 §10.3.4): баланс, теплота, избыток водорода, кислота 31–36 % и сухой газ. */
class HclSynthesisTest {
    @Test
    void acidModeBurnsAllChlorineWithExcessHydrogen() {
        HclSynthesis f = new HclSynthesis();
        f.step(10, 1, 100, 100);
        assertThat(f.cl2UsedMolS()).isEqualTo(1);
        assertThat(f.h2UsedMolS()).isCloseTo(1.07, within(1e-12));
        assertThat(f.cl2SlipMolS()).isZero();
        assertThat(f.hclMolS()).isCloseTo(2, within(1e-12));
        // 2,53 МДж на кг HCl
        assertThat(f.heatW() / (f.hclMolS() * HclSynthesis.M_HCL)).isCloseTo(2.53e6, within(0.01e6));
        // 33 %: на 72,9 г HCl — 148 г воды
        double hclKg = 2 * HclSynthesis.M_HCL;
        assertThat(f.acidKgS()).isCloseTo(hclKg / 0.33, within(1e-9));
        assertThat(hclKg / f.acidKgS()).isCloseTo(0.33, within(1e-12));
        assertThat(f.waterUsedKgS()).isCloseTo(f.acidKgS() - hclKg, within(1e-12));
    }

    @Test
    void limitsByHydrogenWaterRoomAndCapacity() {
        HclSynthesis f = new HclSynthesis();
        f.step(0.535, 10, 100, 100);
        assertThat(f.cl2UsedMolS()).isCloseTo(0.5, within(1e-9));
        f.step(100, 100, 100, 100);
        assertThat(f.cl2UsedMolS()).isEqualTo(HclSynthesis.CAPACITY_CL2_MOL_S);
        f.step(100, 100, 100, 1);
        assertThat(f.hclMolS()).isCloseTo(1, within(1e-9));
        double water = 0.5 * 2 * HclSynthesis.M_HCL * HclSynthesis.waterPerKgHcl(0.33);
        f.step(100, 100, water, 100);
        assertThat(f.cl2UsedMolS()).isCloseTo(0.5, within(1e-9));
        f.setMode(HclSynthesis.Mode.DRY);
        f.step(100, 1, 0, 100);
        assertThat(f.hclMolS()).isCloseTo(2, within(1e-12));
        assertThat(f.acidKgS()).isZero();
        assertThat(f.waterUsedKgS()).isZero();
        f.setOn(false);
        f.step(100, 1, 100, 100);
        assertThat(f.cl2UsedMolS()).isZero();
    }

    @Test
    void lowHydrogenExcessLetsChlorineSlip() {
        assertThat(HclSynthesis.slipFraction(1.07)).isZero();
        assertThat(HclSynthesis.slipFraction(1.05)).isZero();
        assertThat(HclSynthesis.slipFraction(1.00)).isEqualTo(HclSynthesis.SLIP_AT_STOICHIOMETRY);
        HclSynthesis f = new HclSynthesis();
        f.setExcess(0.5);
        assertThat(f.excess()).isEqualTo(HclSynthesis.MIN_EXCESS);
        f.step(10, 1, 100, 100);
        assertThat(f.cl2SlipMolS()).isCloseTo(0.05, within(1e-12));
        assertThat(f.hclMolS()).isCloseTo(1.9, within(1e-12));
        f.setStrength(0.5);
        assertThat(f.strength()).isEqualTo(HclSynthesis.MAX_STRENGTH);
    }

    @Test
    void savesAndLoadsSettings() {
        HclSynthesis f = new HclSynthesis();
        f.setMode(HclSynthesis.Mode.DRY);
        f.setExcess(1.12);
        f.setStrength(0.31);
        f.setOn(false);
        MutableStateTree tree = new MutableStateTree();
        f.save(new TreeStateWriter(tree));
        HclSynthesis g = new HclSynthesis();
        g.load(new TreeStateReader(tree));
        assertThat(g.mode()).isEqualTo(HclSynthesis.Mode.DRY);
        assertThat(g.excess()).isEqualTo(1.12);
        assertThat(g.strength()).isEqualTo(0.31);
        assertThat(g.on()).isFalse();
    }
}
