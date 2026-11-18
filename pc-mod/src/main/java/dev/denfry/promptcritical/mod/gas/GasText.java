package dev.denfry.promptcritical.mod.gas;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Сообщения газопереработки (en_us, ru_ru); ключи собирает {@code PcLanguageProvider}. */
public final class GasText {
    private static final String P = "message.promptcritical.gas.";
    private static final Map<String, String[]> LANG = new LinkedHashMap<>();

    static {
        put("plugged", "HYDRATE PLUG: the LTS choke is blocked. Warm the chiller or inject methanol and wait for it to melt",
                "ГИДРАТНАЯ ПРОБКА: штуцер НТС перекрыт. Поднимите температуру холодильника или дайте метанол и ждите, пока растает");
        put("missing", "Needs a gas compressor on a turning shaft and a glycol regenerator within %s blocks",
                "Нужны газовый компрессор на вращающемся валу и регенератор гликоля в %s блоках");
        put("status", "Gas %s nm³/h · NGL %s l/h · LTS %s °C at %s MPa · dew point %s °C · hydrates %s °C",
                "Газ %s нм³/ч · ШФЛУ %s л/ч · НТС %s °C при %s МПа · точка росы %s °C · гидраты %s °C");
        put("gfu_status", "NGL %s l/h · propane %s %% · butane %s %% · gasoline %s %% pure",
                "ШФЛУ %s л/ч · пропан %s %% · бутан %s %% · газовый бензин %s %% чистоты");
    }

    private GasText() {
    }

    private static void put(String key, String en, String ru) {
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
