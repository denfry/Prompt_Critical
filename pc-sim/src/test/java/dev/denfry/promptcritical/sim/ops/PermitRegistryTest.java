package dev.denfry.promptcritical.sim.ops;

import static org.assertj.core.api.Assertions.assertThat;

import dev.denfry.promptcritical.sim.core.GridPos;
import dev.denfry.promptcritical.sim.core.MutableStateTree;
import dev.denfry.promptcritical.sim.core.TreeStateReader;
import dev.denfry.promptcritical.sim.core.TreeStateWriter;
import dev.denfry.promptcritical.sim.ops.PermitRegistry.Permit;
import dev.denfry.promptcritical.sim.ops.PermitRegistry.Result;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Наряд-допуск 13 §13.2: автомат §13.2.4, газоанализ §13.2.3, огневые работы §13.2.5, T-13-07 и T-13-08. */
class PermitRegistryTest {
    private static final GridPos TANK = new GridPos(0, 64, 0);
    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final long S = Math.round(PermitRegistry.TICKS_PER_S);
    private final PermitRegistry r = new PermitRegistry();

    private Permit hot(long tick) {
        return r.issue(PermitType.HOT, TANK, PermitRegistry.HOT_WORK_RADIUS_M, "tank", A, "a", 0, tick);
    }

    private GasTest gas(long tick, double lel) {
        return new GasTest(tick, A, "portable_gas_detector", true, 0.209, lel, 0, 0);
    }

    @Test
    void hotWorkGoesThroughIsolationGasTestAndAdmission() {
        Permit p = hot(0);
        assertThat(p.state()).isEqualTo(PermitState.ISSUED);
        assertThat(r.admit(p.id(), A, "a", true, 1)).isEqualTo(Result.NOT_ISOLATED);
        assertThat(r.isolate(p.id(), false, 1)).isEqualTo(Result.NOT_ISOLATED);
        assertThat(r.isolate(p.id(), true, 2)).isEqualTo(Result.OK);
        assertThat(r.admit(p.id(), A, "a", true, 3)).isEqualTo(Result.NO_GAS_TEST);
        assertThat(r.gasTest(p.id(), gas(4, 0.03), PermitEnforcement.ENFORCED, 4)).containsExactly(GasLimits.Fault.FLAMMABLE);
        assertThat(p.state()).isEqualTo(PermitState.ISOLATED);
        assertThat(r.gasTest(p.id(), gas(5, 0), PermitEnforcement.ENFORCED, 5)).isEmpty();
        assertThat(p.state()).isEqualTo(PermitState.GAS_TESTED);
        assertThat(r.admit(p.id(), A, "a", false, 6)).isEqualTo(Result.NO_FIRE_WATCH);
        assertThat(r.admit(p.id(), A, "a", true, 6)).isEqualTo(Result.OK);
        assertThat(p.state()).isEqualTo(PermitState.ACTIVE);
        assertThat(r.activeCovering(PermitType.HOT, new GridPos(10, 64, 0))).isSameAs(p);
        assertThat(r.activeCovering(PermitType.HOT, new GridPos(12, 64, 0))).isNull();
        assertThat(r.complete(p.id(), 7)).isEqualTo(Result.OK);
        assertThat(r.close(p.id(), true, 8)).isEqualTo(Result.LOCKS_REMAIN);
        assertThat(r.close(p.id(), false, 9)).isEqualTo(Result.OK);
        assertThat(p.state()).isEqualTo(PermitState.CLOSED);
        assertThat(r.activeCovering(PermitType.HOT, TANK)).isNull();
        assertThat(r.drainTransitions()).extracting(PermitRegistry.Transition::to).containsExactly(PermitState.ISSUED,
                PermitState.ISOLATED, PermitState.GAS_TESTED, PermitState.ACTIVE, PermitState.WORK_COMPLETE, PermitState.CLOSED);
    }

    /** §13.2.3: прибор без проверки газом при ENFORCED не принимается; при ADVISORY — принимается. */
    @Test
    void anUntestedInstrumentCountsOnlyWhenAdvisory() {
        GasTest t = new GasTest(1, A, "portable_gas_detector", false, 0.209, 0, 0, 0);
        assertThat(GasLimits.DEFAULT.faults(PermitType.HOT, t, true)).containsExactly(GasLimits.Fault.NOT_BUMP_TESTED);
        assertThat(GasLimits.DEFAULT.faults(PermitType.HOT, t, false)).isEmpty();
        GasTest bad = new GasTest(1, A, "x", true, 0.18, 0.05, 12, 30);
        assertThat(GasLimits.DEFAULT.faults(PermitType.CONFINED_SPACE, bad, true)).containsExactlyInAnyOrder(
                GasLimits.Fault.O2_LOW, GasLimits.Fault.H2S, GasLimits.Fault.CO);
        assertThat(GasLimits.DEFAULT.faults(PermitType.HOT, bad, true)).contains(GasLimits.Fault.FLAMMABLE);
    }

    /** T-13-08: тревога газоанализатора при ACTIVE приостанавливает наряд в том же тике; повторный анализ — допуск снова. */
    @Test
    void aGasAlarmSuspendsTheActivePermit() {
        Permit p = hot(0);
        r.isolate(p.id(), true, 0);
        r.gasTest(p.id(), gas(0, 0), PermitEnforcement.ENFORCED, 0);
        r.admit(p.id(), A, "a", true, 0);
        r.drainTransitions();
        assertThat(r.gasAlarm(p.id(), 10)).isTrue();
        assertThat(p.state()).isEqualTo(PermitState.SUSPENDED);
        assertThat(r.drainTransitions()).singleElement().satisfies(t -> {
            assertThat(t.tick()).isEqualTo(10);
            assertThat(t.reason()).isEqualTo(PermitRegistry.Reason.GAS_ALARM);
        });
        assertThat(r.activeCovering(PermitType.HOT, TANK)).isNull();
        r.gasTest(p.id(), gas(20, 0), PermitEnforcement.ENFORCED, 20);
        assertThat(p.state()).isEqualTo(PermitState.GAS_TESTED);
        assertThat(r.admit(p.id(), A, "a", true, 21)).isEqualTo(Result.OK);
    }

