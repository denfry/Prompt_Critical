package dev.denfry.promptcritical.mod.mine;

import dev.denfry.promptcritical.mod.block.HeadframeBlock;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.multiblock.Footprint;
import dev.denfry.promptcritical.mod.multiblock.MultiblockPartBlock;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Геометрия подъёма (08 §8.2.8): копёр перед или за подъёмной машиной, ствол под копром, клеть в стволе и посадки.
 * Верхняя посадка — клеть сразу под нижним сегментом копра (верх клети на ряд ниже копра); ствол идёт вниз, пока
 * под клетью свободно; промежуточная посадка — уровень, где рядом с клетью выход в выработку: два свободных блока
 * над твёрдым полом.
 */
public final class HoistGeometry {
    /** Как далеко от машины искать копёр: вперёд/назад, вбок, вниз и вверх. */
    public static final int REACH = 32, LATERAL = 2, BELOW = 4, ABOVE = 16;
    public static final int MAX_DEPTH = 300, MAX_LANDINGS = 16;
    private static final int STEP = 3;

    private HoistGeometry() {
    }

    /** Копёр: якоря нижнего и верхнего сегментов. */
    public record Headframe(BlockPos bottom, BlockPos top, boolean formed) {
        /** Позиция контроллера клети (средний ряд) на верхней посадке при её смещении от якоря копра по x/z. */
        public int cageTopY() {
            return bottom.getY() - 1 - 2;
        }
    }

    /**
     * Ствол под копром.
     *
     * @param cageX/cageZ  контроллер клети по x/z
     * @param cageFacing   фасад клети
     * @param topY         y контроллера клети на верхней посадке
     * @param landings     глубины посадок, блоков, по возрастанию; первая — 0, последняя — дно ствола
     */
    public record Shaft(int cageX, int cageZ, Direction cageFacing, int topY, int[] landings) {
        public int depth() {
            return landings[landings.length - 1];
        }

        public BlockPos cageAt(int depth) {
            return new BlockPos(cageX, topY - depth, cageZ);
        }
    }

    // --- копёр ----------------------------------------------------------------------------------------------

