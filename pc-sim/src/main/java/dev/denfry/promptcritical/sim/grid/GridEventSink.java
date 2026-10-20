package dev.denfry.promptcritical.sim.grid;

/** Приёмник событий сети ({@link PowerGrid#drainEvents}). */
public interface GridEventSink {
    /** Топология пересобрана: столько островов. */
    default void topologyChanged(int islands) {}

    /** Остров без формирующего сеть источника обесточен. */
    default void islandDeenergized(int island) {}

    /** Проворот ротора относительно центра инерции острова (асинхронный ход, §7.3.6). */
    default void poleSlip(int machine) {}

    /** Выключатель перешёл в состояние автомата §7.4.7 ({@code pc_breaker_state}). */
    default void breakerStateChanged(int branch, BreakerState state) {}

    /** Машина включена на параллельную работу ({@code pc_sync_closed}, §7.3.5). */
    default void syncClosed(SyncClosing closing) {}
}
