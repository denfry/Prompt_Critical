package dev.denfry.promptcritical.sim.mining;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.denfry.promptcritical.sim.core.ExternalInputs;
import dev.denfry.promptcritical.sim.core.GridPos;
import dev.denfry.promptcritical.sim.core.MigrationRegistry;
import dev.denfry.promptcritical.sim.core.SimConfig;
import dev.denfry.promptcritical.sim.core.SimulationWorld;
import dev.denfry.promptcritical.sim.core.exec.WorkerPool;
import org.junit.jupiter.api.Test;

/** Отсадочная машина в мире симуляции (08 §8.3, этап 8 M2). */
class CoalJigTest {
    /** Уголь Г из T8.1. */
    private static final CoalAssay GAS_COAL = CoalAssay.typical(0.37, 0.213, 0.06, 0.02, 14);

    private static SimulationWorld world(WorkerPool pool) {
        SimulationWorld w = new SimulationWorld(SimConfig.defaults(), 3, 0, pool);
        w.register(new CoalPrepSubsystem());
        return w;
    }

    private static void run(SimulationWorld w, CoalJig jig, double rpm, double seconds) {
        for (int i = 0; i < Math.round(seconds * 20); i++) {
            jig.shaft.sample(rpm, true, w.clock().tick());
            w.tick(ExternalInputs.NONE);
        }
    }

    @Test
    void washesALotIntoThreeProductsAtShaftSpeed() {
        try (WorkerPool pool = WorkerPool.direct(); SimulationWorld w = world(pool)) {
            CoalJig jig = w.subsystem(CoalPrepSubsystem.class)
                    .unit(w.topology(), w.topology().addDevice(CoalJig.DEVICE_TYPE, new GridPos(0, 0, 0)));
            assertThat(jig.feed(GAS_COAL, 100)).isTrue();
            assertThat(jig.feed(CoalAssay.typical(0.20, 0.10, 0.05, 0.01, 10), 100)).isFalse();   // другой уголь
            assertThat(jig.feed(CoalAssay.typical(0.37, 0.213, 0.06, 0.02, 14), 100)).isTrue();   // тот же по значению

            run(w, jig, 64, 2);
            assertThat(jig.running()).isFalse();                                  // нет воды
            jig.addWater(1_000);
            run(w, jig, 64, 10);                                                  // 10 кг/с
            assertThat(jig.feedKg()).isCloseTo(100, within(1e-6));
            assertThat(jig.waterL()).isCloseTo(1_000 - 50, within(1e-6));
            assertThat(jig.powerW()).isCloseTo(CoalJig.RATED_W, within(1e-9));
            double total = 0;
            for (CoalJig.Product p : CoalJig.Product.values()) total += jig.productKg(p);
            assertThat(total).isCloseTo(100, within(1e-6));

            CoalAssay clean = jig.take(CoalJig.Product.CLEAN, 50);
            assertThat(clean.ashDryFrac()).isLessThan(GAS_COAL.ashDryFrac());
            assertThat(jig.productKg(CoalJig.Product.REFUSE)).isPositive();
            assertThat(jig.take(CoalJig.Product.REFUSE, 100)).isNull();          // столько ещё нет

            run(w, jig, 128, 5);                                                  // вдвое быстрее
            assertThat(jig.feedKg()).isZero();
            run(w, jig, 8, 1);                                                    // ниже 16 об/мин стоит
            assertThat(jig.powerW()).isZero();
        }
    }

    @Test
    void lighterCutGivesCleanerButSmallerConcentrate() {
        DensitySeparation.Result loose = DensitySeparation.wash(GAS_COAL, 1.70, 1.80, DensitySeparation.Separator.JIG);
        DensitySeparation.Result tight = DensitySeparation.wash(GAS_COAL, 1.40, 1.80, DensitySeparation.Separator.JIG);
        assertThat(tight.clean().ashDryFrac()).isLessThan(loose.clean().ashDryFrac());
        assertThat(tight.cleanYield()).isLessThan(loose.cleanYield());
    }

    @Test
    void settingsAndProductsSurviveSaveAndLoad() {
        try (WorkerPool pool = WorkerPool.direct(); SimulationWorld w = world(pool)) {
            CoalJig jig = w.subsystem(CoalPrepSubsystem.class)
                    .unit(w.topology(), w.topology().addDevice(CoalJig.DEVICE_TYPE, new GridPos(0, 0, 0)));
            assertThat(jig.cycleRhoClean()).isEqualTo(1.60);
            jig.feed(GAS_COAL, 200);
            jig.addWater(500);
            run(w, jig, 64, 3);
            long hash = w.stateHash();
            try (SimulationWorld copy = world(pool)) {
                copy.load(SimulationWorld.upgrade(w.saveTree(), new MigrationRegistry()));
                assertThat(copy.stateHash()).isEqualTo(hash);
                CoalJig jig2 = copy.subsystem(CoalPrepSubsystem.class).unit(w.topology().deviceAt(0));
                assertThat(jig2.rhoClean()).isEqualTo(1.60);
                assertThat(jig2.feedKg()).isEqualTo(jig.feedKg());
                assertThat(jig2.productKg(CoalJig.Product.CLEAN)).isEqualTo(jig.productKg(CoalJig.Product.CLEAN));
                assertThat(jig2.feed(GAS_COAL, 1)).isTrue();                      // анализ партии восстановлен
            }
        }
    }
}
