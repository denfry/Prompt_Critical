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
        put("rtg_status", "Telemetry gateway on the RTG: %s machines within %s blocks · draws %s W",
                "Шлюз телемеханики на РИТЭГе: машин %s в %s блоках · берёт %s Вт");
        put("rtg_dark", "Telemetry gateway dark: the RTG does not give its %s W", "Шлюз телемеханики погас: РИТЭГ не даёт его %s Вт");
        put("cc_on", "attach a CC: Tweaked computer to read it (peripheral pc_rtu)",
                "подключите компьютер CC: Tweaked (периферия pc_rtu)");
        put("cc_off", "CC: Tweaked is not installed: the gateway has no computer to talk to",
                "CC: Tweaked не установлен: шлюзу не с чем связаться");
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
