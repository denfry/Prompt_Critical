package dev.denfry.promptcritical.sim.automation;

/** Исполнение реле времени (03d §3d.3.4): разброс выдержки δ_t, диапазон уставки, мощность катушки. */
public enum TimeRelayType {
    /** Пневматическая приставка (ПВЛ): ±10 %. */
    PNEUMATIC(0.10, 0.4, 180, 1),
    /** Синхронный моторчик: ±1 %. */
    MOTOR(0.01, 10, 3 * 3600, 3),
    /** Электронное: ±0,1 %. */
    ELECTRONIC(0.001, 0.1, 3 * 3600, 1);

    public final double spread;
    public final double minS, maxS;
    public final double coilW;

    TimeRelayType(double spread, double minS, double maxS, double coilW) {
        this.spread = spread;
        this.minS = minS;
        this.maxS = maxS;
        this.coilW = coilW;
    }
}
