package dev.denfry.promptcritical.sim.grid;

import static org.assertj.core.api.Assertions.assertThat;

import dev.denfry.promptcritical.sim.core.GridPos;
import dev.denfry.promptcritical.sim.core.SimConfig;
import dev.denfry.promptcritical.sim.core.SimId;
import dev.denfry.promptcritical.sim.core.SimulationWorld;
import dev.denfry.promptcritical.sim.core.exec.WorkerPool;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** План изоляции LOTO по графу сети (13 §13.1.4): аппараты на границе зоны работ, источники внутри неё. */
class IsolationPlannerTest {
    private final WorkerPool pool = WorkerPool.direct();
    private final SimulationWorld w = new SimulationWorld(SimConfig.defaults(), 7, 0, pool);
    private final GridSubsystem grid;
    private int x;

    IsolationPlannerTest() {
        w.register(new GridSubsystem(GridConfig.defaults()));
        grid = w.subsystem(GridSubsystem.class);
    }

    @AfterEach
    void close() {
        w.close();
        pool.close();
    }

    private SimId device(String type) {
        return w.topology().addDevice("promptcritical:" + type, new GridPos(x++, 64, 0));
    }

    private SwitchUnit cell(long bus, long feeder, SwitchUnit.Kind kind) {
        SwitchUnit s = grid.switchUnit(device("switchgear_cell"), kind);
        s.setNodes(bus, feeder);
        return s;
    }

    private ConsumerUnit consumer(long node) {
        ConsumerUnit c = grid.consumer(device("grid_consumer"));
        c.setNodes(node);
        return c;
    }

    @Test
    void consumerBehindTwoFeedersNeedsBothLocked() {
        GeneratorUnit g = grid.generator(device("shaft_generator"));
        g.setVoltageClass(VoltageClass.MV_6_3);
        g.setNodes(1);
        SwitchUnit main = cell(1, 2, SwitchUnit.Kind.MV_BREAKER), tie = cell(3, 2, SwitchUnit.Kind.DISCONNECTOR);
        ConsumerUnit load = consumer(2), neighbour = consumer(2);
        IsolationPlanner.Plan p = IsolationPlanner.electrical(grid, load.id());
        assertThat(p.feeds()).extracting(IsolationPlanner.Feed::device).containsExactlyInAnyOrder(main.id(), tie.id());
        assertThat(p.feeds()).extracting(IsolationPlanner.Feed::device).doesNotContain(neighbour.id());
        assertThat(p.isolable()).as("генератор за выключателем").isTrue();
    }

    @Test
    void sourceOnTheSameBusCannotBeLockedOut() {
        GeneratorUnit g = grid.generator(device("shaft_generator"));
        g.setVoltageClass(VoltageClass.MV_6_3);
        g.setNodes(5);
        ConsumerUnit load = consumer(5);
        IsolationPlanner.Plan p = IsolationPlanner.electrical(grid, load.id());
        assertThat(p.feeds()).isEmpty();
        assertThat(p.unswitchedSources()).containsExactly(g.id());
        assertThat(p.isolable()).isFalse();
    }

    @Test
    void anApparatusIsItsOwnIsolationPoint() {
        SwitchUnit s = cell(7, 8, SwitchUnit.Kind.LV_BREAKER);
        assertThat(IsolationPlanner.electrical(grid, s.id()).feeds()).extracting(IsolationPlanner.Feed::device).containsExactly(s.id());
    }
}
