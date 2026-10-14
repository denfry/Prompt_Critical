package dev.denfry.promptcritical.sim.grid;

/** Приёмник событий сети ({@link PowerGrid#drainEvents}). */
public interface GridEventSink {
    /** Топология пересобрана: столько островов. */
    default void topologyChanged(int islands) {}

    /** Остров без формирующего сеть источника обесточен. */
    default void islandDeenergized(int island) {}

    /** Проворот ротора относительно центра инерции острова (асинхронный ход, §7.3.6). */
    default void poleSlip(int machine) {}
}
