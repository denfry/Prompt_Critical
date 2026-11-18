package dev.denfry.promptcritical.mod.grid;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Загруженные щитки освещения уровня: светильник ищет ближайший щиток в радиусе {@value #RANGE} блоков и питается от
 * него. Только сервер; щитки сами вписываются при загрузке BE и выписываются при выгрузке.
 */
public final class LightingBoards {
    /** Радиус группы щитка, блоков (по каждой оси). */
    public static final int RANGE = 24;
    private static final Map<Level, Set<BlockPos>> BOARDS = new WeakHashMap<>();

    private LightingBoards() {
    }

    static synchronized void add(Level level, BlockPos pos) {
        BOARDS.computeIfAbsent(level, l -> new HashSet<>()).add(pos.immutable());
    }

    static synchronized void remove(Level level, BlockPos pos) {
        Set<BlockPos> s = BOARDS.get(level);
        if (s != null) s.remove(pos);
    }

    /** Ближайший к светильнику щиток в радиусе группы; при равенстве — меньший по координатам (детерминированно). */
    @Nullable
    public static synchronized LightingBoardBlockEntity nearest(Level level, BlockPos lamp) {
        Set<BlockPos> s = BOARDS.get(level);
        if (s == null || s.isEmpty()) return null;
        LightingBoardBlockEntity best = null;
        BlockPos bestPos = null;
        long bestD = Long.MAX_VALUE;
        for (BlockPos p : s) {
            int dx = Math.abs(p.getX() - lamp.getX()), dy = Math.abs(p.getY() - lamp.getY()), dz = Math.abs(p.getZ() - lamp.getZ());
            if (dx > RANGE || dy > RANGE || dz > RANGE) continue;
            long d = (long) dx * dx + (long) dy * dy + (long) dz * dz;
            if (d > bestD || (d == bestD && bestPos != null && p.compareTo(bestPos) >= 0)) continue;
            if (!level.isLoaded(p) || !(level.getBlockEntity(p) instanceof LightingBoardBlockEntity b)) continue;
            best = b;
            bestPos = p;
            bestD = d;
        }
        return best;
    }
}
