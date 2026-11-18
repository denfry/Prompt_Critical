package dev.denfry.promptcritical.sim.chlor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import org.junit.jupiter.api.Test;

/**
 * Сбалансированная схема винилхлорида: прямое хлорирование и оксихлорирование, пиролиз ЭДХ с возвратом HCl,
 * суспензионная полимеризация.
 */
class VinylChlorideTest {
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
