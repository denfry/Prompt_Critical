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

    private static Map<Long, Long> load(String id) {
        try (InputStream in = WholeShapes.class.getResourceAsStream("/assets/" + PromptCritical.MOD_ID + "/wholemodels/" + id + ".json")) {
            if (in == null) return NONE;
            JsonObject m = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            java.util.Set<Long> climb = new java.util.HashSet<>();
            if (m.has("climb"))
                for (JsonElement e : m.getAsJsonArray("climb")) {
                    String[] k = e.getAsString().split(",");
                    climb.add(pack(Integer.parseInt(k[0]), Integer.parseInt(k[1]), Integer.parseInt(k[2])));
                }
            CLIMB.put(id, climb);
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
