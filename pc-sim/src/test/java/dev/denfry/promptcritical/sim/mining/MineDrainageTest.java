package dev.denfry.promptcritical.sim.mining;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.denfry.promptcritical.sim.core.TreeStateReader;
import dev.denfry.promptcritical.sim.core.TreeStateWriter;
import org.junit.jupiter.api.Test;

/** Водоотлив шахты (08 §8.2.9): приток от добычи и фильтрации, водосборник, затопление, насосы. */
class MineDrainageTest {
    private static final DrainageConfig CFG = new DrainageConfig(1.0, 3.6, 100, 3600, 1.5);

    /** Без насосов фильтрация 3,6 м³/ч = 0,001 м³/с наполняет водосборник 100 м³ за 1e5 с, дальше — затопление. */
    @Test
    void filtrationFillsSumpThenFloods() {
        MineDrainage d = new MineDrainage(CFG);
        for (int i = 0; i < 1200; i++) d.stepSlow(100);
        assertThat(d.inflowM3s()).isCloseTo(0.001, within(1e-9));
        assertThat(d.volumeM3()).isCloseTo(120, within(1e-6));
        assertThat(d.floodedM3()).isCloseTo(20, within(1e-6));
        assertThat(d.levelFrac()).isGreaterThan(1);
    }

    /** Вода, вскрытая добычей, стекает с τ: за τ — 63 %; насос с запасом держит водосборник пустым. */
    @Test
    void minedWaterReleasesExponentiallyAndPumpKeepsUp() {
        DrainageConfig noFiltration = new DrainageConfig(1.0, 0, 100, 3600, 1.5);
        MineDrainage d = new MineDrainage(noFiltration);
        d.onMined(50, 0.5);
        for (int i = 0; i < 36; i++) d.stepSlow(100);
        assertThat(d.volumeM3()).isCloseTo(50 * (1 - Math.exp(-1)), within(1e-6));
        assertThat(d.acidic()).isFalse();

        d.setPumpCapacityM3s(100.0 / 3600);
        for (int i = 0; i < 100; i++) d.stepSlow(100);
        assertThat(d.volumeM3()).isZero();
        assertThat(d.pumpLoad()).isLessThan(1);
        assertThat(d.pumpedM3s()).isCloseTo(d.inflowM3s(), within(1e-9));
    }

    /** Сернистый уголь или пирит — вода кислая. */
    @Test
    void sulfurousCoalMakesAcidWater() {
        MineDrainage d = new MineDrainage(CFG);
        d.onMined(1.3, 2.4);
        assertThat(d.acidic()).isTrue();
    }

    /** ПБ: водосборник ≥ 4 ч притока. */
    @Test
    void capacityRule() {
        MineDrainage d = new MineDrainage(CFG);
        d.stepSlow(1);
        assertThat(d.capacityOk()).isTrue();                     // 4 ч × 3,6 = 14,4 м³ ≤ 100
        MineDrainage small = new MineDrainage(new DrainageConfig(1.0, 36, 100, 3600, 1.5));
        small.stepSlow(1);
        assertThat(small.capacityOk()).isFalse();                 // 144 м³ > 100
    }

    /** Насос ЦНС 100 м³/ч на 150 м: 58 кВт (§8.2.9); на напоре выше 1,25H — не качает. */
    @Test
    void pumpCurve() {
        MineSump.PumpSpec p = new MineSump.PumpSpec(100.0 / 3600, 150, 0.7, 64);
        assertThat(MineSump.pumpPowerW(100.0 / 3600, 150, 0.7)).isCloseTo(58e3, within(0.5e3));
        assertThat(p.flowAt(64, 150)).isCloseTo(100.0 / 3600, within(1e-9));
        assertThat(p.flowAt(64, 190)).isZero();
        assertThat(p.flowAt(32, 30)).isLessThan(p.flowAt(64, 30));
    }

    @Test
    void networkSavesDrainageAndFloodBlocks() {
        MineNetwork n = new MineNetwork(7, MiningConfig.defaults(), CFG);
        n.drainage().onMined(13, 3);
        n.drainage().stepSlow(600);
        n.setFloodBlocks(new long[] {42, 17, 99});
        TreeStateWriter w = new TreeStateWriter();
        n.save(w);
        MineNetwork m = new MineNetwork(7, MiningConfig.defaults(), CFG);
        m.load(new TreeStateReader(w.root()));
        assertThat(m.drainage().volumeM3()).isEqualTo(n.drainage().volumeM3());
        assertThat(m.drainage().acidic()).isTrue();
        assertThat(m.floodBlocks()).containsExactly(42, 17, 99);
        m.drainage().stepSlow(600);
        n.drainage().stepSlow(600);
        assertThat(m.drainage().volumeM3()).isEqualTo(n.drainage().volumeM3());
    }
}
