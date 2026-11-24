package dev.denfry.promptcritical.sim.journal;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Прореживание записей об отказах в доступе (04d §4d.8.2, {@code E_ACCESS}): одна запись WARNING на пару (актор,
 * устройство) за окно, по умолчанию 60 с = 1200 тиков. Сам отказ игроку сообщается каждый раз.
 */
public final class AccessDenialLimiter {
    public static final long DEFAULT_WINDOW_TICKS = 60L * 20L;

    private record Key(UUID actor, String device) {}

    private final long windowTicks;
    private final Map<Key, Long> lastLogged = new HashMap<>();

    public AccessDenialLimiter(long windowTicks) {
        this.windowTicks = windowTicks;
    }

    public AccessDenialLimiter() {
        this(DEFAULT_WINDOW_TICKS);
    }

    /** {@code true} — отказ надо записать в журнал; запоминает время записи. */
    public boolean shouldLog(UUID actor, String device, long tick) {
        if (lastLogged.size() > 4096) lastLogged.values().removeIf(t -> tick - t >= windowTicks);
        Key k = new Key(actor, device);
        Long last = lastLogged.get(k);
        if (last != null && tick - last < windowTicks) return false;
        lastLogged.put(k, tick);
        return true;
    }
}
