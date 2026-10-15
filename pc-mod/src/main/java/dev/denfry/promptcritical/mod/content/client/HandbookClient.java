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

    public record View(List<Category> categories, List<Entry> entries, @Nullable String open, boolean document, int page) {
        @Nullable
        public Entry entry(String id) {
            for (Entry e : entries) if (e.id().equals(id)) return e;
            return null;
        }
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
                GsonHelper.getAsBoolean(root, "document", false), GsonHelper.getAsInt(root, "page", 0));
    }
}