    @Nullable
    public static Headframe findHeadframe(Level level, BlockPos hoist, Direction facing) {
        Direction side = facing.getClockWise();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int d = 2; d <= REACH; d++)
            for (Direction dir : new Direction[] {facing, facing.getOpposite()})
                for (int l = -LATERAL; l <= LATERAL; l++)
                    for (int dy = -BELOW; dy <= ABOVE; dy++) {
                        p.set(hoist).move(dir, d).move(side, l).move(Direction.UP, dy);
                        BlockPos anchor = headframeAnchor(level, p);
                        if (anchor != null) return stack(level, anchor);
                    }
        return null;
    }

    /** Копёр, к которому принадлежит якорь (или {@code null}, если его там больше нет). */
    @Nullable
    public static Headframe stackAt(Level level, BlockPos anyAnchor) {
        return level.getBlockState(anyAnchor).is(PcBlocks.HEADFRAME.get()) ? stack(level, anyAnchor) : null;
    }

    @Nullable
    private static BlockPos headframeAnchor(Level level, BlockPos p) {
        BlockState s = level.getBlockState(p);
        if (s.is(PcBlocks.HEADFRAME.get())) return p.immutable();
        if (s.getBlock() instanceof MultiblockPartBlock) {
            BlockPos a = MultiblockPartBlock.anchor(s, p);
            if (level.getBlockState(a).is(PcBlocks.HEADFRAME.get())) return a;
        }
        return null;
    }

    private static Headframe stack(Level level, BlockPos anchor) {
        HeadframeBlock hb = PcBlocks.HEADFRAME.get();
        Direction f = hb.facing(level.getBlockState(anchor));
        BlockPos bottom = anchor, top = anchor;
        while (same(level, bottom.below(STEP), f)) bottom = bottom.below(STEP);
        while (same(level, top.above(STEP), f)) top = top.above(STEP);
        return new Headframe(bottom, top, level.getBlockState(top).getValue(PcProperties.FORMED));
    }

    private static boolean same(Level level, BlockPos p, Direction f) {
        BlockState s = level.getBlockState(p);
        return s.is(PcBlocks.HEADFRAME.get()) && PcBlocks.HEADFRAME.get().facing(s) == f;
    }

    // --- клеть и ствол -------------------------------------------------------------------------------------

    /**
     * Клеть, стоящая блоками в стволе под копром (контроллер; все клетки — в колонне 3×3 копра), или {@code null}.
     */
    @Nullable
    public static BlockPos findCage(Level level, Headframe hf) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int top = hf.cageTopY();
        for (int y = top; y >= top - MAX_DEPTH && y > level.getMinBuildHeight(); y--)
            for (int dx = -1; dx <= 1; dx++)
                for (int dz = -1; dz <= 1; dz++) {
                    p.set(hf.bottom().getX() + dx, y, hf.bottom().getZ() + dz);
                    BlockState s = level.getBlockState(p);
                    if (s.getBlock() instanceof MineCageBlock c && c.isController(s) && insideColumn(c, s, p, hf)) return p.immutable();
                }
        return null;
    }

    private static boolean insideColumn(MineCageBlock c, BlockState s, BlockPos ctrl, Headframe hf) {
        for (Footprint.Cell cell : c.spec().footprint().cells(ctrl, c.facing(s)))
            if (Math.abs(cell.pos().getX() - hf.bottom().getX()) > 1 || Math.abs(cell.pos().getZ() - hf.bottom().getZ()) > 1) return false;
        return true;
    }

    /**
     * Ствол для клети с контроллером по x/z {@code cageX/cageZ} и фасадом {@code facing}: дно и посадки.
     *
     * @return {@code null} — клеть не помещается даже на верхней посадке
     */
    @Nullable
    public static Shaft scanShaft(Level level, Headframe hf, int cageX, int cageZ, Direction facing) {
        int topY = hf.cageTopY();
        // колонны клети: клетки нижнего ряда относительно контроллера (в модели y = −1)
        List<int[]> cols = new ArrayList<>();
        for (Footprint.Cell c : PcBlocks.MINE_CAGE.get().spec().footprint().cells(new BlockPos(cageX, topY, cageZ), facing))
            if (c.pos().getY() == topY - 1) cols.add(new int[] {c.pos().getX(), c.pos().getZ()});
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int[] col : cols)
            for (int y = topY - 1; y <= topY + 1; y++) if (!free(level, p.set(col[0], y, col[1]))) return null;
        int depth = 0;
        while (depth < MAX_DEPTH) {
            int floor = topY - 1 - (depth + 1);
            if (floor <= level.getMinBuildHeight()) break;
            boolean ok = true;
            for (int[] col : cols) ok &= free(level, p.set(col[0], floor, col[1]));
            if (!ok) break;
            depth++;
        }
        if (depth == 0) return null;
        IntArrayList landings = new IntArrayList();
        landings.add(0);
        for (int d = 1; d < depth && landings.size() < MAX_LANDINGS - 1; d++)
            if (d - landings.getInt(landings.size() - 1) >= 3 && depth - d >= 3 && opening(level, cols, topY - 1 - d)) landings.add(d);
        landings.add(depth);
        return new Shaft(cageX, cageZ, facing, topY, landings.toIntArray());
    }

    /** Выход в выработку на уровне пола клети: рядом с клетью два свободных блока над твёрдым полом. */
    private static boolean opening(Level level, List<int[]> cols, int floorY) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int[] col : cols)
            for (Direction d : Direction.Plane.HORIZONTAL) {
                int x = col[0] + d.getStepX(), z = col[1] + d.getStepZ();
                if (inCage(cols, x, z)) continue;
                if (open(level, p.set(x, floorY, z)) && open(level, p.set(x, floorY + 1, z)) && !free(level, p.set(x, floorY - 1, z)))
                    return true;
            }
        return false;
    }

    private static boolean inCage(List<int[]> cols, int x, int z) {
        for (int[] c : cols) if (c[0] == x && c[1] == z) return true;
        return false;
    }

    /** Клеть пройдёт: заменяемый блок (воздух, вода) или клетка самой клети. */
    private static boolean free(Level level, BlockPos p) {
        BlockState s = level.getBlockState(p);
        if (s.canBeReplaced()) return true;
        if (s.getBlock() instanceof MineCageBlock) return true;
        if (s.getBlock() instanceof MultiblockPartBlock) return level.getBlockState(MultiblockPartBlock.anchor(s, p)).getBlock() instanceof MineCageBlock;
        return false;
    }

    /** Проход для человека: воздух без жидкости. */
    private static boolean open(Level level, BlockPos p) {
        BlockState s = level.getBlockState(p);
        return s.canBeReplaced() && s.getFluidState().isEmpty();
    }

    /** Подвеска каната от барабана через шкив до верхней посадки клети, м. */
    public static double reachM(BlockPos hoist, Headframe hf) {
        double dx = hf.top().getX() - hoist.getX(), dz = hf.top().getZ() - hoist.getZ();
        return Math.sqrt(dx * dx + dz * dz) + Math.max(0, hf.top().getY() - hoist.getY()) + (hf.top().getY() - hf.cageTopY());
    }
}
