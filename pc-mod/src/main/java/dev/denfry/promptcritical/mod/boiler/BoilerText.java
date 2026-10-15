package dev.denfry.promptcritical.mod.boiler;

import java.util.LinkedHashMap;
import java.util.Map;

/** Сообщения котельной игроку: ключ → {английский, русский}; язык генерирует datagen. */
public final class BoilerText {
    public static final String BOILER_STATUS = "message.promptcritical.boiler.status";
    public static final String FURNACE_LIT = "message.promptcritical.boiler.furnace_lit";
    public static final String FURNACE_OUT = "message.promptcritical.boiler.furnace_out";
    public static final String PLUG_OK = "message.promptcritical.boiler.plug_ok";
    public static final String PLUG_MELTED = "message.promptcritical.boiler.plug_melted";
    public static final String PLUG_NOT_NEEDED = "message.promptcritical.boiler.plug_not_needed";
    public static final String PLUG_UNDER_PRESSURE = "message.promptcritical.boiler.plug_under_pressure";
    public static final String PLUG_FITTED = "message.promptcritical.boiler.plug_fitted";
    public static final String BED_FULL = "message.promptcritical.boiler.bed_full";
    public static final String CHARGED = "message.promptcritical.boiler.charged";
    public static final String RUPTURED = "message.promptcritical.boiler.ruptured";
    public static final String VALVE_OPEN = "message.promptcritical.engine.valve_open";
    public static final String VALVE_CLOSED = "message.promptcritical.engine.valve_closed";
    public static final String BARRED_OVER = "message.promptcritical.engine.barred_over";
    public static final String ENGINE_STATUS = "message.promptcritical.engine.status";
    public static final String NO_BOILER = "message.promptcritical.engine.no_boiler";
    public static final String DASHPOT_FITTED = "message.promptcritical.engine.dashpot_fitted";
    public static final String DASHPOT_PRESENT = "message.promptcritical.engine.dashpot_present";
    public static final String GEAR = "message.promptcritical.engine.gear";
    public static final String STOKER_STATUS = "message.promptcritical.stoker.status";
    public static final String STOKER_NO_BOILER = "message.promptcritical.stoker.no_boiler";

    private BoilerText() {
    }

    public static Map<String, String[]> all() {
        Map<String, String[]> m = new LinkedHashMap<>();
        m.put(BOILER_STATUS, new String[] {"Fire bed %s kg, feed tank %s L, %s, %s",
                "Слой на решётке %s кг, питательный бак %s л, %s, %s"});
        m.put(FURNACE_LIT, new String[] {"furnace burning", "топка горит"});
        m.put(FURNACE_OUT, new String[] {"furnace out", "топка погашена"});
        m.put(PLUG_OK, new String[] {"fusible plug intact", "пробка цела"});
        m.put(PLUG_MELTED, new String[] {"fusible plug MELTED", "пробка ВЫПЛАВЛЕНА"});
        m.put(PLUG_NOT_NEEDED, new String[] {"The fusible plug is intact", "Легкоплавкая пробка цела"});
        m.put(PLUG_UNDER_PRESSURE, new String[] {"Blow the boiler down first: the plug is changed without pressure",
                "Сначала стравите давление: пробку меняют на котле без давления"});
        m.put(PLUG_FITTED, new String[] {"New fusible plug fitted", "Новая легкоплавкая пробка установлена"});
        m.put(BED_FULL, new String[] {"The grate is full", "Решётка заполнена"});
        m.put(CHARGED, new String[] {"Charged %s kg", "Заброшено %s кг"});
        m.put(RUPTURED, new String[] {"The boiler is wrecked", "Котёл разрушен"});
        m.put(VALVE_OPEN, new String[] {"Stop valve opened", "Стопорный вентиль открыт"});
        m.put(VALVE_CLOSED, new String[] {"Stop valve closed", "Стопорный вентиль закрыт"});
        m.put(BARRED_OVER, new String[] {"Engine barred over off dead centre", "Машина провёрнута с мёртвой точки"});
        m.put(ENGINE_STATUS, new String[] {"%s rpm, stop valve %s, output %s RPM", "%s об/мин, стопорный вентиль %s, выход %s RPM"});
        m.put(NO_BOILER, new String[] {"No boiler next to the engine", "Рядом с машиной нет котла"});
        m.put(DASHPOT_FITTED, new String[] {"Governor dashpot fitted", "Катаракт регулятора установлен"});
        m.put(DASHPOT_PRESENT, new String[] {"The governor already has a dashpot", "Катаракт уже установлен"});
        m.put(GEAR, new String[] {"Output gear: %s RPM at full speed", "Передача: %s RPM на номинальных оборотах"});
        m.put(STOKER_STATUS, new String[] {"Hopper: %s, feeding %s kg/s", "В бункере: %s, подача %s кг/с"});
        m.put(STOKER_NO_BOILER, new String[] {"The stoker must back onto a boiler", "Решётка должна примыкать задом к котлу"});
        return m;
    }
}
