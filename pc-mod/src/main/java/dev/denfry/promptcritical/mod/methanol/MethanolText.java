package dev.denfry.promptcritical.mod.methanol;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Сообщения метанольного производства (en_us, ru_ru); ключи собирает {@code PcLanguageProvider}. */
public final class MethanolText {
    private static final String P = "message.promptcritical.methanol.";
    private static final Map<String, String[]> LANG = new LinkedHashMap<>();

    static {
        put("converter.start", "Methanol unit: reformer burners lit, compressor on, loop circulating",
                "Метанольный агрегат: горелки печи зажжены, компрессор пущен, циркуляция есть");
        put("converter.stop", "Methanol unit stopped: feed off, loop holds its pressure",
                "Метанольный агрегат остановлен: сырьё отсечено, контур держит давление");
        put("converter.no_catalyst", "Methanol unit: load nickel into the reformer tubes and copper into the converter first",
                "Метанольный агрегат: сначала загрузите никель в трубы печи и медный катализатор в колонну синтеза");
        put("catalyst.ni", "Nickel reforming catalyst charged into the reformer tubes", "Никелевый катализатор загружен в трубы печи");
        put("catalyst.cu", "Copper-zinc methanol catalyst charged into the converter tubes",
                "Медноцинковый катализатор синтеза метанола загружен в трубки колонны");
        put("catalyst.full", "The catalyst charge is still good", "Загрузка катализатора ещё годна");
        put("column.start", "Methanol distillation: reboilers on steam, reflux started", "Ректификация метанола: кипятильники на паре, флегма пошла");
        put("column.stop", "Methanol distillation stopped", "Ректификация метанола остановлена");
    }

    private MethanolText() {
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
