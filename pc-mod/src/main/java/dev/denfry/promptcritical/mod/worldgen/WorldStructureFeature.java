package dev.denfry.promptcritical.mod.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Структуры мира 11 §11.4.2 — небольшие постройки, которые генератор собирает кодом в пределах чанка (без NBT-шаблонов):
 * лагерь геологов, старая угольная шахта, урановый рудник, асфальтовое озеро, горящий газовый кратер, маяк с
 * разрушенным РИТЭГом, метеостанция, руины НИИ, разъезд с цистернами, природный реактор. Лут — таблицы
 * {@code promptcritical:chests/<вид>}: информация и ранние детали, без готовых машин и источников (C5) — кроме маяка, где
 * бесхозный источник и есть опасность. Нефтепроявления ставит {@code PetroleumFeature}. Частоты и биомы —
 * {@code worldgen/placed_feature} и модификаторы биомов датапака [BALANCE].
 */
public final class WorldStructureFeature extends Feature<WorldStructureFeature.Config> {
    public enum Kind implements StringRepresentable {
        SURVEY_CAMP, OLD_COAL_MINE, URANIUM_MINE, PITCH_LAKE, GAS_CRATER, RTG_LIGHTHOUSE, WEATHER_STATION, INSTITUTE_RUIN,
        RAILWAY_HALT, NATURAL_REACTOR;

        public static final Codec<Kind> CODEC = StringRepresentable.fromEnum(Kind::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }

        public ResourceKey<LootTable> loot() {
            return ResourceKey.create(Registries.LOOT_TABLE, PromptCritical.id("chests/" + getSerializedName()));
        }
    }

