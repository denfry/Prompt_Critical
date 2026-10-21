package dev.denfry.promptcritical.mod.ironworks;

import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.sim.metallurgy.BlastFurnaceSpec;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * Форма домны (08 §8.5.2), найденная по блокам от контроллера: кожух — кольцо 5×5 из {@code blast_furnace_shell}
 * высотой {@value #MIN_HEIGHT}–{@value #MAX_HEIGHT}, внутри 3×3 пусто, низ (лещадь) — сплошной 5×5; лётка
 * {@code blast_furnace_taphole} — ровно одна в слое 1, фурмы {@code blast_furnace_tuyere} — 4–12 в слое 2 (не в
 * углах); засыпной аппарат {@code blast_furnace_top} — над серединой. Контроллер касается кольца снаружи. Полезный
 * объём — пустые клетки шахты, 1 м³ на клетку.
 *
 * @param cx       середина по X
 * @param cz       середина по Z
 * @param y0       лещадь
 * @param yTop     верхний слой кольца
 * @param taphole  лётка
 * @param tuyeres  фурмы по кругу
 */
public record BlastFurnaceShape(int cx, int cz, int y0, int yTop, BlockPos taphole, List<BlockPos> tuyeres) {
    public static final int MIN_HEIGHT = 12, MAX_HEIGHT = 18, MIN_TUYERES = 4, MAX_TUYERES = 12;

    /** Результат поиска: форма или ключ причины, почему печи нет. */
    public record Scan(@Nullable BlastFurnaceShape shape, String problem) {
        static Scan fail(String problem) {
            return new Scan(null, problem);
        }
    }

    /** Полезный объём V_u, м³: пустые клетки от слоя 1 до верха кольца. */
    public double usefulVolumeM3() {
        return 9.0 * (yTop - y0);
    }

    public BlastFurnaceSpec spec() {
        return BlastFurnaceSpec.of(usefulVolumeM3(), tuyeres.size());
    }

    /** Засыпной аппарат (якорь модели 3×2×3). */
    public BlockPos top() {
        return new BlockPos(cx, yTop + 1, cz);
    }

    /** Наружу от середины для клетки кольца. */
    public Direction outward(BlockPos ring) {
        int dx = ring.getX() - cx, dz = ring.getZ() - cz;
        if (Math.abs(dx) >= Math.abs(dz)) return dx > 0 ? Direction.EAST : Direction.WEST;
        return dz > 0 ? Direction.SOUTH : Direction.NORTH;
    }

    /** Габарит печи с колошником. */
    public AABB bounds() {
        return new AABB(cx - 2, y0, cz - 2, cx + 3, yTop + 3, cz + 3);
    }

    /** Клетка принадлежит кольцу, лещади или колошнику. */
    public boolean contains(BlockPos p) {
        return Math.abs(p.getX() - cx) <= 2 && Math.abs(p.getZ() - cz) <= 2 && p.getY() >= y0 && p.getY() <= yTop + 2;
    }

    public static boolean isWall(BlockState s) {
        return s.is(PcBlocks.BLAST_FURNACE_SHELL.get()) || s.is(PcBlocks.BLAST_FURNACE_TUYERE.get())
                || s.is(PcBlocks.BLAST_FURNACE_TAPHOLE.get());
    }

    /** Найти печь у контроллера {@code controller}. */
    public static Scan scan(Level level, BlockPos controller) {
        Scan best = Scan.fail("no_shell");
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos n = controller.relative(d);
            if (!level.isLoaded(n) || !isWall(level.getBlockState(n))) continue;
            // середина — в двух клетках от кольца вглубь по направлению от контроллера
            for (int s = -2; s <= 2; s++) {
                Direction side = d.getClockWise();
                BlockPos c = n.relative(d, 2).relative(side, s);
                Scan r = at(level, c.getX(), c.getZ(), n);
                if (r.shape() != null) return r;
                if (!r.problem().equals("no_shell")) best = r;
            }
        }
        return best;
    }

    private static Scan at(Level level, int cx, int cz, BlockPos wall) {
        int y0 = wall.getY();
        if (!solidLayer(level, cx, cz, y0)) {
            if (!ring(level, cx, cz, y0)) return Scan.fail("no_shell");
            while (y0 > wall.getY() - MAX_HEIGHT && ring(level, cx, cz, y0 - 1) && !solidLayer(level, cx, cz, y0 - 1)) y0--;
            if (!solidLayer(level, cx, cz, --y0)) return Scan.fail("no_hearth");
        }
        int yTop = y0;
        while (yTop - y0 < MAX_HEIGHT && ring(level, cx, cz, yTop + 1)) yTop++;
        int h = yTop - y0 + 1;
        if (h < MIN_HEIGHT) return Scan.fail("too_low");
        if (h > MAX_HEIGHT) return Scan.fail("too_high");
        for (int yy = y0 + 1; yy <= yTop; yy++)
            for (int dx = -1; dx <= 1; dx++)
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos p = new BlockPos(cx + dx, yy, cz + dz);
                    if (!level.isLoaded(p)) return Scan.fail("unloaded");
                    if (!level.getBlockState(p).isAir()) return Scan.fail("shaft_blocked");
                }
        BlockPos taphole = null;
        List<BlockPos> tuyeres = new ArrayList<>();
        for (int yy = y0; yy <= yTop; yy++)
            for (BlockPos p : ringCells(cx, cz, yy)) {
                BlockState s = level.getBlockState(p);
                boolean corner = Math.abs(p.getX() - cx) == 2 && Math.abs(p.getZ() - cz) == 2;
                if (s.is(PcBlocks.BLAST_FURNACE_TAPHOLE.get())) {
                    if (yy != y0 + 1 || corner) return Scan.fail("taphole_place");
                    if (taphole != null) return Scan.fail("taphole_count");
                    taphole = p;
                } else if (s.is(PcBlocks.BLAST_FURNACE_TUYERE.get())) {
                    if (yy != y0 + 2 || corner) return Scan.fail("tuyere_place");
                    tuyeres.add(p);
                }
            }
        if (taphole == null) return Scan.fail("taphole_count");
        if (tuyeres.size() < MIN_TUYERES || tuyeres.size() > MAX_TUYERES) return Scan.fail("tuyere_count");
        BlockPos top = new BlockPos(cx, yTop + 1, cz);
        if (!level.isLoaded(top) || !level.getBlockState(top).is(PcBlocks.BLAST_FURNACE_TOP.get())) return Scan.fail("no_top");
        return new Scan(new BlastFurnaceShape(cx, cz, y0, yTop, taphole, List.copyOf(tuyeres)), "");
    }

    /** 16 клеток кольца 5×5 в слое. */
    static List<BlockPos> ringCells(int cx, int cz, int y) {
        List<BlockPos> out = new ArrayList<>(16);
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++)
                if (Math.abs(dx) == 2 || Math.abs(dz) == 2) out.add(new BlockPos(cx + dx, y, cz + dz));
        return out;
    }

    private static boolean ring(Level level, int cx, int cz, int y) {
        for (BlockPos p : ringCells(cx, cz, y)) if (!level.isLoaded(p) || !isWall(level.getBlockState(p))) return false;
        return true;
    }

    private static boolean solidLayer(Level level, int cx, int cz, int y) {
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++) {
                BlockPos p = new BlockPos(cx + dx, y, cz + dz);
                if (!level.isLoaded(p) || !level.getBlockState(p).is(PcBlocks.BLAST_FURNACE_SHELL.get())) return false;
            }
        return true;
    }
}
