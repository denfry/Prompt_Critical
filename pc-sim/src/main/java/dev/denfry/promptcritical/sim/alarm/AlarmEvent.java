package dev.denfry.promptcritical.sim.alarm;

/**
 * Событие движка сигнализации — то, что периферия {@code pc_annunciator} отдаёт Lua событиями {@code pc_alarm},
 * {@code pc_alarm_clear}, {@code pc_alarm_ack}, {@code pc_alarm_flood} (03b §3b.2.14, таблица событий).
 *
 * @param id    номер сигнализации; у событий лавины — 0
 * @param value измеренное значение в момент события (у лавины — возникновений за 10 мин)
 */
public record AlarmEvent(Type type, int id, AlarmState state, double tS, double value) {
    public enum Type {
        /** NORMAL/RTNUN → UNACK, повторная сигнализация P1, конец шельфинга или подавления при активном условии. */
        RAISE,
        /** Условие ушло: RTNUN или NORMAL. */
        CLEAR,
        /** Квитирование. */
        ACK,
        /** Вход в лавину (> 10 за 10 мин). */
        FLOOD_ON,
        /** Выход из лавины (< 5 за 10 мин). */
        FLOOD_OFF,
        /** Сигнализация признана дребезжащей (≥ 3 срабатываний за 60 с). */
        CHATTER
    }
}
