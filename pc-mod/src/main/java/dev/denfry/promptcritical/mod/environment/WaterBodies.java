package dev.denfry.promptcritical.mod.environment;

import dev.denfry.promptcritical.mod.block.PcBlock;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import org.jetbrains.annotations.Nullable;

/**
 * Водоёмы мира для гидросооружений: поверхность воды в столбе и площадь зеркала заливкой по поверхности
 * (1 блок — 1 м²). Общее у бассейна приливной станции и верхнего бассейна ГАЭС.
 */
public final class WaterBodies {
    private WaterBodies() {
    }

    /** Поверхность воды в столбе у {@code at} (± 4 блока по высоте); {@code null} — воды нет. */
    @Nullable
    public static BlockPos surface(ServerLevel sl, BlockPos at) {
        for (int dy = 4; dy >= -4; dy--) {
            BlockPos p = at.above(dy);
            if (sl.isLoaded(p) && sl.getFluidState(p).is(FluidTags.WATER) && !sl.getFluidState(p.above()).is(FluidTags.WATER))
                return p.immutable();
        }
        return null;
    }

    /**
     * Площадь зеркала воды, связной по горизонтали с {@code start} на его высоте; блоки машин мода — стенки.
     *
     * @param forbidden колонна, до которой заливка не должна дойти (вода по ту сторону плотины); {@code null} — нет
     * @return площадь, м²; 0 — заливка дошла до {@code forbidden}, превысила {@code max} или упёрлась в незагруженный чанк
     */
    public static int area(ServerLevel sl, BlockPos start, int max, @Nullable BlockPos forbidden) {
        int y = start.getY();
        Set<Long> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        seen.add(start.asLong());
        while (!queue.isEmpty()) {
            BlockPos p = queue.poll();
            if (forbidden != null && p.getX() == forbidden.getX() && p.getZ() == forbidden.getZ()) return 0;
            if (seen.size() > max) return 0;
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos n = p.relative(d);
                if (seen.contains(n.asLong())) continue;
                if (!sl.isLoaded(n)) return 0;
                if (!sl.getFluidState(n).is(FluidTags.WATER) || sl.getBlockState(n).getBlock() instanceof PcBlock) continue;
                seen.add(n.asLong());
                queue.add(new BlockPos(n.getX(), y, n.getZ()));
            }
        }
        return seen.size();
    }
}
