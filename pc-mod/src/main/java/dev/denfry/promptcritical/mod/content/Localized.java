package dev.denfry.promptcritical.mod.content;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Строка датапака на нескольких языках: {@code {"ru_ru": "...", "en_us": "..."}} или одна строка для всех. Справочник
 * — данные сервера (11 §11.1.2), клиент получает уже выбранный язык.
 */
public record Localized(Map<String, String> byLang) {
    public static final Localized EMPTY = new Localized(Map.of());
    public static final String FALLBACK = "en_us";

    public static Localized parse(JsonElement e) {
        if (e == null || e.isJsonNull()) return EMPTY;
        if (e.isJsonPrimitive()) return new Localized(Map.of("", e.getAsString()));
        Map<String, String> m = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> en : e.getAsJsonObject().entrySet()) m.put(en.getKey(), en.getValue().getAsString());
        return new Localized(Map.copyOf(m));
    }

    public static Localized of(JsonObject o, String key) {
        return parse(o.get(key));
    }

    public boolean isEmpty() {
        return byLang.isEmpty();
    }

    /** Строка на языке {@code lang}, иначе английская, иначе любая; пустая, если нет ни одной. */
    public String get(String lang) {
        String s = byLang.get(lang);
        if (s == null) s = byLang.get("");
        if (s == null) s = byLang.get(FALLBACK);
        if (s == null && !byLang.isEmpty()) s = byLang.values().iterator().next();
        return s == null ? "" : s;
    }
}
