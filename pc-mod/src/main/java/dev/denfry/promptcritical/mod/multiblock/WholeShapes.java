package dev.denfry.promptcritical.mod.multiblock;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.denfry.promptcritical.mod.PromptCritical;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Форма клеток машины реального масштаба по её цельной модели (спецификация real-scale machines, M2): клетка объёма
 * твёрдая только там, где модель её действительно занимает. Занятость — из манифеста {@code wholemodels/<id>.json}
 * ({@code cells}: клетка модели → 64 вокселя по 4 пикселя, их пишет нарезчик art/blockbench/lib/pcslice.js), так что
 * вокруг тонкого сосуда в коробке объёма можно ходить, а не упираться в невидимые блоки. Работает и на сервере:
 * манифест лежит в jar мода.
 */
public final class WholeShapes {
    /** Занятость модели: упакованная клетка модели (фасад на север) → маска вокселей; null — манифеста нет. */
    private static final Map<String, Map<Long, Long>> CELLS = new ConcurrentHashMap<>();
    private static final Map<String, VoxelShape> SHAPES = new ConcurrentHashMap<>();
    private static final Map<Long, Long> NONE = Map.of();
    /** Клетки модели с лестницами (манифест, {@code climb}). */
    private static final Map<String, java.util.Set<Long>> CLIMB = new ConcurrentHashMap<>();

    private WholeShapes() {}

    /**
     * Форма клетки в {@code (dx, dy, dz)} от контроллера машины {@code id}, повёрнутой на {@code facing}. Машина без
     * данных о клетках — полный блок, как раньше.
     */
    public static VoxelShape shape(String id, Direction facing, int dx, int dy, int dz) {
        return SHAPES.computeIfAbsent(id + '|' + facing.ordinal() + '|' + dx + '|' + dy + '|' + dz, k -> build(id, facing, dx, dy, dz));
    }

    /** Можно ли лезть по клетке в (dx, dy, dz) от контроллера: в ней у модели нарисована лестница. */
    public static boolean climbable(String id, Direction facing, int dx, int dy, int dz) {
        CELLS.computeIfAbsent(id, WholeShapes::load);
        java.util.Set<Long> c = CLIMB.get(id);
        if (c == null || c.isEmpty()) return false;
        double[] m = unrotate(dx + 0.5, dz + 0.5, facing);
        return c.contains(pack((int) Math.floor(m[0]), dy, (int) Math.floor(m[1])));
    }

    private static VoxelShape build(String id, Direction facing, int dx, int dy, int dz) {
        Map<Long, Long> cells = CELLS.computeIfAbsent(id, WholeShapes::load);
        if (cells == NONE) return Shapes.block();
        // клетка мира → клетка модели: обратный поворот центра клетки вокруг центра контроллера
        double[] c = unrotate(dx + 0.5, dz + 0.5, facing);
        int mx = (int) Math.floor(c[0]), mz = (int) Math.floor(c[1]);
        Long mask = cells.get(pack(mx, dy, mz));
        if (mask == null || mask == 0) return Shapes.empty();
        if (mask == -1L) return Shapes.block();
        VoxelShape out = Shapes.empty();
        for (int vz = 0; vz < 4; vz++)
            for (int vy = 0; vy < 4; vy++)
                for (int vx = 0; vx < 4; vx++) {
                    if ((mask >>> (vx + 4 * vy + 16 * vz) & 1) == 0) continue;
                    // воксель в долях клетки модели → в доли клетки мира (поворот вокруг центра клетки)
                    double[] a = rotateLocal(vx / 4.0, vz / 4.0, facing), b = rotateLocal((vx + 1) / 4.0, (vz + 1) / 4.0, facing);
                    out = Shapes.joinUnoptimized(out, Shapes.box(Math.min(a[0], b[0]), vy / 4.0, Math.min(a[1], b[1]),
                            Math.max(a[0], b[0]), (vy + 1) / 4.0, Math.max(a[1], b[1])), net.minecraft.world.phys.shapes.BooleanOp.OR);
                }
        return out.optimize();
    }

    /** Точка мира (x, z в блоках от угла контроллера) → точка модели; обратный к повороту отрисовки (WholeModelMath). */
    public static double[] unrotate(double x, double z, Direction facing) {
        double px = x - 0.5, pz = z - 0.5, mx, mz;
        switch (facing) {
            case EAST -> { mx = pz; mz = -px; }
            case SOUTH -> { mx = -px; mz = -pz; }
            case WEST -> { mx = -pz; mz = px; }
            default -> { mx = px; mz = pz; }
        }
        return new double[] {mx + 0.5, mz + 0.5};
    }

