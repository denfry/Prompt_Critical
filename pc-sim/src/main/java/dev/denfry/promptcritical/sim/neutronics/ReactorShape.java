package dev.denfry.promptcritical.sim.neutronics;

/**
 * Форма активной части мультиблока, найденная валидатором (01 §1.6.3): размеры в блоках (1 м)
 * или цилиндр с радиусом круга равной площади и высотой, м.
 */
public record ReactorShape(int sizeX, int sizeY, int sizeZ, boolean cylindrical, double equivalentRadius,
        double activeHeight) {
    public static ReactorShape box(int x, int y, int z) {
        return new ReactorShape(x, y, z, false, 0, z);
    }

    public static ReactorShape cylinder(double radius, double height) {
        return new ReactorShape(0, 0, 0, true, radius, height);
    }
}
