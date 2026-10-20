package dev.denfry.promptcritical.mod.content.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

/** Клиент справочника: разбор представления от сервера и открытие экрана. Только клиент. */
public final class HandbookClient {
    private HandbookClient() {
    }

    public record Category(String id, String title, String icon) {
    }

    /** Глава; {@code pages} — страницы как пришли от сервера (у закрытой главы пусто). */
    public record Entry(String id, String category, String title, String icon, boolean unlocked, String hint,
            @Nullable String next, List<JsonObject> pages) {
    }

    public record View(List<Category> categories, List<Entry> entries, @Nullable String open, boolean document, int page,
            @Nullable Route route) {
        @Nullable
        public Entry entry(String id) {
            for (Entry e : entries) if (e.id().equals(id)) return e;
            return null;
        }
    }

    /** Вкладка «Маршрут» (11 §11.1.5): {@code level} — уровень подсказок, эпохи по порядку. */
    public record Route(String level, List<RouteEra> eras) {
        @Nullable
        public RouteTask task(String id) {
            for (RouteEra e : eras) for (RouteTask t : e.tasks()) if (t.id().equals(id)) return t;
            return null;
        }
    }

    public record RouteEra(String id, String era, String title, int done, int total, List<RouteTask> tasks) {
    }

    /**
     * Задание: {@code status} — {@code done}, {@code open} или {@code locked}; цель — {@code count} предметов
     * {@code target}/{@code targetTag} или удержание {@code heldS} из {@code durationS} секунд; {@code options} — варианты
     * развилки. На {@code HARDCORE} текста, подсказки и цели нет.
     */
    public record RouteTask(String id, String title, String icon, String type, String status, int xp, List<String> requires,
            @Nullable String entry, @Nullable String ponder, String text, @Nullable String hint, @Nullable String target,
            @Nullable String targetTag, int count, double durationS, double heldS, List<String> options) {
    }

    public static void open(String json) {
        Minecraft.getInstance().setScreen(new HandbookScreen(parse(json)));
    }

    public static View parse(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        List<Category> cats = new ArrayList<>();
        for (JsonElement c : GsonHelper.getAsJsonArray(root, "categories")) {
            JsonObject o = c.getAsJsonObject();
            cats.add(new Category(GsonHelper.getAsString(o, "id"), GsonHelper.getAsString(o, "title"), GsonHelper.getAsString(o, "icon")));
        }
        List<Entry> entries = new ArrayList<>();
        for (JsonElement e : GsonHelper.getAsJsonArray(root, "entries")) {
            JsonObject o = e.getAsJsonObject();
            List<JsonObject> pages = new ArrayList<>();
            if (o.has("pages")) for (JsonElement p : o.getAsJsonArray("pages")) pages.add(p.getAsJsonObject());
            entries.add(new Entry(GsonHelper.getAsString(o, "id"), GsonHelper.getAsString(o, "category"),
                    GsonHelper.getAsString(o, "title"), GsonHelper.getAsString(o, "icon"), GsonHelper.getAsBoolean(o, "unlocked"),
                    GsonHelper.getAsString(o, "hint", ""), o.has("next") ? o.get("next").getAsString() : null, pages));
        }
        return new View(cats, entries, root.has("open") ? root.get("open").getAsString() : null,
                GsonHelper.getAsBoolean(root, "document", false), GsonHelper.getAsInt(root, "page", 0),
                root.has("route") ? route(root.getAsJsonObject("route")) : null);
    }

    private static Route route(JsonObject r) {
        List<RouteEra> eras = new ArrayList<>();
        for (JsonElement el : GsonHelper.getAsJsonArray(r, "eras")) {
            JsonObject e = el.getAsJsonObject();
            List<RouteTask> tasks = new ArrayList<>();
            for (JsonElement te : GsonHelper.getAsJsonArray(e, "tasks")) {
                JsonObject t = te.getAsJsonObject();
                tasks.add(new RouteTask(GsonHelper.getAsString(t, "id"), GsonHelper.getAsString(t, "title"),
                        GsonHelper.getAsString(t, "icon"), GsonHelper.getAsString(t, "type"), GsonHelper.getAsString(t, "status"),
                        GsonHelper.getAsInt(t, "xp", 0), strings(t, "requires"), optional(t, "entry"), optional(t, "ponder"),
                        GsonHelper.getAsString(t, "text", ""), optional(t, "hint"), optional(t, "target"), optional(t, "target_tag"),
                        GsonHelper.getAsInt(t, "count", 0), GsonHelper.getAsDouble(t, "duration_s", 0),
                        GsonHelper.getAsDouble(t, "held_s", 0), strings(t, "options")));
            }
            eras.add(new RouteEra(GsonHelper.getAsString(e, "id"), GsonHelper.getAsString(e, "era"), GsonHelper.getAsString(e, "title"),
                    GsonHelper.getAsInt(e, "done"), GsonHelper.getAsInt(e, "total"), List.copyOf(tasks)));
        }
        return new Route(GsonHelper.getAsString(r, "level"), List.copyOf(eras));
    }

    private static List<String> strings(JsonObject o, String key) {
        List<String> out = new ArrayList<>();
        if (o.has(key)) for (JsonElement e : o.getAsJsonArray(key)) out.add(e.getAsString());
        return List.copyOf(out);
    }

    @Nullable
    private static String optional(JsonObject o, String key) {
        return o.has(key) ? o.get(key).getAsString() : null;
    }
}
