package dev.denfry.promptcritical.sim.automation;

/** Автомат состояний реле A1 (03d §3d.3.3). Отказы ({@link #isFailed()}) снимаются только заменой реле. */
public enum RelayState {
    DROPPED, PICKING, PICKED, RELEASING,
    /** Обрыв катушки: тока нет, контакты отпущены (безопасный отказ). */
    COIL_OPEN,
    /** Сварка НО-контакта одной группы: она остаётся замкнутой (опасный, у {@code GENERAL} скрытый). */
    WELDED,
    STUCK_PICKED, STUCK_DROPPED;

    public boolean isFailed() {
        return ordinal() >= COIL_OPEN.ordinal();
    }

    /** Якорь притянут: контакты в положении «сработало». */
    boolean armaturePicked() {
        return this == PICKED || this == RELEASING || this == STUCK_PICKED;
    }
}
