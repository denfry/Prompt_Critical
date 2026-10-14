package dev.denfry.promptcritical.sim.hydraulics;

import java.util.Objects;

/**
 * Неизменяемое описание связи (01 §1.13.2). Геометрию свёрнутой цепочки труб даёт
 * {@link PipeChain#contract}. Отметки присоединения абсолютные, м: гравитационный напор
 * считается от центра (или уровня) узла до штуцера и вдоль связи с донорной плотностью
 * (§1.2.2), поэтому отдельный перепад высот связи не хранится.
 *
 * @param inertia     Σ L/A, 1/м (&gt; 0)
 * @param length      длина трения, м, приведённая к {@code area} и {@code hydraulicDiameter};
 *                    0 — связь без трения
 * @param formLossSum Σζ, приведённая к {@code area}; ζ арматуры добавляет {@code valve}
 * @param roughness   абсолютная шероховатость, м
 * @param pump        только для {@link LinkKind#PUMP}
 * @param valve       только для арматуры
 */
public record FlowLinkSpec(long id, LinkKind kind, long fromNode, long toNode,
        double inertia, double length, double hydraulicDiameter, double area,
        double formLossSum, double roughness,
        double junctionElevationFrom, double junctionElevationTo,
        PumpModel.Spec pump, ValveModel.Spec valve) {
    public FlowLinkSpec {
        Objects.requireNonNull(kind, "kind");
    }

    /** Связь по геометрии свёрнутой цепочки; конец {@code to} — выше на {@link PipeChain.Geometry#rise()}. */
    public static FlowLinkSpec of(long id, LinkKind kind, long fromNode, long toNode, PipeChain.Geometry g,
            double junctionElevationFrom, PumpModel.Spec pump, ValveModel.Spec valve) {
        return new FlowLinkSpec(id, kind, fromNode, toNode, g.inertia(), g.frictionLength(), g.hydraulicDiameter(),
                g.area(), g.formLossSum(), g.roughness(), junctionElevationFrom, junctionElevationFrom + g.rise(),
                pump, valve);
    }
}
