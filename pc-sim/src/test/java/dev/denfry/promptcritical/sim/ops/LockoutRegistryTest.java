package dev.denfry.promptcritical.sim.ops;

import static org.assertj.core.api.Assertions.assertThat;

import dev.denfry.promptcritical.sim.core.GridPos;
import dev.denfry.promptcritical.sim.core.MutableStateTree;
import dev.denfry.promptcritical.sim.core.TreeStateReader;
import dev.denfry.promptcritical.sim.core.TreeStateWriter;
import dev.denfry.promptcritical.sim.ops.LockoutRegistry.IsolationState;
import dev.denfry.promptcritical.sim.ops.LockoutRegistry.LockResult;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** LOTO 13 §13.1: автомат точки изоляции §13.1.8, тест-кейсы T-13-01 и T-13-04. */
class LockoutRegistryTest {
    private static final GridPos P = new GridPos(10, 64, -3);
    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a"),
            B = UUID.fromString("00000000-0000-0000-0000-00000000000b"), C = UUID.fromString("00000000-0000-0000-0000-00000000000c");
    private final LockoutRegistry r = new LockoutRegistry();

    private LockResult lock(UUID who, boolean isolated) {
        return r.apply(P, isolated, who, who.toString().substring(35), "", 0, 1, 8);
    }

    @Test
    void aPointInServiceTakesNoLock() {
        assertThat(lock(A, false)).isEqualTo(LockResult.NOT_ISOLATED);
        assertThat(r.state(P, false)).isEqualTo(IsolationState.IN_SERVICE);
        assertThat(r.state(P, true)).isEqualTo(IsolationState.ISOLATED);
        assertThat(r.lockedPoints()).isEmpty();
    }

    /** T-13-01: замок игрока A другой игрок снять не может. */
    @Test
    void onlyTheOwnerRemovesHisLock() {
        assertThat(lock(A, true)).isEqualTo(LockResult.OK);
        assertThat(r.state(P, true)).isEqualTo(IsolationState.LOCKED);
        assertThat(r.remove(P, B)).isEqualTo(LockResult.NOT_YOURS);
        assertThat(r.isLocked(P)).isTrue();
        assertThat(r.remove(P, A)).isEqualTo(LockResult.OK);
        assertThat(r.state(P, true)).isEqualTo(IsolationState.ISOLATED);
    }

    @Test
    void withoutAHaspThePointHoldsOneLock() {
        assertThat(lock(A, true)).isEqualTo(LockResult.OK);
        assertThat(lock(B, true)).isEqualTo(LockResult.NO_ROOM);
    }

    /** T-13-04: накладка с тремя замками; сняты два — точка заперта, после третьего — ISOLATED. */
    @Test
    void haspHoldsTheCrewsLocksUntilTheLastIsRemoved() {
        assertThat(r.fitHasp(P)).isEqualTo(LockResult.OK);
        assertThat(lock(A, true)).isEqualTo(LockResult.OK);
        assertThat(lock(B, true)).isEqualTo(LockResult.OK);
        assertThat(lock(C, true)).isEqualTo(LockResult.OK);
        assertThat(r.removeHasp(P)).isEqualTo(LockResult.HASP_BUSY);
        r.remove(P, A);
        r.remove(P, B);
        assertThat(r.state(P, true)).isEqualTo(IsolationState.LOCKED);
        r.remove(P, C);
        assertThat(r.state(P, true)).isEqualTo(IsolationState.ISOLATED);
        assertThat(r.removeHasp(P)).isEqualTo(LockResult.OK);
    }

    @Test
    void verificationNeedsALockAndNoEnergy() {
        assertThat(r.verify(P, true)).isEqualTo(LockResult.NOT_LOCKED);
        lock(A, true);
        assertThat(r.verify(P, false)).isEqualTo(LockResult.ENERGY_PRESENT);
        assertThat(r.verify(P, true)).isEqualTo(LockResult.OK);
        assertThat(r.state(P, true)).isEqualTo(IsolationState.VERIFIED);
        r.remove(P, A);
        lock(A, true);
        assertThat(r.state(P, true)).as("a new lock needs a new check").isEqualTo(IsolationState.LOCKED);
    }

    @Test
    void aPlayerHasALimitedNumberOfLocks() {
        for (int i = 0; i < 2; i++) assertThat(r.apply(new GridPos(i, 0, 0), true, A, "a", "", 0, 1, 2)).isEqualTo(LockResult.OK);
        assertThat(r.apply(new GridPos(5, 0, 0), true, A, "a", "", 0, 1, 2)).isEqualTo(LockResult.LIMIT);
        assertThat(r.locksOf(A)).isEqualTo(2);
    }

    /** T-13-02 (домен): администратор срезает все замки точки. */
    @Test
    void adminCutRemovesEveryLock() {
        r.fitHasp(P);
        lock(A, true);
        lock(B, true);
        assertThat(r.adminCut(P)).hasSize(2);
        assertThat(r.isLocked(P)).isFalse();
    }

    /** Ящик блокировки: ответственный не снимет замки точек, пока бригада держит ящик. */
    @Test
    void keysInALockedBoxHoldTheResponsiblesLocks() {
        GridPos box = new GridPos(0, 70, 0);
        lock(A, true);
        assertThat(r.depositKeys(A, box)).isEqualTo(LockResult.OK);
        assertThat(r.apply(box, true, B, "b", "", 0, 1, 8)).isEqualTo(LockResult.OK);
        assertThat(r.apply(box, true, C, "c", "", 0, 1, 8)).isEqualTo(LockResult.OK);
        assertThat(r.remove(P, A)).isEqualTo(LockResult.KEYS_IN_BOX);
        assertThat(r.withdrawKeys(A, box)).isEqualTo(LockResult.KEYS_IN_BOX);
        r.remove(box, B);
        r.remove(box, C);
        assertThat(r.withdrawKeys(A, box)).isEqualTo(LockResult.OK);
        assertThat(r.remove(P, A)).isEqualTo(LockResult.OK);
    }

    @Test
    void locksSurviveSaveAndLoad() {
        r.fitHasp(P);
        lock(A, true);
        lock(B, true);
        r.verify(P, true);
        MutableStateTree tree = new MutableStateTree();
        r.save(new TreeStateWriter(tree));
        LockoutRegistry back = new LockoutRegistry();
        back.load(new TreeStateReader(tree));
        assertThat(back.locks(P)).hasSize(2);
        assertThat(back.hasHasp(P)).isTrue();
        assertThat(back.state(P, true)).isEqualTo(IsolationState.VERIFIED);
        assertThat(back.remove(P, C)).isEqualTo(LockResult.NOT_YOURS);
        // номера замков продолжаются после загрузки
        assertThat(back.apply(new GridPos(0, 0, 0), true, C, "c", "", 0, 2, 8)).isEqualTo(LockResult.OK);
        assertThat(back.locks(new GridPos(0, 0, 0)).get(0).lockId()).isGreaterThan(back.locks(P).get(1).lockId());
    }
}
