package dev.denfry.promptcritical.mod.boiler;

/**
 * Контроллер машины-источника, отдающей вращение в сеть Create через клетку {@code kinetic_port} без {@code input}
 * (03a §3a.12.3): паровая машина, электродвигатель реального масштаба. Порт ({@link KineticPortBlockEntity}) берёт у
 * него скорость и ёмкость и возвращает долю нагрузки сети.
 */
public interface ShaftSource {
    /** Обороты вала Create, RPM (0 — вал не крутится). */
    float portRpm();

    /** Ёмкость на 1 RPM. */
    float portCapacityPerRpm();

    /** Доля нагрузки сети Create, Вт (порт пишет каждый тик). */
    void setPortLoad(double watts);
}
