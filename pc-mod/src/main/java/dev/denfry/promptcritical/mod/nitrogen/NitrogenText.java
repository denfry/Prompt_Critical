package dev.denfry.promptcritical.mod.nitrogen;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Сообщения азотного производства (en_us, ru_ru); ключи собирает {@code PcLanguageProvider}. */
public final class NitrogenText {
    private static final String P = "message.promptcritical.nitrogen.";
    private static final Map<String, String[]> LANG = new LinkedHashMap<>();

    static {
        put("reformer.start", "Reformer: burners lit, gas and steam to the tubes", "Риформинг: горелки зажжены, газ и пар в трубы");
        put("reformer.stop", "Reformer: feed off, burners out", "Риформинг: сырьё отсечено, горелки погашены");
        put("reformer.no_catalyst", "Reformer: the tubes hold no nickel catalyst", "Риформинг: в трубах нет никелевого катализатора");
        put("catalyst.ni", "Nickel reforming catalyst charged into the tubes", "Никелевый катализатор загружен в трубы печи");
        put("catalyst.hts", "Iron-chromium catalyst charged into the high temperature shift", "Железохромовый катализатор загружен в конвертор СТК");
        put("catalyst.lts", "Copper-zinc catalyst charged into the low temperature shift", "Медноцинковый катализатор загружен в конвертор НТК");
        put("catalyst.full", "The catalyst charge is still good", "Загрузка катализатора ещё годна");
        put("absorber.reset", "Methanator trip reset", "Блокировка метанатора сброшена");
        put("converter.start", "Synthesis loop: compressor on, loop circulating", "Цикл синтеза: компрессор пущен, циркуляция есть");
        put("converter.stop", "Synthesis loop stopped", "Цикл синтеза остановлен");
        put("nitric.start", "Nitric acid unit: air on, ammonia to the gauze", "Агрегат азотной кислоты: воздух подан, аммиак на сетки");
        put("nitric.stop", "Nitric acid unit stopped", "Агрегат азотной кислоты остановлен");
        put("nitric.no_gauze", "Nitric acid unit: no platinum gauze in the converter", "Агрегат азотной кислоты: в аппарате нет платиновых сеток");
        put("nitric.gauze", "New platinum-rhodium gauze pack laid in the converter", "В аппарат уложен новый пакет платинородиевых сеток");
        put("nitric.denox", "Tail gas reduction catalyst charged", "Загружен катализатор очистки хвостовых газов");
        put("nitric.flash", "AMMONIA-AIR FLASH in the converter: the mixture was above the lower explosive limit, gauze torn",
                "ВСПЫШКА АММИАЧНО-ВОЗДУШНОЙ СМЕСИ в контактном аппарате: смесь выше нижнего предела взрываемости, сетки порваны");
        put("concentrator.start", "Acid concentrator: magnesium nitrate melt circulating, column heated",
                "Концентрирование: плав нитрата магния в циркуляции, колонна обогревается");
        put("concentrator.stop", "Acid concentrator stopped", "Концентрирование остановлено");
    }

    private NitrogenText() {
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
