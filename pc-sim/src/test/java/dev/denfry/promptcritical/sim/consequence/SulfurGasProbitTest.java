package dev.denfry.promptcritical.sim.consequence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

/** Пробиты сероводорода против T9.42 (09 §9.11.8) и сернистого ангидрида против CCPS. */
class SulfurGasProbitTest {
    private static double p(ToxicGas g, double ppm, double min) {
        return g.lethality(g.load(ppm, min));
    }

    @Test
    void hydrogenSulfideT9_42() {
        assertThat(p(ToxicGas.HYDROGEN_SULFIDE, 500, 10)).isCloseTo(0.0029, within(0.002));
        assertThat(p(ToxicGas.HYDROGEN_SULFIDE, 1000, 10)).isCloseTo(0.587, within(0.002));
        assertThat(p(ToxicGas.HYDROGEN_SULFIDE, 300, 30)).isCloseTo(0.049, within(0.002));
    }

    @Test
    void sulfurDioxideIsMilderPerPpmThanHydrogenSulfide() {
        // CCPS: LC50 SO₂ за 10 мин ≈ e^{(5 + 15,67)/2,1}/10 ≈ 1,9·10³ ppm; за 30 мин — втрое меньше
        double lc10 = ToxicGas.SULFUR_DIOXIDE.lc50Ppm(10);
        assertThat(lc10).isBetween(1500.0, 2500.0);
        assertThat(ToxicGas.SULFUR_DIOXIDE.lc50Ppm(30) / lc10).isCloseTo(1.0 / 3, within(1e-9));
        assertThat(p(ToxicGas.SULFUR_DIOXIDE, 1000, 10)).isLessThan(p(ToxicGas.HYDROGEN_SULFIDE, 1000, 10));
    }
}
