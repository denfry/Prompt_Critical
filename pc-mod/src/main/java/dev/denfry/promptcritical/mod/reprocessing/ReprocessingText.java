package dev.denfry.promptcritical.mod.reprocessing;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Сообщения радиохимии (en_us, ru_ru); ключи собирает {@code PcLanguageProvider}. */
public final class ReprocessingText {
    private static final String P = "message.promptcritical.reprocessing.";
    private static final Map<String, String[]> LANG = new LinkedHashMap<>();

    static {
        put("criticality", "CRITICALITY in the plutonium nitrate buffer: oxalate precipitation had stopped. The solution is dumped to waste",
                "СЦР в буферной ёмкости нитрата плутония: осаждение оксалата стояло. Раствор слит в отходы");
        put("pu_lot", "Plutonium %s kg · Pu-239 %s %% · Pu-240 %s %%", "Плутоний %s кг · Pu-239 %s %% · Pu-240 %s %%");
    }

    private ReprocessingText() {
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
