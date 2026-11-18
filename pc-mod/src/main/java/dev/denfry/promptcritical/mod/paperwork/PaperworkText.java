package dev.denfry.promptcritical.mod.paperwork;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Тексты документов завода: ключ → {английский, русский}; язык генерирует datagen. */
public final class PaperworkText {
    private static final String P = "message.promptcritical.paperwork.";
    public static final Map<String, String[]> LANG = new LinkedHashMap<>();

    public static final String STAMP = t("stamp", "day %s, %s", "сутки %s, %s");
    // вахтенный журнал
    public static final String LOG_TITLE = t("log.title", "Shift log", "Оперативный журнал");
    public static final String LOG_WRITTEN = t("log.written", "Written in the log: %s (%s readings)", "Записано в журнал: %s (показаний: %s)");
    public static final String LOG_NO_READINGS = t("log.no_readings", "Nothing to read here: the instruments are dark",
            "Записывать нечего: приборы не показывают");
    public static final String LOG_FULL = t("log.full", "The log is full: hand it to the archive and start a new one",
            "Журнал заполнен: сдайте его в архив и заведите новый");
    public static final String LOG_SIGNED = t("log.signed", "Shift handed over and signed: %s entries this shift",
            "Смена сдана, подпись поставлена: записей за смену — %s");
    public static final String LOG_NOTHING_TO_SIGN = t("log.nothing_to_sign", "No entries this shift — nothing to hand over",
            "За смену нет записей — сдавать нечего");
    public static final String LOG_HANDOVER = t("log.handover", "Shift handed over: %s", "Смену сдал: %s");
    public static final String LOG_EMPTY = t("log.empty", "The log is empty. Walk the plant: right-click a machine with the log.",
            "Журнал пуст. Обойдите цех: правый клик журналом по машине.");
    public static final String LOG_TOOLTIP = t("log.tooltip", "%s entries · shift: %s · Shift + use: hand over the shift",
            "Записей: %s · за смену: %s · Shift + ПКМ: сдать смену");
    // наряд-допуск
    public static final String PERMIT_TITLE = t("permit.title", "Work permit No. %s", "Наряд-допуск № %s");
    public static final String PERMIT_BLANK = t("permit.blank", "Blank work permit: right-click the breaker or disconnector that makes the break",
            "Бланк наряда-допуска: правый клик по выключателю или разъединителю, которым создан разрыв");
    public static final String PERMIT_FORM = t("permit.form", "for work in an electrical installation", "на работу в электроустановке");
    public static final String PERMIT_APPARATUS = t("permit.apparatus", "Break made by: %s at %s", "Разрыв: %s, %s");
    public static final String PERMIT_ISSUED_LINE = t("permit.issued_line", "Issued by %s, %s", "Выдал %s, %s");
    public static final String PERMIT_MEASURES = t("permit.measures",
            "Measures: switch off and lock the drive; hang «Do not switch on! People at work»; prove dead; earth",
            "Меры: отключить и запереть привод; вывесить «Не включать! Работают люди»; проверить отсутствие напряжения; заземлить");
    public static final String PERMIT_ADMITTED_LINE = t("permit.admitted_line", "Brigade admitted by %s", "Бригаду допустил %s");
    public static final String PERMIT_CLOSED_LINE = t("permit.closed_line", "Permit closed by %s", "Наряд закрыл %s");
    public static final String PERMIT_DUPLICATE = t("permit.duplicate", "(closed on the second copy)", "(закрыт по второму экземпляру)");
    public static final String PERMIT_NOT_YET = t("permit.not_yet", "—", "—");
    public static final String PERMIT_ISSUED = t("permit.issued", "Work permit No. %s written: the apparatus is locked open and the poster hangs",
            "Наряд-допуск № %s выписан: аппарат заперт в отключённом положении, плакат вывешен");
    public static final String PERMIT_NOT_OPEN = t("permit.not_open", "Switch it off first: a permit is written on an open apparatus",
            "Сначала отключите: наряд выписывают на отключённый аппарат");
    public static final String PERMIT_NOT_SWITCH = t("permit.not_switch", "A permit is written on a breaker or a disconnector",
            "Наряд выписывают на выключатель или разъединитель");
    public static final String PERMIT_OTHER = t("permit.other", "This permit is for another apparatus (%s)", "Этот наряд — на другой аппарат (%s)");
    public static final String PERMIT_UNMET = t("permit.unmet", "No admission yet: %s", "Допуск нельзя: %s");
    public static final String PERMIT_ADMITTED = t("permit.admitted", "Workplace prepared — brigade admitted. Shift + use to close the permit when done",
            "Рабочее место подготовлено — бригада допущена. Закончив, Shift + ПКМ — закрыть наряд");
    public static final String PERMIT_CLOSED = t("permit.closed",
            "Permit No. %s closed: remove the earthing and the poster, then the apparatus may be switched on",
            "Наряд № %s закрыт: снимите заземление — после этого аппарат можно включать");
    public static final String PERMIT_CLOSE_HINT = t("permit.close_hint", "Shift + use on the apparatus to close the permit",
            "Shift + ПКМ по аппарату — закрыть наряд");
    public static final String PERMIT_ALREADY_CLOSED = t("permit.already_closed", "This permit is closed", "Наряд уже закрыт");
    public static final String PERMIT_DUPLICATE_CLOSED = t("permit.duplicate_closed",
            "Permit closed on the second copy: the lost first copy is void", "Наряд закрыт по второму экземпляру: утерянный первый недействителен");
    public static final String PERMIT_NO_TWIN = t("permit.no_twin", "Simulation is not running here", "Здесь нет симуляции");
    public static final String MEASURE_SWITCHED_OFF = t("measure.switched_off", "not switched off", "не отключён");
    public static final String MEASURE_POSTER = t("measure.poster", "no poster on the drive", "нет плаката на приводе");
    public static final String MEASURE_NO_VOLTAGE = t("measure.no_voltage", "the tester shows voltage", "указатель показывает напряжение");
    public static final String MEASURE_EARTHED = t("measure.earthed", "not earthed", "не заземлено");
    public static final String STAGE_PREPARING = t("stage.preparing", "open · preparing the workplace", "открыт · подготовка рабочего места");
    public static final String STAGE_ADMITTED = t("stage.admitted", "open · brigade at work", "открыт · бригада работает");
    public static final String STAGE_CLOSED = t("stage.closed", "closed", "закрыт");
    public static final String LECTERN_DOC = t("lectern_doc", "Shift log or work permit", "Оперативный журнал или наряд-допуск");

    private PaperworkText() {
    }

    private static String t(String key, String en, String ru) {
        LANG.put(P + key, new String[] {en, ru});
        return P + key;
    }

    public static MutableComponent msg(String key, Object... args) {
        return Component.translatable(key, args);
    }
}
