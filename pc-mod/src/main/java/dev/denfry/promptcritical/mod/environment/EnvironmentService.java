package dev.denfry.promptcritical.mod.environment;

import dev.denfry.promptcritical.mod.config.ServerConfig;
import dev.denfry.promptcritical.sim.environment.Environment;
import dev.denfry.promptcritical.sim.environment.SeasonProvider;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;

/**
 * Погода измерения для машин: {@link Environment} с зерном мира и измерения, климат и шероховатость — по биому
 * точки, дождь и гроза — ванильные, время года — Serene Seasons, если стоит, иначе встроенный календарь. Модель — чистая функция, поэтому кеш по уровню хранит только сам объект.
 */
public final class EnvironmentService {
    private static final Map<ServerLevel, Environment> ENVS = new WeakHashMap<>();

    private EnvironmentService() {
    }

    public static Environment environment(ServerLevel level) {
        return ENVS.computeIfAbsent(level, l -> new Environment(l.getSeed() ^ l.dimension().location().hashCode() * 0x9E3779B97F4A7C15L,
                SereneSeasonsAdapter.forLevel(l, new SeasonProvider.Calendar(ServerConfig.ENVIRONMENT_DAYS_PER_SEASON.get()))));
    }

    /** Погода в точке на высоте {@code heightM} над ней. */
    public static Environment.Sample sample(ServerLevel level, BlockPos pos, double heightM) {
        Holder<Biome> biome = level.getBiome(pos);
        return environment(level).sample(level.getGameTime(), pos.getY(), heightM, roughness(biome),
                Environment.climateC(biome.value().getBaseTemperature()), level.isRaining() && biome.value().hasPrecipitation(),
                level.isThundering() && biome.value().hasPrecipitation());
    }

    /** Средняя годовая температура климата в точке, К: столько держит порода под ней. */
    public static double climateK(ServerLevel level, BlockPos pos) {
        return Environment.climateC(level.getBiome(pos).value().getBaseTemperature()) + 273.15;
    }

    private static double roughness(Holder<Biome> b) {
        Environment.Surface s;
        if (b.is(BiomeTags.IS_OCEAN) || b.is(BiomeTags.IS_DEEP_OCEAN) || b.is(BiomeTags.IS_RIVER) || b.is(BiomeTags.IS_BEACH)) {
            s = Environment.Surface.WATER;
        } else if (b.is(BiomeTags.IS_FOREST) || b.is(BiomeTags.IS_TAIGA) || b.is(BiomeTags.IS_JUNGLE)) {
            s = Environment.Surface.FOREST;
        } else if (b.is(BiomeTags.IS_HILL) || b.is(BiomeTags.IS_MOUNTAIN) || b.is(BiomeTags.IS_SAVANNA)) {
            s = Environment.Surface.SHRUB;
        } else {
            s = Environment.Surface.OPEN;
        }
        return Environment.roughness(s);
    }
}
