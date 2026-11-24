package dev.denfry.promptcritical.sim.geothermal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.denfry.promptcritical.sim.core.MutableStateTree;
import dev.denfry.promptcritical.sim.core.TreeStateReader;
import dev.denfry.promptcritical.sim.core.TreeStateWriter;
import dev.denfry.promptcritical.sim.thermo.if97.If97;
import org.junit.jupiter.api.Test;

/** Геотермальная станция 15 §15.6: флеш T15.14, кремнезём, пласт, НКГ, сейсмичность, ORC. */
class GeothermalTest {
    private static final double C = 273.15;

    @Test
    void flashOfSaturatedLiquidAt250CInto150CSeparatorGivesT1514() {
        double h = If97.satLiquid(If97.psat(250 + C)).h();
        assertThat(h).isCloseTo(1085.8e3, within(1085.8e3 * 0.001));
        double x = FlashSeparator.steamFraction(h, If97.psat(150 + C));
        assertThat(x).isCloseTo(0.2146, within(0.2146 * 0.005));
    }

    @Test
    void separatorSplitsMassAndSendsGasWithSteam() {
        double h = If97.satLiquid(If97.psat(250 + C)).h();
        FlashResult r = new FlashSeparator(0.01).separate(h, 100, If97.psat(150 + C));
        assertThat(r.ncgKgS()).isCloseTo(1, within(1e-9));
        assertThat(r.steamKgS() + r.brineKgS() + r.ncgKgS()).isCloseTo(100, within(1e-9));
        assertThat(r.steamKgS()).isCloseTo(99 * r.steamFraction(), within(1e-9));
    }

    @Test
    void liquidBelowSaturationDoesNotFlashAndKeepsItsGas() {
        double h = If97.satLiquid(If97.psat(140 + C)).h();
        FlashResult r = new FlashSeparator(0.01).separate(h, 50, If97.psat(150 + C));
        assertThat(r.steamFraction()).isZero();
        assertThat(r.ncgKgS()).isZero();
        assertThat(r.brineKgS()).isCloseTo(50, within(1e-9));
    }

    @Test
    void equalSplitRulePutsTheSeparatorHalfwayBetweenReservoirAndCondenser() {
        assertThat(FlashSeparator.optimalSeparatorTempK(250 + C, 50 + C)).isCloseTo(150 + C, within(1e-9));
    }

    @Test
    void silicaFromQuartzAt250CReachesAmorphousSaturationAfterFlashTo150C() {
        double res = Silica.quartzMgKg(250 + C);
        assertThat(res).isCloseTo(487, within(3.0));
        assertThat(Silica.amorphousMgKg(150 + C)).isCloseTo(620, within(3.0));
        double after = Silica.afterFlashMgKg(res, 0.2146);
        assertThat(Silica.saturationIndex(after, 150 + C)).isCloseTo(1.0, within(0.01));
        assertThat(Silica.minRejectTempK(after)).isCloseTo(150 + C, within(1.0));
        assertThat(Silica.saturationIndex(after, 120 + C)).isGreaterThan(1.2);
    }

    @Test
    void productionWithoutInjectionDrawsThePressureDownAndRechargeHoldsIt() {
        var spec = GeothermalReservoir.Spec.typical(240 + C, 1500);
        GeothermalReservoir r = new GeothermalReservoir(spec);
        double p0 = r.pressurePa();
        for (int i = 0; i < 2000; i++) r.step(864_000, 50, 0, 70 + C, 0, true);
        double drop = p0 - r.pressurePa();
        assertThat(drop).isPositive();
        // установившаяся депрессия при подпитке: q/r
        assertThat(drop).isCloseTo(50 / spec.rechargeKgSPerPa(), within(50 / spec.rechargeKgSPerPa() * 0.01));
        GeothermalReservoir balanced = new GeothermalReservoir(spec);
        for (int i = 0; i < 2000; i++) balanced.step(864_000, 50, 50, 70 + C, 0, true);
        assertThat(balanced.pressurePa()).isCloseTo(p0, within(1.0));
    }

