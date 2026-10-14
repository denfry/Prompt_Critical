package dev.denfry.promptcritical.sim.core;

import java.lang.invoke.VarHandle;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Кольцо буферов {@link SimSnapshot} с публикацией по схеме seqlock (05 §5.2.7). Писатель — серверный поток
 * (этап 12 FAST-тика), читатели — любые потоки.
 */
public final class SnapshotPool {
    private final Buffer[] ring;
    private volatile Buffer latest;
    private final AtomicLong lockedCopies = new AtomicLong();

    public SnapshotPool(int buffers) {
        if (buffers < 3) throw new IllegalArgumentException("buffers < 3");
        ring = new Buffer[buffers];
        for (int i = 0; i < buffers; i++) ring[i] = new Buffer();
        latest = ring[0];
    }

    /** Скопировать публичное состояние в следующий буфер кольца и опубликовать его. */
    public synchronized void publish(long tick, double simTime, PublicState state) {
        Buffer b = ring[(int) Long.remainderUnsigned(tick, ring.length)];
        b.generation++;                         // нечётное — «пишется»
        VarHandle.releaseFence();
        b.copyFrom(tick, simTime, state);
        VarHandle.releaseFence();
        b.generation++;                         // чётное — «готов»
        latest = b;
    }

    public SimSnapshot latest() {
        return latest;
    }

    /** Крайний случай {@link SimSnapshot#readConsistent}: частная копия последнего среза. */
    public synchronized SimSnapshot lockedCopy() {
        lockedCopies.incrementAndGet();
        Buffer copy = new Buffer();
        Buffer src = latest;
        copy.tick = src.tick;
        copy.simTime = src.simTime;
        copy.tags = src.tags;
        copy.size = src.size;
        copy.value = src.value.clone();
        copy.quality = src.quality.clone();
        return copy;
    }

    /** Сколько раз читателям понадобилась копия под замком (счётчик профайлера). */
    public long lockedCopyCount() {
        return lockedCopies.get();
    }

    private static final class Buffer implements SimSnapshot {
        private static final Quality[] QUALITIES = Quality.values();

        volatile long generation;
        long tick;
        double simTime;
        TagIndex tags = TagIndex.EMPTY;
        int size;
        double[] value = new double[0];
        byte[] quality = new byte[0];

        void copyFrom(long t, double time, PublicState s) {
            int n = s.size();
            if (value.length < n) {                       // только после изменения топологии
                value = new double[s.values().length];
                quality = new byte[s.values().length];
            }
            System.arraycopy(s.values(), 0, value, 0, n);
            System.arraycopy(s.qualities(), 0, quality, 0, n);
            size = n;
            tags = s.tags();
            tick = t;
            simTime = time;
        }

        @Override
        public long tick() {
            return tick;
        }

        @Override
        public double simTime() {
            return simTime;
        }

        @Override
        public long stamp() {
            return generation;
        }

        @Override
        public boolean validate(long stamp) {
            VarHandle.acquireFence();
            return (stamp & 1) == 0 && generation == stamp;
        }

        @Override
        public TagIndex tags() {
            return tags;
        }

        @Override
        public double measured(int slot) {
            return slot < size ? value[slot] : Double.NaN;
        }

        @Override
        public Quality quality(int slot) {
            return slot < size ? QUALITIES[quality[slot]] : Quality.BAD;
        }
    }
}
