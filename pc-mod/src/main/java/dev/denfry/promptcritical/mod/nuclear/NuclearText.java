package dev.denfry.promptcritical.mod.nuclear;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Сообщения ядерного топливного цикла (en_us, ru_ru); ключи собирает {@code PcLanguageProvider}. */
public final class NuclearText {
    private static final String P = "message.promptcritical.nuclear.";
    private static final Map<String, String[]> LANG = new LinkedHashMap<>();

    static {
        put("cylinder.empty", "Empty · holds up to %s kg UF₆", "Пустой · вмещает до %s кг UF₆");
        put("cylinder.charge", "UF₆ %s kg · U-235 %s %%", "UF₆ %s кг · U-235 %s %%");
        put("cylinder.u234", "U-234 %s %% · uranium %s kg", "U-234 %s %% · урана %s кг");
        put("cylinder.limit", "Limit %s kg, U-235 up to %s %%", "Предел %s кг, U-235 до %s %%");
        put("cascade.start", "Centrifuge hall: rotors spinning up, UF₆ to the cascade", "Цех центрифуг: роторы разгоняются, UF₆ в каскад");
        put("cascade.stop", "Centrifuge hall stopped, rotors coasting down", "Цех центрифуг остановлен, роторы выбегают");
        put("cascade.excursion", "CRITICALITY EXCURSION in the product desublimer: blue flash, your dose about %s Gy",
                "СЦР В ДЕСУБЛИМАТОРЕ ОТБОРА: голубая вспышка, ваша доза около %s Гр");
    }

    private NuclearText() {
    }

    static void put(String key, String en, String ru) {
        LANG.put(P + key, new String[] {en, ru});
    }

    public static MutableComponent msg(String key, Object... args) {
        return Component.translatable(P + key, args);
    }

    public static String num(double v, int decimals) {
        return String.format(Locale.ROOT, "%." + decimals + "f", v);
    }

    public static Map<String, String[]> lang() {
        return LANG;
    }
}
