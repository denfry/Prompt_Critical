package dev.denfry.promptcritical.mod.grid;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Сообщения электросети игроку: ключ → {английский, русский}; язык генерирует datagen. */
public final class GridText {
    private static final String P = "message.promptcritical.grid.";
    public static final Map<String, String[]> LANG = new LinkedHashMap<>();

    public static final String BREAKER_RESPONSE = t("breaker_response", "Breaker: %s", "Выключатель: %s");
    public static final String ACCEPTED = t("response.accepted", "command accepted", "команда принята");
    public static final String INTERLOCK = t("response.interlock", "interlock — not permitted now", "блокировка — сейчас нельзя");
    public static final String NO_CONTROL_POWER = t("response.no_control_power", "no control power", "нет оперативного тока");
    public static final String BUSY = t("response.busy", "mechanism is moving", "привод в ходе");
    public static final String UNAVAILABLE = t("response.unavailable", "out of service — repair needed", "неисправен — нужен ремонт");
    public static final String ARC = t("arc", "Arc! The disconnector was opened under load", "Дуга! Разъединитель отключили под нагрузкой");
    public static final String EARTHED = t("earthed", "Earthing switch closed", "Заземляющие ножи включены");
    public static final String UNEARTHED = t("unearthed", "Earthing switch opened", "Заземляющие ножи отключены");
    public static final String EARTH_REFUSED = t("earth_refused", "Open the disconnector before earthing",
            "Сначала отключите разъединитель");
    public static final String INTERLOCK_FITTED = t("interlock_fitted", "Interlock module fitted: the disconnector is blocked while an adjacent breaker is closed",
            "Модуль блокировки установлен: разъединитель заблокирован при включённом выключателе");
    public static final String FUSE_FITTED = t("fuse_fitted", "Fuse fitted (%s A)", "Вставка установлена (%s А)");
    public static final String FUSE_REMOVED = t("fuse_removed", "Fuse removed — visible break", "Вставка вынута — видимый разрыв");
    public static final String FUSE_BLOWN = t("fuse_blown", "The fuse has blown — fit a new one", "Вставка перегорела — поставьте новую");
    public static final String COUPLING_FITTED = t("coupling_fitted", "Shear-pin coupling fitted", "Муфта со срезным штифтом установлена");
    public static final String COUPLING_SHEARED = t("coupling_sheared", "The shear pin is broken — fit a new coupling",
            "Срезной штифт сломан — поставьте новую муфту");
    public static final String SHAFT_BROKEN = t("shaft_broken", "The generator shaft is broken — replace the generator",
            "Вал генератора сломан — замените генератор");
    public static final String STOP_FIRST = t("stop_first", "Stop the machine first", "Сначала остановите машину");
    public static final String VOLTAGE_CLASS = t("voltage_class", "Rated voltage: %s kV", "Номинальное напряжение: %s кВ");
    public static final String GEAR = t("gear", "Gear: %s RPM", "Передача: %s об/мин");
    public static final String WIRE_START = t("wire_start", "Span started — use the coil on the other connector",
            "Пролёт начат — кликните катушкой по второму коннектору");
    public static final String WIRE_DONE = t("wire_done", "Span strung: %s m", "Провод натянут: %s м");
    public static final String WIRE_TOO_LONG = t("wire_too_long", "Span too long: %s m (at most %s for this class)",
            "Пролёт слишком длинный: %s м (не больше %s для класса)");
    public static final String WIRE_CLASS = t("wire_class", "Connectors of different voltage classes cannot be joined",
            "Коннекторы разных классов напряжения не соединяются");
    public static final String WIRE_KIND = t("wire_kind", "This wire does not fit this connector", "Этот провод не для этого коннектора");
    public static final String WIRE_SAME = t("wire_same", "Pick another connector", "Выберите другой коннектор");
    public static final String WIRE_EXISTS = t("wire_exists", "These connectors are already joined", "Эти коннекторы уже соединены");
    public static final String WIRE_CANCEL = t("wire_cancel", "Span cancelled", "Пролёт отменён");
    public static final String WIRE_BROKEN = t("wire_broken", "An overhead conductor broke from overheating", "Провод ВЛ оборвался от перегрева");
    public static final String TAP = t("tap", "Off-load tap: %s (%s %%)", "ПБВ: ступень %s (%s %%)");
    public static final String TAP_LIVE = t("tap_live", "De-energize the transformer before changing taps",
            "Перед переключением ПБВ снимите напряжение");
    public static final String METER = t("meter", "Energy: %s kWh delivered, %s kWh returned · %s kW now",
            "Энергия: отпущено %s кВт·ч, принято %s кВт·ч · сейчас %s кВт");
    public static final String NO_SWITCH = t("no_switch", "Mount the instrument on a switchgear cell or switchboard",
            "Повесьте прибор на ячейку КРУ или щит");
    public static final String MODULE_REFUSED = t("module_refused", "This module does not fit here", "Этот модуль сюда не подходит");

    private GridText() {
    }

    private static String t(String key, String en, String ru) {
        LANG.put(P + key, new String[] {en, ru});
        return P + key;
    }

    public static MutableComponent msg(String key, Object... args) {
        return Component.translatable(key, args);
    }
}
