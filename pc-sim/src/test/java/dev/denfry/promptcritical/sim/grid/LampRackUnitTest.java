package dev.denfry.promptcritical.sim.grid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.denfry.promptcritical.sim.core.ExternalInputs;
import dev.denfry.promptcritical.sim.core.GridPos;
import dev.denfry.promptcritical.sim.core.MigrationRegistry;
import dev.denfry.promptcritical.sim.core.MutableStateTree;
import dev.denfry.promptcritical.sim.core.SimConfig;
import dev.denfry.promptcritical.sim.core.SimulationWorld;
import dev.denfry.promptcritical.sim.core.exec.WorkerPool;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Зарядный стеллаж на шине 0,4 кВ: выпрямители берут заданную мощность без пересборки сети, без сети — ничего. */
class LampRackUnitTest {
    private static final long BUS = 31;
    private final WorkerPool pool = WorkerPool.direct();
    private SimulationWorld w;
    private GeneratorUnit gen;
    private int x;

    @AfterEach
    void close() {
        if (w != null) w.close();
        pool.close();
    }

    private SimulationWorld world() {
        SimulationWorld world = new SimulationWorld(SimConfig.defaults(), 7, 0, pool);
        world.register(new GridSubsystem(GridConfig.defaults()));
        return world;
    }

    private LampRackUnit plant() {
        w = world();
        GridSubsystem g = w.subsystem(GridSubsystem.class);
        gen = g.generator(w.topology().addDevice("promptcritical:shaft_generator", new GridPos(x++, 64, 0)));
        gen.setNodes(BUS);
        gen.setExciterPresent(true);
        gen.setFieldOn(true);
        LampRackUnit r = g.lampRack(w.topology().addDevice("promptcritical:lamp_rack", new GridPos(x++, 64, 0)));
        r.setNodes(BUS);
        return r;
    }

    private void run(double seconds) {
        for (int i = 0; i < Math.round(seconds * 20); i++) {
            gen.shaft.sample(64, true, w.clock().tick());
            w.tick(ExternalInputs.NONE);
            w.drainEvents(e -> { });
        }
    }

    @Test
    void rackDrawsItsRectifierPowerFromTheBus() {
        LampRackUnit r = plant();
        run(20);
        assertThat(r.supplyPu()).isCloseTo(1, within(0.05));
        assertThat(r.powerW()).isZero();
        r.setDemandW(12 * 11.4); // двенадцать ламп в начале заряда
        run(1);
        assertThat(r.powerW()).isCloseTo(12 * 11.4, within(3.0));
        r.setSwitchedOn(false);
        run(0.5);
        assertThat(r.supplyPu()).isZero();
        assertThat(r.powerW()).isZero();
    }

    @Test
    void rackWithoutASourceSeesNoVoltage() {
        w = world();
        LampRackUnit r = w.subsystem(GridSubsystem.class).lampRack(w.topology().addDevice("promptcritical:lamp_rack", new GridPos(0, 64, 0)));
        r.setNodes(BUS);
        r.setDemandW(100);
        for (int i = 0; i < 20; i++) w.tick(ExternalInputs.NONE);
        assertThat(r.supplyPu()).isZero();
    }

    @Test
    void stateSurvivesSaveAndLoad() {
        LampRackUnit r = plant();
        r.setDemandW(57);
        r.setSwitchedOn(false);
        run(1);
        MutableStateTree saved = w.saveTree();
        SimulationWorld c = world();
        c.load(SimulationWorld.upgrade(saved, new MigrationRegistry()));
        LampRackUnit back = (LampRackUnit) c.subsystem(GridSubsystem.class).unit(r.id());
        assertThat(back.demandW()).isEqualTo(57);
        assertThat(back.switchedOn()).isFalse();
        c.close();
    }
}
