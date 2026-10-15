package dev.denfry.promptcritical.mod.boiler;

import dev.denfry.promptcritical.mod.block.PcBlock;
import dev.denfry.promptcritical.mod.multiblock.Footprint;
import dev.denfry.promptcritical.mod.multiblock.MultiblockPartBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Пересчёт модельных координат (пиксели от угла контроллера, фасад на север) в мир и поиск контроллера по клетке. */
public final class BoilerGeometry {
    private BoilerGeometry() {
    }

    /** Точка модели (px от угла блока контроллера) в мировых координатах. */
    public static Vec3 world(BlockPos controller, Direction facing, double px, double py, double pz) {
        double x = (px - 8) / 16, y = (py - 8) / 16, z = (pz - 8) / 16;
        double rx, rz;
        switch (Footprint.rotation(facing)) {
            case CLOCKWISE_90 -> { rx = -z; rz = x; }
            case CLOCKWISE_180 -> { rx = -x; rz = -z; }
            case COUNTERCLOCKWISE_90 -> { rx = z; rz = -x; }
            default -> { rx = x; rz = z; }
        }
        return new Vec3(controller.getX() + 0.5 + rx, controller.getY() + 0.5 + y, controller.getZ() + 0.5 + rz);
    }

    /** Блок модели (смещение в блоках от контроллера) в мире. */
    public static BlockPos offset(BlockPos controller, Direction facing, int dx, int dy, int dz) {
        return controller.offset(new BlockPos(dx, dy, dz).rotate(Footprint.rotation(facing)));
    }

    /** Контроллер конструкции, которой принадлежит клетка {@code pos}; {@code null} — не наша конструкция. */
    @Nullable
    public static BlockPos controllerOf(Level level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        if (s.getBlock() instanceof MultiblockPartBlock) return PcBlock.structureController(level, MultiblockPartBlock.anchor(s, pos));
        if (s.getBlock() instanceof PcBlock b) return b.controllerPos(s, pos);
        return null;
    }
}
