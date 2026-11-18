package dev.denfry.promptcritical.mod.mes;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.fml.ModList;

/** Сообщения сервера завода (en_us, ru_ru); ключи собирает {@code PcLanguageProvider}. */
public final class MesText {
    private static final String P = "message.promptcritical.mes.";
    private static final Map<String, String[]> LANG = new LinkedHashMap<>();

    static {
        put("status", "Plant server: %s RTUs · %s generators · %s MW · dispatch efficiency %s%% · %s",
                "Сервер завода: КП %s · генераторов %s · %s МВт · экономичность распределения %s%% · %s");
        put("cc_on", "attach a CC: Tweaked computer to read it (peripheral pc_mes)",
                "подключите компьютер CC: Tweaked (периферия pc_mes)");
        put("cc_off", "CC: Tweaked is not installed: the panel is the only way in",
                "CC: Tweaked не установлен: доступ только со щита");
    }

    private MesText() {
    }

    private static void put(String key, String en, String ru) {
        LANG.put(P + key, new String[] {en, ru});
    }

    public static MutableComponent msg(String key, Object... args) {
        return Component.translatable(P + key, args);
    }

    /** Подсказка о компьютере: CC установлен или нет. */
    public static Component cc() {
        return msg(ModList.get().isLoaded("computercraft") ? "cc_on" : "cc_off");
    }

    public static Map<String, String[]> lang() {
        return LANG;
    }
}