    /** Точка внутри клетки модели (доли 0…1) → в клетке мира: тот же поворот, что у отрисовки, вокруг центра клетки. */
    public static double[] rotateLocal(double x, double z, Direction facing) {
        double px = x - 0.5, pz = z - 0.5, rx, rz;
        switch (facing) {
            case EAST -> { rx = -pz; rz = px; }
            case SOUTH -> { rx = -px; rz = -pz; }
            case WEST -> { rx = pz; rz = -px; }
            default -> { rx = px; rz = pz; }
        }
        return new double[] {rx + 0.5, rz + 0.5};
    }

    private static long pack(int x, int y, int z) {
        return ((long) (x & 0x1FFFFF) << 42) | ((long) (y & 0x1FFFFF) << 21) | (z & 0x1FFFFF);
    }

    /**
     * Части, которые отрисовщик рисует поверх нарезанной модели в её же пространстве (фасад на север, клетки от
     * контроллера) и которые нарезчик в манифест не кладёт: вторая цельная модель (барабан подъёмной машины,
     * WinderRenderer — без сдвига, вращение вокруг x) и partial, вращающиеся на месте (маховик паровой машины,
     * MillEngineRenderer {@code part(FLYWHEEL, …, 1, 1, 3)}). Их форма в покое — та же, что заметает вращение, так что
     * клетки под ними твёрдые: сквозь барабан и обод маховика не пройти (плейтест I0, 2026-11-23).
     */
    private record Extra(String path, boolean whole, int dx, int dy, int dz) {}

    private static final Map<String, java.util.List<Extra>> EXTRAS = Map.ofEntries(
            Map.entry("winding_engine", java.util.List.of(new Extra("winding_engine_drum", true, 0, 0, 0))),
            Map.entry("mill_engine", java.util.List.of(new Extra("mill_engine/partial/flywheel", false, 1, 1, 3))),
            // кузница и машиностроение (manufacturing/client/ManufacturingClient): вращаются на месте коленвал молота,
            // маховики прессов (у кривошипного сохранён на блок ниже), барабан, валки, делительный стол, планшайба и
            // катушка; бабы, ползуны и мехи — в покое (ход 1–6 px внутрь станины или от неё), двери печей не двигаются
            Map.entry("power_hammer_real", parts("power_hammer_real", 0, "ram", "crank")),
            Map.entry("bloomery_real", parts("bloomery_real", 0, "bellows")),
            Map.entry("forge_hearth_real", parts("forge_hearth_real", 1, "bellows")),
            Map.entry("crank_press_real", java.util.List.of(new Extra("crank_press_real/partial/ram", false, 0, 0, 0),
                    new Extra("crank_press_real/partial/flywheel", false, 0, 1, 0))),
            Map.entry("wire_mill_real", parts("wire_mill_real", 0, "drum")),
            Map.entry("sheet_mill_real", parts("sheet_mill_real", 0, "roll_top", "roll_bottom")),
            Map.entry("notching_press_real", parts("notching_press_real", 0, "ram", "index", "flywheel")),
            Map.entry("winding_machine_real", parts("winding_machine_real", 0, "faceplate", "spool")),
            Map.entry("heating_furnace_real", parts("heating_furnace_real", 0, "door")),
            Map.entry("hardening_furnace_real", parts("hardening_furnace_real", 0, "door")));

    /** Partial {@code <id>/partial/<name>} машины {@code id}, нарисованные со сдвигом {@code dy} блоков. */
    private static java.util.List<Extra> parts(String id, int dy, String... names) {
        return java.util.Arrays.stream(names).map(n -> new Extra(id + "/partial/" + n, false, 0, dy, 0)).toList();
    }

    /** Клетки модели (фасад на север, от контроллера), в которых что-то нарисовано, вместе с {@link #EXTRAS}. */
    public static java.util.Set<net.minecraft.core.BlockPos> occupied(String id) {
        java.util.Set<net.minecraft.core.BlockPos> out = new java.util.HashSet<>();
        for (Map.Entry<Long, Long> e : CELLS.computeIfAbsent(id, WholeShapes::load).entrySet())
            if (e.getValue() != 0) {
                long k = e.getKey();
                out.add(new net.minecraft.core.BlockPos(signed(k >>> 42), signed(k >>> 21), signed(k)));
            }
        return out;
    }

    private static int signed(long v) {
        return (int) (v & 0x1FFFFF) << 11 >> 11;
    }

    private static Map<Long, Long> load(String id) {
        Map<Long, Long> cells = manifest(id, true);
        if (cells == NONE) return NONE;
        for (Extra x : EXTRAS.getOrDefault(id, java.util.List.of())) {
            Map<Long, Long> more = x.whole() ? manifest(x.path(), false) : partial(x.path(), x.dx(), x.dy(), x.dz());
            if (more == NONE) PromptCritical.LOGGER.error("Whole model {}: drawn part {} has no shape data", id, x.path());
            more.forEach((k, m) -> cells.merge(k, m, (a, b) -> a | b));
        }
        return cells;
    }

