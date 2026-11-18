package dev.denfry.promptcritical.sim.isotopes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.denfry.promptcritical.sim.radiation.ActivityInventory;
import dev.denfry.promptcritical.sim.radiation.Emitter;
import dev.denfry.promptcritical.sim.radiation.SpentFuel;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Изотопы и РИТЭГ против раздела 12 GDD (T12.x) и справочных величин: удельная активность Co-60 и Ir-192, тепловыделение
 * Pu-238 и Sr-90, «Бета-М» 230 Вт(т) → ≈ 10 Вт(э), КПД SiGe, спад РИТЭГ, гамма-постоянная Ir-192, категории МАГАТЭ.
 */
class IsotopesTest {
    private static final double DAY = 86400, YEAR = 365.25 * DAY;

    private static double[] irradiate(TargetMaterial m, double massKg, double phi, double fss, double t, int steps) {
        double[] a = m.freshAtoms(massKg);
        for (int i = 0; i < steps; i++) m.step(a, phi, fss, t / steps, a);
        return a;
    }

    @Test
    void cobalt60MatchesWorkedExampleT12_1() {
        // 1 кг Co, φ = 10¹³, 1 год, f_ss = 1: пример GDD — 468 ТБк при неубывающей мишени и без выгорания продукта
        double nt = TargetMaterial.COBALT_METAL.targetAtoms(1), a0 = 37.2e-24 * 1e13, lambda = Math.log(2) / (5.2714 * YEAR);
        double[] ideal = new double[2];
        BatemanStepper.step(new double[] {0, lambda}, new double[] {0, a0}, new double[] {nt, 0}, YEAR, ideal);
        assertThat(lambda * ideal[1] / 4.68e14).isCloseTo(1, within(0.005));
        // полная модель: Co-59 за год выгорает на 1,2 % (aт = 0,0117) — в среднем мишени на 0,6 % меньше, Co-60 ещё −0,1 %;
        // пример GDD этим пренебрегает, поэтому полная активность на 0,5–0,7 % ниже 468 ТБк
        double[] a = irradiate(TargetMaterial.COBALT_METAL, 1, 1e13, 1, YEAR, 1);
        double bq = TargetMaterial.COBALT_METAL.activityBq(a, 1);
        assertThat(bq / (lambda * ideal[1])).isBetween(0.993, 0.996);
        // удельная 12,6 Ки/г
        assertThat(bq / 3.7e10 / 1000).isCloseTo(12.6, within(0.15));
        // шаг не влияет: 365 суточных шагов — то же самое
        double[] b = irradiate(TargetMaterial.COBALT_METAL, 1, 1e13, 1, YEAR, 365);
        assertThat(TargetMaterial.COBALT_METAL.activityBq(b, 1) / bq).isCloseTo(1, within(1e-9));
    }

    @Test
    void saturatedCobaltReachesTheoreticalSpecificActivity() {
        // чистый Co-60: λN_A/M = 4,19·10¹³ Бк/г (1130 Ки/г) — предел, к которому не подойти из-за выгорания мишени
        double specific = Math.log(2) / (5.2714 * YEAR) * TargetMaterial.AVOGADRO / 59.934;
        assertThat(specific).isCloseTo(4.19e13, within(0.02e13));
        // при φ = 10¹⁴ за 3 года — сотни Ки/г, как у мощных реакторов (12 §12.2.2: 100–250 Ки/г)
        double[] a = irradiate(TargetMaterial.COBALT_METAL, 1e-3, 1e14, 1, 3 * YEAR, 36);
        assertThat(TargetMaterial.COBALT_METAL.activityBq(a, 1) / 3.7e10).isBetween(100.0, 400.0);
    }

    @Test
    void iridium192MatchesWorkedExampleT12_2() {
        double[] a = irradiate(TargetMaterial.IRIDIUM_METAL, 0.010, 1e13, 1, 30 * DAY, 1);
        assertThat(TargetMaterial.IRIDIUM_METAL.activityBq(a, 1) / 2.66e13).isCloseTo(1, within(0.01));
    }

