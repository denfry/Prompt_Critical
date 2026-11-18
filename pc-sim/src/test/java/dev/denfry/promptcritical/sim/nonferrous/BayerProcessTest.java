package dev.denfry.promptcritical.sim.nonferrous;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

/** Глинозём по Байеру против 08 §8.8.1 и балансов. */
class BayerProcessTest {
    private static final double C = 273.15;

    @Test
    void liquorCarriesTheIncrementBetweenPregnantAndSpentModulus() {
        // 200 г/л NaOH: αк 1,7 — 150 г/л Al₂O₃, αк 3,5 — 73 г/л (Лайнер)
        assertThat(BayerProcess.al2o3KgL(BayerProcess.MODULUS_PREGNANT)).isCloseTo(0.150, within(0.002));
        assertThat(BayerProcess.al2o3KgL(BayerProcess.MODULUS_SPENT)).isCloseTo(0.073, within(0.002));
        assertThat(BayerProcess.NET_AL2O3_KG_L).isCloseTo(0.077, within(0.002));
    }

    @Test
    void gibbsiteDigestionNeedsFourToFiveTonnesOfBauxite() {
        // §8.8.1: 4–5 т боксита на т глинозёма — на гиббситовом режиме 145 °C для смешанной руды жилы
        double t = BayerProcess.bauxitePerAlumina(BayerProcess.DEFAULT_AL2O3, 145 + C);
        assertThat(t).isBetween(4.0, 5.0);
        // бёмитовый режим 245 °C растворяет и бёмит: боксита вдвое меньше
        double hot = BayerProcess.bauxitePerAlumina(BayerProcess.DEFAULT_AL2O3, 245 + C);
        assertThat(hot).isBetween(2.0, 2.3);
        // холодный автоклав ничего не берёт
        assertThat(BayerProcess.bauxitePerAlumina(BayerProcess.DEFAULT_AL2O3, 90 + C)).isInfinite();
    }

    @Test
    void reactiveSilicaEatsFiftyToHundredKilogramsOfCausticPerTonne() {
        double t = BayerProcess.bauxitePerAlumina(BayerProcess.DEFAULT_AL2O3, 145 + C);
        double naohKg = t * 1000 * BayerProcess.naohLossPerKg();
        assertThat(naohKg).isBetween(50.0, 100.0);
    }

    @Test
    void bauxiteMassBalanceCloses() {
        // боксит + Na₂O щёлочи = глинозём в раствор + сухой шлам + кристаллизационная вода
        for (double tc : new double[] {145, 245}) {
            double a = BayerProcess.DEFAULT_AL2O3, tk = tc + C;
            double loi = a * ((1 - BayerProcess.BOEHMITE_SHARE) * BayerProcess.LOI_GIBBSITE
                    + BayerProcess.BOEHMITE_SHARE * BayerProcess.LOI_BOEHMITE);
            double in = 1 + BayerProcess.REACTIVE_SIO2 * BayerProcess.DSP_NA2O_PER_SIO2;
            double out = BayerProcess.netAl2o3PerKg(a, tk) + BayerProcess.redMudPerKg(a, tk) + loi;
            assertThat(out).isCloseTo(in, within(1e-9));
        }
    }

    @Test
    void digesterIsLimitedByLiquorBauxiteAndTemperature() {
        BayerDigester d = new BayerDigester();
        d.setTempK(145 + C);
        double dt = 72;
        double l = d.step(dt, 1e9, 1e9, 0.5);
        assertThat(l).isCloseTo(BayerDigester.LIQUOR_LS * dt, within(1e-9));
        assertThat(d.lastAl2o3KgS()).isCloseTo(BayerDigester.LIQUOR_LS * BayerProcess.NET_AL2O3_KG_L, within(1e-9));
        assertThat(d.lastHeatW()).isPositive();
        // мало боксита — раствора прокачано меньше
        double little = d.lastBauxiteKgS() * dt / 4;
        assertThat(d.step(dt, 1e9, little, 0.5)).isCloseTo(l / 4, within(1e-9));
        // без нагрева — ничего
        d.setTempK(100 + C);
        assertThat(d.step(dt, 1e9, 1e9, 0.5)).isZero();
    }

    @Test
    void precipitatorWorksBestInItsWindow() {
        double y60 = AluminaPrecipitator.yieldAt(60 + C);
        assertThat(y60).isCloseTo(1 - Math.exp(-3), within(1e-9));
        assertThat(AluminaPrecipitator.yieldAt(50 + C)).isEqualTo(y60);
        assertThat(AluminaPrecipitator.yieldAt(90 + C)).isCloseTo(y60 / 2, within(1e-9));
        assertThat(AluminaPrecipitator.yieldAt(80 + C)).isBetween(y60 / 2, y60);
        AluminaPrecipitator p = new AluminaPrecipitator();
        double l = p.step(3600, 10_000);
        assertThat(l).isCloseTo(10_000.0 / AluminaPrecipitator.RESIDENCE_H, within(1e-9));
        // гидроксид: 1,53 кг на кг Al₂O₃
        assertThat(p.lastHydroxideKgS() / p.lastAl2o3KgS()).isCloseTo(1.530, within(0.001));
    }

    @Test
    void calcinerSpendsThreeGigajoulesPerTonneInItsWindow() {
        assertThat(AluminaCalciner.heatJPerKg(1050 + C) / 1e6).isCloseTo(3.1, within(1e-9));
        assertThat(AluminaCalciner.conversion(1000 + C)).isEqualTo(1);
        assertThat(AluminaCalciner.conversion(750 + C)).isCloseTo(0.5, within(1e-9));
        AluminaCalciner k = new AluminaCalciner();
        double kg = k.step(100, 1e9, 1e12);
        assertThat(kg).isCloseTo(AluminaCalciner.CAPACITY_KG_S * 100, within(1e-9));
        // гидроксида хватает на 1 кг глинозёма — больше не выйдет; топлива нет — ничего
        assertThat(k.step(100, BayerProcess.HYDROXIDE_PER_AL2O3, 1e12)).isCloseTo(1, within(1e-9));
        assertThat(k.step(100, 1e9, 0)).isZero();
    }
}
