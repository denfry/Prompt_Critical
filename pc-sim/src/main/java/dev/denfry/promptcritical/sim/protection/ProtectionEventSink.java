package dev.denfry.promptcritical.sim.protection;

import java.util.Set;

/**
 * Приёмник событий защиты: {@code ReactorTripEvent}, {@code ResetDeniedEvent} (01 §1.13.3). Идентификатор
 * реактора добавляет владелец приёмника ({@code SimulationWorld}).
 */
public interface ProtectionEventSink {
    ProtectionEventSink NONE = new ProtectionEventSink() {};

    /** АЗ: питание приводов разомкнуто. Вызывается для каждой причины, сработавшей в этом тике. */
    default void reactorTrip(TripReason reason, TripSource source, boolean firstOut, long tick) {}

    /** Сброс отклонён; {@code reasons} — причины, которые ещё держат АЗ (пусто, если мешает выдержка или стержни). */
    default void resetDenied(Set<TripReason> reasons) {}
}
