package dev.denfry.promptcritical.mod.client.whole;

import net.minecraft.core.Direction;

/**
 * Поворот точки модели (блоки от угла контроллера, фасад на север) по фасаду машины вокруг центра контроллера — так
 * же, как {@link WholeModelRenderer} поворачивает куски.
 */
public final class WholeModelMath {
    private WholeModelMath() {}

    public static double[] rotate(double[] p, Direction facing) {
        double x = p[0] - 0.5, z = p[2] - 0.5;
        double rx, rz;
        switch (facing) {
            case EAST -> { rx = -z; rz = x; }
            case SOUTH -> { rx = -x; rz = -z; }
            case WEST -> { rx = z; rz = -x; }
            default -> { rx = x; rz = z; }
        }
        return new double[] {rx + 0.5, p[1], rz + 0.5};
    }
}
