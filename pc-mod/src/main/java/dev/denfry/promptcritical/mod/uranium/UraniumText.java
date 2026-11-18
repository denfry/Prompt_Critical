package dev.denfry.promptcritical.mod.uranium;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Сообщения передела урана (en_us, ru_ru); ключи собирает {@code PcLanguageProvider}. */
public final class UraniumText {
    private static final String P = "message.promptcritical.uranium.";
    private static final Map<String, String[]> LANG = new LinkedHashMap<>();

    static {
        put("on", "%s: started", "%s: пущено");
        put("off", "%s: stopped", "%s: остановлено");
        put("uf6.vent", "UF₆ IS PASSING THE COLD TRAPS: no cylinder to fill. It hydrolyses in air into hydrogen fluoride",
                "UF₆ ПРОХОДИТ ХОЛОДИЛЬНЫЕ ЛОВУШКИ: нет баллона под налив. Во влажном воздухе он гидролизуется во фтористый водород");
    }

    private UraniumText() {
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
