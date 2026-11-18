package dev.denfry.promptcritical.sim.ops;

import static org.assertj.core.api.Assertions.assertThat;

import dev.denfry.promptcritical.sim.core.MutableStateTree;
import dev.denfry.promptcritical.sim.core.TreeStateReader;
import dev.denfry.promptcritical.sim.core.TreeStateWriter;
import dev.denfry.promptcritical.sim.ops.Turnaround.Inputs;
import dev.denfry.promptcritical.sim.ops.Turnaround.Missing;
import org.junit.jupiter.api.Test;

/** Остановочный ремонт 13 §13.6.2: фазы от останова до пуска, предпусковая проверка PSSR. */
class TurnaroundTest {
    private final Turnaround t = new Turnaround();

    private static Inputs in(int running, int open, int completed, int locks, int bypassed, int overdue, boolean gas) {
        return new Inputs(running, open, completed, locks, bypassed, overdue, gas);
    }

    @Test
    void aFullTurnaroundFromShutdownToStartup() {
        assertThat(t.advance(in(2, 0, 0, 0, 0, 0, false), 10)).isEmpty();
        assertThat(t.phase()).isEqualTo(TurnaroundPhase.SHUTDOWN);
        assertThat(t.startTick()).isEqualTo(10);
        assertThat(t.advance(in(1, 0, 0, 0, 0, 0, false), 20)).containsExactly(Missing.MACHINES_RUNNING);
        assertThat(t.advance(in(0, 0, 0, 0, 0, 0, false), 30)).isEmpty();
        assertThat(t.advance(in(0, 0, 0, 0, 0, 0, false), 40)).containsExactly(Missing.GAS_TEST);
        assertThat(t.advance(in(0, 0, 0, 0, 0, 0, true), 50)).isEmpty();
        assertThat(t.phase()).isEqualTo(TurnaroundPhase.OPEN);
        assertThat(t.advance(in(0, 2, 0, 3, 0, 0, true), 60)).containsExactly(Missing.NO_REPAIRS);
        assertThat(t.advance(in(0, 1, 2, 3, 0, 0, true), 70)).isEmpty();
        assertThat(t.repairs()).isEqualTo(2);
        assertThat(t.advance(in(0, 1, 2, 0, 0, 0, true), 80)).containsExactly(Missing.PERMITS_OPEN);
        assertThat(t.advance(in(0, 0, 2, 1, 0, 0, true), 90)).isEmpty();
        assertThat(t.phase()).isEqualTo(TurnaroundPhase.PSSR);
        assertThat(t.advance(in(0, 0, 2, 1, 1, 1, true), 100))
                .containsExactlyInAnyOrder(Missing.LOCKS_HUNG, Missing.SIS_BYPASSED, Missing.SIS_OVERDUE);
        assertThat(t.advance(in(0, 0, 2, 0, 0, 0, true), 110)).isEmpty();
        assertThat(t.advance(in(0, 0, 2, 0, 0, 0, true), 120)).containsExactly(Missing.NOT_RUNNING);
        assertThat(t.advance(in(1, 0, 2, 0, 0, 0, true), 130)).isEmpty();
        assertThat(t.complete()).isTrue();
        assertThat(t.completeTick()).isEqualTo(130);
        assertThat(t.advance(in(1, 0, 2, 0, 0, 0, true), 140)).containsExactly(Missing.DONE);

        MutableStateTree tree = new MutableStateTree();
        t.save(new TreeStateWriter(tree));
        Turnaround b = new Turnaround();
        b.load(new TreeStateReader(tree));
        assertThat(b.phase()).isEqualTo(TurnaroundPhase.COMPLETE);
        assertThat(b.repairs()).isEqualTo(2);
        assertThat(b.startTick()).isEqualTo(10);
        b.reset();
        assertThat(b.phase()).isEqualTo(TurnaroundPhase.PLANNING);
    }
}
