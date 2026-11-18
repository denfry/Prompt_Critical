package dev.denfry.promptcritical.sim.chlor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import org.junit.jupiter.api.Test;

/**
 * Облако хлора (гауссова модель, пробит CCPS) и сбалансированная схема винилхлорида: прямое хлорирование и
 * оксихлорирование, пиролиз ЭДХ с возвратом HCl, суспензионная полимеризация.
 */
class ChlorineCloudAndPvcTest {
    @Test
    void stabilityClassesFollowPasquill() {
        assertThat(ChlorineCloud.stability(1.5, 800)).isEqualTo(ChlorineCloud.Stability.A);
        assertThat(ChlorineCloud.stability(4, 400)).isEqualTo(ChlorineCloud.Stability.B);
        assertThat(ChlorineCloud.stability(7, 200)).isEqualTo(ChlorineCloud.Stability.D);
        assertThat(ChlorineCloud.stability(2, 0)).isEqualTo(ChlorineCloud.Stability.F);
        assertThat(ChlorineCloud.stability(8, 0)).isEqualTo(ChlorineCloud.Stability.D);
    }

    @Test
    void briggsSigmasAtOneKilometre() {
        // Briggs, открытая местность, D: σy ≈ 76 м, σz ≈ 38 м на 1 км (Turner workbook — 68 и 32 по Пасквиллу–Гиффорду)
        assertThat(ChlorineCloud.Stability.D.sigmaY(1000)).isCloseTo(76, within(2.0));
        assertThat(ChlorineCloud.Stability.D.sigmaZ(1000)).isCloseTo(37.9, within(1.0));
        assertThat(ChlorineCloud.Stability.F.sigmaZ(1000)).isLessThan(ChlorineCloud.Stability.D.sigmaZ(1000) / 2);
    }

    @Test
    void plumeIsDangerousDownwindAndNightIsWorse() {
        // утечка 1 кг/с (разрыв трубки ½″ на жидком хлоре) при ветре 3 м/с
        double day = ChlorineCloud.ppm(ChlorineCloud.plumeKgM3(1, 3, ChlorineCloud.Stability.C, 200, 0));
        double night = ChlorineCloud.ppm(ChlorineCloud.plumeKgM3(1, 3, ChlorineCloud.Stability.F, 200, 0));
        assertThat(day).isGreaterThan(ChlorineCloud.IDLH_PPM);
        assertThat(night).isGreaterThan(5 * day);
        // концентрация ∝ 1/u и спадает поперёк
        double side = ChlorineCloud.ppm(ChlorineCloud.plumeKgM3(1, 3, ChlorineCloud.Stability.C, 200, 60));
        assertThat(side).isLessThan(day / 2);
        assertThat(ChlorineCloud.plumeKgM3(1, 6, ChlorineCloud.Stability.C, 200, 0))
                .isCloseTo(ChlorineCloud.plumeKgM3(1, 3, ChlorineCloud.Stability.C, 200, 0) / 2, within(1e-12));
        assertThat(ChlorineCloud.plumeKgM3(1, 3, ChlorineCloud.Stability.C, -50, 0)).isZero();
    }

    @Test
    void puffMassIsConservedOverGround() {
        // интеграл приземного клуба по площади с отражением от земли: ∫∫C dx dy = M·2/(√(2π)·σz)
        double m = 100, u = 2, t = 300;
        ChlorineCloud.Stability s = ChlorineCloud.Stability.D;
        double sum = 0, step = 2;
        for (double xx = 0; xx < 1200; xx += step)
            for (double yy = -300; yy < 300; yy += step) sum += ChlorineCloud.puffKgM3(m, u, s, xx, yy, t) * step * step;
        double sz = s.sigmaZ(u * t);
        assertThat(sum).isCloseTo(m * 2 / (Math.sqrt(2 * Math.PI) * sz), within(sum * 0.02));
    }

    @Test
    void windFrameUsesMeteorologicalDirection() {
        // северный ветер (с −Z) несёт на юг (+Z)
        double[] p = ChlorineCloud.windFrame(0, 100, 0);
        assertThat(p[0]).isCloseTo(100, within(1e-9));
        assertThat(p[1]).isCloseTo(0, within(1e-9));
        // восточный ветер (с +X) несёт на запад
        double[] q = ChlorineCloud.windFrame(-100, 0, Math.PI / 2);
        assertThat(q[0]).isCloseTo(100, within(1e-9));
    }

