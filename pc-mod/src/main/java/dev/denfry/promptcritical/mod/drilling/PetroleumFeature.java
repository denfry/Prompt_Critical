package dev.denfry.promptcritical.mod.drilling;

import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.worldgen.VeinOreFeature;
import dev.denfry.promptcritical.sim.drilling.FormationFluid;
import dev.denfry.promptcritical.sim.drilling.PetroleumField;
import dev.denfry.promptcritical.sim.drilling.Trap;
import dev.denfry.promptcritical.sim.mining.WorldHash;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Нефтегазоносный разрез в чанке по {@link PetroleumField}. Настоящая ловушка лежит на 0,6–2,5 км, ниже дна мира, и
 * живёт в симуляции: её вскрывает буровая, а видит сейсморазведка. Блоками мир показывает её модель в глубинном
 * сланце (−58…−44): под замкнутым контуром купол коллектора толщиной 3 блока под покрышкой из 2 блоков глины, с
 * вертикальным преувеличением — свод на 8 блоков выше точки перелива. Песчаник газовый, нефтяной или водоносный — по
 * флюиду ловушки на соответствующей настоящей глубине. В покрышке изредка — пластовый барит (осадочные баритовые
 * линзы в глинистых толщах — реальный тип месторождений, источник утяжелителя). Над мелкими (до 1200 м) залежами
 * на поверхность выходят нефтепроявления: мелкие ловушки текут вверх по трещинам покрышки (Selley &amp; Sonnenberg,
 * гл. 5, «Seepages»). Пишет только в свой чанк.
 */
public final class PetroleumFeature extends Feature<NoneFeatureConfiguration> {
    /** Отметка модели точки перелива и подъём свода, блоки. */
    public static final int MODEL_SPILL_Y = -52, MODEL_RELIEF = 8, RESERVOIR_BLOCKS = 3, CAP_BLOCKS = 2;
    /** Наибольшая глубина свода, над которой бывают нефтепроявления, м. */
    public static final double SEEP_MAX_DEPTH_M = 1200;
    private static final int SALT_BARITE = 0xBA41, SALT_SEEP = 0x5EE9;
    private static final double BARITE_SHARE = 0.03, SEEP_SHARE = 0.004, SEEP_RADIUS = 0.35;

    public PetroleumFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel level = ctx.level();
        long seed = level.getSeed();
        ChunkPos chunk = new ChunkPos(ctx.origin());
        Trap t = PetroleumFields.get(seed).trapAt(chunk.getMiddleBlockX(), chunk.getMiddleBlockZ());
        if (t == null) return false;
        return fill(level, seed, t, chunk.getMinBlockX(), chunk.getMinBlockZ(), chunk.getMaxBlockX(), chunk.getMaxBlockZ()) > 0;
    }

    /** Разрез ловушки в прямоугольнике (включительно); генератор и тесты. Возвращает число поставленных блоков. */
    public static int fill(WorldGenLevel level, long seed, Trap t, int x0, int z0, int x1, int z1) {
        int placed = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        boolean seeps = t.fluid().hydrocarbon() && t.crestDepthM() <= SEEP_MAX_DEPTH_M;
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++) {
                double r = Math.hypot(x + 0.5 - t.crestX(), z + 0.5 - t.crestZ()) / t.radiusM();
                if (r > 1) continue;
                double top = t.topDepthM(x + 0.5, z + 0.5);
                int yTop = MODEL_SPILL_Y + (int) Math.round(MODEL_RELIEF * (1 - r * r));
                for (int k = 0; k < RESERVOIR_BLOCKS + CAP_BLOCKS; k++) {
                    int y = yTop - RESERVOIR_BLOCKS + 1 + k;
                    pos.set(x, y, z);
                    if (VeinOreFeature.host(level.getBlockState(pos)) == null) continue;
                    BlockState s;
                    if (k < RESERVOIR_BLOCKS) {
                        double depth = top + t.thicknessM() * (RESERVOIR_BLOCKS - 1 - k + 0.5) / RESERVOIR_BLOCKS;
                        s = sandstone(t.fluidAt(depth));
                    } else {
                        s = WorldHash.unit(seed, x, y, z, SALT_BARITE) < BARITE_SHARE ? PcBlocks.BARITE_ORE.get().defaultBlockState()
                                : PcBlocks.CAP_ROCK_SHALE.get().defaultBlockState();
                    }
                    level.setBlock(pos, s, Block.UPDATE_CLIENTS);
                    placed++;
                }
                if (seeps && r < SEEP_RADIUS && WorldHash.unit(seed, x, 0, z, SALT_SEEP) < SEEP_SHARE) placed += seep(level, pos, x, z);
            }
        return placed;
    }

    private static int seep(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z) {
        int y = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
        pos.set(x, y, z);
        if (!level.getBlockState(pos).isAir()) return 0;
        BlockState below = level.getBlockState(pos.below());
        if (!below.isFaceSturdy(level, pos.below(), net.minecraft.core.Direction.UP) || !below.getFluidState().isEmpty()) return 0;
        level.setBlock(pos, PcBlocks.OIL_SEEP.get().defaultBlockState(), Block.UPDATE_CLIENTS);
        return 1;
    }

    static BlockState sandstone(FormationFluid f) {
        return switch (f) {
            case GAS -> PcBlocks.RESERVOIR_SANDSTONE_GAS.get().defaultBlockState();
            case OIL -> PcBlocks.RESERVOIR_SANDSTONE.get().defaultBlockState();
            case WATER -> PcBlocks.RESERVOIR_SANDSTONE_WATER.get().defaultBlockState();
        };
    }
}
