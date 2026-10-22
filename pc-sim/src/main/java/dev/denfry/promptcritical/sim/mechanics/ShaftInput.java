package dev.denfry.promptcritical.sim.mechanics;

/**
 * Вход вала Create у машины-потребителя без собственной инерции (станки, отсадка, формовка, воздуходувка; 03a §3a.1,
 * доменная сторона {@code KineticPort}). Биндинг пишет скорость вала каждый тик, двойник читает её в своём шаге.
 * Отсчёт старше {@link #STALE_TICKS} (BE выгружен) или сеть перегружена — вал стоит. Не сохраняется: после загрузки
 * биндинг пишет его заново.
 *
 * <p>Нагрузку двойник объявляет мощностью; перевод в стресс Create (импульс на об/мин через {@code energy.w_per_su})
 * и прореживание R4 — дело адаптера.
 */
public final class ShaftInput {
    /** Сколько тиков отсчёт считается свежим. */
    public static final long STALE_TICKS = 2;

    private double rpm;
    private boolean healthy;
    private long sampledTick = Long.MIN_VALUE / 2;

    /** Записать скорость вала, об/мин со знаком, и исправность сети (нет overstress). */
    public void sample(double rpm, boolean healthy, long tick) {
        this.rpm = rpm;
        this.healthy = healthy;
        this.sampledTick = tick;
    }

    /** Скорость вала со знаком направления Create, об/мин (толчок 13 §13.6.6); 0 — как у {@link #rpm}. */
    public double signedRpm(long tick) {
        return healthy && tick - sampledTick <= STALE_TICKS ? rpm : 0;
    }

    /** Модуль скорости вала на тике {@code tick}, об/мин; 0 — вала нет, сеть перегружена или отсчёт устарел. */
    public double rpm(long tick) {
        return healthy && tick - sampledTick <= STALE_TICKS ? Math.abs(rpm) : 0;
    }
}
