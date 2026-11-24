package dev.denfry.promptcritical.sim.ops;

import static org.assertj.core.api.Assertions.assertThat;

import dev.denfry.promptcritical.sim.core.SimConfig;
import dev.denfry.promptcritical.sim.core.SimId;
import dev.denfry.promptcritical.sim.core.SimulationWorld;
import dev.denfry.promptcritical.sim.core.exec.WorkerPool;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Проверки I1 (план HP2.2): ячейка выключателя, водотрубный котёл, печь Ачесона, графитовый котёл T0. */
class OpsI1UnitsTest {
    private final WorkerPool pool = WorkerPool.direct();
    private final List<SimulationWorld> worlds = new ArrayList<>();

    @AfterEach
    void close() {
        for (SimulationWorld w : worlds) w.close();
        pool.close();
    }

    private OpsSubsystem ops(OpsConfig cfg) {
        SimulationWorld w = new SimulationWorld(SimConfig.defaults(), 7, 0, pool);
        w.register(new OpsSubsystem(cfg, 7));
        worlds.add(w);
        return w.subsystem(OpsSubsystem.class);
    }

    /** Установка с заданным дефектом монтажа (или без дефектов) вместо случайных. */
    private static SimId unit(OpsSubsystem ops, long serial, UnitKind kind, CommissioningDefectKind defect, double mag) {
        SimId id = new SimId(serial);
        ops.ensure(id, kind, kind == UnitKind.WATER_TUBE_BOILER ? 4e6 : 0, false, Set.of(), false);
        CommissioningService c = ops.commissioning();
        UnitAssembly asm = c.assembly(id).orElseThrow();
        List<CommissioningDefect> defects = new ArrayList<>();
        if (defect != null) {
            UnitComponent role = null;
            for (UnitComponent r : kind.components()) if (r.coveredBy() == defect.revealedBy()) role = r;
            defects.add(CommissioningDefect.of(defect, asm.components().get(role), mag));
        }
        c.unregister(id);
        c.register(asm, defects, 0);
        return id;
    }

    @Test
    void checklistsAndEras() {
        for (UnitKind k : EnumSet.of(UnitKind.BREAKER_CELL, UnitKind.WATER_TUBE_BOILER, UnitKind.ACHESON_FURNACE,
                UnitKind.GRAPHITE_PILE)) {
            assertThat(k.era()).isEqualTo(PlantEra.I1);
            assertThat(k.checklist()).contains(CommissioningCheck.TRIAL_RUN);
            // каждый возможный дефект находит проверка из чек-листа, каждый компонент покрыт проверкой из чек-листа
            for (CommissioningDefectKind d : k.possibleDefects()) assertThat(k.checklist()).contains(d.revealedBy());
            for (UnitComponent c : k.components()) assertThat(k.checklist()).contains(c.coveredBy());
        }
        assertThat(UnitKind.WATER_TUBE_BOILER.checklist()).isEqualTo(EnumSet.of(CommissioningCheck.PRESSURE_TEST,
                CommissioningCheck.INSTRUMENT_CHECK, CommissioningCheck.PURGE, CommissioningCheck.SIS_PROOF_TEST,
                CommissioningCheck.TRIAL_RUN));
    }

    @Test
    void insulationTestDriesBusbar() {
        OpsSubsystem ops = ops(OpsConfig.defaults());
        SimId b = unit(ops, 301, UnitKind.BREAKER_CELL, CommissioningDefectKind.INSULATION_DAMP, 1.5);
        assertThat(ops.perform(b, new CheckInput.InsulationTest(false), null).outcome()).isEqualTo(CheckResult.Outcome.REFUSED);
        CheckResult r = ops.perform(b, new CheckInput.InsulationTest(true), null);
        assertThat(r.passed()).isTrue();
        assertThat(r.found()).extracting(CommissioningDefect::kind).containsExactly(CommissioningDefectKind.INSULATION_DAMP);
        assertThat(r.readings().get(CheckResult.READ_INSULATION_BEFORE_MOHM)).isEqualTo(1.5);
        assertThat(ops.commissioning().has(b, CommissioningDefectKind.INSULATION_DAMP)).isFalse();
    }

