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
        drawing("hot_blast_stove_real", "Cowper Hot Blast Stove", "воздухонагревателя Каупера");
        stage("stove_shell", "riveted shell and dome", "клёпаная обечайка и купол");
        stage("checker", "combustion chamber and firebrick checker", "камера горения и шамотная насадка");
        stage("stove_valves", "hot blast, cold blast, chimney valves and burner", "клапаны горячего и холодного дутья, дымовой клапан и горелка");
        drawing("blast_blower_engine", "Horizontal Blowing Engine", "горизонтальной воздуходувной машины");
        stage("blowing_tub", "bed, blowing tub and crosshead guides", "станина, воздушный цилиндр и параллели");
        stage("blower_flywheel", "crankshaft, flywheel and bevel drive", "коленвал, маховик и коническая передача");
        drawing("deaerator_real", "DA-25 Atmospheric Deaerator", "атмосферного деаэратора ДА-25");
        stage("saddles", "concrete saddles", "бетонные опоры");
        stage("storage_tank", "storage tank and cladding", "бак-аккумулятор и обшивка");
        stage("deaerating_column", "deaerating column, platform and piping", "деаэрационная колонка, площадка и трубопроводы");
        drawing("ion_exchange_column_real", "Sodium Cation Softening Filter", "Na-катионитового фильтра");
        stage("filter_vessel", "vessel on legs with dished heads", "корпус на опорах с днищами");
        stage("valve_nest", "headers, valves and brine line", "коллекторы, задвижки и солевая линия");
        drawing("shaft_generator_real", "500 kVA Open-Frame Alternator", "генератора 500 кВ·А открытого исполнения");
        stage("bedplate", "bedplate and pedestal bearings", "фундаментная плита и стояковые подшипники");
        stage("stator", "stator frame, core and windings", "станина статора, сердечник и обмотки");
        stage("field", "revolving field, slip rings and brush gear", "вращающийся индуктор, кольца и щёткодержатели");
        drawing("electric_motor_mv_real", "1 MW 6 kV Induction Motor", "асинхронного двигателя 1 МВт 6 кВ");
        stage("motor_frame", "frame, stator core and 6 kV winding", "станина, сердечник статора и обмотка 6 кВ");
        stage("motor_rotor", "squirrel-cage rotor and shaft", "короткозамкнутый ротор и вал");
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
        drawing("bof_vessel_real", "LD Converter", "кислородного конвертера");
        stage("converter_stand", "trunnion ring, pedestals and bearings", "цапфовое кольцо, опоры и подшипники");
        stage("tilt_drive_hood", "tilt drive, hood, gas duct and platform", "привод наклона, камин, газоход и площадка");
        drawing("reheating_furnace_real", "Continuous Reheating Furnace", "методической нагревательной печи");
        stage("furnace_brickwork", "base, walls, hearth and sprung arch", "основание, стены, под и распорный свод");
        stage("furnace_ironwork", "buckstays, tie rods and doors", "стойки каркаса, стяжки и заслонки");
        stage("pusher_burner", "pusher, burner and flue damper", "толкатель, горелка и шибер борова");
        drawing("electric_arc_furnace_real", "Héroult Arc Furnace", "трёхэлектродной дуговой печи");
        drawing("ladle_station_real", "Ladle Treatment Station", "установки доводки в ковше");
        drawing("ingot_mold_real", "Big-end-down Ingot Mould", "изложницы");
        drawing("heat_treatment_furnace_real", "Car-bottom Heat Treatment Furnace", "термической печи с выкатным подом");
        drawing("pipe_welder_real", "HF Induction Tube Mill", "трубоэлектросварочного стана ТВЧ");
        drawing("piercing_mill_real", "Mannesmann Piercing Mill", "прошивного стана Маннесмана");
        stage("electrode_gear", "electrode masts, arms, clamps and bus", "стойки, рукава, зажимы электродов и шины");
        stage("station_frame", "portal, alloy bins and argon lance", "портал, бункера ферросплавов и аргонная фурма");
        stage("ladle_car", "ladle car and rails", "тележка ковша и рельсы");
        stage("ingot_mold_casting", "cast iron mould and stool", "чугунная изложница и поддон");
        stage("casing_car", "casing, buckstays, car and door", "кожух, каркас, выкатной под и дверь");
        stage("burners", "burners and gas piping", "горелки и газопровод");
        stage("forming_stands", "forming and fin-pass stands", "формовочные и калибрующие клети");
        stage("weld_box", "weld box, HF generator and squeeze rolls", "сварочный узел, генератор ТВЧ и обжимные валки");
        stage("mill_housing", "housing, rolls, pinion stand and spindles", "станина, валки, шестерённая клеть и шпиндели");
        stage("run_out", "entry trough, mandrel bar, thrust block and exit table", "входной жёлоб, стержень, упор и выходной стол");
        drawing("molding_station_real", "Jolt-Squeeze Moulding Machine", "встряхивающе-прессовой формовочной машины");
        stage("jolt_squeeze", "base, jolt cylinder, table and swing yoke", "станина, цилиндр встряхивания, стол и поворотная траверса");
        stage("sand_stand", "drive box, compressor and sand hopper stand", "коробка привода, компрессор и эстакада бункера песка");
        drawing("shakeout_grate_real", "Vibrating Shakeout Grate", "инерционной выбивной решётки");
        stage("shakeout_deck", "springs, deck frame, bars and vibrator", "пружины, рама настила, колосники и вибратор");
        stage("dust_hood", "side-draft dust hood and duct", "боковой отсос пыли и воздуховод");
        drawing("machine_lathe_real", "16-inch Engine Lathe", "токарно-винторезного станка");
        drawing("drill_press_real", "24-inch Upright Drill", "вертикально-сверлильного станка");
        drawing("milling_machine_real", "Plain Horizontal Knee Mill", "горизонтально-фрезерного станка");
        drawing("shaping_machine_real", "16-inch Crank Shaper", "поперечно-строгального станка");
        drawing("surface_grinder_real", "Belt-driven Surface Grinder", "плоскошлифовального станка");
        stage("machine_frame", "bed, column, slides and spindle", "станина, стойка, направляющие и шпиндель");
        stage("countershaft", "hanger columns, countershaft and belt", "стойки, контрпривод и ремень");
        drawing("degasification_pump_real", "Liquid-Ring Degasification Pump", "водокольцевого дегазационного насоса");
        stage("vacuum_pump", "bed, pump casing, rotor and coupling", "станина, корпус насоса, ротор и муфта");
        stage("separator", "gas-water separator, flame arrester and piping", "сепаратор газ-вода, огнепреградитель и трубопроводы");
        drawing("forced_draft_fan_real", "Forced Draught Blower", "дутьевого вентилятора ВДН");
        drawing("induced_draft_fan_real", "Induced Draught Smoke Exhauster", "дымососа ДН");
        stage("fan_casing", "scroll casing, guide vanes and inlet", "улитка, направляющий аппарат и вход");
        stage("impeller", "impeller, shaft, bearing pedestal and coupling", "рабочее колесо, вал, опора и муфта");
        drawing("magnetic_separator_real", "Wet Drum Magnetic Separator", "барабанного магнитного сепаратора");
        stage("separator_tank", "tank, frame, feed box and launders", "ванна, рама, питающая коробка и лотки");
        stage("separator_drum", "drum, magnet system and worm reducer", "барабан, магнитная система и червячный редуктор");
        drawing("flotation_cell_real", "Mechanical Flotation Cell", "механической флотокамеры");
        stage("cell_tank", "cell, froth launder and chamber box", "камера, пенный желоб и карман");
        stage("aerator", "bridge, standpipe, impeller, motor and froth scraper", "мост, стояк, импеллер, двигатель и пеногон");
        drawing("electrode_boiler_real", "6.3 kV Electrode Boiler", "электродного котла 6,3 кВ");
        stage("hv_terminals", "HV bushings, leads, cable box and control cabinet", "вводы высокого напряжения, шины, кабельная коробка и шкаф управления");
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
