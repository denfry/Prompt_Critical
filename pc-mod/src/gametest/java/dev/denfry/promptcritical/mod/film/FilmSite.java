package dev.denfry.promptcritical.mod.film;

import com.google.gson.GsonBuilder;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.denfry.promptcritical.mod.PromptCritical;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Место для мира-завода (только dev). Работает по шуму генератора, без генерации чанков, поэтому сиды можно
 * перебирать из одного мира с обычной генерацией.
 * <ul>
 * <li>{@code /film survey <seed> <count> <radius>}: для сидов seed..seed+count-1 ищет окно 640×640 в радиусе от 0,0, где
 * есть берег моря, река с устьем, холмы выше моря на 30+ блоков и ровная суша под цеха. Пишет {@code survey.json}.</li>
 * <li>{@code /film heightmap <x1> <z1> <x2> <z2> <step>}: рельеф этого мира (поверхность, дно, биом) в
 * {@code plant_site.json} для {@code art/film/plantworld.py}.</li>
 * </ul>
 * Считается в фоновом потоке, игра не замирает; по готовности пишет в чат.
 */
@EventBusSubscriber(modid = PromptCritical.MOD_ID)
public final class FilmSite {
    private static final int WINDOW = 640, SAMPLE = 16, CENTER_STEP = 64, REFINE = 10;
    private static final AtomicBoolean BUSY = new AtomicBoolean();

    private FilmSite() {
    }

