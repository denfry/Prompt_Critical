package dev.denfry.promptcritical.sim.grid;

/**
 * Ответ выключателя на команду {@link PowerGrid#operateBreaker} (07 §7.4.7).
 * {@code INTERLOCK} — запрет реле контроля синхронизма (в CC — {@code E_INTERLOCK}, §7.17.2).
 */
public enum BreakerResponse { ACCEPTED, INTERLOCK, NO_CONTROL_POWER, BUSY, UNAVAILABLE,
    /** На аппарат выписан открытый наряд-допуск ({@link WorkPermit}): включение запрещено. */
    PERMIT,
    /** На приводе замок LOTO (13 §13.1.3): в CC — {@code E_INTERLOCK: LOCKED_OUT}. */
    LOCKED_OUT }
