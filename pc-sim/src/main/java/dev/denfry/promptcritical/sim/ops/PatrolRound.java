package dev.denfry.promptcritical.sim.ops;

/**
 * Обход оборудования по маршруту (13 §13.3.1): упорядоченный список меток {@code patrol_checkpoint}. Круг начинается
 * с первой метки и засчитывается, когда метки пройдены по порядку до последней; пропущенная метка — круг не
 * засчитан, надо вернуться к ней. Маршрут должен быть пройден за {@value #PERIOD_S} с
 * ({@code ops.patrol_period_s}); иначе — «ОБХОД ПРОСРОЧЕН» (P4, журнальная).
 *
 * <p>Время — в тиках мира (20 в секунду реального времени, как и срок §13.3.1 — реальные секунды).
 */
public final class PatrolRound {
    public static final double PERIOD_S = 3600, TICKS_PER_S = 20;

    public enum Result { STARTED, NEXT, COMPLETE, OUT_OF_ORDER, UNKNOWN }

    private final int size;
    private int next;
    private long roundStart = -1, lastComplete = -1;

    public PatrolRound(int size) {
        this(size, 0, -1, -1);
    }

    public PatrolRound(int size, int next, long roundStart, long lastComplete) {
        this.size = size;
        this.next = Math.clamp(next, 0, Math.max(0, size));
        this.roundStart = roundStart;
        this.lastComplete = lastComplete;
    }

    public int size() {
        return size;
    }

    /** Индекс метки, которую ждёт маршрут. */
    public int next() {
        return next;
    }

    public long roundStart() {
        return roundStart;
    }

    public long lastComplete() {
        return lastComplete;
    }

    /** Отметка на метке {@code index} в тик {@code tick}. */
    public Result visit(int index, long tick) {
        if (index < 0 || index >= size) return Result.UNKNOWN;
        if (index == 0) {
            roundStart = tick;
            next = 1;
            if (size == 1) {
                lastComplete = tick;
                next = 0;
                return Result.COMPLETE;
            }
            return Result.STARTED;
        }
        if (roundStart < 0 || index != next) return Result.OUT_OF_ORDER;
        next++;
        if (next < size) return Result.NEXT;
        lastComplete = tick;
        next = 0;
        return Result.COMPLETE;
    }

    /** Обход просрочен: от последнего пройденного круга (или от начала, если кругов не было) прошло больше периода. */
    public boolean overdue(long tick, long since) {
        long ref = lastComplete >= 0 ? lastComplete : since;
        return tick - ref > PERIOD_S * TICKS_PER_S;
    }
}
