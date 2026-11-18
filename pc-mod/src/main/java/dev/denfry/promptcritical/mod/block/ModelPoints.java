package dev.denfry.promptcritical.mod.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/** Точки моделей блоков в мире: для частиц и звуков, привязанных к детали машины. */
public final class ModelPoints {
    private ModelPoints() {
    }

    /**
     * Точка модели (x, y, z в пикселях от угла клетки контроллера {@code pos}; фасад модели на север) в мире, с
     * поворотом блока к {@code facing} вокруг центра клетки — так же, как поворачивают модель блокстейт и рендеры.
     */
    public static Vec3 toWorld(BlockPos pos, Direction facing, double x, double y, double z) {
        double lx = x / 16 - 0.5, lz = z / 16 - 0.5;
        double wx = switch (facing) {
            case SOUTH -> -lx;
            case EAST -> -lz;
            case WEST -> lz;
            default -> lx;
        };
        double wz = switch (facing) {
            case SOUTH -> -lz;
            case EAST -> lx;
            case WEST -> -lx;
            default -> lz;
        };
        return new Vec3(pos.getX() + 0.5 + wx, pos.getY() + y / 16, pos.getZ() + 0.5 + wz);
    }
}
