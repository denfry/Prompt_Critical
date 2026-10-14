package dev.denfry.promptcritical.sim.core;

/** Контекст подготовки задания SLOW или STORAGE. Один объект на домен, переиспользуется. */
public final class SlowContext {
    private final SimClock clock;
    private final TimeDomain domain;
    private SimConfig config;

    SlowContext(SimClock clock, TimeDomain domain, SimConfig config) {
        this.clock = clock;
        this.domain = domain;
        this.config = config;
    }

    public SimClock clock() {
        return clock;
    }

    public TimeDomain domain() {
        return domain;
    }

    public SimConfig config() {
        return config;
    }

    /** Физическая длительность шага домена, с. */
    public double dtPhys() {
        return domain == TimeDomain.STORAGE ? clock.storageDtPhys() : clock.slowDtPhys();
    }

    void setConfig(SimConfig config) {
        this.config = config;
    }
}
