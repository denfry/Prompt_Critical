package dev.denfry.promptcritical.mod.environment;

import dev.denfry.promptcritical.mod.config.ServerConfig;
import dev.denfry.promptcritical.sim.environment.Environment;
import dev.denfry.promptcritical.sim.environment.SeasonProvider;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
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
        // осадки — в точке, под открытым небом над ней: моды с местной погодой работают сами (16 §16.10.3)
        // (снегопад isRainingAt не считает, а для облачности, ветра и влажности он такие же осадки)
        BlockPos top = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, pos);
        boolean rain = level.isRainingAt(top)
                || level.isRaining() && biome.value().getPrecipitationAt(top) == Biome.Precipitation.SNOW;
        return environment(level).sample(level.getGameTime(), pos.getY(), heightM, Environment.roughness(BiomeEnvironment.surfaceOf(biome)),
                Environment.climateC(biome.value().getBaseTemperature()), rain, rain && level.isThundering());
    }

    /** Температура воды реки или пруда у {@code pos}, °C. */
    public static double waterTempC(ServerLevel level, BlockPos pos) {
        return environment(level).waterTempC(level.getGameTime(),
                Environment.climateC(level.getBiome(pos).value().getBaseTemperature()));
    }

    /** Средняя годовая температура климата в точке, К: столько держит порода под ней. */
    public static double climateK(ServerLevel level, BlockPos pos) {
        return Environment.climateC(level.getBiome(pos).value().getBaseTemperature()) + 273.15;
    }
}
