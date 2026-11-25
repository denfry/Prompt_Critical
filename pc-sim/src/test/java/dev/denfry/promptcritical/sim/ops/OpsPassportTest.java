package dev.denfry.promptcritical.sim.ops;

import static org.assertj.core.api.Assertions.assertThat;

import dev.denfry.promptcritical.sim.core.ExternalInputs;
import dev.denfry.promptcritical.sim.core.GridPos;
import dev.denfry.promptcritical.sim.core.MigrationRegistry;
import dev.denfry.promptcritical.sim.core.MutableStateTree;
import dev.denfry.promptcritical.sim.core.SimConfig;
import dev.denfry.promptcritical.sim.core.SimId;
import dev.denfry.promptcritical.sim.core.SimulationWorld;
import dev.denfry.promptcritical.sim.core.exec.WorkerPool;
import dev.denfry.promptcritical.sim.manufacturing.assembly.MachinePassport;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Паспорт машины задаёт начальное состояние модели обслуживания 13 (18 §18.8.3, §18.15.7). */
class OpsPassportTest {
    private final WorkerPool pool = WorkerPool.direct();
    private final List<SimulationWorld> worlds = new ArrayList<>();

    @AfterEach
    void close() {
        for (SimulationWorld w : worlds) w.close();
        pool.close();
    }

    private SimulationWorld world() {
        SimulationWorld w = new SimulationWorld(SimConfig.defaults(), 7, 0, pool);
        w.register(new OpsSubsystem(OpsConfig.defaults(), 7));
        worlds.add(w);
        return w;
    }

    private static final MachinePassport BAD =
            new MachinePassport(3.0, 1.9, 0.06, 0.016, 0.16, Double.NaN, 1, 40);

    @Test
    void pumpWithABadShaftWearsFaster() {
        SimulationWorld w = world();
        OpsSubsystem ops = w.subsystem(OpsSubsystem.class);
        SimId good = w.topology().addDevice("promptcritical:mine_drainage_pump", new GridPos(0, 64, 0));
        SimId bad = w.topology().addDevice("promptcritical:mine_drainage_pump", new GridPos(8, 64, 0));
        OpsSubsystem.Unit g = ops.ensure(good, UnitKind.MINE_DRAINAGE_PUMP, 0, false, Set.of(), false);
        OpsSubsystem.Unit b = ops.ensure(bad, UnitKind.MINE_DRAINAGE_PUMP, 0, false, Set.of(), false, BAD);
        // одинаковый подшипник, чтобы сравнивать только посадку
        b.wear().bearing().replace(g.wear().bearing().lifeFactor());
        g.setRunning(true, false, 7);
        b.setRunning(true, false, 7);
        for (int i = 0; i < 2000; i++) w.tick(ExternalInputs.NONE);
        double dg = g.wear().bearing().damage(), db = b.wear().bearing().damage();
        assertThat(dg).isPositive();
        assertThat(db / dg).isCloseTo(3.0, org.assertj.core.data.Offset.offset(1e-6));
    }

    @Test
    void assemblyDefectsShowOnThePanelAndSurviveSaveAndRepair() {
        SimulationWorld w = world();
        OpsSubsystem ops = w.subsystem(OpsSubsystem.class);
        SimId pump = w.topology().addDevice("promptcritical:mine_drainage_pump", new GridPos(0, 64, 0));
        OpsSubsystem.Unit u = ops.ensure(pump, UnitKind.MINE_DRAINAGE_PUMP, 0, false, Set.of(), false, BAD);
        int row = OpsSubsystemTestRows.index(u, MaintenanceFaultKind.IMBALANCE);
        assertThat(row).isNotNegative();
        assertThat(u.panel().source(row).value()).isEqualTo(BAD.imbalanceMmS());
        assertThat(OpsSubsystemTestRows.index(u, MaintenanceFaultKind.MISALIGNMENT)).isNotNegative();
        // паспорт при повторной установке той же машины не перезаписывается
        ops.ensure(pump, UnitKind.MINE_DRAINAGE_PUMP, 0, false, Set.of(), false, MachinePassport.nominal());
        assertThat(u.passport()).isEqualTo(BAD);

        MutableStateTree saved = w.saveTree();
        SimulationWorld c = world();
        c.load(SimulationWorld.upgrade(saved, new MigrationRegistry()));
        OpsSubsystem.Unit u2 = c.subsystem(OpsSubsystem.class).unit(pump);
        assertThat(u2.passport()).isEqualTo(BAD);
        assertThat(u2.wear().assemblyWearFactor()).isEqualTo(3.0);
        assertThat(u2.panel().faults()).isEqualTo(u.panel().faults());
        assertThat(c.stateHash()).isEqualTo(w.stateHash());

        ops.repair(pump, row, null, false);                       // балансировка на месте
        assertThat(u.panel().source(row).value()).isZero();
    }

