package dev.denfry.promptcritical.sim.core;

/** Потребитель событий тика: CC, сеть, журнал (05 §5.4.2 п. 8–9). */
@FunctionalInterface
public interface SimEventSink {
    void accept(SimEvent event);
}
