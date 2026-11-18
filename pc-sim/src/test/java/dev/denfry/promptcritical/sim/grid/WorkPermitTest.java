package dev.denfry.promptcritical.sim.grid;

import static org.assertj.core.api.Assertions.assertThat;

import dev.denfry.promptcritical.sim.core.ExternalInputs;
import dev.denfry.promptcritical.sim.core.GridPos;
import dev.denfry.promptcritical.sim.core.MigrationRegistry;
import dev.denfry.promptcritical.sim.core.SimConfig;
import dev.denfry.promptcritical.sim.core.SimId;
import dev.denfry.promptcritical.sim.core.SimulationWorld;
import dev.denfry.promptcritical.sim.core.exec.WorkerPool;
import dev.denfry.promptcritical.sim.grid.WorkPermit.Measure;
import dev.denfry.promptcritical.sim.grid.WorkPermit.Stage;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Наряд-допуск (ПОТ ЭЭ): блокировка включения, порядок технических мероприятий, закрытие. */
class WorkPermitTest {
    private static final long BUS = 21, FEEDER = 22, LOAD = 23;
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

    private void run(SimulationWorld w, double seconds) {
        for (int i = 0, n = (int) Math.round(seconds * 20); i < n; i++) {
            for (GeneratorUnit g : shafts) g.shaft.sample(64, true, w.clock().tick());
            w.tick(ExternalInputs.NONE);
        }
    }

    /** Генератор на шине, ячейка выключателя на отходящую линию, за ней разъединитель и нагрузка. */
    private record Plant(SimulationWorld w, SwitchUnit breaker, SwitchUnit disconnector, ConsumerUnit load) {
    }

    private Plant plant() {
        SimulationWorld w = world();
        GeneratorUnit g = grid(w).generator(device(w, "shaft_generator"));
        g.setVoltageClass(VoltageClass.MV_6_3);
        g.setNodes(BUS);
        g.setExciterPresent(true);
        g.setFieldOn(true);
        shafts.add(g);
        SwitchUnit br = grid(w).switchUnit(device(w, "switchgear_cell"), SwitchUnit.Kind.MV_BREAKER);
        br.setNodes(BUS, FEEDER);
        br.operate(true);
        SwitchUnit qs = grid(w).switchUnit(device(w, "disconnector"), SwitchUnit.Kind.DISCONNECTOR);
        qs.setNodes(FEEDER, LOAD);
        qs.operate(true);
        ConsumerUnit c = grid(w).consumer(device(w, "grid_consumer"));
        c.setNodes(LOAD);
        c.setDemandKw(50);
        run(w, 25);
        assertThat(c.energized()).isTrue();
        return new Plant(w, br, qs, c);
    }

    @Test
    void permitIsWrittenOnlyOnAnOpenApparatusAndBlocksClosing() {
        Plant p = plant();
        assertThat(WorkPermit.issue(p.disconnector(), 1)).isNull();           // включённый — сначала отключить
        p.breaker().operate(false);                                            // ток снимают выключателем, не ножами
        run(p.w(), 1);
        assertThat(p.disconnector().operate(false)).isEqualTo(BreakerResponse.ACCEPTED);
        assertThat(p.disconnector().lastOperationArced()).isFalse();
        run(p.w(), 0.5);
        WorkPermit permit = WorkPermit.issue(p.disconnector(), 1);
        assertThat(permit).isNotNull();
        assertThat(permit.stage()).isEqualTo(Stage.PREPARING);
        assertThat(p.disconnector().operate(true)).isEqualTo(BreakerResponse.PERMIT);
        assertThat(p.disconnector().closed()).isFalse();
    }

    @Test
    void admissionWaitsForTheTechnicalMeasuresInOrder() {
        Plant p = plant();
        p.breaker().operate(false);
        run(p.w(), 1);
        p.disconnector().operate(false);
        run(p.w(), 0.5);
        WorkPermit permit = WorkPermit.issue(p.disconnector(), 7);
        // плакат висит, нож разомкнут, напряжения за разрывом нет, но заземление не наложено
        assertThat(permit.admit(p.disconnector())).containsExactly(Measure.EARTHED);
        assertThat(permit.stage()).isEqualTo(Stage.PREPARING);
        assertThat(p.disconnector().setEarthed(true)).isTrue();
        run(p.w(), 0.5);
        assertThat(permit.admit(p.disconnector())).isEmpty();
        assertThat(permit.stage()).isEqualTo(Stage.ADMITTED);
        // пока наряд открыт, заземление не снять и нож не включить
        assertThat(p.disconnector().setEarthed(false)).isFalse();
        assertThat(p.disconnector().operate(true)).isEqualTo(BreakerResponse.PERMIT);

        assertThat(permit.close(p.disconnector())).isTrue();
        assertThat(permit.close(p.disconnector())).isFalse();
        assertThat(p.disconnector().openPermits()).isZero();
        assertThat(p.disconnector().operate(true)).isEqualTo(BreakerResponse.INTERLOCK); // ещё заземлён
        assertThat(p.disconnector().setEarthed(false)).isTrue();
        assertThat(p.disconnector().operate(true)).isEqualTo(BreakerResponse.ACCEPTED);
    }

    @Test
    void breakerUnderPermitIgnoresTheKeyAndHasNoVoltageCheckWhileTheBusIsLive() {
        Plant p = plant();
        p.breaker().operate(false);
        run(p.w(), 1);
        WorkPermit permit = WorkPermit.issue(p.breaker(), 3);
        run(p.w(), 0.5);
        assertThat(p.breaker().operate(true)).isEqualTo(BreakerResponse.PERMIT);
        // сторона шины под напряжением: проверка отсутствия напряжения не проходит
        assertThat(permit.admit(p.breaker())).contains(Measure.NO_VOLTAGE, Measure.EARTHED);
        run(p.w(), 2);
        assertThat(p.load().energized()).isFalse();
        permit.close(p.breaker());
        assertThat(p.breaker().operate(true)).isEqualTo(BreakerResponse.ACCEPTED);
        run(p.w(), 2);
        assertThat(p.load().energized()).isTrue();
    }

    @Test
    void twoBrigadesHoldTheApparatusUntilBothPermitsAreClosedAndTheLockIsSaved() {
        Plant p = plant();
        p.breaker().operate(false);
        run(p.w(), 1);
        WorkPermit a = WorkPermit.issue(p.breaker(), 1);
        WorkPermit b = WorkPermit.issue(p.breaker(), 2);
        assertThat(p.breaker().openPermits()).isEqualTo(2);

        SimulationWorld copy = world();
        copy.load(SimulationWorld.upgrade(p.w().saveTree(), new MigrationRegistry()));
        SwitchUnit restored = (SwitchUnit) copy.subsystem(GridSubsystem.class).unit(p.w().topology().deviceAt(1));
        assertThat(restored.openPermits()).isEqualTo(2);

        a.close(p.breaker());
        assertThat(p.breaker().operate(true)).isEqualTo(BreakerResponse.PERMIT);
        b.close(p.breaker());
        assertThat(p.breaker().operate(true)).isEqualTo(BreakerResponse.ACCEPTED);
        assertThat(WorkPermit.restore(9, Stage.ADMITTED).stage()).isEqualTo(Stage.ADMITTED);
    }
}
