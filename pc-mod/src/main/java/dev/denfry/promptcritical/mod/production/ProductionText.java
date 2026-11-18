package dev.denfry.promptcritical.mod.production;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Сообщения добычи и переработки (en_us, ru_ru); ключи собирает {@code PcLanguageProvider}. */
public final class ProductionText {
    private static final String P = "message.promptcritical.production.";
    private static final Map<String, String[]> LANG = new LinkedHashMap<>();

    static {
        put("no_bore", "No drilled bore under the wellhead: drill here first", "Под устьем нет пробуренного ствола: сначала пробурите здесь");
        put("cased", "Production casing run: %s of %s joints (12 m each)", "Эксплуатационная колонна: %s из %s труб (по 12 м)");
        put("needs_casing", "Run the production casing first (%s seamless pipes)", "Сначала спустите эксплуатационную колонну (%s бесшовных труб)");
        put("perforated", "Perforated %s m of oil pay at %s m: reservoir %s MPa", "Перфорировано %s м нефтенасыщенного пласта на %s м: пластовое %s МПа");
        put("needs_charges", "Perforating needs %s charges (one per metre of gun)", "Для перфорации нужно %s зарядов (по одному на метр перфоратора)");
        put("no_oil", "No oil column under this point: water, gas or outside the contact", "Под этой точкой нет нефти: вода, газ или за контуром");
        put("status", "%s · oil %s m³/d · water cut %s %% · GOR %s · buffer %s MPa · reservoir %s MPa",
                "%s · нефть %s м³/сут · обводнённость %s %% · газовый фактор %s · буфер %s МПа · пласт %s МПа");
        put("mode.shut_in", "shut in", "закрыта");
        put("mode.flowing", "flowing", "фонтан");
        put("mode.dead", "not flowing: needs a pumpjack", "не фонтанирует: нужен станок-качалка");
        put("mode.pumping", "rod pumping", "ШГН");
        put("missing_tree", "no Christmas tree on the wellhead", "на устье нет фонтанной арматуры");
        put("missing_separator", "no separator within 8 blocks", "нет сепаратора в 8 блоках");
        put("column_missing", "Column needs a tube furnace within 6 blocks and at least %s tray sections under the top (found %s)",
                "Колонне нужна трубчатая печь в 6 блоках и не меньше %s тарельчатых секций под верхом (найдено %s)");
        put("column_status", "Feed %s m³/h · %s trays · reflux %s · outlet %s °C · fuel %s nm³/h · gasoline/kerosene overlap %s K",
                "Сырьё %s м³/ч · тарелок %s · флегмовое число %s · выход печи %s °C · топливо %s нм³/ч · перекрытие бензин/керосин %s К");
    }

    static {
        // ШГН 09 §9.6.5
        put("rod_part", "Sucker rods parted at %s m: the load fell to the rods above. Fish them and replace the rod (sucker rod on the wellhead)",
                "Обрыв штанг на %s м: нагрузка упала до веса штанг выше обрыва. Ловильные работы и замена штанги (штанга на устье)");
        put("rods_fixed", "Rod string fished and repaired", "Колонна штанг извлечена и отремонтирована");
        put("pump_replaced", "Downhole pump replaced: new valves", "Глубинный насос заменён: новые клапаны");
        put("gearbox_fixed", "Gearbox repaired: new gears", "Редуктор отремонтирован: новые шестерни");
        put("echo", "Echo after %s s: fluid level at %s m", "Эхо через %s с: уровень жидкости на %s м");
        // газлифт 09 §9.6.4
        put("valve_set", "Gas lift valve: %s m, opens at %s MPa", "Газлифтный клапан: %s м, открытие %s МПа");
        put("valve_tip", "Depth %s m · opening pressure %s MPa (use: depth, Shift: pressure)",
                "Глубина %s м · давление открытия %s МПа (ПКМ — глубина, Shift — давление)");
        put("valve_installed", "Gas lift valve %s installed at %s m (opens at %s MPa)", "Газлифтный клапан %s установлен на %s м (открытие %s МПа)");
        put("valve_full", "No room for more gas lift valves (%s)", "Больше газлифтных клапанов не поставить (%s)");
        put("valves_pulled", "Tubing pulled: %s gas lift valves out", "НКТ подняты: вынуто газлифтных клапанов — %s");
        put("mode.gas_lift", "gas lift", "газлифт");
        // ППД 09 §9.4.6
        put("mode.injecting", "water injection", "нагнетательная (ППД)");
        put("injector_on", "Injector: water from the pipes goes into the pay at %s MPa on the head", "Нагнетательная: вода из труб уходит в пласт при %s МПа на устье");
        put("injector_off", "Back to production", "Снова добывающая");
    }

    private ProductionText() {
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