    @Test
    void batemanLimitWhenRatesCoincideT12_3() {
        double[] loss = {1e-6, 1e-6}, feed = {0, 1e-6}, out = new double[2];
        BatemanStepper.step(loss, feed, new double[] {1e20, 0}, 1e5, out);
        double expected = 1e-6 * 1e20 * 1e5 * Math.exp(-0.1);
        assertThat(out[1] / expected).isCloseTo(1, within(1e-9));
        // три равные скорости: N₂ = f²N₀t²e^{−at}/2
        double[] o3 = new double[3];
        BatemanStepper.step(new double[] {1e-6, 1e-6, 1e-6}, new double[] {0, 1e-6, 1e-6}, new double[] {1e20, 0, 0}, 1e5, o3);
        assertThat(o3[2] / (1e-12 * 1e20 * 1e10 * Math.exp(-0.1) / 2)).isCloseTo(1, within(1e-6));
        assertThat(Double.isFinite(o3[1]) && Double.isFinite(o3[2])).isTrue();
    }

    @Test
    void stiffStepReachesEquilibriumT12_4() {
        // Tc-99m при шаге 300 ч: λΔt ≈ 35…50 — N_P = aN_T/b без переполнения
        double lambda = Math.log(2) / (6.01 * 3600), a = 1e-9, n = 1e24, dt = 50 / lambda;
        double[] out = new double[2];
        BatemanStepper.step(new double[] {a, lambda}, new double[] {0, a}, new double[] {n, 0}, dt, out);
        assertThat(out[1] / (a * n * Math.exp(-a * dt) / (lambda - a))).isCloseTo(1, within(1e-3));
    }

    @Test
    void threeLinkChainMatchesSmallStepIntegration() {
        // Np-237 → Np-238 → Pu-238 одним шагом против 20 000 мелких явных шагов Эйлера
        TargetMaterial np = TargetMaterial.NEPTUNIUM_OXIDE;
        double phi = 1e14, t = 60 * DAY, fss = 1;
        double[] exact = irradiate(np, 0.1, phi, fss, t, 1);
        double[] e = np.freshAtoms(0.1);
        int steps = 20000;
        double h = t / steps;
        double s0 = 175.9e-24 * phi, l1 = np.chain.get(1).lambda() + 2088e-24 * phi, l2 = np.chain.get(2).lambda() + 557e-24 * phi;
        for (int i = 0; i < steps; i++) {
            double d0 = -s0 * e[0], d1 = s0 * e[0] - l1 * e[1], d2 = np.chain.get(1).lambda() * e[1] - l2 * e[2];
            e[0] += h * d0;
            e[1] += h * d1;
            e[2] += h * d2;
        }
        assertThat(exact[2] / e[2]).isCloseTo(1, within(2e-3));
        assertThat(exact[1] / e[1]).isCloseTo(1, within(2e-3));
    }

    @Test
    void plutonium238YieldPeaksThenFalls() {
        // 12 §12.2.4: выход Pu-238 на загруженный Np-237 при φ = 10¹⁴ растёт, затем падает из-за выгорания Pu-238
        TargetMaterial np = TargetMaterial.NEPTUNIUM_OXIDE;
        double n0 = np.targetAtoms(1), best = 0, bestT = 0;
        for (int months = 1; months <= 120; months++) {
            double[] a = irradiate(np, 1, 1e14, 1, months * 30 * DAY, 1);
            double y = a[2] / n0;
            if (y > best) {
                best = y;
                bestT = months;
            }
        }
        assertThat(best).isBetween(0.10, 0.35);
        assertThat(bestT).isBetween(6.0, 110.0);
    }

    @Test
    void selfShieldingT12_12() {
        assertThat(TargetMaterial.selfShielding(1.0)).isCloseTo(0.632, within(0.001));
        // кобальтовая таблетка Ø 6 мм из примера §12.1.3: Σ_a = 3,35 см⁻¹, f ≈ 0,63
        assertThat(TargetMaterial.COBALT_METAL.sigmaAMacro()).isCloseTo(3.38, within(0.05));
        assertThat(TargetMaterial.COBALT_METAL.selfShielding()).isCloseTo(0.63, within(0.01));
    }

