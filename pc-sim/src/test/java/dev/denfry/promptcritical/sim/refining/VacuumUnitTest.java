package dev.denfry.promptcritical.sim.refining;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class VacuumUnitTest {
    private static final AtmosphericUnit.Result AT = new AtmosphericUnit(4, 1.5, 360 + 273.15).distill(CrudeAssay.WEST_SIBERIAN);

    /** Трутон: под 5 кПа при 400 °C выкипает всё, что кипит при атмосферном давлении до ≈ 590 °C. */
    @Test
    void vacuumLowersTheBoilingPoint() {
        assertThat(VacuumUnit.atmosphericEquivalentK(673.15, 5) - 273.15).isCloseTo(593, within(2.0));
        assertThat(VacuumUnit.atmosphericEquivalentK(673.15, VacuumUnit.P0_KPA)).isCloseTo(673.15, within(1e-9));
    }

    /** Мазут атмосферной колонны раскладывается на газойль и гудрон без потерь; остаток АТ — сумма узких фракций. */
    @Test
    void massBalance() {
        double res = 0;
        for (double w : AT.residueSlices()) res += w;
        assertThat(res).isCloseTo(AT.yield(AtmosphericUnit.Product.RESIDUE), within(1e-9));
        VacuumUnit.Result r = new VacuumUnit(VacuumUnit.STAGES, VacuumUnit.REFLUX, 673.15, 5).distill(CrudeAssay.WEST_SIBERIAN, AT.residueSlices());
        assertThat(r.vgo() + r.residue()).isCloseTo(1, within(1e-12));
    }

    /**
     * Номинал 400 °C, 5 кПа: газойля 350–550 °C в мазуте западносибирской нефти около половины, гудрон с коксуемостью
     * около 20 % из мазута с 10 % (пример коксования §9.9.8: CCR 20).
     */
    @Test
    void nominalYields() {
        VacuumUnit.Result r = new VacuumUnit(VacuumUnit.STAGES, VacuumUnit.REFLUX, 673.15, 5).distill(CrudeAssay.WEST_SIBERIAN, AT.residueSlices());
        assertThat(r.vgo()).isBetween(0.45, 0.65);
        assertThat(r.residueCcr(0.10)).isBetween(0.15, 0.25);
        assertThat(r.dutyJPerKg()).isPositive();
    }

    /** Без вакуума (эжекторы без пара) газойля выходит в разы меньше; глубже вакуум — больше газойля. */
    @Test
    void vacuumDepthMatters() {
        double atm = new VacuumUnit(3, 0.5, 673.15, VacuumUnit.P0_KPA).distill(CrudeAssay.WEST_SIBERIAN, AT.residueSlices()).vgo();
        double p8 = new VacuumUnit(3, 0.5, 673.15, 8).distill(CrudeAssay.WEST_SIBERIAN, AT.residueSlices()).vgo();
        double p3 = new VacuumUnit(3, 0.5, 673.15, 3).distill(CrudeAssay.WEST_SIBERIAN, AT.residueSlices()).vgo();
        assertThat(atm).isLessThan(0.5 * p8);
        assertThat(p3).isGreaterThan(p8);
    }
}
