package dev.denfry.promptcritical.sim.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

class EventBufferTest {
    private static final SimId SRC = SimId.of(SimId.Kind.DEVICE, 1);

    @Test
    @Tag("T-05-07")
    void avalancheOf3000FitsAnd5000ReportsOverflow() {
        EventBuffer buf = new EventBuffer(4096);
        for (int i = 0; i < 3000; i++) buf.commandResult(10, SRC, i, CommandStatus.OK);
        List<SimEvent> out = new ArrayList<>();
        buf.drain(out::add);
        assertThat(out).hasSize(3000).allMatch(e -> e instanceof SimEvent.CommandResult);
        assertThat(buf.overflowTotal()).isZero();

        out.clear();
        for (int i = 0; i < 5000; i++) buf.commandResult(11, SRC, i, CommandStatus.OK);
        buf.drain(out::add);
        assertThat(out).hasSize(4097);
        assertThat(out.getLast()).isInstanceOfSatisfying(SimEvent.Journal.class, j -> {
            assertThat(j.severity()).isEqualTo(Severity.WARNING);
            assertThat(j.message()).contains("904");
        });
        assertThat(buf.overflowTotal()).isEqualTo(904);
    }

    @Test
    void eventsRoundTripThroughPreallocatedSlots() {
        EventBuffer buf = new EventBuffer(16);
        buf.commandResult(5, SRC, 42, CommandStatus.NO_TARGET);
        buf.journal(5, SRC, Severity.CRITICAL, "boiler explosion");
        buf.damageRequested(5, SRC, 9, 100, 3);
        List<SimEvent> out = new ArrayList<>();
        buf.drain(out::add);
        assertThat(out).containsExactly(
                new SimEvent.CommandResult(5, SRC, 42, CommandStatus.NO_TARGET),
                new SimEvent.Journal(5, SRC, Severity.CRITICAL, "boiler explosion"),
                new SimEvent.DamageRequested(5, SRC, 9, 100, 3));
        assertThat(buf.size()).isZero();
    }
}
