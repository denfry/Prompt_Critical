package dev.denfry.promptcritical.sim.core.profile;

/**
 * Профайлер симуляции (05 §5.4.7): на этап — накопитель и логарифмическая гистограмма (64 корзины от 1 мкс до
 * 100 мс), без аллокаций. Единственное место {@code pc-sim}, где разрешено время стены (ArchUnit, 05 §5.1.2).
 */
public final class SimProfiler {
    public static final int BUCKETS = 64;
    private static final double MIN_NANOS = 1_000.0;
    private static final double LOG_RANGE = Math.log(100_000_000.0 / MIN_NANOS);

    private final String[] stages;
    private final long[][] histogram;
    private final long[] totalNanos;
    private final long[] samples;

    public SimProfiler(String... stages) {
        this.stages = stages.clone();
        histogram = new long[stages.length][BUCKETS];
        totalNanos = new long[stages.length];
        samples = new long[stages.length];
    }

    /** Монотонное время, нс. Для остального {@code pc-sim} — только через этот метод. */
    public static long now() {
        return System.nanoTime();
    }

    public void record(int stage, long nanos) {
        histogram[stage][bucket(nanos)]++;
        totalNanos[stage] += nanos;
        samples[stage]++;
    }

    public int stageCount() {
        return stages.length;
    }

    public String stageName(int stage) {
        return stages[stage];
    }

    public long samples(int stage) {
        return samples[stage];
    }

    public double meanNanos(int stage) {
        return samples[stage] == 0 ? 0.0 : (double) totalNanos[stage] / samples[stage];
    }

    /** Квантиль {@code q ∈ (0, 1]} по гистограмме — верхняя граница корзины, нс. */
    public double percentileNanos(int stage, double q) {
        long n = samples[stage];
        if (n == 0) return 0.0;
        long target = (long) Math.ceil(q * n);
        long acc = 0;
        for (int b = 0; b < BUCKETS; b++) {
            acc += histogram[stage][b];
            if (acc >= target) return upperBound(b);
        }
        return upperBound(BUCKETS - 1);
    }

    public void reset() {
        for (int s = 0; s < stages.length; s++) {
            java.util.Arrays.fill(histogram[s], 0);
            totalNanos[s] = 0;
            samples[s] = 0;
        }
    }

    static int bucket(long nanos) {
        if (nanos <= MIN_NANOS) return 0;
        int b = (int) (Math.log(nanos / MIN_NANOS) / LOG_RANGE * (BUCKETS - 1)) + 1;
        return Math.min(b, BUCKETS - 1);
    }

    static double upperBound(int bucket) {
        return MIN_NANOS * Math.exp(LOG_RANGE * bucket / (BUCKETS - 1));
    }
}
