package dev.denfry.promptcritical.mod.npp;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Сообщения CANDU NPD (en_us, ru_ru); ключи собирает {@code PcLanguageProvider}. */
public final class NpdText {
    private static final String P = "message.promptcritical.npd.";
    private static final Map<String, String[]> LANG = new LinkedHashMap<>();

    static {
        put("dump", "MODERATOR DUMP: %s", "СБРОС ЗАМЕДЛИТЕЛЯ: %s");
        put("reset", "Dump valves closed: pump the moderator up to raise the level",
                "Сбросные клапаны закрыты: поднимайте уровень насосами замедлителя");
        put("reset_refused", "Cannot close the dump valves: the cause is still there",
                "Закрыть сбросные клапаны нельзя: причина не устранена");
        put("loaded", "Channel %s loaded with nine bundles", "Канал %s загружен девятью пучками");
        put("fuelling", "Fuelling machines to channel %s: %s bundles", "Перегрузочные машины к каналу %s: пучков %s");
        put("fuelled", "Channel %s refuelled", "Канал %s перегружен");
        put("need_machines", "Fuelling needs a machine at each face of the reactor",
                "Для перегрузки нужна машина у каждого торца реактора");
        put("need_bundles", "Not enough fresh bundles: %s in the bay, %s needed",
                "Мало свежих пучков: в лотке %s, нужно %s");
        put("spent_full", "Take the spent bundles out of the output slot first", "Сначала заберите выгоревшие пучки из слота выдачи");
        put("aborted", "Fuelling aborted: the machines close the channel and return", "Перегрузка прервана: машины закрывают канал и уходят");
        put("overheat", "FUELLING MACHINE: spent bundles overheated in the magazine, cladding failed",
                "ПЕРЕГРУЗОЧНАЯ МАШИНА: выгоревшие пучки перегрелись в магазине, оболочки лопнули");
        put("drained", "Drained %s L from the dump tank", "Из бака сброса слито %s л");
    }

    private NpdText() {
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
