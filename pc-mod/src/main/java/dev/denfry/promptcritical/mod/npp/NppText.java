package dev.denfry.promptcritical.mod.npp;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Сообщения АЭС T1 и T2a (en_us, ru_ru); ключи собирает {@code PcLanguageProvider}. */
public final class NppText {
    private static final String P = "message.promptcritical.npp.";
    private static final Map<String, String[]> LANG = new LinkedHashMap<>();

    static {
        put("scram", "REACTOR SCRAM: %s", "АВАРИЙНАЯ ЗАЩИТА РЕАКТОРА: %s");
        put("reset", "Scram rods cocked", "Стержни АЗ взведены");
        put("reset_refused", "Cannot cock the scram rods: the cause is still there", "Взвести АЗ нельзя: причина срабатывания не устранена");
        put("loaded", "Fuel loaded into channel %s", "Топливо загружено в канал %s");
        put("unloaded", "Channel %s unloaded: %s MWd/kg", "Канал %s выгружен: %s МВт·сут/кг");
        put("not_shut_down", "Refuelling only on a shut-down reactor", "Перегрузка только на заглушенном реакторе");
        put("channel_failed", "CHANNEL FAILURE: dryout broke %s channel(s), water in the graphite, activity in the hall",
                "РАЗРУШЕНИЕ КАНАЛА: кризис теплообмена разрушил каналов: %s; вода в кладке, активность в зале");
        put("protection_off", "Reactor protections BYPASSED", "Защиты реактора ВЫВЕДЕНЫ");
        put("protection_on", "Reactor protections in service", "Защиты реактора введены");
        put("pwr_unloaded", "Assembly %s unloaded: %s MWd/kg", "ТВС %s выгружена: %s МВт·сут/кг");
        put("pwr_permit", "Start-up power trip lifted", "Пусковая уставка АЗ по мощности снята");
        put("pwr_permit_refused", "The start-up trip can be lifted only above 10 % power", "Снять пусковую уставку можно только выше 10 % мощности");
        put("pwr_dnb", "DEPARTURE FROM NUCLEATE BOILING: assemblies with failed cladding: %s, activity in the coolant",
                "КРИЗИС ТЕПЛООБМЕНА: разгерметизированы оболочки ТВС: %s; активность в теплоносителе");
        put("pwr_rupture", "PRIMARY CIRCUIT RUPTURE: the pressure broke the loop", "РАЗРЫВ ПЕРВОГО КОНТУРА: давление разорвало петлю");
    }

    private NppText() {
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