    @Test
    void windingInsulationFromThePassportStartsInsulationAgingAndRewindingRestoresIt() {
        SimulationWorld w = world();
        OpsSubsystem ops = w.subsystem(OpsSubsystem.class);
        SimId gen = w.topology().addDevice("promptcritical:shaft_generator", new GridPos(0, 64, 0));
        MachinePassport wet = new MachinePassport(1, 0, 0, 0, 0, 8, 0.97, 60);
        OpsSubsystem.Unit u = ops.ensure(gen, UnitKind.SYNC_GENERATOR, 0, false, Set.of(), false, wet);
        int row = OpsSubsystemTestRows.index(u, MaintenanceFaultKind.INSULATION_AGING);
        assertThat(row).isNotNegative();
        assertThat(u.panel().source(row).value()).isEqualTo(wet.insulationMOhm());
        assertThat(MaintenanceFaultKind.INSULATION_AGING.severity(u.panel().source(row).value())).isEqualTo(2);

        MutableStateTree saved = w.saveTree();
        SimulationWorld c = world();
        c.load(SimulationWorld.upgrade(saved, new MigrationRegistry()));
        assertThat(c.stateHash()).isEqualTo(w.stateHash());

        ops.repair(gen, row, null, false);                        // перемотка
        assertThat(u.panel().source(row).value()).isEqualTo(MaintenanceFaultKind.INSULATION_AGING.normal());
    }

    @Test
    void machineWithoutWindingGetsNoInsulationRow() {
        SimulationWorld w = world();
        OpsSubsystem ops = w.subsystem(OpsSubsystem.class);
        SimId pump = w.topology().addDevice("promptcritical:mine_drainage_pump", new GridPos(0, 64, 0));
        OpsSubsystem.Unit u = ops.ensure(pump, UnitKind.MINE_DRAINAGE_PUMP, 0, false, Set.of(), false, BAD);
        assertThat(OpsSubsystemTestRows.index(u, MaintenanceFaultKind.INSULATION_AGING)).isNegative();
    }

    @Test
    void nominalPassportChangesNothing() {
        SimulationWorld w = world();
        OpsSubsystem ops = w.subsystem(OpsSubsystem.class);
        SimId pump = w.topology().addDevice("promptcritical:mine_drainage_pump", new GridPos(0, 64, 0));
        OpsSubsystem.Unit u = ops.ensure(pump, UnitKind.MINE_DRAINAGE_PUMP, 0, false, Set.of(), false);
        assertThat(u.passport()).isEqualTo(MachinePassport.nominal());
        assertThat(OpsSubsystemTestRows.index(u, MaintenanceFaultKind.IMBALANCE)).isNegative();
        assertThat(u.wear().assemblyWearFactor()).isEqualTo(1);
    }

    /** Строка щита по виду (как {@code OpsWorld.index} в моде). */
    private static final class OpsSubsystemTestRows {
        static int index(OpsSubsystem.Unit u, MaintenanceFaultKind kind) {
            for (int i = 0; i < u.panel().size(); i++) if (u.panel().source(i).kind() == kind) return i;
            return -1;
        }
    }
}
