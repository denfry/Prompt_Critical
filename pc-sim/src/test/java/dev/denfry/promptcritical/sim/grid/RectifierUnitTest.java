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

/** Выпрямитель электролиза на шине 6,3 кВ: берёт мощность постоянного тока с учётом КПД и грузит генератор. */
class RectifierUnitTest {
    private static final long BUS = 43;
    private final WorkerPool pool = WorkerPool.direct();
    private SimulationWorld w;
    private GeneratorUnit gen;
    private int x;

    @AfterEach
    void close() {
        if (w != null) w.close();
        pool.close();
    }

    private GridSubsystem grid() {
        return w.subsystem(GridSubsystem.class);
    }

    private void plant() {
        w = new SimulationWorld(SimConfig.defaults(), 7, 0, pool);
        w.register(new GridSubsystem(GridConfig.defaults()));
        gen = grid().generator(w.topology().addDevice("promptcritical:shaft_generator", new GridPos(x++, 64, 0)));
        gen.setVoltageClass(VoltageClass.MV_6_3);
        gen.setNodes(BUS);
        gen.setExciterPresent(true);
        gen.setFieldOn(true);
    }

    private void run(double seconds) {
        for (int i = 0; i < Math.round(seconds * 20); i++) {
            gen.shaft.sample(64, true, w.clock().tick());
            w.tick(ExternalInputs.NONE);
            w.drainEvents(e -> { });
        }
    }

    @Test
    void rectifierDrawsDcPowerOverEfficiency() {
        plant();
        RectifierUnit r = grid().rectifier(w.topology().addDevice("promptcritical:rectifier_unit", new GridPos(x++, 64, 0)));
        r.setNodes(BUS);
        run(10);
        double idle = gen.electricalPowerW();
        r.setDcDemandW(200e3);
        run(5);
        assertThat(r.supplyPu()).isGreaterThan(0.9);
        assertThat(r.currentFraction()).isCloseTo(1, within(1e-9));
        assertThat(r.powerW()).isCloseTo(200e3 / RectifierUnit.EFFICIENCY, within(10e3));
        assertThat(gen.electricalPowerW()).isGreaterThan(idle + 180e3);
        r.setSwitchedOn(false);
        run(2);
        assertThat(r.powerW()).isZero();
        assertThat(r.currentFraction()).isZero();
    }

    @Test
    void stateSurvivesSaveAndLoad() {
        plant();
        RectifierUnit r = grid().rectifier(w.topology().addDevice("promptcritical:rectifier_unit", new GridPos(x++, 64, 0)));
        r.setNodes(BUS);
        r.setDcDemandW(150e3);
        r.setSwitchedOn(false);
        run(1);
        MutableStateTree saved = w.saveTree();
        SimulationWorld c = new SimulationWorld(SimConfig.defaults(), 7, 0, pool);
        c.register(new GridSubsystem(GridConfig.defaults()));
        c.load(SimulationWorld.upgrade(saved, new MigrationRegistry()));
        RectifierUnit r2 = (RectifierUnit) c.subsystem(GridSubsystem.class).unit(r.id());
        assertThat(r2.dcDemandW()).isCloseTo(150e3, within(1.0));
        assertThat(r2.switchedOn()).isFalse();
        c.close();
    }
}
