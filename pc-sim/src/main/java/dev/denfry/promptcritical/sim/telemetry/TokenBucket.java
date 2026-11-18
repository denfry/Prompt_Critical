package dev.denfry.promptcritical.sim.telemetry;

/**
 * Ограничитель частоты запросов «корзина токенов»: запас {@code burst} токенов пополняется со скоростью
 * {@code ratePerSecond} в секунду игрового времени (20 тиков), каждый запрос забирает токены. Время — тики мира, не
 * настенные часы: ограничитель детерминирован и не зависит от загрузки сервера.
 */
public final class TokenBucket {
    public static final double TICKS_PER_SECOND = 20;

    private final double ratePerSecond, burst;
    private double tokens;
    private long lastTick = Long.MIN_VALUE;

    public TokenBucket(double ratePerSecond, double burst) {
        if (!(ratePerSecond > 0 && burst >= 1)) throw new IllegalArgumentException("rate > 0 and burst >= 1 required");
        this.ratePerSecond = ratePerSecond;
        this.burst = burst;
        this.tokens = burst;
    }

    private void refill(long tick) {
        if (lastTick != Long.MIN_VALUE && tick > lastTick)
            tokens = Math.min(burst, tokens + (tick - lastTick) / TICKS_PER_SECOND * ratePerSecond);
        if (lastTick == Long.MIN_VALUE || tick > lastTick) lastTick = tick;
    }

    /** Забрать {@code cost} токенов на тике {@code tick}; {@code false} — запас исчерпан, запрос отклонён. */
    public boolean tryTake(long tick, double cost) {
        refill(tick);
        if (tokens < cost) return false;
        tokens -= cost;
        return true;
    }

    /** Запас на тике {@code tick}. */
    public double available(long tick) {
        refill(tick);
        return tokens;
    }
}