    @Test
    void probitLethalityForChlorine() {
        // 50 % летальности: ln(C²t) = (5 + 8,29)/0,92 → при 30 мин ≈ 250 ppm; AEGL-3 на 30 мин — 28 ppm, пробит
        // там даёт доли процента
        double c = Math.sqrt(Math.exp((5 + 8.29) / 0.92) / 30);
        assertThat(c).isBetween(230.0, 270.0);
        assertThat(ChlorineCloud.lethality(28 * 28 * 30)).isLessThan(0.01);
        assertThat(ChlorineCloud.lethality(c * c * 30)).isCloseTo(0.5, within(0.01));
        assertThat(ChlorineCloud.lethality(10 * 10 * 30)).isLessThan(1e-4);
        assertThat(ChlorineCloud.lethality(0)).isZero();
    }

    @Test
    void balancedEdcUsesCrackerHclAndHoldsChlorine() {
        EdcUnit u = new EdcUnit();
        assertThat(EdcUnit.hclConversion(503.15)).isBetween(0.98, 0.995);
        assertThat(EdcUnit.oxySelectivity(503.15)).isBetween(0.94, 0.97);
        assertThat(EdcUnit.oxySelectivity(553.15)).isLessThan(EdcUnit.oxySelectivity(503.15) - 0.05);
        // крекинг 2 моль/с ЭДХ при 55 % → 1,1 моль/с HCl
        EdcCracker k = new EdcCracker();
        k.setOn(true);
        k.step(1, 2);
        assertThat(k.conversion()).isCloseTo(0.55, within(0.005));
        assertThat(EdcCracker.selectivity(k.conversion())).isGreaterThan(0.98);
        double hcl = k.hclMolS();
        u.step(10, 10, hcl, 10);
        // оксихлорирование съедает почти всю HCl, прямое хлорирование — хлор; ЭДХ на 1 моль Cl₂ из ванн
        assertThat(u.hclUsedMolS()).isCloseTo(hcl, within(hcl * 0.02));
        assertThat(u.edcOxyMolS()).isCloseTo(hcl / 2 * EdcUnit.hclConversion(503.15), within(1e-9));
        assertThat(u.edcDirectMolS()).isCloseTo((EdcUnit.CAPACITY_MOL_S - u.edcOxyMolS()) * 0.99, within(1e-9));
        assertThat(u.co2MolS()).isPositive();
        assertThat(u.heatW()).isPositive();
    }

    @Test
    void crackerHotterConvertsMoreAndCokesFaster() {
        assertThat(EdcCracker.conversion(793.15)).isGreaterThan(EdcCracker.conversion(773.15) + 0.1);
        EdcCracker mild = new EdcCracker(), hot = new EdcCracker();
        mild.setOn(true);
        hot.setOn(true);
        hot.setCotK(803.15);
        for (int i = 0; i < 1000; i++) {
            mild.step(3600, 2);
            hot.step(3600, 2);
        }
        assertThat(hot.cokeMm()).isGreaterThan(1.5 * mild.cokeMm());
        assertThat(mild.cokeMm()).isBetween(0.5, EdcCracker.COKE_LIMIT_MM);
    }

    @Test
    void pvcBatchRunsToPressureDrop() {
        PvcReactor r = new PvcReactor();
        r.setTempK(330.15);
        assertThat(PvcReactor.kValue(323.15)).isCloseTo(70, within(1e-9));
        assertThat(PvcReactor.kValue(336.15)).isCloseTo(57, within(1e-9));
        assertThat(PvcReactor.vapourMpa(333.15)).isBetween(0.9, 1.1);
        assertThat(r.start()).isFalse();
        r.charge(PvcReactor.CHARGE_KG, PvcReactor.WATER_KG);
        assertThat(r.start()).isTrue();
        double p0 = r.pressureMpa();
        double heat = 0;
        while (r.phase() == PvcReactor.Phase.REACTING) heat = Math.max(heat, r.step(60));
        assertThat(r.batchTimeS() / 3600).isBetween(5.5, 6.5);
        assertThat(r.pressureMpa()).isLessThan(0.15);
        assertThat(p0).isBetween(0.7, 1.0);
        // начальное тепло ≈ 2000 кг · 1,6 МДж · k
        assertThat(heat).isCloseTo(PvcReactor.CHARGE_KG * PvcReactor.HEAT_J_KG * PvcReactor.rate(330.15), within(heat * 0.02));
        double[] out = r.discharge();
        assertThat(out[0]).isCloseTo(PvcReactor.CHARGE_KG * PvcReactor.END_CONVERSION, within(1e-6));
        assertThat(out[0] + out[1]).isCloseTo(PvcReactor.CHARGE_KG, within(1e-6));
        assertThat(r.phase()).isEqualTo(PvcReactor.Phase.IDLE);
    }
}