    @Test
    void lazyDecayAndCategoryT12_9() {
        ActivityInventory ir = new ActivityInventory(Map.of(Emitter.IR192, 4e12), 0.01, 22.56);
        ActivityInventory later = ir.decayed(73.829 * DAY);
        assertThat(later.bq(Emitter.IR192)).isCloseTo(2e12, within(2e9));
        assertThat(IaeaCategory.of(later)).isEqualTo(2);
        // облучатель Co-60 на 470 ТБк — категория 1, тритиевая подсветка — 5
        assertThat(IaeaCategory.of(new ActivityInventory(Map.of(Emitter.CO60, 4.7e14), 1, 8.9))).isEqualTo(1);
        assertThat(IaeaCategory.of(new ActivityInventory(Map.of(Emitter.CS137, 1e8), 0, 1))).isEqualTo(5);
    }

    @Test
    void iridiumGammaConstantMatchesHandbook() {
        // керм-постоянная Ir-192 ≈ 0,11–0,13 мГр·м²/(ГБк·ч) (справочники по дефектоскопии)
        assertThat(Emitter.IR192.kermaConstant()).isBetween(0.10, 0.15);
    }

    @Test
    void heatSourceSpecificPowers() {
        // Pu-238: 0,568 Вт/г; Sr-90 + Y-90 в равновесии: 0,92 Вт/г металла Sr-90 (12 §12.5.3)
        double puBqPerG = Math.log(2) / (87.7 * YEAR) * TargetMaterial.AVOGADRO / 238.05;
        double w = new ActivityInventory(Map.of(Emitter.PU238, puBqPerG), 0, 1).decayHeatW();
        assertThat(w).isCloseTo(0.568, within(0.003));
        double srBqPerG = Math.log(2) / (28.79 * YEAR) * TargetMaterial.AVOGADRO / 89.908;
        assertThat(srBqPerG).isCloseTo(5.1e12, within(0.05e12));
        double sr = new ActivityInventory(Map.of(Emitter.SR90, srBqPerG, Emitter.Y90, srBqPerG), 0, 1).decayHeatW();
        assertThat(sr).isCloseTo(0.92, within(0.01));
        // нейтроны Pu-238 в оксиде: 1,5·10⁴ н/(с·г)
        assertThat(Emitter.PU238.source(puBqPerG).neutrons()).isCloseTo(1.5e4, within(300.0));
    }

    @Test
    void thermoelectricEfficiencyT12_7() {
        double te = Rtg.teEfficiency(0.6, 1273, 573);
        assertThat(te).isCloseTo(0.0849, within(0.0004));
        assertThat(0.8 * te).isCloseTo(0.0679, within(0.0004));
    }

    @Test
    void betaMGivesAboutTenWatts() {
        // «Бета-М»: ≈ 230 Вт тепла от Sr-90 (≈ 35 кКи) → ≈ 10 Вт электричества
        double bq = 230 / ((Emitter.SR90.heatMeV + Emitter.Y90.heatMeV) * 1.602176634e-13);
        assertThat(bq / 3.7e10).isBetween(33e3, 36e3);
        Rtg.State s = Rtg.steady(Rtg.BETA_M, 230, 293.15, 1, 0, 0.8, 0.03);
        assertThat(s.electricW()).isBetween(8.0, 12.0);
        assertThat(s.hotK()).isBetween(550.0, 700.0);
        assertThat(s.overtemp()).isFalse();
        // под водой холоднее рёбра — больше электричества
        assertThat(Rtg.steady(Rtg.BETA_M, 230, 278.15, 0.3, 0, 0.8, 0.03).electricW()).isGreaterThan(s.electricW());
    }

