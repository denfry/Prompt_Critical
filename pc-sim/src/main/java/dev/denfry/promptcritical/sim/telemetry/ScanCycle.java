package dev.denfry.promptcritical.sim.telemetry;

/**
 * Цикл опроса контролируемого пункта телемеханики: КП опрашивает датчики раз в {@code periodTicks} и держит срез;
 * диспетчерский пункт получает последний срез и его возраст, сколько бы раз он ни спрашивал (циклическая передача
 * телеизмерений; ГОСТ Р МЭК 60870-5-101, МЭК 60870-5-104 — «циклический и спорадический» обмен). Частый опрос
 * диспетчером не нагружает датчики и сервер.
 */
public final class ScanCycle {
    private final long periodTicks;
    private long lastScan = Long.MIN_VALUE;

    public ScanCycle(long periodTicks) {
        if (periodTicks < 1) throw new IllegalArgumentException("period must be at least one tick");
        this.periodTicks = periodTicks;
    }

    public long periodTicks() {
        return periodTicks;
    }

    /** Пора делать новый срез на тике {@code tick}. */
    public boolean due(long tick) {
        return lastScan == Long.MIN_VALUE || tick - lastScan >= periodTicks || tick < lastScan;
    }

    /** Отметить срез, сделанный на тике {@code tick}. */
    public void scanned(long tick) {
        lastScan = tick;
    }

    /** Возраст последнего среза, с; {@code NaN} — срезов ещё не было. */
    public double ageSeconds(long tick) {
        return lastScan == Long.MIN_VALUE ? Double.NaN : Math.max(0, tick - lastScan) / TokenBucket.TICKS_PER_SECOND;
    }
}
