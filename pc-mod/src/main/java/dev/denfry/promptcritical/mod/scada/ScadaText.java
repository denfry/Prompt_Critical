package dev.denfry.promptcritical.mod.scada;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.fml.ModList;

/** Сообщения телемеханики (en_us, ru_ru); ключи собирает {@code PcLanguageProvider}. */
public final class ScadaText {
    private static final String P = "message.promptcritical.scada.";
    private static final Map<String, String[]> LANG = new LinkedHashMap<>();

    static {
        put("status", "Telemetry gateway: %s machines within %s blocks · %s", "Шлюз телемеханики: машин %s в %s блоках · %s");
        put("cc_on", "attach a CC: Tweaked computer to read it (peripheral pc_rtu)",
                "подключите компьютер CC: Tweaked (периферия pc_rtu)");
        put("cc_off", "CC: Tweaked is not installed: the gateway has no computer to talk to",
                "CC: Tweaked не установлен: шлюзу не с чем связаться");
        // сирена сигнализации (03c §3c.2.1)
        put("horn_on", "Alarm horn: sounding for priority %s — acknowledge or silence it at the annunciator",
                "Сирена: звучит приоритет %s — квитируйте или заглушите на табло");
        put("horn_off", "Alarm horn: silent (no unacknowledged alarm on annunciators within 16 blocks)",
                "Сирена молчит (на табло в 16 блоках нет неквитированных сигнализаций)");
    }

    private ScadaText() {
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
