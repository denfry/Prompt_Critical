package dev.denfry.promptcritical.sim.refining;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class RefineryUpgradingTest {
    static final double T360 = 633.15;

    /** GDD 09 §9.9.6: дизель 1,2 % S при 360 °C и 6 МПа — 50 ppm при LHSV 1,5; 23 ppm при 1; 85 ppm при 2. */
    @Test
    void hdsMatchesBalancePointsAgainstLhsv() {
        assertThat(Hydrotreating.outletSulfur(0.012, 1.5, T360, 6, 1) * 1e6).isCloseTo(50, within(3.0));
        assertThat(Hydrotreating.outletSulfur(0.012, 1.0, T360, 6, 1) * 1e6).isCloseTo(23, within(2.0));
        assertThat(Hydrotreating.outletSulfur(0.012, 2.0, T360, 6, 1) * 1e6).isCloseTo(85, within(4.0));
    }

    /** −20 К уменьшают k вдвое (E = 110 кДж/моль); ниже давление H₂ и H₂S в рецикле — хуже очистка. */
    @Test
    void hdsTemperaturePressureAndH2sInhibition() {
        assertThat(Hydrotreating.rateConstant(T360 - 20) / Hydrotreating.rateConstant(T360)).isCloseTo(0.5, within(0.03));
        double base = Hydrotreating.outletSulfur(0.012, 1.5, T360, 6, 1);
        assertThat(Hydrotreating.outletSulfur(0.012, 1.5, T360 - 20, 6, 1)).isGreaterThan(2 * base);
        assertThat(Hydrotreating.outletSulfur(0.012, 1.5, T360, 3, 1)).isGreaterThan(base);
        assertThat(Hydrotreating.h2sInhibition(2)).isCloseTo(0.5, within(1e-12));
    }

    /** Сероводород и водород по стехиометрии; прямогонный дизель греет слой на 10–25 К; кокс за 3 года — полактивности. */
    @Test
    void hdsBalancesAndAging() {
        Hydrotreating h = new Hydrotreating();
        Hydrotreating.Result r = h.treat(0.012, 1.5, T360 - 7, 6, 0.3, 0);
        assertThat(r.conversion()).isGreaterThan(0.99);
        assertThat(r.h2sKgPerKg()).isCloseTo((0.012 - r.productSulfur()) * 34.08 / 32.06, within(1e-9));
        assertThat(r.bedRiseK()).isBetween(10.0, 25.0);
        assertThat(r.hydrocracking()).isFalse();
        // химический расход водорода прямогонного ДТ — 0,2–0,5 % мас. (Gary, гл. 9)
        assertThat(r.h2KgPerKg()).isBetween(0.002, 0.005);
        h.age(3 * 365.25 * 86400, T360, 6);
        assertThat(h.activity()).isCloseTo(0.5, within(0.01));
    }

    /** Claus: X_eq по узлам и интерполяция; пример GDD 300/240/240 °C → 97,4 %. */
    @Test
    void clausStagesAndOverallRecovery() {
        assertThat(ClausUnit.equilibriumConversion(300)).isEqualTo(0.50);
        assertThat(ClausUnit.equilibriumConversion(270)).isCloseTo(0.55, within(1e-12));
        assertThat(ClausUnit.equilibriumConversion(200)).isEqualTo(0.65);
        ClausUnit c = new ClausUnit();
        ClausUnit.Result r = c.run(1, new double[] {300, 240, 240}, 0);
        assertThat(r.remaining()[0]).isCloseTo(0.33, within(1e-12));
        assertThat(r.remaining()[1]).isCloseTo(0.165, within(1e-12));
        assertThat(r.recovery()).isCloseTo(0.974, within(1e-3));
        assertThat(r.wetStage()).isEqualTo(-1);
        // две ступени — 93–95 %: типичная нижняя граница
        assertThat(c.run(1, new double[] {300, 240}, 0).recovery()).isBetween(0.92, 0.95);
    }

    /** GDD: 1000 кг/ч H₂S → 940,7 кг/ч серы по стехиометрии × 0,96 = 903 кг/ч; ошибка воздуха 10 % — минус 3 %. */
    @Test
    void clausMassBalanceAndAirRatio() {
        ClausUnit c = new ClausUnit();
        double kgS = 1000.0 / 3600;
        ClausUnit.Result r = c.run(kgS, new double[] {300, 250, 230}, 0);
        assertThat(r.recovery()).isBetween(0.95, 0.975);
        double stoich = 1000 / 34.08 * 32.06;
        assertThat(stoich).isCloseTo(940.7, within(0.5));
        assertThat(r.sulfurKgS() * 3600).isCloseTo(stoich * r.recovery(), within(0.1));
        assertThat(r.so2KgS() * 3600).isCloseTo(stoich * (1 - r.recovery()) * 64.07 / 32.06, within(0.1));
        ClausUnit.Result off = c.run(kgS, new double[] {300, 250, 230}, 0.10);
        assertThat(r.recovery() - off.recovery()).isCloseTo(0.03, within(1e-9));
        assertThat(off.so2KgS()).isGreaterThan(r.so2KgS());
        // термическая ступень: треть H₂S сгорает, 518 кДж/моль
        assertThat(r.boilerW()).isCloseTo(kgS * 1000 / 34.08 / 3 * 518e3, within(1.0));
    }

    /** Слой холоднее точки росы + 10 К: флаг и падение активности. */
    @Test
    void clausDewPointLimit() {
        ClausUnit c = new ClausUnit();
        assertThat(c.run(1, new double[] {215, 240}, 0).wetStage()).isEqualTo(1);
        c.ageStage(0, 200, 86400);
        assertThat(c.activity(0)).isLessThan(0.5);
        assertThat(c.run(1, new double[] {300, 240}, 0).recovery()).isLessThan(new ClausUnit().run(1, new double[] {300, 240}, 0).recovery());
    }

    /** Smith 1959, нафта P50/N40/A10 (ОЧИ ≈ 64) при 500 °C, 2,5 МПа: реформат ОЧИ ≈ 80, H₂ 1,5–3 % мас., выход 85–95 % об., ΔT первого реактора 50–90 К. */
    @Test
    void reformingOctaneHydrogenAndTemperatureDrop() {
        CatalyticReforming cr = new CatalyticReforming();
        CatalyticReforming.Feed f = CatalyticReforming.Feed.HEAVY_NAPHTHA;
        CatalyticReforming.Result r = cr.run(f, 773.15, 2.5, 6, 1.5, 0);
        assertThat(f.a()).isEqualTo(0.10);
        assertThat(r.ron()).isBetween(78.0, 90.0);
        assertThat(r.h2Mass()).isBetween(0.015, 0.03);
        assertThat(r.reformateVolume()).isBetween(0.85, 0.95);
        assertThat(r.reformateMass() + r.h2Mass() + r.gasMass()).isCloseTo(1, within(0.01));
        assertThat(773.15 - r.outletK()[0]).isBetween(50.0, 90.0);
        assertThat(r.outletK()[0]).isLessThan(r.outletK()[2]);
    }

    /** Выше T — больше октан и меньше выход; сера в сырье глушит Pt; кокс снижает активность. */
    @Test
    void reformingSeverityPoisonAndCoke() {
        CatalyticReforming.Feed f = CatalyticReforming.Feed.HEAVY_NAPHTHA;
        CatalyticReforming.Result mild = new CatalyticReforming().run(f, 753.15, 2.5, 6, 1.5, 0);
        CatalyticReforming.Result hard = new CatalyticReforming().run(f, 788.15, 2.5, 6, 1.5, 0);
        assertThat(hard.ron()).isGreaterThan(mild.ron());
        assertThat(hard.reformateVolume()).isLessThan(mild.reformateVolume());
        CatalyticReforming.Result sour = new CatalyticReforming().run(f, 773.15, 2.5, 6, 1.5, 1);
        assertThat(sour.ron()).isLessThan(new CatalyticReforming().run(f, 773.15, 2.5, 6, 1.5, 0).ron());
        CatalyticReforming cr = new CatalyticReforming();
        cr.age(9 * 30.4 * 86400, 773.15, 6, 0.65);
        assertThat(cr.activity()).isCloseTo(Math.exp(-1), within(0.01));
        cr.regenerate(false);
        assertThat(cr.activity()).isCloseTo(0.85, within(1e-9));
        // K₁ при 500 °C ~ 10⁵…10⁶ атм³ (ΔG° ≈ −95 кДж/моль для C₆H₁₂ → C₆H₆): дегидрирование нафтенов практически необратимо
        assertThat(CatalyticReforming.eq1(773.15)).isBetween(1e5, 5e6);
    }
}
