package dev.denfry.promptcritical.mod.multiblock;

import com.simibubi.create.content.fluids.pipes.EncasedPipeBlock;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import com.simibubi.create.content.fluids.pipes.GlassFluidPipeBlock;
import com.simibubi.create.content.fluids.pipes.SmartFluidPipeBlock;
import com.simibubi.create.content.fluids.pipes.valve.FluidValveBlock;
import com.simibubi.create.content.fluids.pump.PumpBlock;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BiFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Связи машин реального масштаба по патрубкам: у старой машины 3×3×3 соседа (котёл, колонну, газоразделение) искали
 * по касанию любой клетки, у цельной модели сосед стоит у объявленного патрубка ({@link WholePorts}) — вплотную или в
 * конце трубы Create от него (трубы, насосы, задвижки; не дальше {@value #TRACE_LIMIT} клеток).
 */
public final class WholeLinks {
    public static final int TRACE_LIMIT = 256;

    private WholeLinks() {
    }

    /** Клетка {@code cell} модели (фасад на север, от контроллера) в мире при фасаде {@code facing}. */
    public static BlockPos portCell(BlockPos controller, Direction facing, BlockPos cell) {
        return controller.offset(cell.rotate(Footprint.rotation(facing)));
    }

    /** Мировая грань для грани {@code modelFace} модели при фасаде {@code facing}. */
    public static Direction worldFace(Direction modelFace, Direction facing) {
        return modelFace.getAxis().isVertical() ? modelFace : Footprint.rotation(facing).rotate(modelFace);
    }

    /** Труба Create: обычная, в корпусе, стеклянная, умная, с задвижкой или насос. */
    public static boolean pipe(BlockState s) {
        var b = s.getBlock();
        return b instanceof FluidPipeBlock || b instanceof EncasedPipeBlock || b instanceof GlassFluidPipeBlock || b instanceof FluidValveBlock
                || b instanceof SmartFluidPipeBlock || b instanceof PumpBlock;
    }

    /**
     * Машина за патрубком: клетка {@code cell} модели с гранью {@code modelFace} у контроллера {@code controller} с
     * фасадом {@code facing}; {@code at} узнаёт машину по клетке. Сначала клетка вплотную к грани, затем обход труб.
     */
    @Nullable
    public static <T> T across(Level level, BlockPos controller, Direction facing, BlockPos cell, Direction modelFace,
            BiFunction<Level, BlockPos, T> at) {
        BlockPos port = portCell(controller, facing, cell);
        BlockPos first = port.relative(worldFace(modelFace, facing));
        if (!level.isLoaded(first)) return null;
        T direct = at.apply(level, first);
        if (direct != null) return direct;
        if (!pipe(level.getBlockState(first))) return null;
        ArrayDeque<BlockPos> q = new ArrayDeque<>();
        Set<BlockPos> seen = new HashSet<>();
        q.add(first);
        seen.add(first);
        seen.add(port);
        while (!q.isEmpty() && seen.size() < TRACE_LIMIT) {
            BlockPos p = q.poll();
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (!seen.add(n) || !level.isLoaded(n)) continue;
                if (pipe(level.getBlockState(n))) {
                    q.add(n);
                    continue;
                }
                T t = at.apply(level, n);
                if (t != null) return t;
            }
        }
        return null;
    }
}
