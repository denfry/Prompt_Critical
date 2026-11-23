package dev.denfry.promptcritical.sim.grid;

import static org.assertj.core.api.Assertions.assertThat;

import dev.denfry.promptcritical.sim.core.ExternalInputs;
import dev.denfry.promptcritical.sim.core.GridPos;
import dev.denfry.promptcritical.sim.core.SimConfig;
import dev.denfry.promptcritical.sim.core.SimulationWorld;
import dev.denfry.promptcritical.sim.core.exec.WorkerPool;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Инвертор в собранной сети (15 §15.9): подключается через 5 с в окне и не теряет выдержку от пересборок сети —
 * в мире любая постройка в измерении пересобирает сеть, и первый тик после неё шины ещё не посчитаны.
 */
class InverterUnitTest {
    private static final long BUS = 31;
    private final WorkerPool pool = WorkerPool.direct();
    private SimulationWorld w;
    private int x;

    @AfterEach
    void close() {
        if (w != null) w.close();
        pool.close();
    }

    @Test
    void connectsAndExportsAlthoughTheGridIsRebuiltEveryTwoSeconds() {
        w = new SimulationWorld(SimConfig.defaults(), 7, 0, pool);
        w.register(new GridSubsystem(GridConfig.defaults()));
        GridSubsystem grid = w.subsystem(GridSubsystem.class);
        GeneratorUnit g = grid.generator(w.topology().addDevice("promptcritical:shaft_generator", new GridPos(x++, 64, 0)));
        g.setVoltageClass(VoltageClass.MV_6_3);
        g.setNodes(BUS);
        g.setExciterPresent(true);
        g.setFieldOn(true);
        ConsumerUnit c = grid.consumer(w.topology().addDevice("promptcritical:grid_consumer", new GridPos(x++, 64, 0)));
        c.setNodes(BUS);
        c.setDemandKw(200);
        InverterUnit inv = grid.inverter(w.topology().addDevice("promptcritical:battery_inverter", new GridPos(x++, 64, 0)));
        inv.configure(500e3, 0.97);
        inv.setNodes(BUS);
        inv.setDispatch(100e3, 125e3, 0);
        boolean connected = false;
        for (int i = 0; i < 40 * 20; i++) {
            if (i % 40 == 39) grid.markDirty();
            g.shaft.sample(64, true, w.clock().tick());
            w.tick(ExternalInputs.NONE);
            connected |= inv.source().connected();
        }
        assertThat(connected).as("inverter connects within 40 s").isTrue();
        assertThat(inv.source().connected()).isTrue();
        assertThat(inv.powerW()).isGreaterThan(50e3);
    }
}
