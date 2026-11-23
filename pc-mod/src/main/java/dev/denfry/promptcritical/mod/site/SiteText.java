package dev.denfry.promptcritical.mod.site;

import java.util.LinkedHashMap;
import java.util.Map;

/** Тексты стройплощадки (en, ru). */
public final class SiteText {
    public static final Map<String, String[]> LANG = new LinkedHashMap<>();
    private static final String P = "promptcritical.site.";
    public static final String UNKNOWN = P + "unknown", BLOCKED = P + "blocked", NO_GROUND = P + "no_ground", EMPTY = P + "empty",
            STAGE = P + "stage", ERECTING = P + "erecting", NEED = P + "need", ACCEPTED = P + "accepted";

    static {
        put(UNKNOWN, "This drawing has no construction plan", "По этому чертежу стройка не предусмотрена");
        put(BLOCKED, "The site is not free: %s at %s", "Место занято: %s в %s");
        put(NO_GROUND, "No solid ground under the foundation at %s", "Нет твёрдого основания под фундаментом в %s");
        put(EMPTY, "Empty construction site: use a design drawing on it", "Пустая стройплощадка: приложите к ней чертёж");
        put(STAGE, "Stage %s of %s: %s (%s)", "Этап %s из %s: %s (%s)");
        put(ERECTING, "Erecting, about %s s left", "Идёт монтаж, осталось около %s с");
        put(NEED, "Deliver:", "Привезти:");
        put(ACCEPTED, "Drawing accepted: the site is marked out", "Чертёж принят: площадка размечена");
        stage("foundation", "foundation", "фундамент");
        stage("vessel", "vessel", "корпус");
        stage("upper_unit", "upper unit and drives", "верхний блок и приводы");
        stage("piping", "main circulation piping", "главный циркуляционный трубопровод");
        stage("shell", "shell", "корпус");
        stage("tubes", "tube bundle", "трубный пучок");
        stage("casing", "pump casing", "корпус насоса");
        stage("drive", "motor and flywheel", "электродвигатель и маховик");
        stage("pedestal", "pedestal", "пьедестал");
        stage("drives", "control rod drives", "приводы СУЗ");
        stage("vault", "reactor vault", "свод реактора");
        stage("calandria", "calandria and end shields", "каландр и торцевые экраны");
        stage("end_fittings", "fuel channels, end fittings and feeders", "каналы, концевые фитинги и питатели");
        stage("fuelling", "fuelling machine bridge", "мост перегрузочной машины");
        stage("shaft", "concrete shaft", "бетонная шахта");
        stage("pool_equipment", "pumps and intermediate heat exchangers", "насосы и промежуточные теплообменники");
        stage("roof", "roof, rotating plugs and drives", "крыша, поворотные пробки и приводы");
        stage("graphite", "graphite stack", "графитовая кладка");
        stage("channels", "channels, top plate and rod drives", "каналы, верхняя плита и приводы СУЗ");
        stage("setting", "brick setting and flues", "кирпичная обмуровка и газоходы");
        stage("mountings", "mountings, fire doors and grates", "арматура, топочные дверцы и колосники");
        put("block.promptcritical.construction_site", "Construction Site", "Стройплощадка");
        drawing("vver_reactor", "VVER-1000 Reactor", "реактора ВВЭР-1000");
        drawing("vver_steam_generator", "PGV-1000 Steam Generator", "парогенератора ПГВ-1000");
        drawing("vver_coolant_pump", "GCN-195 Reactor Coolant Pump", "ГЦН-195");
        drawing("vver_pressurizer", "VVER-1000 Pressurizer", "компенсатора давления ВВЭР-1000");
        drawing("bwr240_reactor", "BWR-240 Boiling Water Reactor", "кипящего реактора BWR-240");
        drawing("candu6_reactor", "CANDU 6 Reactor Assembly", "реакторной сборки CANDU 6");
        drawing("bn600_reactor", "BN-600 Sodium Fast Reactor", "реактора БН-600");
        drawing("am1_reactor", "AM-1 Channel Power Reactor", "реактора АМ-1");
        drawing("lancashire_boiler", "Lancashire Boiler", "ланкаширского котла");
        drawing("headframe_steel", "Steel Headframe", "стального копра");
        drawing("mill_engine", "Horizontal Mill Engine", "горизонтальной паровой машины");
        stage("bed", "bed, cylinder and crosshead guides", "станина, цилиндр и параллели");
        stage("flywheel", "crankshaft, flywheel and governor", "коленвал, маховик и регулятор");
        stage("tower", "shaft tower and back struts", "башня над стволом и подкосы");
        stage("sheave_deck", "sheave deck and sheave", "площадка и шкив");
    }

    private SiteText() {}

    static String stageKey(String key) {
        return P + "stage." + key;
    }

    private static void stage(String key, String en, String ru) {
        put(stageKey(key), en, ru);
    }

    private static void drawing(String id, String en, String ru) {
        put("item.promptcritical." + id + "_drawing", "Design Drawing: " + en, "Чертёж " + ru);
    }

    private static void put(String key, String en, String ru) {
        LANG.put(key, new String[] {en, ru});
    }
}
