package dev.denfry.promptcritical.sim.neutronics;

import java.util.Objects;

/**
 * Конструкция решётки (01 §1.6.1, §1.13.3) — несёт предмет ТВС / блок канала (02/03a). Размеры —
 * м; доли — объёмные доли узла 1 м³. Кроме полей сигнатуры GDD хранит материал оболочек и виды
 * теплоносителя и замедлителя: без них модель ячейки не знает, что в долях объёма.
 *
 * @param rodsPerCell     топливных элементов (стержней, блочков) на 1 м² сечения узла
 * @param channelsPerCell каналов на 1 м² (для открытой решётки — 0)
 */
public record LatticeDesign(String id, FuelGeometry geometry, FuelMaterial fuelMaterial, CladMaterial cladMaterial,
        CoolantKind coolant, ModeratorKind moderator, double rodOuterRadius, double pelletRadius,
        double cladThickness, double pitch, int rodsPerAssembly, double tubeInnerRadius, double tubeThickness,
        int channelsPerCell, int rodsPerCell, double fuelFraction, double cladFraction, double coolantFraction,
        double moderatorFraction) {
    public LatticeDesign {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(geometry, "geometry");
        Objects.requireNonNull(fuelMaterial, "fuelMaterial");
        Objects.requireNonNull(cladMaterial, "cladMaterial");
        Objects.requireNonNull(coolant, "coolant");
        Objects.requireNonNull(moderator, "moderator");
        if (geometry == FuelGeometry.NONE) throw new IllegalArgumentException("geometry");
        if (!(pelletRadius > 0)) throw new IllegalArgumentException("pelletRadius");
        if (rodsPerCell < 1) throw new IllegalArgumentException("rodsPerCell");
        if (geometry != FuelGeometry.ROD_LATTICE && channelsPerCell < 1) throw new IllegalArgumentException("channels");
        double sum = fuelFraction + cladFraction + coolantFraction + moderatorFraction;
        if (!(fuelFraction > 0) || cladFraction < 0 || coolantFraction < 0 || moderatorFraction < 0 || sum > 1 + 1e-9) {
            throw new IllegalArgumentException("volume fractions");
        }
    }
}
