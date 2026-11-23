package dev.denfry.promptcritical.mod.client.whole;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.Nullable;

/**
 * Цельные модели машин, нарезанные {@code art/blockbench/lib/pcslice.js}: манифест {@code wholemodels/<id>.json}
 * читается из jar мода при регистрации (до запекания моделей), каждый кусок — partial Flywheel.
 */
public final class WholeModels {
    /** Кусок: partial и смещение его среднего блока от контроллера, блоки (пространство модели, фасад на север). */
    public record Chunk(PartialModel partial, int x, int y, int z) {}

    /** Модель: куски и границы в блоках относительно контроллера. */
    public record Model(String id, List<Chunk> chunks, double[] min, double[] max) {}

    private static final Map<String, Model> MODELS = new ConcurrentHashMap<>();

    private WholeModels() {}

    /** Регистрирует модель; вызывать при загрузке клиента, до запекания моделей. Нет манифеста — предупреждение. */
    public static void register(String id) {
        try (InputStream in = WholeModels.class.getResourceAsStream("/assets/" + PromptCritical.MOD_ID + "/wholemodels/" + id + ".json")) {
            if (in == null) {
                PromptCritical.LOGGER.warn("Whole model {} has no manifest yet; the machine renders nothing", id);
                return;
            }
            JsonObject m = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            List<Chunk> chunks = new ArrayList<>();
            for (JsonElement e : m.getAsJsonArray("chunks")) {
                JsonArray a = e.getAsJsonArray();
                int x = a.get(0).getAsInt(), y = a.get(1).getAsInt(), z = a.get(2).getAsInt();
                int cx = Math.floorDiv(x - 1, 3), cy = Math.floorDiv(y - 1, 3), cz = Math.floorDiv(z - 1, 3);
                PartialModel p = PartialModel.of(PromptCritical.id("block/" + id + "/chunk/" + cx + "_" + cy + "_" + cz));
                chunks.add(new Chunk(p, x, y, z));
            }
            JsonObject b = m.getAsJsonObject("bounds");
            MODELS.put(id, new Model(id, List.copyOf(chunks), vec(b.getAsJsonArray("min")), vec(b.getAsJsonArray("max"))));
        } catch (Exception e) {
            PromptCritical.LOGGER.error("Whole model {} could not be read", id, e);
        }
    }

    @Nullable
    public static Model get(String id) {
        return MODELS.get(id);
    }

    private static double[] vec(JsonArray a) {
        return new double[] {a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble()};
    }
}
