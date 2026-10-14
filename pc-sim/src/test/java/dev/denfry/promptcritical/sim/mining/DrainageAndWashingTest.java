package dev.denfry.promptcritical.sim.mining;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

/** Водоотлив (08 §8.2.9) и обогащение угля по кривой Тромпа (08 §8.3). */
class DrainageAndWashingTest {
    private static final MineSump.PumpSpec PUMP = new MineSump.PumpSpec(100.0 / 3600, 150, 0.7, 1500);

    /** 100 м³/ч на 150 м при η 0,7 → 58 кВт. */
    @Test
    void pumpPowerExample() {
        assertThat(MineSump.pumpPowerW(100.0 / 3600, 150, 0.7)).isCloseTo(58.4e3, within(0.1e3));
        assertThat(PUMP.flowAt(1500, 150)).isCloseTo(100.0 / 3600, within(1e-12));
        assertThat(PUMP.flowAt(1500, 150 * 1.25)).isZero();
        assertThat(PUMP.flowAt(1000, 150)).isZero();
    }

    /** Насос держит приток; без насоса водосборник за 4 ч заполняется и топит горизонт. */
    @Test
    void sumpFillsAndFloodsWhenPumpStops() {
        double inflow = 60.0 / 3600;
        MineSump s = new MineSump(4 * 60, 150, PUMP);
        assertThat(s.capacityOk(inflow)).isTrue();
        assertThat(s.capacityOk(70.0 / 3600)).isFalse();
        for (int i = 0; i < 3600; i++) s.stepSlow(1, inflow, 1500);
        assertThat(s.volumeM3()).isZero();
        assertThat(s.pumpedM3s()).isCloseTo(inflow, within(1e-12));
        assertThat(s.shaftPowerW()).isCloseTo(MineSump.pumpPowerW(inflow, 150, 0.7), within(1.0));

        for (int i = 0; i < 6 * 3600; i++) s.stepSlow(1, inflow, 0);
        assertThat(s.levelFrac()).isCloseTo(1.5, within(1e-9));
        assertThat(s.floodedM3()).isCloseTo(120, within(1e-6));
        // откачка: 100 − 60 = 40 м³/ч → 360 м³ за 9 ч
        for (int i = 0; i < 9 * 3600; i++) s.stepSlow(1, inflow, 1500);
        assertThat(s.volumeM3()).isCloseTo(0, within(1e-6));
        assertThat(MineSump.inflowM3s(2, 0.01, 0.001)).isCloseTo(0.021, within(1e-12));
    }

    /** E_p — полуразность плотностей ε = 0,25 и 0,75; при ρ = ρ_p — пополам. */
    @Test
    void trompCurveShape() {
        double ep = 0.10, rp = 1.5;
        assertThat(DensitySeparation.partition(rp, rp, ep)).isCloseTo(0.5, within(1e-12));
        double r25 = rp + 0.91 * ep * Math.log(3), r75 = rp - 0.91 * ep * Math.log(3);
        assertThat(DensitySeparation.partition(r25, rp, ep)).isCloseTo(0.25, within(1e-12));
        assertThat((r25 - r75) / 2).isCloseTo(ep, within(0.001));
        assertThat(DensitySeparation.FRACTION_DENSITY[3]).isGreaterThan(1.8);
        assertThat(DensitySeparation.FRACTION_DENSITY[0]).isBetween(1.25, 1.40);
    }

    /** Баланс массы, золы и серы; концентрат под кокс (A^d ≤ 10 %), пиритная сера — в тяжёлое. */
    @Test
    void jigWashesCoalWithBalances() {
        CoalAssay feed = CoalAssay.typical(0.30, 0.25, 0.06, 0.02, 20);
        DensitySeparation.Result r = DensitySeparation.wash(feed, 1.45, 1.80, DensitySeparation.Separator.JIG);
        double gamma = r.cleanYield() + r.middlingsYield() + r.refuseYield();
        assertThat(gamma).isCloseTo(1, within(1e-9));
        double ash = r.cleanYield() * r.clean().ashDryFrac() + r.middlingsYield() * r.middlings().ashDryFrac()
                + r.refuseYield() * r.refuse().ashDryFrac();
        assertThat(ash).isCloseTo(feed.ashDryFrac(), within(1e-6));
        double sulfur = r.cleanYield() * r.clean().sulfurDryFrac() + r.middlingsYield() * r.middlings().sulfurDryFrac()
                + r.refuseYield() * r.refuse().sulfurDryFrac();
        assertThat(sulfur).isCloseTo(feed.sulfurDryFrac(), within(1e-6));

        assertThat(r.clean().ashDryFrac()).isLessThan(0.10);
        assertThat(r.middlings().ashDryFrac()).isBetween(0.2, 0.45);
        assertThat(r.refuse().ashDryFrac()).isGreaterThan(0.5);
        assertThat(r.clean().pyriticShare()).isLessThan(feed.pyriticShare());
        assertThat(r.refuse().pyriticShare()).isGreaterThan(feed.pyriticShare());
        assertThat(r.clean().volatileDaf()).isEqualTo(feed.volatileDaf());
        assertThat(r.clean().rank()).isEqualTo(feed.rank());
        // уголь после обогащения горит лучше
        assertThat(r.clean().toFuel().lhv()).isGreaterThan(feed.toFuel().lhv());
    }

    /** Тяжелосредный сепаратор (E_p 0,03) режет чище отсадки (0,10). */
    @Test
    void denseMediumIsSharperThanJig() {
        CoalAssay feed = CoalAssay.typical(0.30, 0.25, 0.06, 0.02, 20);
        DensitySeparation.Result jig = DensitySeparation.wash(feed, 1.5, 1.5, DensitySeparation.Separator.JIG);
        DensitySeparation.Result dms = DensitySeparation.wash(feed, 1.5, 1.5,
                DensitySeparation.Separator.DENSE_MEDIUM);
        assertThat(jig.middlings()).isNull();
        assertThat(dms.clean().ashDryFrac()).isLessThan(jig.clean().ashDryFrac());
        assertThat(dms.refuse().ashDryFrac()).isGreaterThan(jig.refuse().ashDryFrac());
    }
}
