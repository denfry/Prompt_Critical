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

/** Проверки I1 (план HP2.1): изоляция и центровка генератора, чек-лист домны. */
class OpsI1ChecksTest {
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

    /** Генератор с заданными дефектами монтажа вместо случайных. */
    private static SimId generator(OpsSubsystem ops, long serial, CommissioningDefectKind... kinds) {
        SimId g = new SimId(serial);
        ops.ensure(g, UnitKind.SYNC_GENERATOR, 0, false, Set.of(), false);
        CommissioningService c = ops.commissioning();
        UnitAssembly asm = c.assembly(g).orElseThrow();
        List<CommissioningDefect> defects = new ArrayList<>();
        for (CommissioningDefectKind k : kinds) {
            UnitComponent role = k == CommissioningDefectKind.WINDING_DAMP ? UnitComponent.STATOR : UnitComponent.COUPLING;
            double mag = k == CommissioningDefectKind.WINDING_DAMP ? 2.0 : 0.3;
            defects.add(CommissioningDefect.of(k, asm.components().get(role), mag));
        }
        c.unregister(g);
        c.register(asm, defects, 0);
        return g;
    }

    @Test
    void insulationTestFindsDampWinding() {
        OpsSubsystem ops = ops(OpsConfig.defaults());
        SimId g = generator(ops, 201, CommissioningDefectKind.WINDING_DAMP);
        CheckResult r = ops.perform(g, new CheckInput.InsulationTest(true), null);
        assertThat(r.passed()).isTrue();
        assertThat(r.found()).extracting(CommissioningDefect::kind).containsExactly(CommissioningDefectKind.WINDING_DAMP);
        assertThat(r.readings().get(CheckResult.READ_INSULATION_BEFORE_MOHM))
                .isLessThan(r.readings().get(CheckResult.READ_INSULATION_AFTER_MOHM));
        assertThat(ops.commissioning().has(g, CommissioningDefectKind.WINDING_DAMP)).isFalse();
    }

    @Test
    void insulationTestRefusedWhileRunning() {
        OpsSubsystem ops = ops(OpsConfig.defaults());
        SimId g = generator(ops, 202);
        CheckResult r = ops.perform(g, new CheckInput.InsulationTest(false), null);
        assertThat(r.outcome()).isEqualTo(CheckResult.Outcome.REFUSED);
        assertThat(r.unmet()).contains(CheckCondition.UNIT_RUNNING);
    }

    @Test
    void alignmentCorrectsCoupling() {
        OpsSubsystem ops = ops(OpsConfig.defaults());
        SimId g = generator(ops, 203, CommissioningDefectKind.COUPLING_MISALIGNED);
        CheckResult r = ops.perform(g, new CheckInput.Alignment(true), null);
        assertThat(r.passed()).isTrue();
        assertThat(r.readings().get(CheckResult.READ_MISALIGNMENT_MM)).isGreaterThan(0);
        assertThat(ops.commissioning().has(g, CommissioningDefectKind.COUPLING_MISALIGNED)).isFalse();
        assertThat(ops.perform(g, new CheckInput.Alignment(false), null).outcome()).isEqualTo(CheckResult.Outcome.REFUSED);
    }

    @Test
    void generatorAcceptedAfterChecksAndTrial() {
        OpsSubsystem ops = ops(OpsConfig.defaults().withTrialRunS(1));
        SimId g = generator(ops, 204);
        assertThat(ops.perform(g, new CheckInput.InsulationTest(true), null).passed()).isTrue();
        assertThat(ops.perform(g, new CheckInput.Alignment(true), null).passed()).isTrue();
        ops.observeTrial(g, 2, true, 1, false, Baseline.NONE);
        ops.observeTrial(g, 1, false, 0, false, Baseline.NONE);
        assertThat(ops.commissioning().state(g)).contains(CommissioningState.ACCEPTED);
    }

    /** Ревью HP2.1: заделанный шов держит пробное давление и у низконапорной установки (0,4 МПа → 0,7 МПа). */
    @Test
    void caulkedSeamHoldsTheTestPressureOfALowPressureUnit() {
        OpsSubsystem ops = ops(OpsConfig.defaults());
        SimId f = new SimId(205);
        double pw = 0.4e6;
        ops.ensure(f, UnitKind.BLAST_FURNACE, pw, false, Set.of(), false);
        CommissioningService c = ops.commissioning();
        UnitAssembly asm = c.assembly(f).orElseThrow();
        c.unregister(f);
        c.register(asm, List.of(new CommissioningDefect(CommissioningDefectKind.SEAM_WEEP, asm.components().get(UnitComponent.SHELL),
                1.1, 2.0, false)), 0);
        assertThat(c.caulk(f, true)).isTrue();
        double test = ops.config().testPressurePa(pw);
        CheckResult r = ops.perform(f, new CheckInput.PressureTest(true, 290, test, ops.config().hydrotestHoldPhysS()), null);
        assertThat(r.outcome()).isEqualTo(CheckResult.Outcome.PASSED);
    }

    @Test
    void blastFurnaceChecklist() {
        assertThat(UnitKind.BLAST_FURNACE.era()).isEqualTo(PlantEra.I1);
        assertThat(UnitKind.SYNC_GENERATOR.era()).isEqualTo(PlantEra.I1);
        assertThat(UnitKind.BLAST_FURNACE.checklist()).isEqualTo(EnumSet.of(CommissioningCheck.PRESSURE_TEST,
                CommissioningCheck.INSTRUMENT_CHECK, CommissioningCheck.TRIAL_RUN));
        assertThat(UnitKind.SYNC_GENERATOR.checklist()).isEqualTo(EnumSet.of(CommissioningCheck.INSULATION_TEST,
                CommissioningCheck.ALIGNMENT, CommissioningCheck.TRIAL_RUN));
    }
}
