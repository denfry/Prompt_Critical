package dev.denfry.promptcritical.mod.environment;

import dev.denfry.promptcritical.mod.config.ServerConfig;
import dev.denfry.promptcritical.sim.environment.Tide;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

/**
 * Прилив в мире: уровень моря в точке по гармоникам {@link Tide} на игровом календаре (фазы Луны Minecraft — фаза 0
 * полнолуние, синодический месяц 8 суток), амплитуда — по биомам вокруг.
 *
 * <p><b>Уровень воды — только в модели, блоки воды не двигаются.</b> Прилив в пару метров у берега — это полосы в
 * десятки блоков осушки: переставлять их пришлось бы тысячами, по загруженным чанкам, с откатом при выгрузке и падении
 * сервера. Ванильная вода к тому же растекается и плодит источники, разрушая постройки, водоводы и контрапции Create
 * на берегу. Поэтому уровень моря — величина модели: его показывают мареограф и щит приливной станции, по нему
 * считается напор. Видимое море стоит на своём среднем уровне.
 *
 * <p>Амплитуда M2 в открытом океане — {@value #OCEAN_M2_M} м (порядок амплитуд глубоководной M2 по моделям
 * приливов, напр. TPXO; Pugh, Woodworth, «Sea-Level Science», 2014, гл. 5). К берегу она растёт по закону Грина
 * ({@link Tide#greensLaw}): глубина — по биому (глубокий океан {@value #DEEP_M} м, океан-шельф {@value #SHELF_M} м,
 * пляж, река, побережье {@value #COAST_M} м), ширина потока — доля морских биомов на кольце радиусом
 * {@value #FAR_RING} блоков (вход в залив) против доли воды на кольце {@value #NEAR_RING} блоков (горло у точки): узкий
 * залив, открытый в широкое море, усиливает прилив, как Бристольский залив или губы Белого моря. Если на дальнем кольце
 * моря нет — это озеро или река вдали от моря, прилива нет. Состав прилива — полусуточный, как у Бостона (NOAA).
 * Результат кешируется по клеткам 64×64 блока: биомы не меняются.
 */
public final class TideService {
    public static final double OCEAN_M2_M = 0.5, DEEP_M = 4000, SHELF_M = 100, COAST_M = 10;
    public static final int FAR_RING = 256, NEAR_RING = 32, RING_POINTS = 32, CELL = 64;

    /** Прилив в клетке: амплитуда M2, м; 0 — моря нет. */
    private record Cell(double m2, Tide tide) {
    }

    private static final Map<ServerLevel, Map<Long, Cell>> CACHE = new WeakHashMap<>();

    private TideService() {
    }

    /** Игровой календарь прилива: Луна Minecraft, год — календаря сезонов мода. */
    public static Tide.Calendar calendar() {
        return Tide.Calendar.game(4.0 * ServerConfig.ENVIRONMENT_DAYS_PER_SEASON.get());
    }

    /** Амплитуда M2 у {@code pos}, м; 0 — до моря далеко. */
    public static double m2AmplitudeM(ServerLevel level, BlockPos pos) {
        return cell(level, pos).m2();
    }

    /** Прилив в точке; {@code null} — моря нет. */
    public static Tide tide(ServerLevel level, BlockPos pos) {
        return cell(level, pos).tide();
    }

    /** Уровень моря над средним у {@code pos} сейчас, м; 0 — моря нет. */
    public static double seaLevelM(ServerLevel level, BlockPos pos) {
        Tide t = tide(level, pos);
        return t == null ? 0 : t.heightM(Tide.hoursFromDayTime(level.getDayTime()));
    }

    /** Фаза Луны Minecraft 0…7 (0 — полнолуние, 4 — новолуние). */
    public static int moonPhase(ServerLevel level) {
        return level.getMoonPhase();
    }

    private static Cell cell(ServerLevel level, BlockPos pos) {
        Map<Long, Cell> m = CACHE.computeIfAbsent(level, l -> new HashMap<>());
        long key = (((long) Math.floorDiv(pos.getX(), CELL)) << 32) ^ (Math.floorDiv(pos.getZ(), CELL) & 0xffffffffL);
        return m.computeIfAbsent(key, k -> compute(level, new BlockPos(Math.floorDiv(pos.getX(), CELL) * CELL + CELL / 2,
                pos.getY(), Math.floorDiv(pos.getZ(), CELL) * CELL + CELL / 2)));
    }

    private static Cell compute(ServerLevel level, BlockPos c) {
        BiomeSource source = level.getChunkSource().getGenerator().getBiomeSource();
        Climate.Sampler sampler = level.getChunkSource().randomState().sampler();
        int sea = level.getSeaLevel();
        double far = 0, near = 0;
        for (int i = 0; i < RING_POINTS; i++) {
            double a = 2 * Math.PI * i / RING_POINTS;
            if (sea(biome(source, sampler, c, FAR_RING, a, sea))) far++;
            Holder<Biome> n = biome(source, sampler, c, NEAR_RING, a, sea);
            if (sea(n) || n.is(BiomeTags.IS_BEACH) || n.is(BiomeTags.IS_RIVER)) near++;
        }
        if (far == 0) return new Cell(0, null);
        Holder<Biome> here = biome(source, sampler, c, 0, 0, sea);
        double depth = here.is(BiomeTags.IS_DEEP_OCEAN) ? DEEP_M : here.is(BiomeTags.IS_OCEAN) ? SHELF_M : COAST_M;
        double gain = Tide.greensLaw(DEEP_M, depth, far / RING_POINTS, Math.max(1, near) / RING_POINTS);
        double m2 = OCEAN_M2_M * gain;
        return new Cell(m2, new Tide(Tide.Site.semidiurnal(m2), calendar()));
    }

    private static boolean sea(Holder<Biome> b) {
        return b.is(BiomeTags.IS_OCEAN) || b.is(BiomeTags.IS_DEEP_OCEAN);
    }

    private static Holder<Biome> biome(BiomeSource source, Climate.Sampler sampler, BlockPos c, int r, double a, int y) {
        int x = c.getX() + (int) Math.round(r * Math.cos(a)), z = c.getZ() + (int) Math.round(r * Math.sin(a));
        return source.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z), sampler);
    }
}
