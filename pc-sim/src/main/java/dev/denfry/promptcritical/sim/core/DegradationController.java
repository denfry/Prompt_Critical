package dev.denfry.promptcritical.sim.core;

/**
 * Выбор уровня деградации по скользящему p95 времени {@code tick()} за 200 тиков (05 §5.4.6). p95 пересчитывается
 * раз в 20 тиков частичным отбором верхних значений окна, без аллокаций ({@code Arrays.sort} выделяет память на
 * упорядоченных сериях).
 */
public final class DegradationController {
    public static final int WINDOW = 200;
    private static final int EVALUATE_EVERY = 20;
    private static final DegradationLevel[] LEVELS = DegradationLevel.values();

    private final long[] window = new long[WINDOW];
    /** Верхние значения окна по убыванию: p95 из 200 — 11-е сверху. */
    private final long[] top = new long[WINDOW / 20 + 1];
    private int count;
    private int next;
    private long recorded;
    private double budgetNanos;
    private double p95Nanos;
    private DegradationLevel level = DegradationLevel.L0;

    public DegradationController(double budgetMs) {
        setBudget(budgetMs);
    }

    public void setBudget(double budgetMs) {
        if (!(budgetMs > 0)) throw new IllegalArgumentException("budget_ms must be > 0");
        budgetNanos = budgetMs * 1e6;
    }

    /** Учесть длительность тика и вернуть действующий уровень. */
    public DegradationLevel record(long tickNanos) {
        window[next] = tickNanos;
        next = (next + 1) % WINDOW;
        if (count < WINDOW) count++;
        if (++recorded % EVALUATE_EVERY == 0) evaluate();
        return level;
    }

    private void evaluate() {
        int rank = count - Math.min(count - 1, (int) Math.ceil(0.95 * count) - 1);   // 1 — наибольшее
        int kept = 0;
        for (int i = 0; i < count; i++) {
            long v = window[i];
            if (kept == rank && v <= top[kept - 1]) continue;
            int j = kept < rank ? kept++ : kept - 1;
            while (j > 0 && top[j - 1] < v) {
                top[j] = top[j - 1];
                j--;
            }
            top[j] = v;
        }
        p95Nanos = top[rank - 1];
        double load = p95Nanos / budgetNanos;
        DegradationLevel up = level;
        for (int i = level.ordinal() + 1; i < LEVELS.length; i++) {
            if (load > LEVELS[i].enterFraction) up = LEVELS[i];
        }
        if (up != level) {
            level = up;
        } else if (level != DegradationLevel.L0 && load < level.exitFraction) {
            level = LEVELS[level.ordinal() - 1];
        }
    }

    public DegradationLevel level() {
        return level;
    }

    public double p95Ms() {
        return p95Nanos / 1e6;
    }
}
