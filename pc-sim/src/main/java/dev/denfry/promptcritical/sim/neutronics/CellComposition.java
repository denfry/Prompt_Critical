package dev.denfry.promptcritical.sim.neutronics;

import java.util.Objects;

/**
 * Состав нейтронного узла 1 м³ (01 §1.6.1). Топливная ячейка берёт доли объёма из
 * {@link LatticeDesign}; узел без топлива (отражатель, канал СУЗ, вода, стеллаж) задаёт их сам.
 * Поглотитель и конструкционный материал добавляются поверх — доля объёма узла, занятая ими
 * сейчас (для стержня — доля вставки × доля сечения, её ведёт СУЗ, §1.10).
 *
 * <p>Отличие от сигнатуры GDD: вместо {@code rodSegmentFractions} — одна текущая доля
 * поглотителя; сегменты вытеснителя T1 (§1.10.4) появятся вместе с {@code RodBank}.
 */
public record CellComposition(LatticeDesign lattice, CoolantKind coolant, double coolantFraction,
        ModeratorKind moderator, double moderatorFraction, double moderatorImpurityPpmB, CladMaterial structure,
        double structureFraction, AbsorberMaterial absorber, double absorberFraction) {
    public CellComposition {
        Objects.requireNonNull(coolant, "coolant");
        Objects.requireNonNull(moderator, "moderator");
        Objects.requireNonNull(structure, "structure");
        Objects.requireNonNull(absorber, "absorber");
        if (coolantFraction < 0 || moderatorFraction < 0 || structureFraction < 0 || absorberFraction < 0
                || moderatorImpurityPpmB < 0) {
            throw new IllegalArgumentException("negative fraction");
        }
    }

    /** Топливная ячейка решётки {@code d}; примесь замедлителя — ppm бор-эквивалента. */
    public static CellComposition fuel(LatticeDesign d, double moderatorImpurityPpmB) {
        return new CellComposition(d, d.coolant(), d.coolantFraction(), d.moderator(), d.moderatorFraction(),
                moderatorImpurityPpmB, CladMaterial.STEEL, 0, AbsorberMaterial.NONE, 0);
    }

    /** Узел без топлива: отражатель, водяная камера, канал СУЗ без стержня. */
    public static CellComposition nonFuel(ModeratorKind moderator, double moderatorFraction, CoolantKind coolant,
            double coolantFraction, double moderatorImpurityPpmB) {
        return new CellComposition(null, coolant, coolantFraction, moderator, moderatorFraction,
                moderatorImpurityPpmB, CladMaterial.STEEL, 0, AbsorberMaterial.NONE, 0);
    }

    public CellComposition withAbsorber(AbsorberMaterial material, double fraction) {
        return new CellComposition(lattice, coolant, coolantFraction, moderator, moderatorFraction,
                moderatorImpurityPpmB, structure, structureFraction, material, fraction);
    }

    public CellComposition withStructure(CladMaterial material, double fraction) {
        return new CellComposition(lattice, coolant, coolantFraction, moderator, moderatorFraction,
                moderatorImpurityPpmB, material, fraction, absorber, absorberFraction);
    }

    public boolean hasFuel() {
        return lattice != null;
    }

    public FuelGeometry geometry() {
        return lattice == null ? FuelGeometry.NONE : lattice.geometry();
    }
}
