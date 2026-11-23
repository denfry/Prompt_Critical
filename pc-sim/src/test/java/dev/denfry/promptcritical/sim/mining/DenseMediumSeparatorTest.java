package dev.denfry.promptcritical.sim.mining;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.denfry.promptcritical.sim.core.ExternalInputs;
import dev.denfry.promptcritical.sim.core.GridPos;
import dev.denfry.promptcritical.sim.core.SimConfig;
import dev.denfry.promptcritical.sim.core.SimulationWorld;
import dev.denfry.promptcritical.sim.core.TreeStateReader;
import dev.denfry.promptcritical.sim.core.TreeStateWriter;
import dev.denfry.promptcritical.sim.core.exec.WorkerPool;
import org.junit.jupiter.api.Test;

/** Тяжелосредный сепаратор (08 §8.3). */
class DenseMediumSeparatorTest {
    /** Уголь Г из T8.1. */
    private static final CoalAssay GAS_COAL = CoalAssay.typical(0.37, 0.213, 0.06, 0.02, 14);

    private static SimulationWorld world(WorkerPool pool) {
        SimulationWorld w = new SimulationWorld(SimConfig.defaults(), 3, 0, pool);
        w.register(new DenseMediumSubsystem());
        return w;
    }

    private static DenseMediumSeparator separator(SimulationWorld w) {
        return w.subsystem(DenseMediumSubsystem.class)
                .unit(w.topology(), w.topology().addDevice(DenseMediumSeparator.DEVICE_TYPE, new GridPos(0, 0, 0)));
    }

    private static void run(SimulationWorld w, DenseMediumSeparator s, double rpm, double seconds) {
        for (int i = 0; i < Math.round(seconds * 20); i++) {
            s.shaft.sample(rpm, true, w.clock().tick());
            w.tick(ExternalInputs.NONE);
        }
    }

    /** Магнетит на заданную плотность суспензии в {@code waterL} воды: M = W(ρ − 1)/(1 − ρ/ρ_mag). */
    private static double magnetiteFor(double waterL, double rho) {
        return waterL * (rho - 1) / (1 - rho / DenseMediumSeparator.MAGNETITE_DENSITY);
    }

    @Test
    void mediumDensityFollowsTheMagnetiteDose() {
        try (WorkerPool pool = WorkerPool.direct(); SimulationWorld w = world(pool)) {
            DenseMediumSeparator s = separator(w);
            assertThat(s.mediumDensity()).isEqualTo(1);
            s.addWater(1_000);
            assertThat(s.addMagnetite(magnetiteFor(1_000, 1.5))).isTrue();
            assertThat(s.mediumDensity()).isCloseTo(1.5, within(1e-9));
            assertThat(s.addMagnetite(10_000)).isFalse();                           // гуще 2,0 и больше контура
            assertThat(s.mediumDensity()).isCloseTo(1.5, within(1e-9));
            s.addWater(10_000);                                                      // разбавление до объёма контура
            assertThat(s.mediumL()).isCloseTo(DenseMediumSeparator.MEDIUM_CAPACITY_L, within(1e-9));
            assertThat(s.mediumDensity()).isLessThan(1.5);
        }
    }

    @Test
    void washesSharperThanTheJigAndLosesMagnetiteWithTheProducts() {
        try (WorkerPool pool = WorkerPool.direct(); SimulationWorld w = world(pool)) {
            DenseMediumSeparator s = separator(w);
            assertThat(s.feed(GAS_COAL, 100)).isTrue();
            run(w, s, 64, 2);
            assertThat(s.running()).isFalse();                                       // нет суспензии
            s.addWater(1_000);
            double m0 = magnetiteFor(1_000, 1.5);
            s.addMagnetite(m0);
            double v0 = s.mediumL();
            run(w, s, 64, 10);                                                       // 10 кг/с
            assertThat(s.feedKg()).isZero();
            assertThat(s.productKg(DenseMediumSeparator.Product.CLEAN) + s.productKg(DenseMediumSeparator.Product.REFUSE))
                    .isCloseTo(100, within(1e-6));
            assertThat(s.magnetiteKg()).isCloseTo(m0 - 0.075, within(1e-6));        // 0,75 кг/т × 0,1 т
            assertThat(s.mediumL()).isLessThan(v0);
            assertThat(s.mediumDensity()).isCloseTo(1.5, within(1e-9));             // унос не меняет плотность

            CoalAssay clean = s.take(DenseMediumSeparator.Product.CLEAN, 50);
            DensitySeparation.Result jig = DensitySeparation.wash(GAS_COAL, 1.5, 1.5, DensitySeparation.Separator.JIG);
            assertThat(clean.ashDryFrac()).isLessThan(jig.clean().ashDryFrac());   // E_p 0,03 против 0,10
        }
    }

    @Test
    void heavierMediumFloatsMoreCoal() {
        DensitySeparation.Result light = DensitySeparation.wash(GAS_COAL, 1.4, 1.4, DensitySeparation.Separator.DENSE_MEDIUM);
        DensitySeparation.Result heavy = DensitySeparation.wash(GAS_COAL, 1.8, 1.8, DensitySeparation.Separator.DENSE_MEDIUM);
        assertThat(heavy.cleanYield()).isGreaterThan(light.cleanYield());
        assertThat(heavy.clean().ashDryFrac()).isGreaterThan(light.clean().ashDryFrac());
    }

    @Test
    void stateRoundTrips() {
        try (WorkerPool pool = WorkerPool.direct(); SimulationWorld w = world(pool)) {
            DenseMediumSeparator s = separator(w);
            s.addWater(800);
            s.addMagnetite(magnetiteFor(800, 1.6));
            s.feed(GAS_COAL, 100);
            run(w, s, 64, 3);
            TreeStateWriter out = new TreeStateWriter();
            s.save(out);
            DenseMediumSeparator t = new DenseMediumSeparator();
            t.load(new TreeStateReader(out.root()));
            assertThat(t.mediumDensity()).isCloseTo(s.mediumDensity(), within(1e-12));
            assertThat(t.feedKg()).isCloseTo(s.feedKg(), within(1e-12));
            assertThat(t.productKg(DenseMediumSeparator.Product.CLEAN)).isCloseTo(s.productKg(DenseMediumSeparator.Product.CLEAN), within(1e-12));
        }
    }
}
