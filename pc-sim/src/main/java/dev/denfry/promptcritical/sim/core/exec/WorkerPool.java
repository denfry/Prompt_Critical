package dev.denfry.promptcritical.sim.core.exec;

import dev.denfry.promptcritical.sim.core.SlowJob;
import dev.denfry.promptcritical.sim.core.SlowResult;
import dev.denfry.promptcritical.sim.core.profile.SimProfiler;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Пул воркеров заданий SLOW/STORAGE (05 §5.4.2). Единственное место {@code pc-sim}, создающее потоки (ArchUnit).
 * {@link #direct()} — без потоков: задание выполняет серверный поток при слиянии (тесты, одноядерные серверы).
 * Результат не зависит от числа потоков и скорости воркеров: слияние всегда на тике t + период.
 */
public final class WorkerPool implements AutoCloseable {
    private final ThreadPoolExecutor executor;
    private final int threads;
    private final AtomicLong stolenPortions = new AtomicLong();

    private WorkerPool(int threads) {
        this.threads = threads;
        if (threads == 0) {
            executor = null;
        } else {
            AtomicInteger n = new AtomicInteger();
            executor = new ThreadPoolExecutor(threads, threads, 0L, TimeUnit.MILLISECONDS,
                    new ArrayBlockingQueue<>(1024), r -> {
                        Thread t = new Thread(r, "PC-Sim-Worker-" + n.incrementAndGet());
                        t.setDaemon(true);
                        return t;
                    }, new ThreadPoolExecutor.DiscardPolicy());     // отброшенное доделает серверный поток
        }
    }

    /** Пул из {@code threads} потоков; 0 — то же, что {@link #direct()}. */
    public static WorkerPool create(int threads) {
        if (threads < 0) throw new IllegalArgumentException("threads < 0");
        return new WorkerPool(threads);
    }

    public static WorkerPool direct() {
        return new WorkerPool(0);
    }

    /** {@code perf.worker_threads} по умолчанию: max(1, min(2, N_cpu − 2)) (05 §5.4.2). */
    public static int defaultThreads(int cpus) {
        return Math.max(1, Math.min(2, cpus - 2));
    }

    public int threads() {
        return threads;
    }

    /** Порций, выполненных серверным потоком из-за опоздания воркеров (счётчик {@code slow_steal}). */
    public long stolenPortions() {
        return stolenPortions.get();
    }

    /** Ячейка для заданий одной подсистемы в одном домене; переиспользуется без аллокаций. */
    public JobSlot newSlot() {
        return new JobSlot();
    }

    @Override
    public void close() {
        if (executor != null) executor.shutdownNow();
    }

    public final class JobSlot implements Runnable {
        private volatile SlowJob job = SlowJob.NONE;
        private volatile int portions;
        /** Старшие 32 бита — поколение задания, младшие — следующая незанятая порция. */
        private final AtomicLong state = new AtomicLong();
        private final AtomicInteger done = new AtomicInteger();
        private volatile Throwable failure;
        private boolean active;

        private JobSlot() {
        }

        public boolean active() {
            return active;
        }

        /** Текущее (или последнее) задание ячейки. */
        public SlowJob job() {
            return job;
        }

        /** Запустить задание. Серверный поток; предыдущее должно быть слито через {@link #join}. */
        public void start(SlowJob j) {
            if (active) throw new IllegalStateException("previous job was not joined");
            int p = j.portions();
            job = j;
            portions = p;
            done.set(0);
            failure = null;
            long gen = (state.get() >>> 32) + 1;
            state.set(gen << 32);
            active = true;
            if (executor != null && p > 0) {
                for (int k = Math.min(p, threads); k > 0; k--) executor.execute(this);
            }
        }

        /**
         * Дождаться задания: до {@code timeoutMs} — воркеров, затем выполнить оставшиеся порции самому.
         *
         * @return результат или {@code null}, если задания не было
         */
        public SlowResult join(long timeoutMs) {
            if (!active) return null;
            if (executor != null) awaitDone(timeoutMs);
            if (done.get() < portions) {
                int ran = runPortions();
                if (executor != null) stolenPortions.addAndGet(ran);
                awaitDone(-1);
            }
            active = false;
            Throwable f = failure;
            if (f != null) throw new IllegalStateException("slow job failed: " + job, f);
            return job.result();
        }

        @Override
        public void run() {
            runPortions();
        }

        private int runPortions() {
            int ran = 0;
            while (true) {
                long s = state.get();
                int idx = (int) s;
                int p = portions;
                SlowJob j = job;
                if (idx >= p) return ran;
                if (!state.compareAndSet(s, s + 1)) continue;
                try {
                    j.run(idx);
                } catch (Throwable t) {
                    failure = t;
                }
                ran++;
                if (done.incrementAndGet() == p) {
                    synchronized (this) {
                        notifyAll();
                    }
                }
            }
        }

        /** Ждать завершения всех порций; {@code timeoutMs < 0} — без ограничения. */
        private void awaitDone(long timeoutMs) {
            if (done.get() >= portions || timeoutMs == 0) return;
            long deadline = timeoutMs > 0 ? SimProfiler.now() + timeoutMs * 1_000_000L : Long.MAX_VALUE;
            synchronized (this) {
                while (done.get() < portions) {
                    long leftMs = timeoutMs > 0 ? (deadline - SimProfiler.now()) / 1_000_000L : 0L;
                    if (timeoutMs > 0 && leftMs <= 0) return;
                    try {
                        wait(timeoutMs > 0 ? leftMs : 0L);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }
    }
}
