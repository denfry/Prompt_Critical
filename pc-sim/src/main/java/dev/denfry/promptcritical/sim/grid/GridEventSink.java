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

    /** Реле подало команду ({@code pc_breaker_trip}; у АПВ — включение, у АЧР — отключение нагрузки). */
    default void relayOperated(int relay, ProtectionFunction function) {}

    /** Замыкание на землю в сети с изолированной нейтралью появилось или ушло ({@code pc_ground_fault}, §7.5.1). */
    default void groundFault(int bus, boolean active) {}

    /** Нагрузка отключена или включена автоматикой (АЧР, ЧАПВ, 27). */
    default void loadSwitched(int load, boolean connected) {}

    /** Провод оборвался от перегрева (§7.4.1): КЗ на землю до ремонта. */
    default void conductorBroken(int branch) {}

    /** Пробой изоляции блока при U/U_ном выше испытательного (§7.4.8): внутреннее КЗ на шине. */
    default void insulationBreakdown(int bus) {}
}
