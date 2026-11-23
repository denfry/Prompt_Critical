package dev.denfry.promptcritical.mod.environment;

import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.sim.radiation.NaturalBackground;
import dev.denfry.promptcritical.sim.radiation.NaturalBackground.Dimension;
import dev.denfry.promptcritical.sim.radiation.NaturalBackground.Ground;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;

/**
 * Природный фон в точке мира (04b §4b.13): земное γ от грунта, который игрок видит из 26 направлений куба 3×3×3,
 * плюс космическое с учётом высоты и толщи породы над головой. Заменяет константу {@link RadiationService#BACKGROUND_USV_H}
 * (она остаётся только запасным значением для документации). Результат кэшируется на игрока и пересчитывается лишь при
 * смене блока: 26 лучей по 12 шагов каждую секунду на каждого игрока — лишняя цена, а мир вокруг стоящего меняется редко.
 */
public final class BackgroundField {
    /** Лучи: 3×3×3 без центра, нормированные (04b §4b.13.1). */
    private static final Vec3[] RAYS = rays();
    /** Длина луча, блоков — по заданию B1 (дальше грунт уже не определяет угол обзора). [моё] */
    private static final double RAY_BLOCKS = 3;
    /** Шаг выборки вдоль луча, блоков: меньше блока, чтобы не проскочить грань по диагонали. [моё] */
    private static final double STEP = 0.25;
    /** Высота столба породы над головой, блоков = метров (04b §4b.13.2). */
    private static final int OVERBURDEN_MAX_M = 32;

    private static final Set<Block> DEEP = Set.of(Blocks.DEEPSLATE, Blocks.COBBLED_DEEPSLATE, Blocks.POLISHED_DEEPSLATE,
            Blocks.DEEPSLATE_BRICKS, Blocks.CRACKED_DEEPSLATE_BRICKS, Blocks.DEEPSLATE_TILES, Blocks.CRACKED_DEEPSLATE_TILES,
            Blocks.CHISELED_DEEPSLATE, Blocks.BASALT, Blocks.SMOOTH_BASALT, Blocks.POLISHED_BASALT, Blocks.TUFF,
            Blocks.POLISHED_TUFF, Blocks.TUFF_BRICKS, Blocks.CHISELED_TUFF, Blocks.CHISELED_TUFF_BRICKS);
    private static final Set<Block> SANDSTONE = Set.of(Blocks.SANDSTONE, Blocks.CUT_SANDSTONE, Blocks.CHISELED_SANDSTONE,
            Blocks.SMOOTH_SANDSTONE, Blocks.RED_SANDSTONE, Blocks.CUT_RED_SANDSTONE, Blocks.CHISELED_RED_SANDSTONE,
            Blocks.SMOOTH_RED_SANDSTONE);

    private record Cached(ResourceKey<Level> dimension, BlockPos pos, double svH) {
    }

    /** Только серверный поток. */
    private static final Map<UUID, Cached> CACHE = new HashMap<>();

    private BackgroundField() {
    }

    /** Фон в зивертах в час в блоке игрока; для неизвестного измерения — как Верхний мир. */
    public static double ambientSvH(ServerLevel level, ServerPlayer p) {
        BlockPos feet = p.blockPosition();
        ResourceKey<Level> dim = level.dimension();
        Cached c = CACHE.get(p.getUUID());
        if (c != null && c.pos.equals(feet) && c.dimension.equals(dim)) return c.svH;
        boolean[] gap = new boolean[1];
        double v = compute(level, p, feet, gap);
        // луч или столб дошли до незагруженного чанка: значение неполное, пересчитаем в следующий раз
        if (gap[0]) CACHE.remove(p.getUUID());
        else CACHE.put(p.getUUID(), new Cached(dim, feet.immutable(), v));
        return v;
    }

    /** Забыть кэш игрока (выход с сервера). */
    public static void forget(UUID player) {
        CACHE.remove(player);
    }

    /** Число игроков в кэше (для тестов). */
    public static int cacheSize() {
        return CACHE.size();
    }