    /** §13.2.3, §13.2.4: без повторного анализа через 7200 с наряд приостанавливается, по сроку 4 ч — истекает. */
    @Test
    void retestAndExpiryTimers() {
        Permit p = hot(0);
        r.isolate(p.id(), true, 0);
        r.gasTest(p.id(), gas(0, 0), PermitEnforcement.ENFORCED, 0);
        r.admit(p.id(), A, "a", true, 0);
        r.step(7200 * S);
        assertThat(p.state()).isEqualTo(PermitState.ACTIVE);
        r.step(7200 * S + 1);
        assertThat(p.state()).isEqualTo(PermitState.SUSPENDED);
        long late = 7201 * S;
        r.gasTest(p.id(), gas(late, 0), PermitEnforcement.ENFORCED, late);
        assertThat(r.admit(p.id(), A, "a", true, late)).isEqualTo(Result.OK);
        r.step(14400 * S + 1);
        assertThat(p.state()).isEqualTo(PermitState.SUSPENDED);
        r.step(14400 * S + 2);
        assertThat(p.state()).isEqualTo(PermitState.EXPIRED);
        assertThat(r.gasTest(p.id(), gas(late, 0), PermitEnforcement.ENFORCED, late)).isEmpty();
        assertThat(p.state()).isEqualTo(PermitState.EXPIRED);
    }

    @Test
    void aColdPermitNeedsNoGasTestAndCountsWork() {
        Permit p = r.issue(PermitType.RADIATION, TANK, 5, "promptcritical:primary_pump", A, "a", 60, 0);
        r.isolate(p.id(), true, 0);
        assertThat(r.admit(p.id(), A, "a", false, 1)).isEqualTo(Result.OK);
        r.addDose(p.id(), A, 0.002);
        r.addDose(p.id(), UUID.randomUUID(), 1); // не член бригады — не учитывается
        r.addWork(p.id(), 30);
        assertThat(r.complete(p.id(), 2)).isEqualTo(Result.WORK_NOT_DONE);
        r.addWork(p.id(), 30);
        assertThat(r.complete(p.id(), 3)).isEqualTo(Result.OK);
        assertThat(p.collectiveDoseSv()).isEqualTo(0.002);
        assertThat(r.completedNear(TANK, 16, 0)).isEqualTo(1);
        assertThat(r.openNear(TANK, 16)).isEqualTo(1);
        r.close(p.id(), false, 4);
        assertThat(r.openNear(TANK, 16)).isZero();
    }

    @Test
    void permitsSurviveSaveAndLoad() {
        Permit p = r.issue(PermitType.RADIATION, TANK, 5, "pump", A, "a", 60, 0);
        r.isolate(p.id(), true, 0);
        r.admit(p.id(), A, "a", false, 1);
        r.addDose(p.id(), A, 0.004);
        r.addWork(p.id(), 12);
        Permit h = hot(2);
        r.isolate(h.id(), true, 2);
        r.gasTest(h.id(), gas(3, 0), PermitEnforcement.ENFORCED, 3);
        MutableStateTree tree = new MutableStateTree();
        r.save(new TreeStateWriter(tree));
        PermitRegistry b = new PermitRegistry();
        b.load(new TreeStateReader(tree));
        Permit q = b.permit(p.id());
        assertThat(q.state()).isEqualTo(PermitState.ACTIVE);
        assertThat(q.crew()).containsEntry(A, "a");
        assertThat(q.collectiveDoseSv()).isEqualTo(0.004);
        assertThat(q.workS()).isEqualTo(12);
        assertThat(q.requiredWorkS()).isEqualTo(60);
        assertThat(q.targetKind()).isEqualTo("pump");
        assertThat(b.permit(h.id()).gas()).isEqualTo(h.gas());
        assertThat(b.issue(PermitType.COLD, TANK, 3, "", A, "a", 0, 5).id()).isEqualTo(3);
    }

    /** §13.2.5: огневое действие без наряда — по уровню принуждения; воспламенение — физика 27 точек. */
    @Test
    void hotWorkVerdictAndIgnition() {
        assertThat(HotWork.verdict(PermitEnforcement.ENFORCED, false, false)).isEqualTo(HotWork.Verdict.ALLOW);
        assertThat(HotWork.verdict(PermitEnforcement.ENFORCED, true, false)).isEqualTo(HotWork.Verdict.REFUSE);
        assertThat(HotWork.verdict(PermitEnforcement.ADVISORY, true, false)).isEqualTo(HotWork.Verdict.WARN);
        assertThat(HotWork.verdict(PermitEnforcement.OFF, true, false)).isEqualTo(HotWork.Verdict.ALLOW);
        assertThat(HotWork.verdict(PermitEnforcement.ENFORCED, true, true)).isEqualTo(HotWork.Verdict.ALLOW);
        double[] pts = new double[HotWork.SAMPLE_POINTS];
        assertThat(HotWork.ignites(pts)).isFalse();
        pts[13] = 0.99;
        assertThat(HotWork.ignites(pts)).isFalse();
        pts[26] = 1.2; // T-13-22: 120 % НКПР в одной из 27 точек
        assertThat(HotWork.ignites(pts)).isTrue();
    }
}
