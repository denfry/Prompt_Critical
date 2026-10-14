package dev.denfry.promptcritical.sim.mining;

/** Назначение ветви — для верхнего предела скорости воздуха по ПБ (08 §8.2.7). */
public enum BranchKind {
    /** ствол: ≤ 12 м/с */
    SHAFT(12),
    /** штрек, квершлаг, уклон: ≤ 8 м/с */
    DRIFT(8),
    /** очистной забой (лава): ≤ {@code mining.air_velocity_face_max_ms} */
    FACE(4);

    /** Предел скорости по умолчанию, м/с. */
    public final double maxVelocityMs;

    BranchKind(double maxVelocityMs) {
        this.maxVelocityMs = maxVelocityMs;
    }
}