    @SubscribeEvent
    static void onCommands(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("film").requires(s -> s.hasPermission(2))
                .then(Commands.literal("survey").then(Commands.argument("seed", LongArgumentType.longArg())
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 500))
                                .then(Commands.argument("radius", IntegerArgumentType.integer(0, 8192)).executes(FilmSite::survey)))))
                .then(Commands.literal("gorge").then(Commands.argument("x", IntegerArgumentType.integer())
                        .then(Commands.argument("z", IntegerArgumentType.integer())
                                .then(Commands.argument("radius", IntegerArgumentType.integer(64, 4096)).executes(FilmSite::gorge)))))
                .then(Commands.literal("heightmap").then(Commands.argument("x1", IntegerArgumentType.integer())
                        .then(Commands.argument("z1", IntegerArgumentType.integer())
                                .then(Commands.argument("x2", IntegerArgumentType.integer())
                                        .then(Commands.argument("z2", IntegerArgumentType.integer())
                                                .then(Commands.argument("step", IntegerArgumentType.integer(1, 16))
                                                        .executes(c -> heightmap(c, null))
                                                        .then(Commands.argument("seed", LongArgumentType.longArg())
                                                                .executes(c -> heightmap(c, LongArgumentType.getLong(c, "seed")))))))))));
    }

    /** Поверхность (с водой), дно и биом в точке, без генерации чанка. */
    private record Column(int surface, int floor, Holder<Biome> biome) {
    }

    private static Column column(ServerLevel level, ChunkGenerator gen, RandomState rs, int x, int z) {
        int surface = gen.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, rs);
        int floor = gen.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, rs);
        BiomeSource src = gen.getBiomeSource();
        Holder<Biome> b = src.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(Math.max(floor, gen.getSeaLevel() - 4)),
                QuartPos.fromBlock(z), rs.sampler());
        return new Column(surface, floor, b);
    }

    private static boolean start(CommandSourceStack src) {
        if (BUSY.compareAndSet(false, true)) return true;
        src.sendFailure(Component.literal("[film] site: another job is running"));
        return false;
    }

    private static void done(ServerLevel level, String msg) {
        BUSY.set(false);
        PromptCritical.LOGGER.info(msg);
        level.getServer().execute(() -> level.getServer().getPlayerList().broadcastSystemMessage(Component.literal(msg), false));
    }

    // ----------------------------------------------------------------------------------------------------- survey

    private static int survey(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        ServerLevel level = src.getLevel();
        if (!(level.getChunkSource().getGenerator() instanceof NoiseBasedChunkGenerator gen)) {
            src.sendFailure(Component.literal("[film] survey needs a world with normal generation"));
            return 0;
        }
        if (!start(src)) return 0;
        long seed0 = LongArgumentType.getLong(ctx, "seed");
        int count = IntegerArgumentType.getInteger(ctx, "count");
        int radius = IntegerArgumentType.getInteger(ctx, "radius");
        src.sendSuccess(() -> Component.literal("[film] survey " + count + " seeds from " + seed0 + "..."), false);
        Thread t = new Thread(() -> {
            try {
                // pass 1: biomes only (climate noise, microseconds a point) over every seed
                List<long[]> coarse = new ArrayList<>(IntStream.range(0, count).parallel()
                        .mapToObj(i -> biomeSite(level, gen, seed0 + i, radius)).toList());
                coarse.sort(Comparator.comparingLong((long[] a) -> a[3]).reversed());
                // pass 2: terrain heights only in the best windows
                List<Map<String, Object>> top = new ArrayList<>(coarse.subList(0, Math.min(REFINE, coarse.size()))
                        .parallelStream().map(a -> bestSite(level, gen, a[0], (int) a[1], (int) a[2])).toList());
                top.sort(Comparator.comparingDouble((Map<String, Object> m) -> (double) m.get("score")).reversed());
                Files.writeString(Path.of("survey.json"), new GsonBuilder().setPrettyPrinting().create().toJson(top));
                Map<String, Object> b = top.get(0);
                done(level, "[film] survey done, best seed " + b.get("seed") + " at " + b.get("x") + " " + b.get("z")
                        + " score " + String.format("%.2f", (double) b.get("score")) + " (survey.json)");
            } catch (Throwable ex) {
                done(level, "[film] survey failed: " + ex);
            }
        }, "film-survey");
        t.setDaemon(true);
        t.start();
        return 1;
    }

    private static RandomState randomState(ServerLevel level, NoiseBasedChunkGenerator gen, long seed) {
        return RandomState.create(level.registryAccess().asGetterLookup(), gen.generatorSettings().unwrapKey().orElseThrow(), seed);
    }

    /**
     * Pass 1: the best window of a seed by biomes alone. Coast (ocean 12–40 %), river cells, an estuary (river next to
     * ocean), hills (mountain and hill biomes) and ordinary land for the works. Returns {seed, x, z, score × 1000}.
     */
    private static long[] biomeSite(ServerLevel level, NoiseBasedChunkGenerator gen, long seed, int radius) {
        RandomState rs = randomState(level, gen, seed);
        BiomeSource src = gen.getBiomeSource();
        int sea = gen.getSeaLevel(), step = 32, half = WINDOW / 2, lo = -radius - half, n = (2 * radius + WINDOW) / step + 1;
        byte[] kind = new byte[n * n];   // 0 land, 1 ocean, 2 river, 3 hills, 4 beach
        for (int i = 0; i < n; i++)
            for (int j = 0; j < n; j++) {
                Holder<Biome> b = src.getNoiseBiome(QuartPos.fromBlock(lo + i * step), QuartPos.fromBlock(sea),
                        QuartPos.fromBlock(lo + j * step), rs.sampler());
                byte k = (byte) (b.is(BiomeTags.IS_OCEAN) ? 1 : b.is(BiomeTags.IS_RIVER) ? 2
                        : b.is(BiomeTags.IS_MOUNTAIN) || b.is(BiomeTags.IS_HILL) ? 3 : b.is(BiomeTags.IS_BEACH) ? 4 : 0);
                // frozen sea and rivers kill the tide and hydro shots, snow plains read as another game: mark cold
                if (k != 3 && b.value().getBaseTemperature() < 0.3f) k = 5;
                kind[i * n + j] = k;
            }
        int w = WINDOW / step;
        long[] best = {seed, 0, 0, -1};
        for (int cx = -radius; cx <= radius; cx += CENTER_STEP)
            for (int cz = -radius; cz <= radius; cz += CENTER_STEP) {
                int i0 = (cx - half - lo) / step, j0 = (cz - half - lo) / step;
                int ocean = 0, river = 0, estuary = 0, hills = 0, land = 0, cold = 0;
                for (int i = i0; i < i0 + w; i++)
                    for (int j = j0; j < j0 + w; j++) {
                        switch (kind[i * n + j]) {
                            case 1 -> ocean++;
                            case 2 -> {
                                river++;
                                if (near(kind, n, i, j, 1)) estuary++;
                            }
                            case 3 -> hills++;
                            case 0 -> land++;
                            case 5 -> cold++;
                            default -> { }
                        }
                    }
                double cells = w * w, o = ocean / cells;
                double score = (o >= 0.12 && o <= 0.40 ? 2 : 0) + Math.min(river, 30) / 15.0 + (estuary > 0 ? 2 : 0)
                        + Math.min(hills, 40) / 20.0 + Math.min(land / cells, 0.4) / 0.4 * 3 - cold / cells * 12;
                if (score * 1000 > best[3]) best = new long[] {seed, cx, cz, Math.round(score * 1000)};
            }
        return best;
    }

    /** Pass 2: terrain heights around the window found by biomes; the final score uses real relief. */
    private static Map<String, Object> bestSite(ServerLevel level, NoiseBasedChunkGenerator gen, long seed, int x0, int z0) {
        RandomState rs = randomState(level, gen, seed);
        int sea = gen.getSeaLevel(), radius = 2 * CENTER_STEP;
        int half = WINDOW / 2, n = (2 * radius + WINDOW) / SAMPLE + 1;
        int lox = x0 - radius - half, loz = z0 - radius - half;
        // kinds: 0 land, 1 ocean, 2 river; h = floor height
        byte[] kind = new byte[n * n];
        int[] h = new int[n * n];
        boolean[] cold = new boolean[n * n];
        for (int i = 0; i < n; i++)
            for (int j = 0; j < n; j++) {
                Column c = column(level, gen, rs, lox + i * SAMPLE, loz + j * SAMPLE);
                int k = i * n + j;
                h[k] = c.floor();
                kind[k] = (byte) (c.biome().is(BiomeTags.IS_OCEAN) ? 1 : c.biome().is(BiomeTags.IS_RIVER) ? 2 : 0);
                if (!c.biome().is(BiomeTags.IS_MOUNTAIN) && c.biome().value().getBaseTemperature() < 0.3f) cold[k] = true;
            }
        int w = WINDOW / SAMPLE;
        Map<String, Object> best = null;
        for (int cx = x0 - radius; cx <= x0 + radius; cx += CENTER_STEP)
            for (int cz = z0 - radius; cz <= z0 + radius; cz += CENTER_STEP) {
                int i0 = (cx - half - lox) / SAMPLE, j0 = (cz - half - loz) / SAMPLE;
                int ocean = 0, river = 0, estuary = 0, ridge = 0, flat = 0, land = 0, top = sea, frozen = 0;
                for (int i = i0; i < i0 + w; i++)
                    for (int j = j0; j < j0 + w; j++) {
                        int k = i * n + j;
                        if (cold[k]) frozen++;
                        if (kind[k] == 1) ocean++;
                        else if (kind[k] == 2) {
                            river++;
                            if (near(kind, n, i, j, 1)) estuary++;
                        } else {
                            land++;
                            top = Math.max(top, h[k]);
                            if (h[k] >= sea + 30) ridge++;
                            if (h[k] >= sea + 2 && h[k] <= sea + 16 && slope(h, n, i, j) <= 3) flat++;
                        }
                    }
                double cells = w * w, o = ocean / cells, f = flat / cells;
                double score = -frozen / (double) (w * w) * 12;
                score += o >= 0.12 && o <= 0.40 ? 2 : 0;                  // a coast, not open sea
                score += Math.min(river, 60) / 30.0;                      // a river through the site
                score += estuary > 0 ? 2 : 0;                             // the river reaches the sea
                score += Math.min(ridge, 40) / 20.0;                      // hills for the wind farm, the mine, the dam
                score += Math.min(f, 0.35) / 0.35 * 3;                    // room for the works
                if (best == null || score > (double) best.get("score")) {
                    best = new LinkedHashMap<>();
                    best.put("seed", seed);
                    best.put("x", cx);
                    best.put("z", cz);
                    best.put("score", score);
                    best.put("ocean", Math.round(o * 100) / 100.0);
                    best.put("river", river);
                    best.put("estuary", estuary);
                    best.put("ridge", ridge);
                    best.put("flat", Math.round(f * 100) / 100.0);
                    best.put("top", top);
                }
            }
        return best;
    }

    private static boolean near(byte[] kind, int n, int i, int j, int want) {
        for (int di = -1; di <= 1; di++)
            for (int dj = -1; dj <= 1; dj++) {
                int a = i + di, b = j + dj;
                if (a >= 0 && b >= 0 && a < n && b < n && kind[a * n + b] == want) return true;
            }
        return false;
    }

    private static int slope(int[] h, int n, int i, int j) {
        int m = 0, c = h[i * n + j];
        if (i + 1 < n) m = Math.max(m, Math.abs(h[(i + 1) * n + j] - c));
        if (j + 1 < n) m = Math.max(m, Math.abs(h[i * n + j + 1] - c));
        return m;
    }

    // ------------------------------------------------------------------------------------------------------ gorge

    /**
     * {@code /film gorge <x> <z> <radius>}: dam sites in this world. A river cell qualifies when the ground on both
     * banks, measured across the river (east-west and north-south pairs, 16..48 blocks out), rises well above the bed:
     * depth = min(left max, right max) - bed. Writes the best 20 to {@code gorge.json}.
     */
    private static int gorge(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        ServerLevel level = src.getLevel();
        if (!start(src)) return 0;
        int cx = IntegerArgumentType.getInteger(ctx, "x"), cz = IntegerArgumentType.getInteger(ctx, "z");
        int radius = IntegerArgumentType.getInteger(ctx, "radius");
        ChunkGenerator gen = level.getChunkSource().getGenerator();
        RandomState rs = level.getChunkSource().randomState();
        int step = 8, n = 2 * radius / step + 1;
        Thread t = new Thread(() -> {
            try {
                List<Map<String, Object>> found = IntStream.range(0, n * n).parallel().mapToObj(k -> {
                    int x = cx - radius + (k / n) * step, z = cz - radius + (k % n) * step;
                    BiomeSource bs = gen.getBiomeSource();
                    Holder<Biome> b = bs.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(gen.getSeaLevel()),
                            QuartPos.fromBlock(z), rs.sampler());
                    if (!b.is(BiomeTags.IS_RIVER)) return null;
                    int bed = gen.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, rs);
                    int best = 0, axis = 0;
                    for (int a = 0; a < 2; a++) {
                        int l = Integer.MIN_VALUE, r = Integer.MIN_VALUE;
                        for (int d = 16; d <= 48; d += 8) {
                            int lx = a == 0 ? x - d : x, lz = a == 0 ? z : z - d;
                            int rx = a == 0 ? x + d : x, rz = a == 0 ? z : z + d;
                            l = Math.max(l, gen.getBaseHeight(lx, lz, Heightmap.Types.OCEAN_FLOOR_WG, level, rs));
                            r = Math.max(r, gen.getBaseHeight(rx, rz, Heightmap.Types.OCEAN_FLOOR_WG, level, rs));
                        }
                        int depth = Math.min(l, r) - bed;
                        if (depth > best) {
                            best = depth;
                            axis = a;
                        }
                    }
                    if (best < 20) return null;
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("x", x);
                    m.put("z", z);
                    m.put("bed", bed);
                    m.put("depth", best);
                    m.put("dam_axis", axis == 0 ? "x" : "z");     // the dam runs along this axis, across the river
                    m.put("dist", (int) Math.hypot(x - cx, z - cz));
                    return m;
                }).filter(java.util.Objects::nonNull).sorted(Comparator.comparingInt((Map<String, Object> m) -> -(int) m.get("depth"))).limit(20).toList();
                Files.writeString(Path.of("gorge.json"), new GsonBuilder().setPrettyPrinting().create().toJson(found));
                done(level, "[film] gorge done: " + found.size() + " sites (gorge.json)"
                        + (found.isEmpty() ? "" : ", best " + found.get(0)));
            } catch (Throwable ex) {
                done(level, "[film] gorge failed: " + ex);
            }
        }, "film-gorge");
        t.setDaemon(true);
        t.start();
        return 1;
    }

    // -------------------------------------------------------------------------------------------------- heightmap

    private static int heightmap(CommandContext<CommandSourceStack> ctx, Long seed) {
        CommandSourceStack src = ctx.getSource();
        ServerLevel level = src.getLevel();
        if (!start(src)) return 0;
        int x1 = IntegerArgumentType.getInteger(ctx, "x1"), z1 = IntegerArgumentType.getInteger(ctx, "z1");
        int x2 = IntegerArgumentType.getInteger(ctx, "x2"), z2 = IntegerArgumentType.getInteger(ctx, "z2");
        int step = IntegerArgumentType.getInteger(ctx, "step");
        int nx = (x2 - x1) / step + 1, nz = (z2 - z1) / step + 1;
        ChunkGenerator gen = level.getChunkSource().getGenerator();
        if (seed != null && !(gen instanceof NoiseBasedChunkGenerator)) {
            src.sendFailure(Component.literal("[film] heightmap with a seed needs a world with normal generation"));
            BUSY.set(false);
            return 0;
        }
        RandomState rs = seed == null ? level.getChunkSource().randomState() : randomState(level, (NoiseBasedChunkGenerator) gen, seed);
        long worldSeed = seed == null ? level.getSeed() : seed;
        String file = seed == null ? "plant_site.json" : "site_" + seed + ".json";
        src.sendSuccess(() -> Component.literal("[film] heightmap " + nx + "x" + nz + "..."), false);
        Thread t = new Thread(() -> {
            try {
                int[] surface = new int[nx * nz], floor = new int[nx * nz], biome = new int[nx * nz];
                Map<String, Integer> names = new LinkedHashMap<>();
                Column[] cols = new Column[nx * nz];
                IntStream.range(0, nx * nz).parallel().forEach(k ->
                        cols[k] = column(level, gen, rs, x1 + (k / nz) * step, z1 + (k % nz) * step));
                for (int k = 0; k < cols.length; k++) {
                    surface[k] = cols[k].surface();
                    floor[k] = cols[k].floor();
                    String id = cols[k].biome().unwrapKey().map(r -> r.location().getPath()).orElse("?");
                    biome[k] = names.computeIfAbsent(id, s -> names.size());
                }
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("seed", worldSeed);
                out.put("sea", gen.getSeaLevel());
                out.put("x1", x1);
                out.put("z1", z1);
                out.put("step", step);
                out.put("nx", nx);
                out.put("nz", nz);
                out.put("order", "k = ix * nz + iz");
                out.put("biomes", new ArrayList<>(names.keySet()));
                out.put("surface", surface);
                out.put("floor", floor);
                out.put("biome", biome);
                Files.writeString(Path.of(file), new GsonBuilder().create().toJson(out));
                done(level, "[film] heightmap done: " + file + ", " + names.size() + " biomes");
            } catch (IOException | RuntimeException ex) {
                done(level, "[film] heightmap failed: " + ex);
            }
        }, "film-heightmap");
        t.setDaemon(true);
        t.start();
        return 1;
    }
}
