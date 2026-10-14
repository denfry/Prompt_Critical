package dev.denfry.promptcritical.sim.mining;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.denfry.promptcritical.sim.combustion.FuelSpec;
import org.junit.jupiter.api.Test;

/** {@code coal_assay} (08 §8.2.1) и марки угля (08 §8.1.4). */
class CoalAssayTest {
    /** T8.1: уголь Г (V^daf 0,37, A^d 0,213, W^r 0,06) → toFuel() → Q_н^р = 23,9 МДж/кг ±5 %. */
    @Test
    void t8_1_gradeGCoalHeatingValue() {
        CoalAssay g = CoalAssay.typical(0.37, 0.213, 0.06, 0.016, 10);
        assertThat(g.rank()).isEqualTo(CoalRank.G);
        FuelSpec f = g.toFuel();
        assertThat(f.lhv()).isCloseTo(23.9e6, within(0.05 * 23.9e6));
        assertThat(f.ash()).isCloseTo(0.20022, within(1e-5));
        assertThat(f.c()).as("C^r, в 07 §7.8.1 — 0,60").isBetween(0.57, 0.62);
        assertThat(f.c() + f.h() + f.o() + f.n() + f.s() + f.ash() + f.moisture()).isCloseTo(1, within(1e-12));
    }

    @Test
    void ranksByVolatilesAndPlasticLayer() {
        assertThat(CoalRank.classify(0.45, 0)).isEqualTo(CoalRank.B);
        assertThat(CoalRank.classify(0.40, 3)).isEqualTo(CoalRank.D);
        assertThat(CoalRank.classify(0.37, 10)).isEqualTo(CoalRank.G);
        assertThat(CoalRank.classify(0.30, 20)).isEqualTo(CoalRank.ZH);
        assertThat(CoalRank.classify(0.22, 15)).isEqualTo(CoalRank.K);
        assertThat(CoalRank.classify(0.15, 8)).isEqualTo(CoalRank.OS);
        assertThat(CoalRank.classify(0.12, 3)).isEqualTo(CoalRank.T);
        assertThat(CoalRank.classify(0.05, 0)).isEqualTo(CoalRank.A);
        assertThat(CoalRank.K.coking()).isTrue();
        assertThat(CoalRank.A.coking()).isFalse();
    }

    /** Теплота сгорания растёт от бурого угля к антрациту на daf и падает с влагой и золой. */
    @Test
    void heatingValueAcrossRanks() {
        FuelSpec lignite = CoalAssay.typical(0.50, 0.15, 0.35, 0.01, 0).toFuel();
        FuelSpec coking = CoalAssay.typical(0.22, 0.08, 0.08, 0.008, 15).toFuel();
        FuelSpec anthracite = CoalAssay.typical(0.04, 0.10, 0.05, 0.01, 0).toFuel();
        assertThat(lignite.lhv()).isBetween(11e6, 17e6);
        assertThat(coking.lhv()).isBetween(28e6, 32e6);
        assertThat(anthracite.lhv()).isBetween(27e6, 31e6);
        assertThat(anthracite.o()).isGreaterThan(0);
    }

    @Test
    void densityProfileReproducesAsh() {
        for (double ash : new double[] {0.15, 0.213, 0.30, 0.45}) {
            float[] w = CoalAssay.densityProfile(ash);
            double sum = 0, a = 0;
            for (int i = 0; i < w.length; i++) {
                sum += w[i];
                a += w[i] * CoalAssay.FRACTION_ASH[i];
            }
            assertThat(sum).isCloseTo(1, within(1e-6));
            assertThat(a).isCloseTo(ash, within(1e-6));
        }
    }
}
