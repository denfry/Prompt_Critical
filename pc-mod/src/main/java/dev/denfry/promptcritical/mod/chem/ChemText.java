package dev.denfry.promptcritical.mod.chem;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Сообщения химии I3 (en_us, ru_ru); ключи собирает {@code PcLanguageProvider}. */
public final class ChemText {
    private static final String P = "message.promptcritical.chem.";
    private static final Map<String, String[]> LANG = new LinkedHashMap<>();

    static {
        put("furnace.run", "Pyrolysis furnace: feed on, cracking", "Печь пиролиза: сырьё подано, крекинг");
        put("furnace.stop", "Pyrolysis furnace: feed off", "Печь пиролиза: сырьё отсечено");
        put("furnace.decoke", "Pyrolysis furnace: steam-air decoking", "Печь пиролиза: паровоздушный выжиг кокса");
        put("ldpe.start", "LDPE unit: hyper compressor on, oxygen to the reactor", "Установка ПЭВД: гиперкомпрессор пущен, кислород в реактор");
        put("ldpe.stop", "LDPE unit stopped", "Установка ПЭВД остановлена");
        put("ldpe.decomposed", "ETHYLENE DECOMPOSITION in the LDPE reactor: rupture discs burst, hot gas vented and ignited",
                "РАЗЛОЖЕНИЕ ЭТИЛЕНА в реакторе ПЭВД: мембраны вскрыты, горячий газ выброшен и вспыхнул");
        put("ldpe.repaired", "LDPE reactor: new rupture discs, tube cleaned of soot", "Реактор ПЭВД: новые мембраны, труба очищена от сажи");
    }

    private ChemText() {
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
