package dev.denfry.promptcritical.mod.worldgen;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.sim.mining.VeinBody;
import dev.denfry.promptcritical.sim.mining.VeinField;
import dev.denfry.promptcritical.sim.mining.VeinHit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import org.jetbrains.annotations.Nullable;

/**
 * Смешанные жилы и угольные пласты (08 §8.1) в чанке: тела {@link VeinField}, задевающие чанк, замещают вмещающую
 * породу (камень, глубинный сланец, туф — свойство {@code host}); воздух, вода и пещерный декор не трогаются.
 * Пишет только в свой чанк, поэтому результат не зависит от порядка генерации соседей.
 */
public final class VeinOreFeature extends Feature<NoneFeatureConfiguration> {
    public VeinOreFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel level = ctx.level();
        VeinField field = VeinFields.get(level.getSeed());
        if (!field.config().enabled()) return false;
        ChunkPos chunk = new ChunkPos(ctx.origin());
        return fill(level, field, chunk.getMinBlockX(), chunk.getMinBlockZ(), chunk.getMaxBlockX(), chunk.getMaxBlockZ(),
                level.getMinBuildHeight(), level.getMaxBuildHeight() - 1, VeinFields.biomes(level.getLevel())) > 0;
    }

    /**
     * Заместить породу в прямоугольнике (включительно); генератор и тесты.
     *
     * @return число поставленных рудных блоков
     */
    public static int fill(WorldGenLevel level, VeinField field, int x0, int z0, int x1, int z1, int yMin, int yMax,
            VeinField.BiomeProbe biomes) {
        VeinField.Sampler s = field.sampler(x0, z0, x1, z1, biomes);
        int placed = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < s.bodyCount(); i++) {
            VeinBody b = s.body(i);
            int bx0 = Math.max(x0, b.minX()), bx1 = Math.min(x1, b.maxX());
            int bz0 = Math.max(z0, b.minZ()), bz1 = Math.min(z1, b.maxZ());
            int by0 = Math.max(yMin, b.minY()), by1 = Math.min(yMax, b.maxY());
            for (int x = bx0; x <= bx1; x++)
                for (int z = bz0; z <= bz1; z++)
                    for (int y = by0; y <= by1; y++) {
                        VeinHit hit = s.sample(x, y, z);
                        if (hit == null || hit.body() != b) continue;
                        pos.set(x, y, z);
                        PcProperties.OreHost host = host(level.getBlockState(pos));
                        if (host == null) continue;
                        BlockState ore = oreState(hit, host, field, x, y, z);
                        if (ore == null) continue;
                        level.setBlock(pos, ore, Block.UPDATE_CLIENTS);
                        placed++;
                    }
        }
        return placed;
    }

    /** Вмещающая порода или {@code null}, если блок не замещается. */
    @Nullable
    public static PcProperties.OreHost host(BlockState s) {
        if (s.is(Blocks.STONE) || s.is(Blocks.GRANITE) || s.is(Blocks.DIORITE) || s.is(Blocks.ANDESITE)) return PcProperties.OreHost.STONE;
        if (s.is(Blocks.DEEPSLATE)) return PcProperties.OreHost.DEEPSLATE;
        if (s.is(Blocks.TUFF)) return PcProperties.OreHost.TUFF;
        return null;
    }

    /** Блок рудной роли: руды 08 — со свойством {@code host}, руды 02 — {@code deepslate_*} в глубинном сланце. */
    @Nullable
    public static BlockState oreState(VeinHit hit, PcProperties.OreHost host, VeinField field, int x, int y, int z) {
        if (hit.coal()) {
            PcProperties.CoalRank rank = switch (field.seamRank(x, y, z)) {
                case LIGNITE -> PcProperties.CoalRank.LIGNITE;
                case ANTHRACITE -> PcProperties.CoalRank.ANTHRACITE;
                case BITUMINOUS -> PcProperties.CoalRank.BITUMINOUS;
            };
            return PcBlocks.COAL_SEAM.get().defaultBlockState().setValue(PcProperties.HOST, host).setValue(PcProperties.RANK, rank);
        }
        String mineral = hit.mineral();
        if (host == PcProperties.OreHost.DEEPSLATE) {
            Block deep = BuiltInRegistries.BLOCK.get(PromptCritical.id("deepslate_" + mineral));
            if (deep != Blocks.AIR) return deep.defaultBlockState();
        }
        Block b = BuiltInRegistries.BLOCK.get(PromptCritical.id(mineral));
        if (b == Blocks.AIR) return null;
        BlockState st = b.defaultBlockState();
        return st.hasProperty(PcProperties.HOST) ? st.setValue(PcProperties.HOST, host) : st;
    }
}