    @Test
    void rtgDecaysWithHalfLifeAndAgingT12_16() {
        // 1 кг PuO₂ при чистоте 85 % → ≈ 400 Вт; через 87,7 г тепла вдвое меньше
        double bq = 1000 * 0.882 * 0.85 * Math.log(2) / (87.7 * YEAR) * TargetMaterial.AVOGADRO / 238.05;
        ActivityInventory pu = new ActivityInventory(Map.of(Emitter.PU238, bq), 1, 11.5);
        double p0 = pu.decayHeatW(), p1 = pu.decayed(87.7 * YEAR).decayHeatW();
        assertThat(p0).isCloseTo(426, within(5.0));
        assertThat(p1 / p0).isCloseTo(0.5, within(0.0025));
        // деградация элементов: 0,97^87,7 при неизменных температурах
        assertThat(Rtg.degradation(87.7, 0.03)).isCloseTo(Math.pow(0.97, 87.7), within(1e-12));
        Rtg.State s0 = Rtg.steady(Rtg.SIGE, p0, 293.15, 1, 0, 0.8, 0.03), s1 = Rtg.steady(Rtg.SIGE, p1, 293.15, 1, 87.7, 0.8, 0.03);
        // вместе с остыванием спаев электричество падает сильнее, чем тепло × деградация
        assertThat(s1.electricW() / s0.electricW()).isLessThan(0.5 * Math.pow(0.97, 87.7)).isGreaterThan(0);
    }

    @Test
    void radiographyExposureAndFilm() {
        // 2 ТБк Ir-192, 0,5 м, стенка 10 мм: секунды физического времени при плёнке 2·10⁻⁴ Гр
        var src = Emitter.IR192.source(2e12);
        double t = Radiography.exposureS(src, 0.5, 1.0, 2e-4);
        assertThat(t).isBetween(0.5, 10.0);
        // толще стенка — дольше
        assertThat(Radiography.exposureS(src, 0.5, 3.0, 2e-4)).isGreaterThan(t * 2);
        assertThat(Radiography.film(2e-4, 2e-4)).isEqualTo(Radiography.Film.GOOD);
        assertThat(Radiography.film(0.5e-4, 2e-4)).isEqualTo(Radiography.Film.UNDEREXPOSED);
        assertThat(Radiography.film(5e-4, 2e-4)).isEqualTo(Radiography.Film.OVEREXPOSED);
    }

    @Test
    void stepwiseFissionProductsMatchDischarge() {
        // блочки, облучённые шагами с постоянной скоростью делений, — те же активности, что и разовая оценка при выгрузке
        double hm = 1, bu = 0.5, wPerG = 1;
        ActivityInventory once = SpentFuel.discharge(hm, bu, wPerG, 19, false);
        double fissions = SpentFuel.fissions(hm, bu), seconds = hm * bu * SpentFuel.J_PER_MWD / (hm * 1000 * wPerG);
        ActivityInventory inv = new ActivityInventory(Map.of(), hm, 19);
        int steps = 200;
        for (int i = 0; i < steps; i++) inv = SpentFuel.irradiate(inv, fissions / seconds, seconds / steps);
        assertThat(inv.bq(Emitter.CS137) / once.bq(Emitter.CS137)).isCloseTo(1, within(1e-6));
        assertThat(inv.bq(Emitter.I131) / once.bq(Emitter.I131)).isCloseTo(1, within(1e-6));
        assertThat(inv.bq(Emitter.Y90) / inv.bq(Emitter.SR90)).isCloseTo(1, within(0.02));
    }

    @Test
    void capsuleOverheatsWithBadCooling() {
        // 100 г кобальта на номинале: 500 Вт γ-нагрева; в воде канала (0,05 К/Вт) — 25 К над водой, а в газе — перегрев
        double[] a = TargetMaterial.COBALT_METAL.freshAtoms(0.1);
        double p = CapsuleThermal.powerW(TargetMaterial.COBALT_METAL, a, 0.1, 5, 1);
        assertThat(p).isCloseTo(500, within(1.0));
        assertThat(CapsuleThermal.tempK(553, p, 0.05)).isLessThan(TargetMaterial.COBALT_METAL.capsuleLimitK);
        assertThat(CapsuleThermal.tempK(553, 3 * p, 0.5)).isGreaterThan(TargetMaterial.COBALT_METAL.capsuleLimitK);
    }
}
