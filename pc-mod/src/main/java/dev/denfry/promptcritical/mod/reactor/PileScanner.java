package dev.denfry.promptcritical.mod.reactor;

import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.sim.reactor.PileLayout;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Кладка графитового котла в мире: связная по граням область графита, топливных каналов, каналов стержней и
 * источника, касающаяся пульта. Стержень — колонна каналов под приводом (регулирующий) или лебёдкой (аварийный),
 * счётчик — блок, прижатый к кладке. Порядок обхода детерминирован.
 */
public final class PileScanner {
    /** Итог обхода; {@code truncated} — кладка больше {@link PileLayout#MAX_NODES}, котёл не собран. */
    public record Scan(PileLayout layout, boolean truncated, List<BlockPos> top) {}

    private PileScanner() {
    }

    @Nullable
    static PileLayout.NodeKind kind(BlockState s) {
        if (s.is(PcBlocks.MODERATOR_BLOCK_GRAPHITE.get())) return PileLayout.NodeKind.GRAPHITE;
        if (s.is(PcBlocks.FUEL_CHANNEL.get())) return PileLayout.NodeKind.FUEL;
        if (s.is(PcBlocks.CONTROL_ROD_CHANNEL.get())) return PileLayout.NodeKind.ROD_CHANNEL;
        if (s.is(PcBlocks.STARTUP_SOURCE.get())) return PileLayout.NodeKind.SOURCE;
        return null;
    }

    public static boolean isPileBlock(BlockState s) {
        return kind(s) != null;
    }

    public static Scan scan(Level level, BlockPos controller) {
        PileLayout.Builder b = PileLayout.builder();
        Map<BlockPos, Integer> index = new HashMap<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        for (Direction d : Direction.values()) {
            BlockPos p = controller.relative(d);
            if (isPileBlock(level.getBlockState(p)) && !index.containsKey(p)) {
                if (!add(level, b, index, p)) return truncated();
                queue.add(p);
            }
        }
        List<BlockPos> order = new ArrayList<>();
        while (!queue.isEmpty()) {
            BlockPos p = queue.poll();
            order.add(p);
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (index.containsKey(n) || !level.isLoaded(n) || !isPileBlock(level.getBlockState(n))) continue;
                if (!add(level, b, index, n)) return truncated();
                queue.add(n);
            }
        }
        // стержни: колонна каналов вниз от привода или лебёдки
        int rods = 0;
        for (BlockPos p : order) {
            if (!level.getBlockState(p).is(PcBlocks.CONTROL_ROD_CHANNEL.get())) continue;
            BlockState above = level.getBlockState(p.above());
            PileLayout.RodKind rod = above.is(PcBlocks.CONTROL_ROD_DRIVE.get()) ? PileLayout.RodKind.CONTROL
                    : above.is(PcBlocks.ZIP_ROD_WINCH.get()) ? PileLayout.RodKind.ZIP : null;
            if (rod == null) continue;
            List<Integer> column = new ArrayList<>();
            for (BlockPos q = p; index.containsKey(q) && level.getBlockState(q).is(PcBlocks.CONTROL_ROD_CHANNEL.get()); q = q.below()) {
                column.add(index.get(q));
            }
            if (!column.isEmpty() && rods < PileLayout.MAX_RODS) {
                b.addRod(p.above().asLong(), rod, column.stream().mapToInt(Integer::intValue).toArray());
                rods++;
            }
        }
        // счётчики у граней кладки
        Set<BlockPos> detectors = new HashSet<>();
        for (BlockPos p : order) {
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (detectors.size() >= PileLayout.MAX_DETECTORS || detectors.contains(n)) continue;
                if (level.getBlockState(n).is(PcBlocks.NEUTRON_DETECTOR.get())) {
                    detectors.add(n);
                    b.addDetector(n.asLong(), index.get(p));
                }
            }
        }
        List<BlockPos> top = new ArrayList<>();
        for (BlockPos p : order) if (!index.containsKey(p.above())) top.add(p);
        return new Scan(b.build(), false, top);
    }

    private static boolean add(Level level, PileLayout.Builder b, Map<BlockPos, Integer> index, BlockPos p) {
        if (b.nodeCount() >= PileLayout.MAX_NODES) return false;
        BlockState s = level.getBlockState(p);
        PileLayout.NodeKind k = kind(s);
        int load = k == PileLayout.NodeKind.FUEL && level.getBlockEntity(p) instanceof FuelChannelBlockEntity f ? f.load() : 0;
        index.put(p.immutable(), b.addNode(p.getX(), p.getY(), p.getZ(), k, load));
        return true;
    }

    private static Scan truncated() {
        return new Scan(PileLayout.EMPTY, true, List.of());
    }
}
