package dev.denfry.promptcritical.mod.environment;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.sim.environment.Environment;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;

/**
 * Свойства биома для слоя среды (16 §16.10.3): шероховатость поверхности, признак реки и моря, глубина для прилива.
 * Чужие биомы описываются в таком порядке: карта данных {@code promptcritical:biome_environment} (её дополняет
 * любой мод или сборка), теги {@code c:is_*}, ванильные {@code minecraft:is_*}, иначе луг. Одних тегов мало:
 * Terralith не кладёт свои биомы в {@code c:is_ocean}, а Biomes O' Plenty — в {@code c:is_river} и {@code c:is_forest}.
 *
 * @param surface поверхность для шероховатости z₀; пусто — по тегам
 * @param water вода биома; пусто — по тегам
 */
public record BiomeEnvironment(Optional<Environment.Surface> surface, Optional<Water> water) {
    /** Вода биома: от неё зависят водосбор реки и глубина для прилива. */
    public enum Water implements StringRepresentable {
        NONE, RIVER, COAST, OCEAN, DEEP_OCEAN;

        public static final Codec<Water> CODEC = StringRepresentable.fromEnum(Water::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }

        public boolean sea() {
            return this == OCEAN || this == DEEP_OCEAN;
        }
    }

    private static final Codec<Environment.Surface> SURFACE_CODEC = Codec.STRING.xmap(
            s -> Environment.Surface.valueOf(s.toUpperCase(Locale.ROOT)), s -> s.name().toLowerCase(Locale.ROOT));

    public static final Codec<BiomeEnvironment> CODEC = RecordCodecBuilder.create(i -> i.group(
            SURFACE_CODEC.optionalFieldOf("surface").forGetter(BiomeEnvironment::surface),
            Water.CODEC.optionalFieldOf("water").forGetter(BiomeEnvironment::water)).apply(i, BiomeEnvironment::new));

    public static final DataMapType<Biome, BiomeEnvironment> TYPE =
            DataMapType.builder(PromptCritical.id("biome_environment"), Registries.BIOME, CODEC).build();

    private static final TagKey<Biome> C_RIVER = common("is_river"), C_OCEAN = common("is_ocean"),
            C_DEEP_OCEAN = common("is_deep_ocean"), C_SHALLOW_OCEAN = common("is_shallow_ocean"), C_BEACH = common("is_beach"),
            C_FOREST = common("is_forest"), C_JUNGLE = common("is_jungle"), C_TAIGA = common("is_taiga"),
            C_MOUNTAIN = common("is_mountain"), C_HILL = common("is_hill"), C_SAVANNA = common("is_savanna");

    public static void register(RegisterDataMapTypesEvent e) {
        e.register(TYPE);
    }

    /** Поверхность биома для шероховатости z₀. */
    public static Environment.Surface surfaceOf(Holder<Biome> b) {
        BiomeEnvironment d = b.getData(TYPE);
        if (d != null && d.surface.isPresent()) return d.surface.get();
        if (waterOf(b) != Water.NONE || b.is(C_BEACH) || b.is(BiomeTags.IS_BEACH)) return Environment.Surface.WATER;
        if (b.is(C_FOREST) || b.is(C_JUNGLE) || b.is(C_TAIGA) || b.is(BiomeTags.IS_FOREST) || b.is(BiomeTags.IS_TAIGA)
                || b.is(BiomeTags.IS_JUNGLE)) return Environment.Surface.FOREST;
        if (b.is(C_MOUNTAIN) || b.is(C_HILL) || b.is(C_SAVANNA) || b.is(BiomeTags.IS_HILL)
                || b.is(BiomeTags.IS_MOUNTAIN) || b.is(BiomeTags.IS_SAVANNA)) return Environment.Surface.SHRUB;
        return Environment.Surface.OPEN;
    }

    /** Вода биома: река, море, глубокое море или нет. */
    public static Water waterOf(Holder<Biome> b) {
        BiomeEnvironment d = b.getData(TYPE);
        if (d != null && d.water.isPresent()) return d.water.get();
        if (b.is(C_DEEP_OCEAN) || b.is(BiomeTags.IS_DEEP_OCEAN)) return Water.DEEP_OCEAN;
        if (b.is(C_OCEAN) || b.is(C_SHALLOW_OCEAN) || b.is(BiomeTags.IS_OCEAN)) return Water.OCEAN;
        if (b.is(C_RIVER) || b.is(BiomeTags.IS_RIVER)) return Water.RIVER;
        return Water.NONE;
    }

    private static TagKey<Biome> common(String path) {
        return TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath("c", path));
    }
}
