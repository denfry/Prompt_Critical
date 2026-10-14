package dev.denfry.promptcritical.sim.hydraulics;

/**
 * Приёмник событий гидросети. Примитивные аргументы — без аллокаций в горячем цикле;
 * в {@code EventBuffer} (05 §5.2.6) их переложит {@code SimulationWorld}.
 */
public interface HydraulicEventSink {
    HydraulicEventSink NONE = new HydraulicEventSink() {};

    /** Гидроудар по Жуковскому ΔP_J = ρ c Δv (01 §1.2.9, событие {@code HydraulicTransientEvent}). */
    default void waterHammer(long linkId, double deltaPressure, double riseTime) {}

    /** Предохранительный клапан поднялся или сел. */
    default void reliefLift(long linkId, boolean open) {}

    /** Компонента не уложилась в минимальный шаг и принята с ограничением (SIM_WARNING, §1.2.9). */
    default void simWarning(long nodeId) {}
}
