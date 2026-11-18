package dev.denfry.promptcritical.sim.grid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.denfry.promptcritical.sim.core.ExternalInputs;
import dev.denfry.promptcritical.sim.core.GridPos;
import dev.denfry.promptcritical.sim.core.SimConfig;
import dev.denfry.promptcritical.sim.core.SimId;
import dev.denfry.promptcritical.sim.core.SimulationWorld;
import dev.denfry.promptcritical.sim.core.exec.WorkerPool;
import dev.denfry.promptcritical.sim.core.MigrationRegistry;
import dev.denfry.promptcritical.sim.core.MutableStateTree;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Щиток освещения: группа светильников — одна нагрузка 0,4 кВ за трансформатором 6/0,4 кВ; мощность группы меняется
 * без пересборки сети, групповой автомат гасит группу, без источника лампы не горят, состояние переживает сохранение.
 */
class LightingBoardTest {
    private static final long BUS_A = 21, BUS_B = 22, BUS_LV = 23;
    private final WorkerPool pool = WorkerPool.direct();
    private final List<SimulationWorld> worlds = new ArrayList<>();
    private final List<GeneratorUnit> shafts = new ArrayList<>();
    private int x;

    @AfterEach
    void close() {
        for (SimulationWorld w : worlds) w.close();
        pool.close();
    }

    private SimulationWorld world() {
        SimulationWorld w = new SimulationWorld(SimConfig.defaults(), 7, 0, pool);
        w.register(new GridSubsystem(GridConfig.defaults()));
        worlds.add(w);
        return w;
    }

    private static GridSubsystem grid(SimulationWorld w) {
        return w.subsystem(GridSubsystem.class);
    }

    private SimId device(SimulationWorld w, String type) {
        return w.topology().addDevice("promptcritical:" + type, new GridPos(x++, 64, 0));
    }

    /** Генератор 6,3 кВ на шине A, ячейка A–B, трансформатор B → 0,4 кВ, щиток на стороне 0,4 кВ. */
    private LightingBoardUnit plant(SimulationWorld w, boolean withSource, double demandW) {
        if (withSource) {
            GeneratorUnit g = grid(w).generator(device(w, "shaft_generator"));
            g.setVoltageClass(VoltageClass.MV_6_3);
            g.setNodes(BUS_A);
            g.setExciterPresent(true);
            g.setFieldOn(true);
            shafts.add(g);
            SwitchUnit s = grid(w).switchUnit(device(w, "switchgear_cell"), SwitchUnit.Kind.MV_BREAKER);
            s.setNodes(BUS_A, BUS_B);
            s.operate(true);
        }
        grid(w).transformer(device(w, "distribution_transformer")).setNodes(BUS_B, BUS_LV);
        LightingBoardUnit l = grid(w).lightingBoard(device(w, "lighting_board"));
        l.setNodes(BUS_LV);
        l.setDemandW(demandW);
        return l;
    }

    private void run(SimulationWorld w, double seconds) {
        int n = (int) Math.round(seconds * 20);
        for (int i = 0; i < n; i++) {
            for (GeneratorUnit g : shafts) if (g.owner == grid(w)) g.shaft.sample(64, true, w.clock().tick());
            w.tick(ExternalInputs.NONE);
            w.drainEvents(e -> { });
        }
    }

    @Test
    void groupDrawsItsLampsAndFollowsTheirNumberWithoutRebuild() {
        SimulationWorld w = world();
        LightingBoardUnit l = plant(w, true, 4_000);
        run(w, 30);
        assertThat(l.inputPu()).isGreaterThan(0.9);
        assertThat(l.lampsLit()).isTrue();
        assertThat(l.powerW()).isCloseTo(4_000, within(4_000 * 0.12));
        int rebuilds = grid(w).rebuilds();
        l.setDemandW(10_000); // поставили ещё светильников
        run(w, 1);
        assertThat(grid(w).rebuilds()).isEqualTo(rebuilds);
        assertThat(l.powerW()).isCloseTo(10_000, within(10_000 * 0.12));
    }

    @Test
    void groupBreakerPutsTheLampsOut() {
        SimulationWorld w = world();
        LightingBoardUnit l = plant(w, true, 2_000);
        run(w, 30);
        l.setSwitchedOn(false);
        run(w, 0.5);
        assertThat(l.lampsLit()).isFalse();
        assertThat(l.powerW()).isZero();
        l.setSwitchedOn(true);
        run(w, 0.5);
        assertThat(l.lampsLit()).isTrue();
    }

    @Test
    void lampsStayDarkWithoutASource() {
        SimulationWorld w = world();
        LightingBoardUnit l = plant(w, false, 2_000);
        run(w, 2);
        assertThat(l.inputPu()).isZero();
        assertThat(l.lampsLit()).isFalse();
    }

    @Test
    void demandAndBreakerSurviveSaveAndLoad() {
        SimulationWorld w = world();
        LightingBoardUnit l = plant(w, true, 3_000);
        l.setSwitchedOn(false);
        run(w, 1);
        MutableStateTree saved = w.saveTree();
        SimulationWorld c = world();
        c.load(SimulationWorld.upgrade(saved, new MigrationRegistry()));
        assertThat(c.stateHash()).isEqualTo(w.stateHash());
        LightingBoardUnit back = (LightingBoardUnit) grid(c).unit(l.id());
        assertThat(back.demandW()).isEqualTo(3_000);
        assertThat(back.switchedOn()).isFalse();
    }
}