    /** Грунт по первому совпадению: урановая руда, глубинные породы, камень, почва, песок, глина, остальное — ничто. */
    public static Ground ground(BlockState s) {
        if (s.isAir()) return Ground.NONE;
        if (s.is(PcBlocks.URANINITE_ORE.get()) || s.is(PcBlocks.DEEPSLATE_URANINITE_ORE.get())
                || s.is(PcBlocks.OKLO_URANINITE_ORE.get())) return Ground.URANIUM_ORE;
        if (s.is(BlockTags.DEEPSLATE_ORE_REPLACEABLES) || DEEP.contains(s.getBlock())) return Ground.DEEP;
        // не урановая руда (ванильная или чужая) излучает как порода, в которой лежит: deepslate_* — глубинная
        if (s.is(Tags.Blocks.ORES))
            return BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath().startsWith("deepslate_") ? Ground.DEEP : Ground.STONE;
        if (s.is(BlockTags.BASE_STONE_OVERWORLD)) return Ground.STONE;
        if (s.is(BlockTags.DIRT) || s.is(Blocks.MUD)) return Ground.SOIL;
        if (s.is(BlockTags.SAND) || SANDSTONE.contains(s.getBlock())) return Ground.SAND;
        if (s.is(Blocks.CLAY) || s.is(BlockTags.TERRACOTTA)) return Ground.CLAY;
        return Ground.NONE;
    }

    private static double compute(ServerLevel level, ServerPlayer p, BlockPos feet, boolean[] gap) {
        Vec3 origin = Vec3.atCenterOf(feet);
        Map<Ground, Double> fractions = new EnumMap<>(Ground.class);
        double w = 1.0 / RAYS.length;
        for (Vec3 dir : RAYS) fractions.merge(firstGround(level, origin, dir, feet, gap), w, Double::sum);
        double terrestrial = NaturalBackground.terrestrialNGyH(fractions);
        double cosmic = NaturalBackground.cosmicUSvH(dimension(level), p.getY(), overburden(level, feet, gap));
        return NaturalBackground.ambientUSvH(terrestrial, cosmic) * 1e-6;
    }

    /** Грунт первого непустого блока на луче (блок самого игрока не считается); луч в пустоту — {@link Ground#NONE}. */
    private static Ground firstGround(ServerLevel level, Vec3 origin, Vec3 dir, BlockPos feet, boolean[] gap) {
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (double t = STEP; t <= RAY_BLOCKS; t += STEP) {
            at.set(origin.x + dir.x * t, origin.y + dir.y * t, origin.z + dir.z * t);
            if (at.equals(feet) || level.isOutsideBuildHeight(at)) continue;
            if (!level.hasChunkAt(at)) {
                gap[0] = true;
                continue;
            }
            BlockState s = level.getBlockState(at);
            if (!s.isAir()) return ground(s);
        }
        return Ground.NONE;
    }

    /** Сплошные блоки в столбе над головой, не выше {@value #OVERBURDEN_MAX_M}: 1 блок = 1 м. */
    private static double overburden(ServerLevel level, BlockPos feet, boolean[] gap) {
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        int solid = 0;
        for (int dy = 2; dy < 2 + OVERBURDEN_MAX_M; dy++) {
            at.set(feet.getX(), feet.getY() + dy, feet.getZ());
            if (level.isOutsideBuildHeight(at)) break;
            if (!level.hasChunkAt(at)) {
                gap[0] = true;
                break;
            }
            if (level.getBlockState(at).isSolidRender(level, at)) solid++;
        }
        return solid;
    }

    private static Dimension dimension(ServerLevel level) {
        ResourceKey<Level> k = level.dimension();
        if (k == Level.NETHER) return Dimension.NETHER;
        if (k == Level.END) return Dimension.END;
        return Dimension.OVERWORLD;
    }

    private static Vec3[] rays() {
        Vec3[] r = new Vec3[26];
        int i = 0;
        for (int x = -1; x <= 1; x++)
            for (int y = -1; y <= 1; y++)
                for (int z = -1; z <= 1; z++) if (x != 0 || y != 0 || z != 0) r[i++] = new Vec3(x, y, z).normalize();
        return r;
    }
}
