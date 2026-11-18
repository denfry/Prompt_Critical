package dev.denfry.promptcritical.sim.refining;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.denfry.promptcritical.sim.refining.AtmosphericUnit.Product;
import org.junit.jupiter.api.Test;

class RefiningTest {
    /** Бензол–толуол, α = 2,5, 95/5 %: N_min = ln(19·19)/ln 2,5 = 6,43; R_min при x_F = 0,5 — (1,9 − 0,25)/1,5 = 1,10. */
    @Test
    void fenskeAndUnderwoodTextbookPair() {
        assertThat(Fractionation.fenskeMinStages(2.5, 0.95, 0.05, 0.05, 0.95)).isCloseTo(6.43, within(0.01));
        assertThat(Fractionation.underwoodMinRefluxBinary(2.5, 0.5, 0.95)).isCloseTo(1.10, within(1e-9));
    }

    /** Молоканов: Y(0) = 1, Y(1) = 0; на X = 0,2 — около 0,46, как на графике Гиллиленда (0,43–0,47). */
    @Test
    void gillilandCorrelation() {
        assertThat(Fractionation.gilliland(0)).isEqualTo(1);
        assertThat(Fractionation.gilliland(1)).isEqualTo(0);
        assertThat(Fractionation.gilliland(0.2)).isBetween(0.43, 0.48);
        double prev = 1;
        for (double x = 0.05; x < 1; x += 0.05) {
            double y = Fractionation.gilliland(x);
            assertThat(y).isLessThan(prev);
            prev = y;
        }
    }

    /** При бесконечной флегме эффективное число тарелок — все тарелки; при флегме ниже минимальной — ноль. */
    @Test
    void effectiveStagesLimits() {
        assertThat(Fractionation.effectiveStages(10, 1e6, 500)).isCloseTo(10, within(0.05));
        assertThat(Fractionation.effectiveStages(10, 2, 500)).isLessThan(Fractionation.effectiveStages(20, 2, 500));
        assertThat(Fractionation.effectiveStages(10, 1, 500)).isLessThan(Fractionation.effectiveStages(10, 4, 500));
    }

    @Test
    void assayReadsTheTable() {
        CrudeAssay a = CrudeAssay.WEST_SIBERIAN;
        assertThat(a.yieldBelow(180 + 273.15)).isCloseTo(0.25, within(1e-12));
        assertThat(a.yieldBelow(350 + 273.15)).isCloseTo(0.535, within(1e-12));
        assertThat(a.temperatureAt(0.535)).isCloseTo(350 + 273.15, within(1e-9));
        double sum = 0;
        for (double w : a.sliceFractions()) sum += w;
        assertThat(sum).isCloseTo(1, within(1e-12));
    }

    /** Чёткая колонна и полный отгон дают выходы по ИТК: 25 / 11 / 17,5 / 46,5 %. */
    @Test
    void sharpColumnGivesTbpCuts() {
        AtmosphericUnit.Result r = new AtmosphericUnit(40, 50, 380 + 273.15).distill(CrudeAssay.WEST_SIBERIAN);
        assertThat(r.yield(Product.GASOLINE)).isCloseTo(0.25, within(0.01));
        assertThat(r.yield(Product.KEROSENE)).isCloseTo(0.11, within(0.01));
        assertThat(r.yield(Product.DIESEL)).isCloseTo(0.175, within(0.01));
        assertThat(r.yield(Product.RESIDUE)).isCloseTo(0.465, within(0.01));
        assertThat(r.yields()[0] + r.yields()[1] + r.yields()[2] + r.yields()[3]).isCloseTo(1, within(1e-9));
    }

    /** Больше тарелок и флегмы — меньше наложение фракций; флегма стоит тепла печи; низкая температура — мазут тяжелеет. */
    @Test
    void traysRefluxAndHeatMatter() {
        double t = 360 + 273.15;
        var crude = CrudeAssay.WEST_SIBERIAN;
        AtmosphericUnit.Result few = new AtmosphericUnit(3, 1.5, t).distill(crude);
        AtmosphericUnit.Result many = new AtmosphericUnit(10, 1.5, t).distill(crude);
        AtmosphericUnit.Result lean = new AtmosphericUnit(10, 0.8, t).distill(crude);
        assertThat(many.overlapK()[1]).isLessThan(few.overlapK()[1]);
        assertThat(many.overlapK()[1]).isLessThan(lean.overlapK()[1]);
        assertThat(few.overlapK()[1]).isPositive();
        assertThat(many.dutyJPerKg()).isGreaterThan(lean.dutyJPerKg());
        AtmosphericUnit.Result cold = new AtmosphericUnit(10, 1.5, 300 + 273.15).distill(crude);
        assertThat(cold.yield(Product.RESIDUE)).isGreaterThan(many.yield(Product.RESIDUE));
        assertThat(cold.vaporized()).isLessThan(many.vaporized());
        // тепло печи — порядка 1 МДж на кг нефти без рекуперации
        assertThat(many.dutyJPerKg()).isBetween(0.8e6, 1.4e6);
    }
}
