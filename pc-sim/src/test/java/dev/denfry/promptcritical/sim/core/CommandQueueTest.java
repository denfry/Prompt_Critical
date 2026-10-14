package dev.denfry.promptcritical.sim.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.SplittableRandom;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

class CommandQueueTest {
    private static final SimId VALVE = SimId.of(SimId.Kind.VALVE, 7);

    private record Item(CommandSource source, long seq) {
    }

    @Test
    @Tag("T-05-06")
    void applicationOrderFollowsSortKeyRegardlessOfArrivalOrder() {
        CommandSource lua = new CommandSource.Lua(3, UUID.nameUUIDFromBytes(new byte[] {1}));
        CommandSource panel = new CommandSource.Panel(UUID.nameUUIDFromBytes(new byte[] {2}), 1234L);
        CommandSource auto = new CommandSource.Automatic("PROTECTION");
        List<Item> items = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            items.add(new Item(lua, i));
            items.add(new Item(panel, i));
            items.add(new Item(auto, i));
        }

        List<String> first = applyInArrivalOrder(items);
        for (long seed = 1; seed <= 5; seed++) {
            List<Item> shuffled = new ArrayList<>(items);
            Collections.shuffle(shuffled, new java.util.Random(seed));
            assertThat(applyInArrivalOrder(shuffled)).isEqualTo(first);
        }
        // автоматика (0) → пульт (1) → Lua (2), внутри источника — по номеру
        assertThat(first.get(0)).isEqualTo("Automatic#0");
        assertThat(first.get(99)).isEqualTo("Automatic#99");
        assertThat(first.get(100)).isEqualTo("Panel#0");
        assertThat(first.get(299)).isEqualTo("Lua#99");
    }

    private static List<String> applyInArrivalOrder(List<Item> items) {
        CommandQueue q = new CommandQueue(512);
        for (Item it : items) assertThat(q.submit(new SimCommand.ValveOpenClose(VALVE, true), it.source, it.seq)).isPositive();
        List<String> applied = new ArrayList<>();
        q.drainSorted(512, e -> applied.add(e.source().getClass().getSimpleName() + "#" + e.sourceSeq()));
        return applied;
    }

    @Test
    void overflowRejectsWithMinusOne() {
        CommandQueue q = new CommandQueue(4);
        CommandSource src = new CommandSource.Lua(1, null);
        for (int i = 0; i < 4; i++) assertThat(q.submit(new SimCommand.ValveOpenClose(VALVE, true), src, i)).isPositive();
        assertThat(q.submit(new SimCommand.ValveOpenClose(VALVE, true), src, 4)).isEqualTo(-1);
    }

    @Test
    void perTickLimitCarriesRemainderToNextTickInOrder() {
        CommandQueue q = new CommandQueue(64);
        CommandSource src = new CommandSource.Lua(1, null);
        for (int i = 0; i < 10; i++) q.submit(new SimCommand.ValveOpenClose(VALVE, true), src, i);
        List<Long> seqs = new ArrayList<>();
        assertThat(q.drainSorted(4, e -> seqs.add(e.sourceSeq()))).isEqualTo(4);
        assertThat(q.drainSorted(4, e -> seqs.add(e.sourceSeq()))).isEqualTo(4);
        assertThat(q.drainSorted(4, e -> seqs.add(e.sourceSeq()))).isEqualTo(2);
        assertThat(seqs).containsExactly(0L, 1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L);
    }

    @Test
    void concurrentProducersLoseNothing() throws InterruptedException {
        CommandQueue q = new CommandQueue(8192);
        Thread[] producers = new Thread[4];
        for (int p = 0; p < producers.length; p++) {
            int id = p;
            producers[p] = new Thread(() -> {
                CommandSource src = new CommandSource.Lua(id, null);
                SplittableRandom rnd = new SplittableRandom(id);
                for (int i = 0; i < 1000; i++) {
                    if (rnd.nextInt(50) == 0) Thread.yield();
                    q.submit(new SimCommand.ValveOpenClose(VALVE, true), src, i);
                }
            });
            producers[p].start();
        }
        for (Thread t : producers) t.join();
        List<CommandEnvelope> got = new ArrayList<>();
        q.drainSorted(10_000, got::add);
        assertThat(got).hasSize(4000);
        for (int i = 1; i < got.size(); i++) assertThat(CommandEnvelope.compare(got.get(i - 1), got.get(i))).isNegative();
    }
}
