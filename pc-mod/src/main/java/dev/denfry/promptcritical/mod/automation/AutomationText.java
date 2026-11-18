package dev.denfry.promptcritical.mod.automation;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Сообщения автоматики игроку: ключ → {английский, русский}; язык генерирует datagen. */
public final class AutomationText {
    private static final String P = "message.promptcritical.automation.";
    public static final Map<String, String[]> LANG = new LinkedHashMap<>();

    public static final String WIRE_FROM = t("wire_from", "Terminal %s — now click the other terminal", "Клемма %s — теперь кликните вторую клемму");
    public static final String WIRE_DONE = t("wire_done", "Wired %s → %s (%s m)", "Провод %s → %s (%s м)");
    public static final String WIRE_TYPE = t("wire_type", "Incompatible terminals: %s and %s", "Клеммы несовместимы: %s и %s");
    public static final String WIRE_LONG = t("wire_long", "Too far: %s m (at most %s m)", "Слишком далеко: %s м (не больше %s м)");
    public static final String WIRE_SAME = t("wire_same", "Pick another terminal", "Выберите другую клемму");
    public static final String WIRE_EXISTS = t("wire_exists", "These terminals are already wired", "Эти клеммы уже соединены");
    public static final String WIRE_CANCEL = t("wire_cancel", "Wiring cancelled", "Провод отменён");
    public static final String WIRE_REMOVED = t("wire_removed", "Removed %s wire(s) from %s", "Снято проводов с %s: %s");
    public static final String NO_TERMINAL = t("no_terminal", "Aim at a terminal", "Наведите на клемму");
    public static final String TERMINAL = t("terminal", "%s · %s", "%s · %s");
    public static final String TYPE_DISCRETE = t("type.discrete", "24 V DC", "24 В пост.");
    public static final String TYPE_CURRENT = t("type.current", "4–20 mA", "4–20 мА");
    public static final String TYPE_VOLTAGE = t("type.voltage", "±10 V", "±10 В");
    public static final String KEY_ARMED = t("key_armed", "Key selected — turn right (sneak) to ON, left to OFF within 5 s",
            "Ключ выбран — поверните вправо (Shift) на ВКЛ или влево на ОТКЛ в течение 5 с");
    public static final String KEY_ON = t("key_on", "Key: ON", "Ключ: ВКЛ");
    public static final String KEY_OFF = t("key_off", "Key: OFF", "Ключ: ОТКЛ");
    public static final String SELECTOR = t("selector", "Selector: position %s", "Переключатель: положение %s");
    public static final String STATUS = t("status", "%s", "%s");
    public static final String RELAY_STATUS = t("relay_status", "Relay %s · %s operations", "Реле %s · коммутаций %s");
    public static final String LAMP_STATUS = t("lamp_status", "Lamp %s", "Лампа %s");
    public static final String ON = t("on", "on", "горит");
    public static final String OFF = t("off", "off", "не горит");
    public static final String INTERFACE_STATUS = t("interface_status", "Coil %s · redstone in %s", "Катушка %s · редстоун на входе %s");
    public static final String REPLACED = t("replaced", "Element replaced", "Элемент заменён");
    public static final String CALIBRATED = t("calibrated", "Integrator zero balanced", "Нуль интегратора сбалансирован");
    public static final String CALIBRATE_REFUSED = t("calibrate_refused", "Put the integrator in HOLD or IC first",
            "Сначала переведите интегратор в HOLD или IC");

    private AutomationText() {
    }

    // программатор ПЛК (A3)
    public static final String PLC_TITLE = t("plc.title", "PLC programmer · Instruction List", "Программатор ПЛК · список инструкций");
    public static final String PLC_UPLOAD = t("plc.upload", "Load into PLC", "Загрузить в ПЛК");
    public static final String PLC_RUN = t("plc.run", "Key: RUN", "Ключ: RUN");
    public static final String PLC_STOP = t("plc.stop", "Key: STOP", "Ключ: STOP");
    public static final String PLC_MODE = t("plc.mode", "Mode: %s", "Режим: %s");
    public static final String PLC_SCAN = t("plc.scan", "Last scan: %s instructions", "Последний цикл: %s инструкций");
    public static final String PLC_INPUTS = t("plc.inputs", "Inputs %%IX0.0–7", "Входы %%IX0.0–7");
    public static final String PLC_OUTPUTS = t("plc.outputs", "Outputs %%QX0.0–7", "Выходы %%QX0.0–7");
    public static final String PLC_ANALOG = t("plc.analog", "%%IW: %s  %%QW: %s", "%%IW: %s  %%QW: %s");
    public static final String PLC_ERROR = t("plc.error", "Line %s: %s %s", "Строка %s: %s %s");
    public static final String PLC_OK = t("plc.ok", "Program compiled without errors", "Программа скомпилирована без ошибок");
    public static final String PLC_UNSAVED = t("plc.unsaved", "Edited — load into the PLC to apply", "Изменено — загрузите в ПЛК");
    public static final String[] PLC_MODES = {
            t("plc.mode.off", "OFF — no 24 V", "ВЫКЛ — нет 24 В"),
            t("plc.mode.stop", "STOP", "STOP"),
            t("plc.mode.run", "RUN", "RUN"),
            t("plc.mode.fault", "FAULT — watchdog; turn the key to STOP and back", "АВАРИЯ — сторожевой таймер; ключ в STOP и обратно")};

    static {
        String[][] errors = {
                {"UNKNOWN_OPERATOR", "unknown operator", "неизвестный оператор"},
                {"BAD_OPERAND", "bad operand", "неверный операнд"},
                {"NOT_WRITABLE", "cannot write to", "нельзя записать в"},
                {"MISSING_OPERAND", "operand missing after", "нет операнда после"},
                {"UNEXPECTED_OPERAND", "unexpected operand", "лишний операнд"},
                {"UNKNOWN_LABEL", "no such label", "нет такой метки"},
                {"DUPLICATE_LABEL", "label used twice", "метка повторяется"},
                {"UNBALANCED_PARENTHESIS", "unbalanced bracket", "непарная скобка"},
                {"NESTING_TOO_DEEP", "brackets nested too deep", "слишком глубокие скобки"},
                {"BAD_CALL", "bad block call", "неверный вызов блока"},
                {"TOO_LONG", "program too long", "программа слишком длинная"}};
        for (String[] e : errors) t("plc.err." + e[0], e[1], e[2]);
    }

    /** Ключ перевода вида ошибки компиляции IL. */
    public static String plcError(String kind) {
        return P + "plc.err." + kind;
    }

    private static String t(String key, String en, String ru) {
        LANG.put(P + key, new String[] {en, ru});
        return P + key;
    }

    public static MutableComponent msg(String key, Object... args) {
        return Component.translatable(key, args);
    }
}
