package dev.denfry.promptcritical.sim.telemetry;

/**
 * Подписка {@code watch(tag, lo, hi, deadband, onDelay, offDelay)} (03b §3b.1.9, событие {@code pc_setpoint_crossed}
 * §3b.3.3): величина выходит за {@code hi} — состояние {@code HIGH}, за {@code lo} — {@code LOW}; назад в
 * {@code NORMAL} — только когда она вернулась внутрь на зону нечувствительности {@code deadband} (гистерезис).
 * Новое состояние сообщается, лишь если продержалось задержку включения ({@code onDelay}, для HIGH/LOW) или снятия
 * ({@code offDelay}, для NORMAL) — дебаунс у источника, как в ISA-18.2 (§3b.3.2). Время — тики мира.
 */
public final class SetpointWatch {
    public enum Direction { NORMAL, HIGH, LOW }

    private final double lo, hi, deadband;
    private final long onDelayTicks, offDelayTicks;
    private Direction reported = Direction.NORMAL, candidate = Direction.NORMAL;
    private long candidateSince;

    /**
     * @param lo       нижняя граница (может быть {@code -inf}: только верхняя)
     * @param hi       верхняя граница (может быть {@code +inf})
     * @param deadband зона нечувствительности в единицах величины, ≥ 0
     * @param onDelayS задержка включения, с, ≥ 0
     * @param offDelayS задержка снятия, с, ≥ 0
     */
    public SetpointWatch(double lo, double hi, double deadband, double onDelayS, double offDelayS) {
        if (Double.isNaN(lo) || Double.isNaN(hi) || !(lo < hi)) throw new IllegalArgumentException("lo < hi required");
        if (!(deadband >= 0) || !Double.isFinite(deadband)) throw new IllegalArgumentException("deadband >= 0 required");
        if (!(onDelayS >= 0 && onDelayS <= 3600) || !(offDelayS >= 0 && offDelayS <= 3600))
            throw new IllegalArgumentException("delays in [0, 3600] s required");
        this.lo = lo;
        this.hi = hi;
        this.deadband = deadband;
        this.onDelayTicks = Math.round(onDelayS * TokenBucket.TICKS_PER_SECOND);
        this.offDelayTicks = Math.round(offDelayS * TokenBucket.TICKS_PER_SECOND);
    }

    public double lo() {
        return lo;
    }

    public double hi() {
        return hi;
    }

    public Direction state() {
        return reported;
    }

    /** Порог, пересечение которого привело в состояние {@code d}. */
    public double threshold(Direction d) {
        return switch (d) {
            case HIGH -> hi;
            case LOW -> lo;
            case NORMAL -> reported == Direction.LOW ? lo : hi;
        };
    }

    private Direction raw(double v) {
        if (v > hi) return Direction.HIGH;
        if (v < lo) return Direction.LOW;
        // внутри границ: из HIGH/LOW выходим только за зону нечувствительности
        if (reported == Direction.HIGH && v > hi - deadband) return Direction.HIGH;
        if (reported == Direction.LOW && v < lo + deadband) return Direction.LOW;
        return Direction.NORMAL;
    }

    /**
     * Новое значение на тике {@code tick}; возвращает новое сообщаемое состояние или {@code null}, если оно не
     * изменилось. NaN (нет сигнала) не меняет состояния.
     */
    public Direction update(double v, long tick) {
        if (Double.isNaN(v)) return null;
        Direction r = raw(v);
        if (r != candidate) {
            candidate = r;
            candidateSince = tick;
        }
        if (candidate == reported) return null;
        long delay = candidate == Direction.NORMAL ? offDelayTicks : onDelayTicks;
        if (tick - candidateSince < delay) return null;
        reported = candidate;
        return reported;
    }
}