    @Test
    void proofTestFindsSlowTripOnlyInSafeState() {
        OpsSubsystem ops = ops(OpsConfig.defaults());
        SimId p = unit(ops, 302, UnitKind.GRAPHITE_PILE, CommissioningDefectKind.TRIP_SLOW, 3);
        CheckResult refused = ops.perform(p, new CheckInput.SisProofTest(false), null);
        assertThat(refused.unmet()).containsExactly(CheckCondition.UNIT_RUNNING);
        CheckResult r = ops.perform(p, new CheckInput.SisProofTest(true), null);
        assertThat(r.passed()).isTrue();
        assertThat(r.readings().get(CheckResult.READ_TRIP_TIME_RATIO)).isEqualTo(3.0);
        assertThat(ops.commissioning().has(p, CommissioningDefectKind.TRIP_SLOW)).isFalse();
        // повтор без дефекта: защита в норме, меньше допустимого времени
        assertThat(ops.perform(p, new CheckInput.SisProofTest(true), null).readings().get(CheckResult.READ_TRIP_TIME_RATIO))
                .isLessThan(1.0);
    }

    @Test
    void loopCheckCorrectsMiswiredChannel() {
        OpsSubsystem ops = ops(OpsConfig.defaults());
        SimId p = unit(ops, 303, UnitKind.GRAPHITE_PILE, CommissioningDefectKind.LOOP_MISWIRED, 0.3);
        CheckResult r = ops.perform(p, new CheckInput.LoopCheck(true), null);
        assertThat(r.passed()).isTrue();
        assertThat(r.readings().get(CheckResult.READ_LOOP_ERROR)).isEqualTo(0.3);
        assertThat(ops.commissioning().has(p, CommissioningDefectKind.LOOP_MISWIRED)).isFalse();
    }

    @Test
    void shortPurgeRefused() {
        OpsSubsystem ops = ops(OpsConfig.defaults());
        SimId w = unit(ops, 304, UnitKind.WATER_TUBE_BOILER, null, 0);
        assertThat(ops.perform(w, new CheckInput.Purge(3), null).unmet()).containsExactly(CheckCondition.PURGE_SHORT);
        CheckResult r = ops.perform(w, new CheckInput.Purge(5.2), null);
        assertThat(r.passed()).isTrue();
        assertThat(r.readings().get(CheckResult.READ_PURGE_VOLUMES)).isEqualTo(5.2);
    }

    @Test
    void checkOutsideTheChecklistRefused() {
        OpsSubsystem ops = ops(OpsConfig.defaults());
        SimId a = unit(ops, 305, UnitKind.ACHESON_FURNACE, null, 0);
        assertThat(ops.perform(a, new CheckInput.LoopCheck(true), null).unmet()).containsExactly(CheckCondition.NOT_IN_CHECKLIST);
    }

    @Test
    void breakerAcceptedAfterChecksAndTrial() {
        OpsSubsystem ops = ops(OpsConfig.defaults().withTrialRunS(1));
        SimId b = unit(ops, 306, UnitKind.BREAKER_CELL, null, 0);
        assertThat(ops.perform(b, new CheckInput.InsulationTest(true), null).passed()).isTrue();
        assertThat(ops.perform(b, new CheckInput.SisProofTest(true), null).passed()).isTrue();
        ops.observeTrial(b, 2, true, 1, false, Baseline.NONE);
        ops.observeTrial(b, 1, false, 0, false, Baseline.NONE);
        assertThat(ops.commissioning().state(b)).contains(CommissioningState.ACCEPTED);
    }

    /** Новые установки без своих подшипников: нет износа и строк на щите обслуживания. */
    @Test
    void newUnitsHaveNoWear() {
        OpsSubsystem ops = ops(OpsConfig.defaults());
        SimId w = unit(ops, 307, UnitKind.WATER_TUBE_BOILER, null, 0);
        assertThat(ops.unit(w).wear()).isNull();
    }
}