    /**
     * Занятость partial-модели {@code models/block/<path>.json}, сдвинутой на (dx, dy, dz) блоков: как у нарезчика
     * (pcslice.js {@code cellsOf}) — воксели по 4 px под рамкой каждого элемента с учётом его поворота.
     */
    private static Map<Long, Long> partial(String path, int dx, int dy, int dz) {
        try (InputStream in = WholeShapes.class.getResourceAsStream("/assets/" + PromptCritical.MOD_ID + "/models/block/" + path + ".json")) {
            if (in == null) return NONE;
            JsonObject m = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            Map<Long, Long> out = new HashMap<>();
            int[] off = {16 * dx, 16 * dy, 16 * dz};
            for (JsonElement el : m.getAsJsonArray("elements")) {
                double[][] b = aabb(el.getAsJsonObject());
                int[] v0 = new int[3], v1 = new int[3];
                for (int i = 0; i < 3; i++) {
                    // до 0,1 px за линией вокселя — на ней: грани ломтей 16-угольника разнесены на 0,01 px от z-fighting,
                    // и диск, нарисованный до края клетки, не должен занимать воксель соседней
                    v0[i] = (int) Math.floor((b[0][i] + off[i]) / 4 + 0.025);
                    v1[i] = (int) Math.ceil((b[1][i] + off[i]) / 4 - 0.025) - 1;
                }
                for (int x = v0[0]; x <= v1[0]; x++)
                    for (int y = v0[1]; y <= v1[1]; y++)
                        for (int z = v0[2]; z <= v1[2]; z++) {
                            long bit = 1L << (Math.floorMod(x, 4) + 4 * Math.floorMod(y, 4) + 16 * Math.floorMod(z, 4));
                            out.merge(pack(Math.floorDiv(x, 4), Math.floorDiv(y, 4), Math.floorDiv(z, 4)), bit, (p, q) -> p | q);
                        }
            }
            return out;
        } catch (Exception e) {
            PromptCritical.LOGGER.error("Partial {} could not be read for cell shapes", path, e);
            return NONE;
        }
    }

    /** Рамка элемента модели в px с его поворотом (одна ось, вокруг origin), как {@code aabb} в pcslice.js. */
    private static double[][] aabb(JsonObject e) {
        double[] f = vec(e.getAsJsonArray("from")), t = vec(e.getAsJsonArray("to"));
        JsonObject r = e.has("rotation") ? e.getAsJsonObject("rotation") : null;
        if (r == null || r.get("angle").getAsDouble() == 0) return new double[][] {f, t};
        double a = Math.toRadians(r.get("angle").getAsDouble());
        int ax = switch (r.get("axis").getAsString()) { case "x" -> 0; case "y" -> 1; default -> 2; };
        double[] o = vec(r.getAsJsonArray("origin")), lo = {1e9, 1e9, 1e9}, hi = {-1e9, -1e9, -1e9};
        int u = (ax + 1) % 3, v = (ax + 2) % 3;
        for (int k = 0; k < 8; k++) {
            double[] p = {(k & 1) != 0 ? t[0] : f[0], (k & 2) != 0 ? t[1] : f[1], (k & 4) != 0 ? t[2] : f[2]};
            double pu = p[u] - o[u], pv = p[v] - o[v];
            double[] q = p.clone();
            q[u] = o[u] + pu * Math.cos(a) - pv * Math.sin(a);
            q[v] = o[v] + pu * Math.sin(a) + pv * Math.cos(a);
            for (int i = 0; i < 3; i++) { lo[i] = Math.min(lo[i], q[i]); hi[i] = Math.max(hi[i], q[i]); }
        }
        return new double[][] {lo, hi};
    }

    private static double[] vec(com.google.gson.JsonArray a) {
        return new double[] {a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble()};
    }

    private static Map<Long, Long> manifest(String id, boolean main) {
        try (InputStream in = WholeShapes.class.getResourceAsStream("/assets/" + PromptCritical.MOD_ID + "/wholemodels/" + id + ".json")) {
            if (in == null) return NONE;
            JsonObject m = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            java.util.Set<Long> climb = new java.util.HashSet<>();
            if (m.has("climb"))
                for (JsonElement e : m.getAsJsonArray("climb")) {
                    String[] k = e.getAsString().split(",");
                    climb.add(pack(Integer.parseInt(k[0]), Integer.parseInt(k[1]), Integer.parseInt(k[2])));
                }
            if (main) CLIMB.put(id, climb);
            if (!m.has("cells")) return NONE;
            Map<Long, Long> out = new HashMap<>();
            for (Map.Entry<String, JsonElement> e : m.getAsJsonObject("cells").entrySet()) {
                String[] k = e.getKey().split(",");
                out.put(pack(Integer.parseInt(k[0]), Integer.parseInt(k[1]), Integer.parseInt(k[2])), Long.parseUnsignedLong(e.getValue().getAsString(), 16));
            }
            return out;
        } catch (Exception e) {
            PromptCritical.LOGGER.error("Whole model {} cells could not be read; its cells stay full blocks", id, e);
            return NONE;
        }
    }
}
