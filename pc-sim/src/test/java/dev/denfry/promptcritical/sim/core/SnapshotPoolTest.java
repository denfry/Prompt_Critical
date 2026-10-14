package dev.denfry.promptcritical.sim.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

class SnapshotPoolTest {
    /** Упрощённый T-05-05 (полный — jcstress ночью): все поля прочитанного среза — из одного тика. */
    @Test
    @Tag("T-05-05")
    void readersNeverSeeTornSnapshots() throws InterruptedException {
        PublicState state = new PublicState();
        int slots = 512;
        for (int i = 0; i < slots; i++) state.register("ch" + i);
        SnapshotPool pool = new SnapshotPool(4);
        AtomicBoolean stop = new AtomicBoolean();
        AtomicLong torn = new AtomicLong();
        AtomicLong reads = new AtomicLong();

        Thread[] readers = new Thread[4];
        for (int r = 0; r < readers.length; r++) {
            readers[r] = new Thread(() -> {
                while (!stop.get()) {
                    boolean ok = SimSnapshot.readConsistent(pool::latest, pool::lockedCopy, s -> {
                        double t = s.tick();
                        for (int i = 0; i < slots; i++) if (s.measured(i) != t) return false;
                        return true;
                    });
                    if (!ok) torn.incrementAndGet();
                    reads.incrementAndGet();
                }
            });
            readers[r].start();
        }
        for (long tick = 1; tick <= 20_000; tick++) {
            for (int i = 0; i < slots; i++) state.set(i, tick, Quality.GOOD);
            pool.publish(tick, tick * SimClock.FAST_DT, state);
        }
        stop.set(true);
        for (Thread t : readers) t.join();
        assertThat(torn.get()).isZero();
        assertThat(reads.get()).isPositive();
    }

    @Test
    void snapshotExposesTagsAndQuality() {
        PublicState state = new PublicState();
        int p = state.register("boiler1.pressure");
        state.set(p, 1.2e6, Quality.GOOD);
        SnapshotPool pool = new SnapshotPool(4);
        pool.publish(7, 0.35, state);
        SimSnapshot s = pool.latest();
        assertThat(s.tick()).isEqualTo(7);
        assertThat(s.tags().slot("boiler1.pressure")).isEqualTo(p);
        assertThat(s.measured(p)).isEqualTo(1.2e6);
        assertThat(s.quality(p)).isEqualTo(Quality.GOOD);
        assertThat(s.validate(s.stamp())).isTrue();
    }
}
