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
        drawing("winding_engine", "Steam Winding Engine", "паровой подъёмной машины");
        drawing("guibal_fan", "Guibal Fan", "вентилятора Гибаля");
        stage("fan_house", "fan pit, brick casing and évasé", "приямок, кирпичный кожух и эвазе");
        stage("rotor", "axle, arms, blades and shutter", "ось, спицы, лопатки и шибер");
        stage("fan_engine", "engine on the axle crank and its bed", "машина на кривошипе оси и её фундамент");
        drawing("cupola_furnace_real", "Foundry Cupola", "литейной вагранки");
        stage("lining", "firebrick lining", "футеровка шамотным кирпичом");
        stage("blower_platform", "blower, air chamber and charging platform", "воздуходувка, воздушная коробка и колошниковая площадка");
        drawing("mine_drainage_pump_geared", "Geared Three-Throw Ram Pump", "трёхплунжерного насоса водоотлива");
        stage("pump_bed", "cast iron bed and crankshaft standards", "чугунная станина и стойки коленвала");
        stage("gearing", "crankshaft, spur wheel and pinion", "коленвал, зубчатое колесо и шестерня");
        stage("rams", "rams, valve boxes, manifolds and air vessel", "плунжеры, клапанные коробки, коллекторы и воздушный колпак");
        stage("bed", "bed, cylinder and crosshead guides", "станина, цилиндр и параллели");
        stage("flywheel", "crankshaft, flywheel and governor", "коленвал, маховик и регулятор");
        stage("tower", "shaft tower and back struts", "башня над стволом и подкосы");
        stage("sheave_deck", "sheave deck and sheave", "площадка и шкив");
        stage("engines", "engine beds, cylinders and valve gear", "станины, цилиндры и парораспределение");
        stage("drum", "drum shaft, drum and post brake", "вал, барабан и тормоз");
        drawing("crucible_furnace_real", "Crucible Melting Hole", "тигельной горновой печи");
        stage("pit", "pit, firebrick lining and grate", "яма, футеровка и колосники");
        stage("flue", "flue, stack stub and lid", "боров, трубка и крышка");
        drawing("lancashire_stoker", "Lancashire Boiler Stokers", "механических забрасывателей ланкаширского котла");
        stage("stoker_frame", "bed plate, standards and line shaft", "фундаментная плита, стойки и линия вала");
        stage("stokers", "hoppers, feeding boxes and rams", "бункера, питательные коробки и толкатели");
        drawing("longwall_shearer_real", "Ranging Drum Shearer", "двухшнекового очистного комбайна");
        stage("face_conveyor", "armoured face conveyor pans and rack", "рештаки забойного конвейера и рейка");
        stage("shearer_body", "shearer body, haulage and cable handler", "корпус комбайна, механизм подачи и кабелеукладчик");
        stage("ranging_arms", "ranging arms, gearheads and cutting drums", "поворотные рукояти, редукторы и шнеки");
        drawing("coal_jig_real", "Baum Coal Jig", "отсадочной машины Баума");
        stage("jig_tank", "riveted tank, bed screens and hutch hoppers", "клёпаный бак, решёта постели и подрешётные бункера");
        stage("air_valves", "fan, line shaft and rotary air valves", "вентилятор, линия вала и поворотные воздушные клапаны");
        drawing("quench_tower_real", "Wet Coke Quench Tower", "башни мокрого тушения кокса");
        stage("quench_chamber", "spray chamber, header and nozzles", "камера орошения, коллектор и форсунки");
        stage("stack", "hood and steel stack", "зонт и стальная труба");
        drawing("powered_roof_support_real", "Two-Leg Shield Support", "двухстоечной щитовой секции крепи");
        stage("pontoons", "base pontoons, rear bridge and advancing ram", "основание, задний мост и домкрат передвижки");
        stage("support_legs", "hydraulic legs and valve bank", "гидростойки и блок клапанов");
        stage("canopy", "canopy, caving shield and lemniscate links", "перекрытие, ограждение и лемнискатные рычаги");
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
