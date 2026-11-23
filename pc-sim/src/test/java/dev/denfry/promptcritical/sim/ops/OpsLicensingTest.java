package dev.denfry.promptcritical.sim.ops;

import static org.assertj.core.api.Assertions.assertThat;

import dev.denfry.promptcritical.sim.core.MutableStateTree;
import dev.denfry.promptcritical.sim.core.SimConfig;
import dev.denfry.promptcritical.sim.core.SimId;
import dev.denfry.promptcritical.sim.core.SimulationWorld;
import dev.denfry.promptcritical.sim.core.TreeStateReader;
import dev.denfry.promptcritical.sim.core.TreeStateWriter;
import dev.denfry.promptcritical.sim.core.exec.WorkerPool;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Профиль licensing в {@link OpsSubsystem}: принудительный ENFORCED, владелец по первой проверке, учёт принятых типов. */
class OpsLicensingTest {
    private static final double PW = 1.2e6;
    private static final UUID TEAM = new UUID(0, 9), PLAYER = new UUID(0, 5);
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

    private OpsSubsystem licensed(OpsConfig cfg) {
        OpsSubsystem ops = ops(cfg);
        ops.setLicensing(true);
        return ops;
    }

    private static SimId boiler(OpsSubsystem ops, long serial) {
        SimId b = new SimId(serial);
        ops.ensure(b, UnitKind.FIRE_TUBE_BOILER, PW, false, Set.of(), false);
        return b;
    }

    private static void passStaticChecks(OpsSubsystem ops, SimId b, UUID team) {
        double test = ops.config().testPressurePa(PW);
        assertThat(ops.perform(b, new CheckInput.PressureTest(true, 300, test, 600), PLAYER, team).passed()).isTrue();
        assertThat(ops.perform(b, new CheckInput.InstrumentCheck(), PLAYER, team).passed()).isTrue();
        assertThat(ops.perform(b, new CheckInput.ReliefValveSet(false), PLAYER, team).passed()).isTrue();
    }

    @Test
    void licensingForcesEnforced() {
        OpsSubsystem ops = licensed(OpsConfig.defaults().withCommissioning(CommissioningMode.ADVISORY).withDefectRate(0));
        SimId b = boiler(ops, 101);
        assertThat(ops.canStart(b).allowed()).isFalse();
        assertThat(ops.licensing()).isTrue();
    }

    @Test
    void licensingRegistersUnitsEvenWhenCommissioningIsOff() {
        OpsSubsystem ops = licensed(OpsConfig.defaults().withCommissioning(CommissioningMode.OFF).withDefectRate(0));
        SimId b = boiler(ops, 102);
        assertThat(ops.commissioning().registered(b)).isTrue();
        assertThat(ops.canStart(b).allowed()).isFalse();
    }

    @Test
    void unownedUnitIsRefused() {
        OpsSubsystem ops = licensed(OpsConfig.defaults().withDefectRate(0));
        SimId b = boiler(ops, 103);
        StartPermission p = ops.canStart(b);
        assertThat(p.allowed()).isFalse();
        assertThat(p.unlicensedEra()).isNull();
        assertThat(p.missing()).isNotEmpty();
        assertThat(ops.licences().owner(b)).isEmpty();
    }

    @Test
    void i0UnitStartsAfterChecks() {
        OpsSubsystem ops = licensed(OpsConfig.defaults().withDefectRate(0));
        SimId b = boiler(ops, 104);
        passStaticChecks(ops, b, TEAM);
        assertThat(ops.licences().owner(b)).contains(TEAM);
        StartPermission p = ops.requestStart(b);
        assertThat(p.allowed()).isTrue();
        assertThat(p.unlicensedEra()).isNull();
    }

    @Test
    void acceptanceRecordsKind() {
        OpsSubsystem ops = licensed(OpsConfig.defaults().withDefectRate(0).withTrialRunS(1));
        SimId b = boiler(ops, 105);
        passStaticChecks(ops, b, TEAM);
        ops.observeTrial(b, 2, true, 1, false, Baseline.NONE);
        ops.observeTrial(b, 1, false, 0, false, Baseline.NONE);
        assertThat(ops.commissioning().state(b)).contains(CommissioningState.ACCEPTED);
        assertThat(ops.licences().acceptedKinds(TEAM)).containsExactly(UnitKind.FIRE_TUBE_BOILER);
    }

    /** Ревью HP1: отказанная проверка не делает команду владельцем чужой установки. */
    @Test
    void refusedCheckDoesNotClaimTheUnit() {
        OpsSubsystem ops = licensed(OpsConfig.defaults().withDefectRate(0));
        SimId b = boiler(ops, 108);
        CheckResult r = ops.perform(b, new CheckInput.ReliefValveSet(false), PLAYER, TEAM);
        assertThat(r.outcome()).isEqualTo(CheckResult.Outcome.REFUSED);
        assertThat(ops.licences().owner(b)).isEmpty();
    }

    /** Ревью HP1: все читатели режима видят ENFORCED, а не сырое значение конфига. */
    @Test
    void configReportsEnforcedUnderLicensing() {
        OpsSubsystem ops = licensed(OpsConfig.defaults().withCommissioning(CommissioningMode.ADVISORY));
        assertThat(ops.config().commissioning()).isEqualTo(CommissioningMode.ENFORCED);
    }

    @Test
    void withoutLicensingNothingChanges() {
        OpsSubsystem ops = ops(OpsConfig.defaults().withCommissioning(CommissioningMode.ADVISORY).withDefectRate(0));
        SimId b = boiler(ops, 106);
        assertThat(ops.canStart(b).allowed()).isTrue();
    }

    @Test
    void saveLoadKeepsOwnerAndLicences() {
        OpsSubsystem ops = licensed(OpsConfig.defaults().withDefectRate(0));
        SimId b = boiler(ops, 107);
        passStaticChecks(ops, b, TEAM);
        ops.licences().grant(TEAM, PlantEra.I1, 0);
        MutableStateTree tree = new MutableStateTree();
        ops.save(new TreeStateWriter(tree));
        OpsSubsystem back = ops(OpsConfig.defaults().withDefectRate(0));
        back.load(new TreeStateReader(tree));
        assertThat(back.licences().owner(b)).contains(TEAM);
        assertThat(back.licences().licensed(TEAM, PlantEra.I1)).isTrue();
    }
}
