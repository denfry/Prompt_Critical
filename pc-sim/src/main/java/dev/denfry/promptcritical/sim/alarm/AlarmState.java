package dev.denfry.promptcritical.sim.alarm;

/**
 * Состояние сигнализации по ISA-18.2 (03c §3c.6.2). {@link #SHELVED}, {@link #SUPPRESSED} и {@link #OOS} — скрытые:
 * условие отслеживается, но оператору сигнализация не показывается и не звучит.
 */
public enum AlarmState {
    /** Условия нет. */
    NORMAL,
    /** Условие есть, не квитировано: окно мигает, звучит сирена. */
    UNACK,
    /** Условие есть, квитировано: ровный свет. */
    ACKED,
    /** Условие ушло, но сигнализацию не квитировали (return to normal unacknowledged). */
    RTNUN,
    /** Временно скрыта оператором (шельфинг, 03c §3c.6.4). */
    SHELVED,
    /** Подавлена по состоянию установки (группа, 03c §3c.6.4). */
    SUPPRESSED,
    /** Выведена из работы (ремонт датчика). */
    OOS;

    /** Скрытое состояние: условие отслеживается без показа. */
    public boolean hidden() {
        return this == SHELVED || this == SUPPRESSED || this == OOS;
    }

    /** Показывается оператору (горит или мигает окно). */
    public boolean annunciated() {
        return this == UNACK || this == ACKED || this == RTNUN;
    }
}
