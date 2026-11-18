package dev.denfry.promptcritical.mod.npp;

import dev.denfry.promptcritical.mod.registry.PcBlocks;
import java.util.ArrayDeque;
import java.util.HashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jetbrains.annotations.Nullable;

/**
 * Containment around a reactor (04c M10): a bounded flood fill from the reactor through everything that is not a
 * containment boundary. Closed when the fill stays within {@link #RADIUS} and {@link #MAX_VOLUME}: walls and shut
 * hatches stop it; an open hatch or a gap lets it out. The filled blocks are the free volume in m³; passive recombiners
 * inside are counted. A fill that reaches an unloaded chunk proves nothing either way and returns {@code null}, so the
 * caller keeps its last answer instead of venting a closed building whose neighbour chunk is still loading.
 */
public record ContainmentScan(boolean intact, int volumeM3, int recombiners) {
    public static final int RADIUS = 24;
    public static final int MAX_VOLUME = 40_000;
    public static final ContainmentScan OPEN = new ContainmentScan(false, 0, 0);

    @Nullable
    public static ContainmentScan scan(ServerLevel sl, BlockPos origin) {
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        HashSet<BlockPos> seen = new HashSet<>();
        queue.add(origin);
        seen.add(origin);
        int pars = 0;
        while (!queue.isEmpty()) {
            BlockPos p = queue.poll();
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (seen.contains(n)) continue;
                if (Math.abs(n.getX() - origin.getX()) > RADIUS || Math.abs(n.getY() - origin.getY()) > RADIUS
                        || Math.abs(n.getZ() - origin.getZ()) > RADIUS || seen.size() >= MAX_VOLUME)
                    return OPEN;
                if (!sl.isLoaded(n)) return null;
                BlockState s = sl.getBlockState(n);
                if (isBoundary(s)) continue;
                if (s.is(PcBlocks.HYDROGEN_RECOMBINER.get())) pars++;
                seen.add(n);
                queue.add(n);
            }
        }
        return new ContainmentScan(true, seen.size(), pars);
    }

    static boolean isBoundary(BlockState s) {
        if (s.is(PcBlocks.CONTAINMENT_WALL.get())) return true;
        return s.is(PcBlocks.CONTAINMENT_HATCH.get()) && !s.getValue(BlockStateProperties.OPEN);
    }
}
