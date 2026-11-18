package dev.denfry.promptcritical.sim.ops;

/** Состояние наряда-допуска (13 §13.2.4, §13.12.1). {@code EXPIRED}, {@code CLOSED} и {@code CANCELLED} — конечные. */
public enum PermitState {
    DRAFT, ISSUED, ISOLATED, GAS_TESTED, ACTIVE, SUSPENDED, WORK_COMPLETE, CLOSED, EXPIRED, CANCELLED;

    public boolean terminal() {
        return this == CLOSED || this == EXPIRED || this == CANCELLED;
    }

    /** Наряд ещё держит объём работ: не закрыт, не просрочен, не отменён. */
    public boolean open() {
        return !terminal();
    }
}