    @Test
    void coldFrontBreaksThroughAfterTheSweptVolumeAndProductionCoolsToTheInjection() {
        var spec = GeothermalReservoir.Spec.typical(240 + C, 1500);
        GeothermalReservoir r = new GeothermalReservoir(spec);
        double inj = 50, tInj = 70 + C;
        double tBreak = spec.sweptBulkM3() * spec.bulkRhoC() / (inj * GeothermalReservoir.WATER_C);
        assertThat(tBreak / 3.156e7).isBetween(5.0, 20.0);
        double dt = tBreak / 1000;
        for (int i = 0; i < 990; i++) r.step(dt, inj, inj, tInj, 0, true);
        assertThat(r.coldBreakthrough()).isFalse();
        assertThat(r.productionTempK()).isEqualTo(240 + C);
        for (int i = 0; i < 510; i++) r.step(dt, inj, inj, tInj, 0, true);
        assertThat(r.coldBreakthrough()).isTrue();
        assertThat(r.productionTempK()).isCloseTo(155 + C, within(2.0));
        for (int i = 0; i < 600; i++) r.step(dt, inj, inj, tInj, 0, true);
        assertThat(r.productionTempK()).isCloseTo(tInj, within(1e-6));
    }

    @Test
    void supersaturatedInjectionScalesTheInjectorAndAcidRestoresIt() {
        GeothermalReservoir r = new GeothermalReservoir(GeothermalReservoir.Spec.typical(240 + C, 1500));
        double ii = r.injectivity();
        for (int i = 0; i < 365; i++) r.step(86_400, 50, 50, 100 + C, 1.5, true);
        assertThat(r.silicaMm()).isCloseTo(5, within(0.1));
        assertThat(r.injectivity()).isLessThan(ii);
        assertThat(r.injectionBottomholePa(50)).isGreaterThan(new GeothermalReservoir(r.spec()).injectionBottomholePa(50));
        r.acidize();
        assertThat(r.silicaMm()).isCloseTo(1, within(0.05));
    }

    @Test
    void flashingInTheBoreGrowsCalciteUnlessInhibited() {
        var spec = GeothermalReservoir.Spec.typical(250 + C, 1500);
        GeothermalReservoir plain = new GeothermalReservoir(spec), dosed = new GeothermalReservoir(spec);
        for (int i = 0; i < 100; i++) {
            plain.step(86_400, 60, 60, 150 + C, 0, false);
            dosed.step(86_400, 60, 60, 150 + C, 0, true);
        }
        assertThat(plain.flashingInBore()).isTrue();
        assertThat(plain.calciteMm()).isPositive();
        assertThat(plain.productivity()).isLessThan(dosed.productivity());
        assertThat(dosed.calciteMm()).isZero();
        plain.reamCalcite();
        assertThat(plain.calciteMm()).isZero();
    }

    @Test
    void wellheadPressureFallsWithFlowAndShutInIsTheHighest() {
        GeothermalReservoir r = new GeothermalReservoir(GeothermalReservoir.Spec.typical(240 + C, 1500));
        double shut = r.wellheadPressurePa(0);
        assertThat(shut).isBetween(1e6, 5e6);
        assertThat(r.wellheadPressurePa(30)).isLessThan(shut);
        double qMax = r.maxProductionKgS(0);
        assertThat(r.wellheadPressurePa(qMax)).isCloseTo(0, within(1.0));
    }

    @Test
    void reservoirStateSurvivesSaveAndLoad() {
        var spec = GeothermalReservoir.Spec.typical(240 + C, 1500);
        GeothermalReservoir a = new GeothermalReservoir(spec);
        for (int i = 0; i < 50; i++) a.step(86_400 * 30, 50, 45, 90 + C, 1.2, false);
        MutableStateTree tree = new MutableStateTree();
        a.save(new TreeStateWriter(tree));
        GeothermalReservoir b = new GeothermalReservoir(spec);
        b.load(new TreeStateReader(tree));
        assertThat(b.pressurePa()).isEqualTo(a.pressurePa());
        assertThat(b.productionTempK()).isEqualTo(a.productionTempK());
        assertThat(b.silicaMm()).isEqualTo(a.silicaMm());
        assertThat(b.calciteMm()).isEqualTo(a.calciteMm());
    }

