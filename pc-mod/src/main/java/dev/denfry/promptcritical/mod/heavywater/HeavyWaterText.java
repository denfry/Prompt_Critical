package dev.denfry.promptcritical.mod.heavywater;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Сообщения тяжёлой воды (en_us, ru_ru); ключи собирает {@code PcLanguageProvider}. */
public final class HeavyWaterText {
    private static final String P = "message.promptcritical.heavy_water.";
    private static final Map<String, String[]> LANG = new LinkedHashMap<>();

    static {
        put("gs.start", "GS plant: H₂S circulation on, lake water to the cold tower", "Завод GS: сероводород в работе, озёрная вода — в холодную колонну");
        put("gs.stop", "GS plant stopped", "Завод GS остановлен");
        put("column.start", "Finishing column: still boiling under vacuum", "Колонна доводки: куб кипит под вакуумом");
        put("column.stop", "Finishing column stopped", "Колонна доводки остановлена");
        put("candu.drained", "Moderator: %s L drained as %s", "Замедлитель: слито %s л — %s");
        put("candu.drain_full", "Moderator drain tank is full: pipe it to the finishing column",
                "Бак слива замедлителя полон: отведите его трубой на колонну доводки");
    }

    private HeavyWaterText() {
    }

    private static void put(String key, String en, String ru) {
        LANG.put(P + key, new String[] {en, ru});
    }

    public static MutableComponent msg(String key, Object... args) {
        return Component.translatable(P + key, args);
    }

    public static Map<String, String[]> lang() {
        return LANG;
    }
}
