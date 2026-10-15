package dev.denfry.promptcritical.mod.worldgen;

import dev.denfry.promptcritical.mod.binding.SimConfigs;
import dev.denfry.promptcritical.sim.mining.BiomeClass;
import dev.denfry.promptcritical.sim.mining.VeinCatalog;
import dev.denfry.promptcritical.sim.mining.VeinConfig;
import dev.denfry.promptcritical.sim.mining.VeinField;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.neoforged.neoforge.common.Tags;

/**
 * {@link VeinField} мира (08 §8.1): одно на сид и набор ключей {@code worldgen.*}. Поле неизменяемо и
 * потокобезопасно, поэтому общее для генератора чанков, лута и шахты.
 */
public final class VeinFields {
    private record Cached(long seed, VeinField field) {
    }

    private static volatile Cached cached;

    private VeinFields() {
    }

    public static VeinField get(long seed) {
        VeinConfig cfg = SimConfigs.veins();
        Cached c = cached;
        if (c != null && c.seed == seed && c.field.config().equals(cfg)) return c.field;
        VeinField f = new VeinField(seed, cfg, VeinCatalog.M2);
        cached = new Cached(seed, f);
        return f;
    }

    /**
     * Классы биомов для весов жил: шумовой биом на уровне моря прямо из {@link BiomeSource}. Чанки не читаются:
     * центр ячейки жилы бывает дальше, чем разрешает {@code WorldGenRegion}, а тип жилы должен совпадать у
     * генератора, пробы и геологической карты независимо от того, сгенерирован ли чанк.
     */
    public static VeinField.BiomeProbe biomes(ServerLevel level) {
        BiomeSource source = level.getChunkSource().getGenerator().getBiomeSource();
        Climate.Sampler sampler = level.getChunkSource().randomState().sampler();
        int qy = QuartPos.fromBlock(level.getSeaLevel());
        return (x, z) -> classify(source.getNoiseBiome(QuartPos.fromBlock(x), qy, QuartPos.fromBlock(z), sampler));
    }

    static BiomeClass classify(Holder<Biome> b) {
        if (b.is(BiomeTags.IS_JUNGLE)) return BiomeClass.JUNGLE;
        if (b.is(BiomeTags.IS_SAVANNA)) return BiomeClass.SAVANNA;
        if (b.is(Tags.Biomes.IS_SWAMP)) return BiomeClass.SWAMP;
        if (b.is(BiomeTags.IS_MOUNTAIN) || b.is(Tags.Biomes.IS_MOUNTAIN)) return BiomeClass.MOUNTAINS;
        if (b.is(BiomeTags.IS_TAIGA)) return BiomeClass.TAIGA;
        if (b.is(Tags.Biomes.IS_SNOWY)) return BiomeClass.TUNDRA;
        if (b.is(Tags.Biomes.IS_PLAINS)) return BiomeClass.PLAINS;
        return BiomeClass.OTHER;
    }
}