    public record Config(Kind kind) implements FeatureConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(i -> i.group(
                Kind.CODEC.fieldOf("kind").forGetter(Config::kind)).apply(i, Config::new));
    }

    public WorldStructureFeature() {
        super(Config.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<Config> ctx) {
        return build(ctx.level(), ctx.config().kind(), ctx.origin(), ctx.random());
    }

    /** Построить структуру с центром у {@code origin} (на поверхности; природный реактор — глубоко под ним). */
    public static boolean build(WorldGenLevel level, Kind kind, BlockPos origin, RandomSource r) {
        int x = origin.getX(), z = origin.getZ();
        // при генерации карта высот — WG; в живом мире (команды, тесты) — обычная
        Heightmap.Types surface = level instanceof net.minecraft.server.level.WorldGenRegion ? Heightmap.Types.WORLD_SURFACE_WG
                : Heightmap.Types.WORLD_SURFACE;
        int y = level.getHeight(surface, x, z);
        return buildAt(level, kind, new BlockPos(x, y, z), r);
    }

    /** Построить структуру на поверхности {@code base} (первый блок воздуха над грунтом). */
    public static boolean buildAt(WorldGenLevel level, Kind kind, BlockPos base, RandomSource r) {
        if (kind != Kind.NATURAL_REACTOR && kind != Kind.OLD_COAL_MINE && kind != Kind.URANIUM_MINE
                && !level.getBlockState(base.below()).getFluidState().isEmpty()) return false;
        return switch (kind) {
            case SURVEY_CAMP -> surveyCamp(level, base, r);
            case OLD_COAL_MINE -> mine(level, base, r, Blocks.COAL_ORE.defaultBlockState(), Blocks.DEEPSLATE_COAL_ORE.defaultBlockState(),
                    kind);
            case URANIUM_MINE -> mine(level, base, r, PcBlocks.URANINITE_ORE.get().defaultBlockState(),
                    PcBlocks.DEEPSLATE_URANINITE_ORE.get().defaultBlockState(), kind);
            case PITCH_LAKE -> pitchLake(level, base, r);
            case GAS_CRATER -> gasCrater(level, base, r);
            case RTG_LIGHTHOUSE -> lighthouse(level, base, r);
            case WEATHER_STATION -> hut(level, base, r, kind, Blocks.SPRUCE_PLANKS.defaultBlockState());
            case INSTITUTE_RUIN -> ruin(level, base, r);
            case RAILWAY_HALT -> railwayHalt(level, base, r);
            case NATURAL_REACTOR -> naturalReactor(level, base, r);
        };
    }

    // ------------------------------------------------------------------ помощники

    private static void set(WorldGenLevel level, BlockPos p, BlockState s) {
        level.setBlock(p, s, Block.UPDATE_CLIENTS);
    }

    private static void chest(WorldGenLevel level, BlockPos p, RandomSource r, Kind kind, Direction facing) {
        set(level, p, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing));
        RandomizableContainer.setBlockEntityLootTable(level, r, p, kind.loot());
    }

    /** Площадка: под ней — грунт, над ней — воздух на {@code h} блоков. */
    private static void pad(WorldGenLevel level, BlockPos base, int rx, int rz, int h, BlockState floor) {
        for (int dx = -rx; dx <= rx; dx++)
            for (int dz = -rz; dz <= rz; dz++) {
                set(level, base.offset(dx, -1, dz), floor);
                for (int dy = 0; dy < h; dy++) set(level, base.offset(dx, dy, dz), Blocks.AIR.defaultBlockState());
            }
    }

    // ------------------------------------------------------------------ виды

    /** Лагерь геологоразведочной партии: палатка, погасший костёр, ящик с кернами и картой. */
    private static boolean surveyCamp(WorldGenLevel level, BlockPos b, RandomSource r) {
        pad(level, b, 3, 3, 4, Blocks.COARSE_DIRT.defaultBlockState());
        BlockState canvas = Blocks.WHITE_WOOL.defaultBlockState();
        for (int dz = -1; dz <= 1; dz++) {
            set(level, b.offset(-1, 0, dz), canvas);
            set(level, b.offset(1, 0, dz), canvas);
            set(level, b.offset(0, 1, dz), canvas);
        }
        set(level, b.offset(0, 0, 1), Blocks.WHITE_WOOL.defaultBlockState());
        chest(level, b.offset(0, 0, 0), r, Kind.SURVEY_CAMP, Direction.NORTH);
        set(level, b.offset(0, 0, -3), Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false));
        set(level, b.offset(2, 0, -2), Blocks.BARREL.defaultBlockState());
        return true;
    }

    /**
     * Старая шахта: ствол с лестницей на {@value #MINE_DEPTH} блоков, внизу штрек с гнилой крепью; в стенах — пласт
     * (уголь или урановая смолка). Угольный пласт при отбойке в шахтной сети отдаёт метан (08).
     */
    static final int MINE_DEPTH = 14;

    private static boolean mine(WorldGenLevel level, BlockPos b, RandomSource r, BlockState ore, BlockState deepOre, Kind kind) {
        BlockState air = Blocks.AIR.defaultBlockState(), post = Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState(),
                beam = Blocks.DARK_OAK_PLANKS.defaultBlockState();
        for (int dy = -MINE_DEPTH; dy <= 2; dy++) {
            set(level, b.offset(0, dy, 0), air);
            set(level, b.offset(1, dy, 0), Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.WEST));
            set(level, b.offset(-1, dy, 0), Blocks.COBBLESTONE.defaultBlockState());
        }
        set(level, b.offset(0, 3, 0), Blocks.OAK_FENCE.defaultBlockState());
        BlockPos floor = b.offset(0, -MINE_DEPTH, 0);
        for (int dz = 1; dz <= 9; dz++) {
            for (int dx = -1; dx <= 1; dx++) for (int dy = 0; dy <= 2; dy++) set(level, floor.offset(dx, dy, dz), air);
            if (dz % 3 == 0 && r.nextFloat() < 0.75f) { // крепь — местами обрушена
                set(level, floor.offset(-1, 0, dz), post);
                set(level, floor.offset(-1, 1, dz), post);
                set(level, floor.offset(1, 0, dz), post);
                set(level, floor.offset(1, 1, dz), post);
                for (int dx = -1; dx <= 1; dx++) set(level, floor.offset(dx, 2, dz), beam);
            } else if (dz % 3 == 0) {
                set(level, floor.offset(0, 0, dz), Blocks.GRAVEL.defaultBlockState());
            }
            BlockState wallOre = floor.getY() < 0 ? deepOre : ore;
            for (int dx : new int[] {-2, 2}) for (int dy = 0; dy <= 2; dy++) if (r.nextFloat() < 0.5f) set(level, floor.offset(dx, dy, dz), wallOre);
            if (kind == Kind.URANIUM_MINE && dz <= 7)
                set(level, floor.offset(0, 0, dz), Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE, RailShape.NORTH_SOUTH));
        }
        chest(level, floor.offset(1, 0, 9), r, kind, Direction.WEST);
        return true;
    }

    /** Асфальтовое озеро: чаша природного битума с нефтепроявлениями по краю. */
    private static boolean pitchLake(WorldGenLevel level, BlockPos b, RandomSource r) {
        int rx = 4 + r.nextInt(2), rz = 3 + r.nextInt(2);
        for (int dx = -rx - 1; dx <= rx + 1; dx++)
            for (int dz = -rz - 1; dz <= rz + 1; dz++) {
                double e = (double) dx * dx / (rx * rx) + (double) dz * dz / (rz * rz);
                if (e > 1.3) continue;
                BlockPos p = b.offset(dx, -1, dz);
                if (e <= 1) {
                    set(level, p, PcBlocks.NATURAL_BITUMEN.get().defaultBlockState());
                    if (e < 0.4) set(level, p.below(), PcBlocks.NATURAL_BITUMEN.get().defaultBlockState());
                    set(level, p.above(), Blocks.AIR.defaultBlockState());
                } else if (r.nextFloat() < 0.25f) {
                    set(level, p.above(), PcBlocks.OIL_SEEP.get().defaultBlockState());
                }
            }
        return true;
    }

    /** Горящий газовый кратер: чаша с выходами газа в середине. */
    private static boolean gasCrater(WorldGenLevel level, BlockPos b, RandomSource r) {
        int rad = 5;
        for (int dx = -rad; dx <= rad; dx++)
            for (int dz = -rad; dz <= rad; dz++) {
                double d = Math.hypot(dx, dz);
                if (d > rad) continue;
                int depth = (int) Math.round(3 * (1 - d / rad));
                for (int dy = -depth; dy <= 3; dy++) set(level, b.offset(dx, dy, dz), Blocks.AIR.defaultBlockState());
                BlockPos floor = b.offset(dx, -depth - 1, dz);
                set(level, floor, d < 1.8 ? PcBlocks.GAS_SEEP_VENT.get().defaultBlockState()
                        : r.nextFloat() < 0.3f ? Blocks.BLACKSTONE.defaultBlockState() : Blocks.TERRACOTTA.defaultBlockState());
            }
        return true;
    }

    /**
     * Маяк на холодном берегу: башня из потрескавшегося кирпича, у подножия — вскрытый кожух РИТЭГа и ящик, где лежит
     * брошенный Sr-90 источник тепла с отметкой «бесхозный» (12 §12.3.3): он излучает.
     */
    private static boolean lighthouse(WorldGenLevel level, BlockPos b, RandomSource r) {
        pad(level, b, 3, 3, 10, Blocks.STONE.defaultBlockState());
        for (int dy = 0; dy < 8; dy++)
            for (int dx = -1; dx <= 1; dx++)
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    set(level, b.offset(dx, dy, dz), r.nextFloat() < 0.3f ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState()
                            : Blocks.STONE_BRICKS.defaultBlockState());
                }
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) set(level, b.offset(dx, 8, dz), Blocks.GLASS.defaultBlockState());
        set(level, b.offset(0, 9, 0), Blocks.LANTERN.defaultBlockState());
        set(level, b.offset(2, 0, 0), Blocks.IRON_BARS.defaultBlockState());
        set(level, b.offset(2, 0, 1), Blocks.CAULDRON.defaultBlockState());
        chest(level, b.offset(2, 0, -1), r, Kind.RTG_LIGHTHOUSE, Direction.EAST);
        return true;
    }

    /** Будка: метеостанция (анемометр на мачте, журнал погоды). */
    private static boolean hut(WorldGenLevel level, BlockPos b, RandomSource r, Kind kind, BlockState wall) {
        pad(level, b, 2, 2, 5, Blocks.COBBLESTONE.defaultBlockState());
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++) {
                boolean edge = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                for (int dy = 0; dy < 3; dy++) if (edge && !(dx == 0 && dz == -2 && dy < 2)) set(level, b.offset(dx, dy, dz), wall);
                set(level, b.offset(dx, 3, dz), Blocks.SPRUCE_SLAB.defaultBlockState());
            }
        chest(level, b.offset(1, 0, 1), r, kind, Direction.NORTH);
        for (int dy = 4; dy <= 7; dy++) set(level, b.offset(-2, dy, -2), Blocks.OAK_FENCE.defaultBlockState());
        set(level, b.offset(-2, 8, -2), Blocks.LIGHTNING_ROD.defaultBlockState());
        return true;
    }

    /** Руины отраслевого НИИ: обвалившиеся стены и два шкафа с ранней электроникой. */
    private static boolean ruin(WorldGenLevel level, BlockPos b, RandomSource r) {
        pad(level, b, 5, 4, 5, Blocks.STONE_BRICKS.defaultBlockState());
        for (int dx = -5; dx <= 5; dx++)
            for (int dz = -4; dz <= 4; dz++) {
                if (Math.abs(dx) != 5 && Math.abs(dz) != 4) continue;
                int h = r.nextInt(4);
                for (int dy = 0; dy < h; dy++)
                    set(level, b.offset(dx, dy, dz), r.nextFloat() < 0.4f ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState()
                            : Blocks.STONE_BRICKS.defaultBlockState());
            }
        chest(level, b.offset(-3, 0, 2), r, Kind.INSTITUTE_RUIN, Direction.SOUTH);
        chest(level, b.offset(3, 0, -2), r, Kind.INSTITUTE_RUIN, Direction.NORTH);
        set(level, b.offset(0, 0, 0), Blocks.CRAFTING_TABLE.defaultBlockState());
        return true;
    }

    /** Разъезд: путь с двумя пустыми цистернами и будкой стрелочника. */
    private static boolean railwayHalt(WorldGenLevel level, BlockPos b, RandomSource r) {
        pad(level, b, 6, 2, 4, Blocks.GRAVEL.defaultBlockState());
        for (int dx = -6; dx <= 6; dx++) set(level, b.offset(dx, 0, 0), Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE, RailShape.EAST_WEST));
        for (int[] tank : new int[][] {{-4, -2}, {1, 3}})
            for (int dx = tank[0]; dx <= tank[1]; dx++) {
                set(level, b.offset(dx, 1, 0), Blocks.IRON_BLOCK.defaultBlockState());
                set(level, b.offset(dx, 2, 0), Blocks.SMOOTH_STONE_SLAB.defaultBlockState());
            }
        chest(level, b.offset(0, 0, 2), r, Kind.RAILWAY_HALT, Direction.NORTH);
        return true;
    }

    /**
     * Природный реактор: под небольшим телом урановой смолки на −40…−34 — линза руды с обеднённым U-235. С виду руда
     * обычная; заметить её можно только по анализу (станция пробирного анализа).
     */
    private static boolean naturalReactor(WorldGenLevel level, BlockPos b, RandomSource r) {
        BlockPos c = new BlockPos(b.getX(), -38, b.getZ());
        BlockState oklo = PcBlocks.OKLO_URANINITE_ORE.get().defaultBlockState(),
                ore = PcBlocks.DEEPSLATE_URANINITE_ORE.get().defaultBlockState();
        int placed = 0;
        for (int dx = -4; dx <= 4; dx++)
            for (int dz = -4; dz <= 4; dz++)
                for (int dy = -2; dy <= 4; dy++) {
                    double e = dx * dx / 16.0 + dz * dz / 16.0 + dy * dy / (dy < 0 ? 4.0 : 9.0);
                    if (e > 1) continue;
                    BlockPos p = c.offset(dx, dy, dz);
                    if (!level.getBlockState(p).is(Blocks.DEEPSLATE) && !level.getBlockState(p).is(Blocks.TUFF)) continue;
                    // ядро внизу линзы — «реакторная зона», выше — обычная руда тела
                    set(level, p, dy <= 0 ? oklo : r.nextFloat() < 0.6f ? ore : Blocks.DEEPSLATE.defaultBlockState());
                    placed++;
                }
        return placed > 0;
    }
}
