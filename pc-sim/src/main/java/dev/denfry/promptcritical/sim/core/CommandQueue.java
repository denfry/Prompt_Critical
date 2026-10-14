package dev.denfry.promptcritical.sim.core;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.function.Consumer;

/**
 * Ограниченная очередь команд на массиве (05 §5.4.3): производители — потоки CC, сетевой поток и сам серверный
 * поток; потребитель — серверный поток. Без блокировок (схема Вьюкова: номер последовательности на ячейку) и без
 * аллокаций у потребителя. При переполнении {@link #submit} возвращает −1, Lua получает {@code E_RATE}.
 */
public final class CommandQueue {
    private final int mask;
    private final CommandEnvelope[] ring;
    private final AtomicLongArray sequence;
    private final AtomicLong head = new AtomicLong();
    private final AtomicLong tail = new AtomicLong();
    private final AtomicLong tickets = new AtomicLong();

    /** Извлечённые, но ещё не применённые (лимит за тик) команды; сортируются вместе с новыми. */
    private final CommandEnvelope[] staged;
    private int stagedCount;
    private volatile long currentTick;

    public CommandQueue(int capacity) {
        if (capacity < 2 || Integer.bitCount(capacity) != 1)
            throw new IllegalArgumentException("capacity must be a power of two: " + capacity);
        mask = capacity - 1;
        ring = new CommandEnvelope[capacity];
        sequence = new AtomicLongArray(capacity);
        for (int i = 0; i < capacity; i++) sequence.set(i, i);
        staged = new CommandEnvelope[capacity * 2];
    }

    /**
     * Поставить команду; потокобезопасно.
     *
     * @return билет (> 0) или −1, если очередь полна
     */
    public long submit(SimCommand command, CommandSource source, long sourceSeq) {
        long ticket = tickets.incrementAndGet();
        CommandEnvelope env = new CommandEnvelope(command, source, ticket, currentTick, source.sortClass(), sourceSeq);
        return offer(env) ? ticket : -1;
    }

    private boolean offer(CommandEnvelope env) {
        while (true) {
            long pos = tail.get();
            int idx = (int) (pos & mask);
            long seq = sequence.getAcquire(idx);
            long dif = seq - pos;
            if (dif == 0) {
                if (tail.compareAndSet(pos, pos + 1)) {
                    ring[idx] = env;
                    sequence.setRelease(idx, pos + 1);
                    return true;
                }
            } else if (dif < 0) {
                return false;
            }
            Thread.onSpinWait();
        }
    }

    private CommandEnvelope poll() {
        long pos = head.get();
        int idx = (int) (pos & mask);
        if (sequence.getAcquire(idx) != pos + 1) return null;
        CommandEnvelope env = ring[idx];
        ring[idx] = null;
        head.set(pos + 1);
        sequence.setRelease(idx, pos + mask + 1);
        return env;
    }

    /**
     * Извлечь всё поступившее к началу тика, отсортировать по ключу 05 §5.4.3 и отдать не более {@code max}
     * команд; остаток ждёт следующего тика. Только серверный поток.
     *
     * @return число отданных команд
     */
    public int drainSorted(int max, Consumer<CommandEnvelope> consumer) {
        CommandEnvelope env;
        while (stagedCount < staged.length && (env = poll()) != null) staged[stagedCount++] = env;
        if (stagedCount == 0) return 0;
        heapSort(staged, stagedCount);
        int n = Math.min(max, stagedCount);
        for (int i = 0; i < n; i++) consumer.accept(staged[i]);
        int rest = stagedCount - n;
        System.arraycopy(staged, n, staged, 0, rest);
        for (int i = rest; i < stagedCount; i++) staged[i] = null;
        stagedCount = rest;
        return n;
    }

    /** Команд в очереди и в остатке прошлого тика (оценка; точна на серверном потоке между тиками). */
    public int size() {
        return (int) (tail.get() - head.get()) + stagedCount;
    }

    void setCurrentTick(long tick) {
        currentTick = tick;
    }

    private static void heapSort(CommandEnvelope[] a, int n) {
        for (int i = n / 2 - 1; i >= 0; i--) siftDown(a, i, n);
        for (int end = n - 1; end > 0; end--) {
            CommandEnvelope t = a[0];
            a[0] = a[end];
            a[end] = t;
            siftDown(a, 0, end);
        }
    }

    private static void siftDown(CommandEnvelope[] a, int i, int n) {
        while (true) {
            int l = 2 * i + 1;
            if (l >= n) return;
            int r = l + 1;
            int big = r < n && CommandEnvelope.compare(a[r], a[l]) > 0 ? r : l;
            if (CommandEnvelope.compare(a[big], a[i]) <= 0) return;
            CommandEnvelope t = a[i];
            a[i] = a[big];
            a[big] = t;
            i = big;
        }
    }
}
