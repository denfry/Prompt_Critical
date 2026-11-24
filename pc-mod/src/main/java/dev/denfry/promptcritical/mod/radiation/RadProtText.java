package dev.denfry.promptcritical.mod.radiation;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Тексты узлов радзащиты реального масштаба (en_us, ru_ru): горячая камера, контейнер ОЯТ, тележка, модуль хранения. */
public final class RadProtText {
    private static final String P = "message.promptcritical.radprot.";
    private static final Map<String, String[]> LANG = new LinkedHashMap<>();

    static {
        put("no_power", "No power: the cell lamps, ventilation and manipulator drives are off",
                "Нет питания: лампы камеры, вентиляция и приводы манипуляторов выключены");
        put("cell.status", "Hot cell: %s items, %s GBq · %s on the work table at 1 m · %s at the operator's window",
                "Горячая камера: предметов %s, %s ГБк · %s на столе в 1 м · %s у окна оператора");
        put("cell.empty", "Hot cell: empty. Pass irradiated items in through the transfer port, take them out sneaking",
                "Горячая камера пуста. Облучённые предметы — через шлюз, забрать — с Shift");
        put("cell.full", "The cell is full", "Камера заполнена");
        put("cask.status", "Cask %s: %s of %s assemblies, %s W · surface %s, at 2 m %s",
                "Контейнер %s: ТВС %s из %s, %s Вт · поверхность %s, в 2 м %s");
        put("cask.open", "open", "открыт");
        put("cask.closed", "closed, lids bolted", "закрыт, крышки затянуты");
        put("cask.refused", "The lid will not close: %s. Limits: surface 2 mSv/h, 0.1 mSv/h at 2 m, heat 35 kW",
                "Крышка не закрывается: %s. Пределы: поверхность 2 мЗв/ч, 0,1 мЗв/ч в 2 м, тепло 35 кВт");
        put("cask.reason.heat", "decay heat %s kW", "остаточное тепло %s кВт");
        put("cask.reason.dose", "dose rate %s at the surface", "мощность дозы %s на поверхности");
        put("cask.closed_now", "Lids bolted, protective plate on: the cask may travel",
                "Крышки затянуты, защитная плита на месте: контейнер можно перевозить");
        put("cask.opened_now", "Protective plate and lids off", "Защитная плита и крышки сняты");
        put("cask.sealed", "The cask is closed: open it (sneak, empty hand) to load", "Контейнер закрыт: откройте его (Shift, пустая рука), чтобы загрузить");
        put("cart.status", "Cask cart: %s", "Тележка контейнера: %s");
        put("cart.empty", "empty", "пустая");
        put("cart.loaded", "cask with %s assemblies, %s W", "контейнер, ТВС %s, %s Вт");
        put("cart.no_cask", "No closed cask within %s m of the cart", "Нет закрытого контейнера в пределах %s м от тележки");
        put("cart.no_module", "No empty dry storage module within %s m of the cart", "Нет пустого модуля сухого хранения в пределах %s м от тележки");
        put("cart.picked", "The cask is lifted onto the cradle and strapped down", "Контейнер поднят на ложемент и закреплён");
        put("cart.pushed", "The canister is pushed into the module and the shield door closed", "Пенал задвинут в модуль, защитная дверь закрыта");
        put("cart.retrieved", "The canister is pulled back into the cask on the cart", "Пенал вытянут обратно в контейнер на тележке");
        put("module.status", "Dry storage module: %s assemblies, %s W · cladding %s °C (limit 400) · vents open %s%%",
                "Модуль сухого хранения: ТВС %s, %s Вт · оболочки %s °C (предел 400) · вентканалы открыты на %s %%");
        put("module.empty", "Dry storage module: empty. Bring a loaded cask on the cart to the canister port",
                "Модуль сухого хранения пуст. Подвезите загруженный контейнер на тележке к порту пенала");
        put("module.hot", "DRY STORAGE MODULE OVERHEATING: cladding %s °C. Clear the air inlets and outlets",
                "ПЕРЕГРЕВ МОДУЛЯ СУХОГО ХРАНЕНИЯ: оболочки %s °C. Освободите входы и выходы воздуха");
    }

    private RadProtText() {
    }

    private static void put(String key, String en, String ru) {
        LANG.put(P + key, new String[] {en, ru});
    }

    public static MutableComponent msg(String key, Object... args) {
        return Component.translatable(P + key, args);
    }

    static String num(double v) {
        return String.format(Locale.ROOT, "%.3g", v);
    }

    public static Map<String, String[]> lang() {
        return LANG;
    }
}
