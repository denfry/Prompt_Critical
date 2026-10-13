package dev.denfry.promptcritical.sim.core;

/**
 * Часы симуляции (05 §5.2.2). Единственный источник времени в {@code sim}:
 * обращение к часам стены запрещено ArchUnit-правилом (05 §5.1.2).
 */
public final class SimClock {
    /** Шаг FAST-домена, с (00 §0.4). */
    public static final double FAST_DT = 0.05;

    private static final int SLOW_PERIOD = TimeDomain.SLOW.periodTicks;

    /** Монотонный счётчик тиков, сохраняется вместе с миром. */
    private long tick;
    /** Физических секунд на 1 с реального времени в SLOW, ключ {@code time.k_slow} [BALANCE]. */
    private final double kSlow;
    /** Физических секунд на 1 с реального времени в STORAGE, ключ {@code storage.k_storage} [BALANCE]. */
    private final double kStorage;

    public SimClock(double kSlow, double kStorage) {
        this(0L, kSlow, kStorage);
    }

    public SimClock(long tick, double kSlow, double kStorage) {
        if (tick < 0) throw new IllegalArgumentException("tick < 0: " + tick);
        if (!(kSlow > 0)) throw new IllegalArgumentException("kSlow must be > 0: " + kSlow);
        if (!(kStorage > 0)) throw new IllegalArgumentException("kStorage must be > 0: " + kStorage);
        this.tick = tick;
        this.kSlow = kSlow;
        this.kStorage = kStorage;
    }

    public long tick() {
        return tick;
    }

    public double kSlow() {
        return kSlow;
    }

    public double kStorage() {
        return kStorage;
    }

    /** «Реальное» время FAST-физики, с. */
    public double simTime() {
        return tick * FAST_DT;
    }

    /** Накопленное физическое время SLOW, с: учитываются только завершённые SLOW-шаги. */
    public double slowPhysTime() {
        return (tick / SLOW_PERIOD) * kSlow;
    }

    /**
     * Должен ли домен {@code d} выполняться в этом тике при фазе {@code phase}.
     * Фазы разносят SLOW-задания подсистем по разным тикам (05 §5.2.2).
     */
    public boolean due(TimeDomain d, int phase) {
        if (phase < 0 || phase >= d.periodTicks) {
            throw new IllegalArgumentException("phase " + phase + " out of [0, " + d.periodTicks + ") for " + d);
        }
        return tick % d.periodTicks == phase;
    }

    /** Физическая длительность одного SLOW-шага, с. */
    public double slowDtPhys() {
        return kSlow * (SLOW_PERIOD * FAST_DT);
    }

    /** Физическая длительность одного STORAGE-шага, с. */
    public double storageDtPhys() {
        return kStorage * (TimeDomain.STORAGE.periodTicks * FAST_DT);
    }

    /** Доля интерполяции между двумя SLOW-результатами для FAST-потребителей (00 §0.4). */
    public double slowAlpha() {
        return (tick % SLOW_PERIOD) / (double) SLOW_PERIOD;
    }

    void advance() {
        tick++;
    }
}
