package dev.denfry.promptcritical.sim.reactor;

import static org.assertj.core.api.Assertions.assertThat;

import dev.denfry.promptcritical.sim.core.ExternalInputs;
import dev.denfry.promptcritical.sim.core.GridPos;
import dev.denfry.promptcritical.sim.core.MigrationRegistry;
import dev.denfry.promptcritical.sim.core.MutableStateTree;
import dev.denfry.promptcritical.sim.core.SimConfig;
import dev.denfry.promptcritical.sim.core.SimEvent;
import dev.denfry.promptcritical.sim.core.SimId;
import dev.denfry.promptcritical.sim.core.SimulationWorld;
import dev.denfry.promptcritical.sim.core.exec.WorkerPool;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Подсистема реакторов в мире: двойник пульта, вехи для маршрута, журнал защиты, сохранение. */
class ReactorSubsystemTest {
    private final WorkerPool pool = WorkerPool.direct();
    private final List<SimulationWorld> worlds = new ArrayList<>();

    @AfterEach
    void close() {
        for (SimulationWorld w : worlds) w.close();
        pool.close();
    }

    private SimulationWorld world() {
        SimulationWorld w = new SimulationWorld(SimConfig.defaults(), 7, 0, pool);
        w.register(new ReactorSubsystem(7));
        worlds.add(w);
        return w;
    }

    private static List<SimEvent> run(SimulationWorld w, double seconds) {
        List<SimEvent> out = new ArrayList<>();
        for (int i = 0; i < Math.round(seconds * 20); i++) {
            w.tick(ExternalInputs.NONE);
            w.drainEvents(out::add);
        }
        return out;
    }

    @Test
    void controllerGetsAPileThatReportsMilestonesAndScrams() {
        SimulationWorld w = world();
        SimId id = w.topology().addDevice(ReactorSubsystem.PILE_CONTROLLER, new GridPos(0, 64, 0));
        PileUnit u = w.subsystem(ReactorSubsystem.class).unit(w.topology(), id);
        assertThat(u).isNotNull();
        assertThat(w.subsystem(ReactorSubsystem.class).unit(w.topology(),
                w.topology().addDevice("promptcritical:coke_oven", new GridPos(5, 64, 0)))).isNull();
        u.form(Piles.cube(4, 1, 8, true, true, true));
        List<SimEvent> ev = run(w, 3);
        assertThat(ev).anySatisfy(e -> {
            assertThat(e).isInstanceOf(SimEvent.Milestone.class);
            SimEvent.Milestone m = (SimEvent.Milestone) e;
            assertThat(m.event()).isEqualTo(ReactorSubsystem.PILE_STATE);
            assertThat(m.tag()).isEqualTo("counts_cps");
            assertThat(m.value()).isPositive();
        });
        u.pile().windZip();
        run(w, 70);
        assertThat(u.pile().zipCocked()).isTrue();
        u.scram();
        ev = run(w, 1);
        assertThat(u.pile().trip()).isEqualTo(PileTrip.MANUAL);
        assertThat(ev).anySatisfy(e -> assertThat(e).isInstanceOfSatisfying(SimEvent.Journal.class,
                j -> assertThat(j.message()).contains("scram manual")));
        assertThat(ev).anySatisfy(e -> assertThat(e).isInstanceOfSatisfying(SimEvent.Milestone.class,
                m -> assertThat(m.tag()).isEqualTo("scram")));
    }

    @Test
    void pileStateSurvivesWorldSave() {
        SimulationWorld w = world();
        SimId id = w.topology().addDevice(ReactorSubsystem.PILE_CONTROLLER, new GridPos(0, 64, 0));
        PileUnit u = w.subsystem(ReactorSubsystem.class).unit(w.topology(), id);
        u.form(Piles.cube(4, 1, 8, true, true, true));
        u.pile().setRodInsertion(Piles.ZIP_KEY, 0);
        u.pile().setRodInsertion(Piles.CONTROL_KEY, 0.7);
        run(w, 5);

        MutableStateTree saved = w.saveTree();
        SimulationWorld c = world();
        c.load(SimulationWorld.upgrade(saved, new MigrationRegistry()));
        PileUnit u2 = c.subsystem(ReactorSubsystem.class).unit(c.topology(), id);
        assertThat(u2).isNotNull();
        u2.form(Piles.cube(4, 1, 8, true, true, true));
        assertThat(u2.pile().rodInsertion(Piles.CONTROL_KEY)).isEqualTo(u.pile().rodInsertion(Piles.CONTROL_KEY));
        assertThat(u2.pile().zipCocked()).isTrue();
        assertThat(u2.pile().trip()).isEqualTo(PileTrip.NONE);
    }

    @Test
    void safeStateWhenTheSaveIsLost() {
        SimulationWorld w = world();
        SimId id = w.topology().addDevice(ReactorSubsystem.PILE_CONTROLLER, new GridPos(0, 64, 0));
        PileUnit u = w.subsystem(ReactorSubsystem.class).unit(w.topology(), id);
        u.form(Piles.cube(4, 1, 8, true, true, true));
        u.pile().setRodInsertion(Piles.ZIP_KEY, 0);
        w.subsystem(ReactorSubsystem.class).resetToSafeState();
        assertThat(u.pile().tripped()).isTrue();
        assertThat(u.pile().zipInsertion()).isEqualTo(1.0);
    }
}
