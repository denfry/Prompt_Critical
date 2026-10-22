package dev.denfry.promptcritical.mod.powerhouse;

import dev.denfry.promptcritical.mod.registry.PcBlocks;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * Форма водотрубного котла I1 (07 §7.9.5), найденная от щита {@code water_tube_boiler}: топка — полый
 * параллелепипед из экранов {@code boiler_wall} со всех шести сторон, снаружи {@value #MIN_SIDE}–{@value #MAX_SIDE}
 * в плане и {@value #MIN_HEIGHT}–{@value #MAX_HEIGHT} в высоту, внутри пусто; горелки {@code boiler_burner}
 * ({@value #MIN_BURNERS}–{@value #MAX_BURNERS}) — в боковых стенах не на рёбрах; барабан {@code steam_drum} — хотя бы
 * один на крыше. Пароперегреватель, экономайзер и воздухоподогреватель — блоки в пределах {@value #SURFACE_REACH}
 * клеток от топки снаружи (конвективная шахта), до {@value #MAX_SURFACE_BLOCKS} каждого. Щит касается стены топки
 * снаружи. Объём топки — пустые клетки (1 м³), площадь экранов F_ст — внутренняя поверхность (1 м² на грань).
 *
 * @param min       нижний угол топки (стена)
 * @param max       верхний угол топки (стена)
 * @param burners   горелки
 * @param drums     барабаны на крыше
 */
public record WaterTubeBoilerShape(BlockPos min, BlockPos max, List<BlockPos> burners, List<BlockPos> drums,
        int superheaters, int economizers, int airPreheaters) {
    public static final int MIN_SIDE = 5, MAX_SIDE = 9, MIN_HEIGHT = 8, MAX_HEIGHT = 16;
    public static final int MIN_BURNERS = 1, MAX_BURNERS = 8, SURFACE_REACH = 2, MAX_SURFACE_BLOCKS = 6;
    private static final int MAX_WALL_BLOCKS = 2 * (MAX_SIDE * MAX_SIDE + 2 * MAX_SIDE * MAX_HEIGHT);

    /** Результат поиска: форма или ключ причины, почему котла нет. */
    public record Scan(@Nullable WaterTubeBoilerShape shape, String problem) {
        static Scan fail(String problem) {
            return new Scan(null, problem);
        }
    }

    public int innerX() {
        return max.getX() - min.getX() - 1;
    }

    public int innerY() {
        return max.getY() - min.getY() - 1;
    }

    public int innerZ() {
        return max.getZ() - min.getZ() - 1;
    }

    /** Объём топки V_т, м³. */
    public double volumeM3() {
        return (double) innerX() * innerY() * innerZ();
    }

    /** Площадь экранов F_ст, м². */
    public double wallAreaM2() {
        return 2.0 * (innerX() * innerY() + innerY() * innerZ() + innerX() * innerZ());
    }

    /** Габарит топки с барабанами. */
    public AABB bounds() {
        return new AABB(min.getX(), min.getY(), min.getZ(), max.getX() + 1, max.getY() + 2, max.getZ() + 1);
    }

    /** Клетка — стена, горелка или барабан этого котла. */
    public boolean contains(BlockPos p) {
        boolean inBox = p.getX() >= min.getX() && p.getX() <= max.getX() && p.getZ() >= min.getZ() && p.getZ() <= max.getZ();
        return inBox && (p.getY() >= min.getY() && p.getY() <= max.getY() || drums.contains(p));
    }

    /** Середина топки (взрыв, звук факела). */
    public BlockPos center() {
        return new BlockPos((min.getX() + max.getX()) / 2, (min.getY() + max.getY()) / 2, (min.getZ() + max.getZ()) / 2);
    }

    public static boolean isWall(BlockState s) {
        return s.is(PcBlocks.BOILER_WALL.get()) || s.is(PcBlocks.BOILER_BURNER.get());
    }

    /** Найти котёл у щита {@code controller}. */
    public static Scan scan(Level level, BlockPos controller) {
        Scan best = Scan.fail("no_wall");
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos n = controller.relative(d);
            if (!level.isLoaded(n) || !isWall(level.getBlockState(n))) continue;
            Scan r = from(level, n);
            if (r.shape() != null) return r;
            best = r;
        }
        return best;
    }

    /** Топка по стене {@code start}: связная группа стен и горелок, проверенная как полый параллелепипед. */
    private static Scan from(Level level, BlockPos start) {
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        seen.add(start);
        int x0 = start.getX(), y0 = start.getY(), z0 = start.getZ(), x1 = x0, y1 = y0, z1 = z0;
        while (!queue.isEmpty()) {
            BlockPos p = queue.poll();
            x0 = Math.min(x0, p.getX());
            y0 = Math.min(y0, p.getY());
            z0 = Math.min(z0, p.getZ());
            x1 = Math.max(x1, p.getX());
            y1 = Math.max(y1, p.getY());
            z1 = Math.max(z1, p.getZ());
            if (seen.size() > MAX_WALL_BLOCKS) return Scan.fail("too_big");
            for (Direction d : Direction.values()) {
                BlockPos q = p.relative(d);
                if (seen.contains(q)) continue;
                if (!level.isLoaded(q)) return Scan.fail("unloaded");
                if (isWall(level.getBlockState(q))) {
                    seen.add(q);
                    queue.add(q);
                }
            }
        }
        int sx = x1 - x0 + 1, sy = y1 - y0 + 1, sz = z1 - z0 + 1;
        if (sx < MIN_SIDE || sz < MIN_SIDE || sy < MIN_HEIGHT) return Scan.fail("too_small");
        if (sx > MAX_SIDE || sz > MAX_SIDE || sy > MAX_HEIGHT) return Scan.fail("too_big");
        List<BlockPos> burners = new ArrayList<>();
        for (int x = x0; x <= x1; x++)
            for (int y = y0; y <= y1; y++)
                for (int z = z0; z <= z1; z++) {
                    BlockPos p = new BlockPos(x, y, z);
                    BlockState s = level.getBlockState(p);
                    int faces = (x == x0 || x == x1 ? 1 : 0) + (y == y0 || y == y1 ? 1 : 0) + (z == z0 || z == z1 ? 1 : 0);
                    if (faces == 0) {
                        if (!s.isAir()) return Scan.fail("furnace_blocked");
                        continue;
                    }
                    if (!isWall(s)) return Scan.fail("wall_gap");
                    if (s.is(PcBlocks.BOILER_BURNER.get())) {
                        if (faces > 1 || y == y0 || y == y1) return Scan.fail("burner_place");
                        burners.add(p);
                    }
                }
        if (burners.size() < MIN_BURNERS || burners.size() > MAX_BURNERS) return Scan.fail("burner_count");
        List<BlockPos> drums = new ArrayList<>();
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++) {
                BlockPos p = new BlockPos(x, y1 + 1, z);
                if (level.getBlockState(p).is(PcBlocks.STEAM_DRUM.get())) drums.add(p);
            }
        if (drums.isEmpty()) return Scan.fail("no_drum");
        int sh = 0, eco = 0, aph = 0, r = SURFACE_REACH;
        for (BlockPos p : BlockPos.betweenClosed(x0 - r, y0, z0 - r, x1 + r, y1 + r, z1 + r)) {
            BlockState s = level.getBlockState(p);
            if (s.is(PcBlocks.BOILER_SUPERHEATER.get())) sh++;
            else if (s.is(PcBlocks.BOILER_ECONOMIZER.get())) eco++;
            else if (s.is(PcBlocks.AIR_PREHEATER.get())) aph++;
        }
        return new Scan(new WaterTubeBoilerShape(new BlockPos(x0, y0, z0), new BlockPos(x1, y1, z1), List.copyOf(burners),
                List.copyOf(drums), Math.min(sh, MAX_SURFACE_BLOCKS), Math.min(eco, MAX_SURFACE_BLOCKS),
                Math.min(aph, MAX_SURFACE_BLOCKS)), "");
    }
}
