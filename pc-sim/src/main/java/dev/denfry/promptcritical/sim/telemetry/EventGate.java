package dev.denfry.promptcritical.sim.telemetry;

import java.util.Map;
import java.util.TreeMap;

/**
 * Защита очереди событий компьютера от флуда (03b §3b.3.2): не более {@link #RATE} событий в секунду от одного
 * устройства одному компьютеру (корзина ёмкостью {@link #BURST}); лишние отбрасываются и считаются по типам, а не
 * чаще раза в секунду программа получает сводку {@code pc_event_overflow} — знак перечитать состояние. Время — тики.
 */
public final class EventGate {
    /** {@code cc.events_per_device_per_s = 20} [BALANCE], ёмкость 40 (03b §3b.3.2). */
    public static final double RATE = 20, BURST = 40;
    /** Сводка о потерях — не чаще раза в секунду. */
    public static final long OVERFLOW_PERIOD_TICKS = 20;

    private final TokenBucket bucket = new TokenBucket(RATE, BURST);
    private final Map<String, Integer> dropped = new TreeMap<>();
    private long lastOverflow = Long.MIN_VALUE;

    /** Пропустить событие {@code name} на тике {@code tick}; {@code false} — отброшено и учтено. */
    public boolean admit(String name, long tick) {
        if (bucket.tryTake(tick, 1)) return true;
        dropped.merge(name, 1, Integer::sum);
        return false;
    }

    /**
     * Сводка потерь, если пора её отправить: таблица «событие → число» и сброс счётчиков; иначе {@code null}.
     * Сводка сама токена не тратит — без неё программа не узнает о потере.
     */
    public Map<String, Integer> overflow(long tick) {
        if (dropped.isEmpty()) return null;
        if (lastOverflow != Long.MIN_VALUE && tick - lastOverflow < OVERFLOW_PERIOD_TICKS && tick >= lastOverflow) return null;
        lastOverflow = tick;
        Map<String, Integer> out = new TreeMap<>(dropped);
        dropped.clear();
        return out;
    }
}
