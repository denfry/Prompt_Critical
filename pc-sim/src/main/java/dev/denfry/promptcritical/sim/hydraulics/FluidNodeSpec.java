package dev.denfry.promptcritical.sim.hydraulics;

import java.util.Objects;

/**
 * Неизменяемое описание узла при сборке сети (от биндинга pc-mod, 01 §1.13.2).
 *
 * @param id              устойчивый идентификатор ({@code SimId.raw})
 * @param volume          объём, м³ (у {@code BOUNDARY} не используется)
 * @param elevationBottom отметка низа узла, м
 * @param height          высота, м. Давление {@code HEM} и {@code BOUNDARY} относится к центру
 *                        {@code elevationBottom + height/2}, давление {@code SEPARATED} — к уровню смеси
 * @param crossSection    сечение, м² (уровень {@code SEPARATED})
 * @param sourceBlockKeys блоки мира, из которых собран узел: перенос состояния при пересборке
 */
public record FluidNodeSpec(long id, NodeKind kind, double volume, double elevationBottom,
        double height, double crossSection, long[] sourceBlockKeys) {
    public FluidNodeSpec {
        Objects.requireNonNull(kind, "kind");
        sourceBlockKeys = sourceBlockKeys == null ? new long[0] : sourceBlockKeys.clone();
    }

    public static FluidNodeSpec hem(long id, double volume, double elevationBottom, double height) {
        return new FluidNodeSpec(id, NodeKind.HEM, volume, elevationBottom, height, volume / height, null);
    }

    public static FluidNodeSpec separated(long id, double elevationBottom, double height, double crossSection) {
        return new FluidNodeSpec(id, NodeKind.SEPARATED, height * crossSection, elevationBottom, height,
                crossSection, null);
    }

    public static FluidNodeSpec boundary(long id, double elevation) {
        return new FluidNodeSpec(id, NodeKind.BOUNDARY, 0, elevation, 0, 0, null);
    }

    @Override
    public long[] sourceBlockKeys() {
        return sourceBlockKeys.clone();
    }
}