    @Test
    void ncgWithoutEjectorKillsTheVacuumAndTheEjectorHoldsIt() {
        double tcw = 30 + C;
        NcgCondenser off = new NcgCondenser(200, 0.5), on = new NcgCondenser(200, 0.5);
        for (int i = 0; i < 600; i++) {
            off.step(1, 0.2, false, tcw);
            on.step(1, 0.2, true, tcw);
        }
        double clean = If97.psat(tcw + NcgCondenser.TTD_K);
        assertThat(on.pressurePa(tcw)).isLessThan(clean * 1.05);
        assertThat(on.motiveSteamKgS()).isCloseTo(0.2 * NcgCondenser.MOTIVE_STEAM_PER_KG, within(0.01));
        assertThat(off.pressurePa(tcw)).isGreaterThan(clean * 5);
        double pSep = If97.psat(150 + C);
        assertThat(NcgCondenser.turbineWorkJkg(pSep, off.pressurePa(tcw), 0.85))
                .isLessThan(NcgCondenser.turbineWorkJkg(pSep, on.pressurePa(tcw), 0.85) * 0.7);
    }

    @Test
    void noSeismicityBelowTheThresholdAndTheSameSeedGivesTheSameEvents() {
        double lith = 2500 * 9.806_65 * 1500;
        InducedSeismicity quiet = new InducedSeismicity(7);
        for (int i = 0; i < 10_000; i++)
            assertThat(quiet.step(60, 0.59 * lith, lith, 0.6)).isNaN();
        assertThat(quiet.light()).isEqualTo(InducedSeismicity.Light.GREEN);

        InducedSeismicity a = new InducedSeismicity(42), b = new InducedSeismicity(42);
        int events = 0;
        for (int i = 0; i < 10_000; i++) {
            double ma = a.step(60, 0.9 * lith, lith, 0.6), mb = b.step(60, 0.9 * lith, lith, 0.6);
            assertThat(Double.doubleToLongBits(ma)).isEqualTo(Double.doubleToLongBits(mb));
            if (!Double.isNaN(ma)) {
                events++;
                assertThat(ma).isBetween(InducedSeismicity.MIN_ML, InducedSeismicity.MAX_ML);
            }
        }
        // 50 % сверх порога: вероятность события за шаг 60 с — 1 − exp(−0,5/360·60) ≈ 0,080, ожидание ≈ 800
        assertThat(events).isBetween(700, 970);
    }

    @Test
    void trafficLightFollowsThePeakMagnitudeOfTheWindow() {
        double lith = 2500 * 9.806_65 * 1500;
        InducedSeismicity s = new InducedSeismicity(3);
        for (int i = 0; i < 20_000 && s.light() != InducedSeismicity.Light.RED; i++) s.step(60, 1.2 * lith, lith, 0.6);
        assertThat(s.light()).isEqualTo(InducedSeismicity.Light.RED);
        MutableStateTree tree = new MutableStateTree();
        s.save(new TreeStateWriter(tree));
        InducedSeismicity t = new InducedSeismicity(3);
        t.load(new TreeStateReader(tree));
        assertThat(t.light()).isEqualTo(InducedSeismicity.Light.RED);
        for (int i = 0; i < 400; i++) s.step(60, 0, lith, 0.6);
        assertThat(s.light()).isEqualTo(InducedSeismicity.Light.GREEN);
    }

    @Test
    void binaryPlantEfficiencyAndIsobutanePressure() {
        assertThat(OrcCycle.thermalEfficiency(150 + C)).isCloseTo(0.1170, within(1e-4));
        assertThat(OrcCycle.thermalEfficiency(20 + C)).isZero();
        assertThat(OrcCycle.netPowerW(100, 150 + C, 80 + C)).isCloseTo(0.1170 * 100 * 4200 * 70, within(1e3));
        // нормальная точка кипения изобутана −11,7 °C (Poling, прил. A)
        assertThat(OrcCycle.isobutaneSaturationPa(261.4)).isCloseTo(101_325, within(101_325 * 0.03));
        assertThat(OrcCycle.isobutaneSaturationPa(OrcCycle.evaporationK(80 + C))).isBetween(1.0e6, 1.6e6);
    }
}
