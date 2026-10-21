package dev.denfry.promptcritical.mod.ironworks;

import dev.denfry.promptcritical.mod.block.PcBlock;
import dev.denfry.promptcritical.mod.multiblock.MultiblockPartBlock;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Форма коксовой батареи (08 §8.4.1), найденная по блокам: камеры — стенки из {@code coke_oven} высотой 3 и длиной
 * 4–8 вдоль оси {@link #axis}, 3–12 камер; между камерами и по краям — простенки {@code coke_battery_wall} той же
 * высоты и длины; над каждой камерой — хотя бы один стояк {@code gas_collector_main}. Газосборник — связная группа
 * {@code gas_collector_main}; эксгаустеры и аппараты улавливания касаются её.
 *
 * @param axis    ось камер (вдоль неё — длина и выталкивание)
 * @param y0      низ камер
 * @param wall0   поперечная координата первого простенка; камера i — {@code wall0 + 1 + 2i}
 * @param l0      начало камер вдоль оси
 * @param length  длина камеры, блоков
 * @param ovens   число камер
 * @param mains   блоки газосборника
 * @param devices контроллеры устройств у газосборника
 */
public record CokeBatteryShape(Direction.Axis axis, int y0, int wall0, int l0, int length, int ovens, Set<BlockPos> mains,
        List<BlockPos> devices) {
    public static final int HEIGHT = 3, MIN_LENGTH = 4, MAX_LENGTH = 8, MIN_OVENS = 3, MAX_OVENS = 12;
    private static final int MAX_MAIN = 512;

    /** Результат поиска: форма или ключ причины, почему батареи нет. */
    public record Scan(@Nullable CokeBatteryShape shape, String problem) {
        static Scan fail(String problem) {
            return new Scan(null, problem);
        }
    }

    /** Поперечная ось. */
    public Direction.Axis across() {
        return axis == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
    }

    public BlockPos at(int acrossC, int along, int y) {
        return axis == Direction.Axis.X ? new BlockPos(along, y, acrossC) : new BlockPos(acrossC, y, along);
    }

    /** Клетка камеры {@code oven}: {@code l} — 0…length−1 вдоль оси, {@code dy} — 0…2. */
    public BlockPos oven(int oven, int l, int dy) {
        return at(wall0 + 1 + 2 * oven, l0 + l, y0 + dy);
    }

    /** Ключ камеры — её первая нижняя клетка. */
    public long ovenKey(int oven) {
        return oven(oven, 0, 0).asLong();
    }

    /** Контроллер батареи по умолчанию: первая клетка первой камеры. */
    public BlockPos origin() {
        return oven(0, 0, 0);
    }

    /** Камера, которой принадлежит клетка, или −1. */
    public int ovenAt(BlockPos p) {
        int c = across() == Direction.Axis.X ? p.getX() : p.getZ();
        int l = axis.choose(p.getX(), p.getY(), p.getZ());
        if (p.getY() < y0 || p.getY() >= y0 + HEIGHT || l < l0 || l >= l0 + length) return -1;
        int k = c - wall0 - 1;
        return k >= 0 && k % 2 == 0 && k / 2 < ovens ? k / 2 : -1;
    }

    /** Вдоль-координата клетки. */
    public int along(BlockPos p) {
        return axis.choose(p.getX(), p.getY(), p.getZ());
    }

    public boolean contains(BlockPos p) {
        int c = across() == Direction.Axis.X ? p.getX() : p.getZ();
        int l = along(p);
        return p.getY() >= y0 && p.getY() < y0 + HEIGHT && l >= l0 && l < l0 + length && c >= wall0 && c <= wall0 + 2 * ovens;
    }

    public static Scan scan(Level level, BlockPos start) {
        if (!level.isLoaded(start) || !isOven(level, start)) return Scan.fail("no_oven");
        int y0 = start.getY();
        while (y0 > start.getY() - HEIGHT && loadedOven(level, start.atY(y0 - 1))) y0--;
        BlockPos base = start.atY(y0);
        Direction.Axis axis;
        if (loadedOven(level, base.east()) || loadedOven(level, base.west())) axis = Direction.Axis.X;
        else if (loadedOven(level, base.south()) || loadedOven(level, base.north())) axis = Direction.Axis.Z;
        else return Scan.fail("short_oven");
        Direction pos = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE);
        BlockPos a = base, b = base;
        for (int i = 0; i < MAX_LENGTH && loadedOven(level, a.relative(pos.getOpposite())); i++) a = a.relative(pos.getOpposite());
        for (int i = 0; i < MAX_LENGTH && loadedOven(level, b.relative(pos)); i++) b = b.relative(pos);
        int l0 = axis.choose(a.getX(), a.getY(), a.getZ()), length = axis.choose(b.getX(), b.getY(), b.getZ()) - l0 + 1;
        if (length < MIN_LENGTH || length > MAX_LENGTH) return Scan.fail("oven_length");
        Direction.Axis across = axis == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
        int cs = across.choose(base.getX(), base.getY(), base.getZ());
        CokeBatteryShape probe = new CokeBatteryShape(axis, y0, 0, l0, length, 0, Set.of(), List.of());
        if (!chamber(level, probe, cs)) return Scan.fail("oven_incomplete");
        int lo = cs, hi = cs;
        while (hi - lo < 2 * MAX_OVENS && wall(level, probe, lo - 1) && chamber(level, probe, lo - 2)) lo -= 2;
        while (hi - lo < 2 * MAX_OVENS && wall(level, probe, hi + 1) && chamber(level, probe, hi + 2)) hi += 2;
        if (!wall(level, probe, lo - 1) || !wall(level, probe, hi + 1)) return Scan.fail("wall_missing");
        int ovens = (hi - lo) / 2 + 1;
        if (ovens < MIN_OVENS || ovens > MAX_OVENS) return Scan.fail("oven_count");
        CokeBatteryShape s = new CokeBatteryShape(axis, y0, lo - 1, l0, length, ovens, Set.of(), List.of());
        Set<BlockPos> roots = new LinkedHashSet<>();
        for (int i = 0; i < ovens; i++) {
            boolean found = false;
            for (int l = 0; l < length; l++) {
                BlockPos m = s.oven(i, l, HEIGHT);
                if (!level.isLoaded(m)) return Scan.fail("unloaded");
                if (level.getBlockState(m).is(PcBlocks.GAS_COLLECTOR_MAIN.get())) {
                    roots.add(m);
                    found = true;
                }
            }
            if (!found) return Scan.fail("no_standpipe");
        }
        Set<BlockPos> mains = new HashSet<>();
        List<BlockPos> devices = new ArrayList<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>(roots);
        mains.addAll(roots);
        while (!queue.isEmpty()) {
            BlockPos p = queue.poll();
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (mains.contains(n) || !level.isLoaded(n)) continue;
                BlockState st = level.getBlockState(n);
                if (st.is(PcBlocks.GAS_COLLECTOR_MAIN.get())) {
                    if (mains.size() >= MAX_MAIN) continue;
                    mains.add(n);
                    queue.add(n);
                    continue;
                }
                BlockPos c = controller(level, n, st);
                if (c != null && !devices.contains(c) && (level.getBlockEntity(c) instanceof GasExhausterBlockEntity
                        || level.getBlockEntity(c) instanceof ByproductPlantBlockEntity)) devices.add(c);
            }
        }
        return new Scan(new CokeBatteryShape(axis, y0, lo - 1, l0, length, ovens, Set.copyOf(mains), List.copyOf(devices)), "");
    }

    @Nullable
    static BlockPos controller(Level level, BlockPos p, BlockState st) {
        if (st.getBlock() instanceof MultiblockPartBlock) return PcBlock.structureController(level, MultiblockPartBlock.anchor(st, p));
        if (st.getBlock() instanceof PcBlock b && !b.spec().footprint().isSingle()) return b.controllerPos(st, p);
        return p;
    }

    private static boolean chamber(Level level, CokeBatteryShape s, int c) {
        for (int dy = 0; dy < HEIGHT; dy++)
            for (int l = 0; l < s.length; l++) if (!loadedOven(level, s.at(c, s.l0 + l, s.y0 + dy))) return false;
        for (int dy = 0; dy < HEIGHT; dy++) {
            if (loadedOven(level, s.at(c, s.l0 - 1, s.y0 + dy)) || loadedOven(level, s.at(c, s.l0 + s.length, s.y0 + dy)))
                return false;
        }
        return !loadedOven(level, s.at(c, s.l0, s.y0 - 1)) && !loadedOven(level, s.at(c, s.l0, s.y0 + HEIGHT));
    }

    private static boolean wall(Level level, CokeBatteryShape s, int c) {
        for (int dy = 0; dy < HEIGHT; dy++)
            for (int l = 0; l < s.length; l++) {
                BlockPos p = s.at(c, s.l0 + l, s.y0 + dy);
                if (!level.isLoaded(p) || !level.getBlockState(p).is(PcBlocks.COKE_BATTERY_WALL.get())) return false;
            }
        return true;
    }

    private static boolean isOven(Level level, BlockPos p) {
        return level.getBlockState(p).is(PcBlocks.COKE_OVEN.get());
    }

    private static boolean loadedOven(Level level, BlockPos p) {
        return level.isLoaded(p) && isOven(level, p);
    }
}
