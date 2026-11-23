package dev.denfry.promptcritical.mod.data;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.foundry.FoundryText;
import dev.denfry.promptcritical.mod.manufacturing.ManufacturingText;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.LanguageProvider;

/** Локализация en_us и ru_ru (05 §5.3.7): блоки, предметы, жидкости, вкладка, команды. */
public final class PcLanguageProvider extends LanguageProvider {
    private final boolean russian;

    public PcLanguageProvider(PackOutput output, String locale) {
        super(output, PromptCritical.MOD_ID, locale);
        this.russian = locale.startsWith("ru");
    }

    /** id → {английское, русское}. */
    private static final Map<String, String[]> BLOCKS = new LinkedHashMap<>();
    private static final Map<String, String[]> ITEMS = new LinkedHashMap<>();
    private static final Map<String, String[]> FLUIDS = new LinkedHashMap<>();
    private static final Map<String, String[]> MISC = new LinkedHashMap<>();

    private static void b(String id, String en, String ru) {
        BLOCKS.put(id, new String[] {en, ru});
    }

    private static void i(String id, String en, String ru) {
        ITEMS.put(id, new String[] {en, ru});
    }

    static {
        b("fire_tube_boiler", "Fire-Tube Boiler", "Жаротрубный котёл");
        b("lancashire_boiler", "Lancashire Boiler", "Ланкаширский котёл");
        b("headframe_steel", "Steel Headframe", "Стальной копёр");
        b("mill_engine", "Horizontal Mill Engine", "Горизонтальная паровая машина");
        b("winding_engine", "Steam Winding Engine", "Паровая подъёмная машина");
        b("guibal_fan", "Guibal Fan", "Вентилятор Гибаля");
        b("cupola_furnace_real", "Foundry Cupola", "Литейная вагранка");
        b("crucible_furnace_real", "Crucible Melting Hole", "Тигельная горновая печь");
        b("deaerator_real", "DA-25 Atmospheric Deaerator", "Атмосферный деаэратор ДА-25");
        b("ion_exchange_column_real", "Sodium Cation Softening Filter", "Na-катионитовый фильтр");
        b("shaft_generator_real", "500 kVA Open-Frame Alternator", "Генератор 500 кВ·А открытого исполнения");
        b("electric_motor_mv_real", "1 MW 6 kV Induction Motor", "Асинхронный двигатель 1 МВт 6 кВ");
        b("longwall_shearer_real", "Ranging Drum Shearer", "Двухшнековый очистной комбайн");
        b("coal_jig_real", "Baum Coal Jig", "Отсадочная машина Баума");
        b("quench_tower_real", "Wet Coke Quench Tower", "Башня мокрого тушения кокса");
        b("powered_roof_support_real", "Two-Leg Shield Support", "Двухстоечная щитовая секция крепи");
        b("lancashire_stoker", "Lancashire Boiler Stokers", "Механические забрасыватели ланкаширского котла");
        b("grate_stoker", "Chain Grate Stoker", "Цепная колосниковая решётка");
        b("chimney", "Chimney", "Дымовая труба");
        b("chimney_stack", "Brick Chimney Stack", "Кирпичная дымовая труба");
        b("mine_drainage_pump_geared", "Geared Three-Throw Ram Pump", "Трёхплунжерный насос водоотлива с передачей");
        b("steam_engine_cylinder", "Steam Engine", "Паровая машина");
        b("magnetite_ore", "Magnetite Ore", "Магнетитовая руда");
        b("hematite_ore", "Hematite Ore", "Гематитовая руда");
        b("pyrolusite_ore", "Pyrolusite Ore", "Пиролюзитовая руда");
        b("coal_seam", "Coal Seam", "Угольный пласт");
        b("fireclay", "Fireclay", "Огнеупорная глина");
        b("pentlandite_ore", "Pentlandite Ore", "Пентландитовая руда");
        b("chalcopyrite_ore", "Chalcopyrite Ore", "Халькопиритовая руда");
        b("sperrylite_ore", "Sperrylite Ore", "Сперрилитовая руда");
        b("molybdenite_ore", "Molybdenite Ore", "Молибденитовая руда");
        b("sphalerite_ore", "Sphalerite Ore", "Сфалеритовая руда");
        b("chromite_ore", "Chromite Ore", "Хромитовая руда");
        b("vein_quartz", "Vein Quartz", "Жильный кварц");
        b("cassiterite_ore", "Cassiterite Ore", "Касситеритовая руда");
        b("bauxite_ore", "Bauxite", "Боксит");
        b("mine_support", "Mine Support", "Шахтная крепь");
        b("powered_roof_support", "Powered Roof Support", "Механизированная крепь");
        b("longwall_shearer", "Longwall Shearer", "Очистной комбайн");
        b("main_mine_fan", "Main Mine Fan", "Вентилятор главного проветривания");
        b("ventilation_door", "Ventilation Door", "Вентиляционная дверь");
        b("explosion_barrier", "Explosion Barrier", "Взрывозащитный заслон");
        b("degasification_borehole", "Degasification Borehole", "Дегазационная скважина");
        b("degasification_pump", "Degasification Pump", "Дегазационный насос");
        b("degasification_pump_real", "Liquid-Ring Degasification Pump", "Водокольцевой дегазационный насос");
        b("mine_dispatch_cabinet", "Mine Gas Monitoring Cabinet", "Шкаф аппаратуры газовой защиты");
        b("mine_hoist", "Mine Hoist", "Шахтная подъёмная машина");
        b("headframe", "Headframe", "Копёр");
        b("mine_cage", "Mine Cage", "Шахтная клеть");
        b("cage_track", "Cage Track", "Путь клети");
        b("shaft_signal", "Shaft Signal Box", "Стволовой сигнальный аппарат");
        b("mine_drainage_pump", "Mine Drainage Pump", "Насос главного водоотлива");
        b("coal_jig", "Coal Jig", "Отсадочная машина");
        b("dense_medium_separator", "Dense Medium Separator", "Тяжелосредный сепаратор");
        b("cupola_furnace", "Cupola Furnace", "Вагранка");
        b("crucible_furnace", "Crucible Furnace", "Тигельная печь");
        b("bloomery", "Bloomery", "Сыродутный горн");
        b("forge_hearth", "Forge Hearth", "Кузнечный горн");
        b("smithing_anvil", "Smithing Anvil", "Кузнечная наковальня");
        b("draw_plate", "Draw Plate", "Волочильная доска");
        b("power_hammer", "Power Hammer", "Механический молот");
        b("crank_press", "Crank Press", "Кривошипный пресс");
        b("wire_mill", "Wire Drawing Mill", "Волочильный стан");
        b("sheet_mill", "Cold Sheet Mill", "Листовой стан холодной прокатки");
        b("notching_press", "Slot Notching Press", "Пазовырубной пресс");
        b("winding_machine", "Stator Winding Machine", "Намоточный станок");
        b("heating_furnace", "Heating Furnace", "Нагревательная печь");
        b("hardening_furnace", "Hardening Furnace", "Закалочная печь");
        b("sand_mold", "Sand Mould", "Песчаная форма");
        b("molding_station", "Moulding Machine", "Формовочная машина");
        b("shakeout_grate", "Shakeout Grate", "Выбивная решётка");
        b("molding_station_real", "Jolt-Squeeze Moulding Machine", "Встряхивающе-прессовая формовочная машина");
        b("shakeout_grate_real", "Vibrating Shakeout Grate", "Инерционная выбивная решётка");
        b("power_hammer_real", "Pneumatic Power Hammer", "Пневматический молот");
        b("bloomery_real", "Clay Bloomery", "Сыродутный горн");
        b("forge_hearth_real", "Smith's Forge Hearth", "Кузнечный горн");
        b("crank_press_real", "Inclinable Crank Press", "Открытый кривошипный пресс");
        b("wire_mill_real", "Wire Drawing Block", "Волочильный барабан");
        b("sheet_mill_real", "Two-High Sheet Mill", "Двухвалковый листовой стан");
        b("notching_press_real", "Lamination Notching Press", "Пазовырубной пресс");
        b("winding_machine_real", "Stator Winding Lathe", "Намоточный станок для статоров");
        b("heating_furnace_real", "Coal-Fired Reheating Furnace", "Нагревательная печь на угле");
        b("hardening_furnace_real", "Muffle Hardening Furnace", "Муфельная закалочная печь");
        b("machine_lathe", "Lathe", "Токарный станок");
        b("drill_press", "Drill Press", "Сверлильный станок");
        b("milling_machine", "Knee Milling Machine", "Консольно-фрезерный станок");
        b("shaping_machine", "Shaping Machine", "Поперечно-строгальный станок");
        b("surface_grinder", "Surface Grinder", "Плоскошлифовальный станок");
        b("machine_lathe_real", "16-inch Engine Lathe", "Токарно-винторезный станок 16″");
        b("drill_press_real", "24-inch Upright Drill", "Вертикально-сверлильный станок 24″");
        b("milling_machine_real", "Plain Horizontal Knee Mill", "Горизонтально-фрезерный консольный станок");
        b("shaping_machine_real", "16-inch Crank Shaper", "Поперечно-строгальный станок 16″");
        b("surface_grinder_real", "Belt-driven Surface Grinder", "Плоскошлифовальный станок с ремённым приводом");
        b("coke_oven", "Coke Oven", "Коксовая камера");
        b("coke_battery_wall", "Coke Battery Flue Wall", "Простенок коксовой батареи");
        b("gas_collector_main", "Gas Collector Main", "Газосборник");
        b("coke_pusher_ram", "Coke Pusher Ram", "Штанга коксовыталкивателя");
        b("gas_exhauster", "Coke Oven Gas Exhauster", "Эксгаустер коксового газа");
        b("byproduct_plant", "By-Product Recovery Unit", "Аппарат улавливания");
        b("quench_tower", "Coke Quench Tower", "Тушильная башня");
        b("electrode_extruder", "Electrode Extrusion Press", "Электродный пресс");
        b("baking_furnace", "Electrode Baking Furnace", "Обжиговая печь электродов");
        // графитовый котёл T0 (I1)
        b("uraninite_ore", "Pitchblende Ore", "Урановая смолка");
        b("deepslate_uraninite_ore", "Deepslate Pitchblende Ore", "Урановая смолка в глубинном сланце");
        b("moderator_block_graphite", "Reactor Graphite", "Реакторный графит");
        b("fuel_channel", "Graphite Fuel Channel Block", "Графитовый блок с топливными каналами");
        b("control_rod_channel", "Graphite Rod Channel Block", "Графитовый блок с каналом стержня");
        b("startup_source", "Radium-Beryllium Neutron Source", "Радий-бериллиевый источник нейтронов");
        b("control_rod_drive", "Control Rod Drive", "Привод регулирующего стержня");
        b("zip_rod_winch", "Zip Rod Winch", "Лебёдка аварийного стержня");
        b("neutron_detector", "BF3 Neutron Counter", "Нейтронный счётчик BF₃");
        b("reactor_controller", "Pile Control Desk", "Пульт графитового котла");
        b("scram_button", "Scram Button", "Кнопка аварийной защиты");
        b("acheson_furnace", "Acheson Graphitizing Furnace", "Печь графитации Ачесона");
        b("acheson_furnace_real", "Acheson Graphitizing Furnace Bed", "Ванна графитации Ачесона");
        b("electrode_extruder_real", "Hydraulic Electrode Extrusion Press", "Гидравлический электродный пресс");
        b("baking_furnace_real", "Carbon Baking Furnace Chamber", "Камера обжиговой печи электродов");
        i("graphite_electrode", "Graphite Electrode", "Графитированный электрод");
        i("raw_uraninite", "Raw Pitchblende", "Урановая смолка (сырьё)");
        i("crushed_uranium_ore", "Crushed Uranium Ore", "Дроблёная урановая руда");
        i("ground_uranium_ore", "Ground Uranium Ore", "Молотая урановая руда");
        i("yellowcake", "Yellowcake", "Жёлтый кек");
        i("uo2_powder", "Uranium Dioxide Powder", "Порошок диоксида урана");
        i("magnesium_ingot", "Magnesium Ingot", "Слиток магния");
        i("uranium_slug", "Natural Uranium Slug Cassette", "Кассета блочков природного урана");
        b("magnetic_separator", "Magnetic Separator", "Магнитный сепаратор");
        b("magnetic_separator_real", "Wet Drum Magnetic Separator", "Барабанный магнитный сепаратор");
        b("sinter_strand", "Sinter Strand", "Агломашина");
        b("blast_furnace_shell", "Blast Furnace Shell", "Кожух домны");
        b("blast_furnace_tuyere", "Blast Furnace Tuyere", "Фурма домны");
        b("blast_furnace_taphole", "Blast Furnace Taphole", "Лётка домны");
        b("blast_furnace_controller", "Blast Furnace Control Panel", "Пульт домны");
        b("blast_furnace_top", "Blast Furnace Top", "Засыпной аппарат");
        b("hot_blast_stove", "Hot Blast Stove", "Воздухонагреватель");
        b("blast_blower", "Blast Blower", "Воздуходувка");
        b("blast_furnace_taphole_real", "Hearth Jacket Taphole", "Лётка в кожухе горна");
        b("blast_furnace_controller_real", "Blast Furnace Instrument Cabinet", "Приборный шкаф домны");
        b("hot_blast_stove_real", "Cowper Hot Blast Stove", "Воздухонагреватель Каупера");
        b("blast_blower_engine", "Horizontal Blowing Engine", "Горизонтальная воздуходувная машина");
        b("sinter_strand_real", "Dwight-Lloyd Sinter Strand", "Агломерационная машина Дуайта — Ллойда");
        b("dust_catcher_real", "Blast Furnace Dust Catcher", "Пылеуловитель доменного газа");
        b("gas_exhauster_real", "Centrifugal Gas Exhauster", "Центробежный эксгаустер");
        b("byproduct_plant_cooler_real", "Primary Gas Cooler", "Первичный газовый холодильник");
        b("byproduct_plant_precipitator_real", "Electrostatic Tar Precipitator", "Электрофильтр смолы");
        b("byproduct_plant_saturator_real", "Ammonium Sulfate Saturator", "Сатуратор сульфата аммония");
        b("byproduct_plant_scrubber_real", "Benzol Scrubber", "Бензольный скруббер");
        b("cast_house_mud_gun_real", "Taphole Clay Gun", "Пушка для забивки лётки");
        b("cast_house_drill_real", "Taphole Drill", "Бурмашина лётки");
        b("ladle_real", "Hot-Metal Ladle in Stand", "Чугуновозный ковш в стойке");
        b("slag_granulator_real", "Slag Granulation Plant", "Установка грануляции шлака");
        b("dust_catcher", "Dust Catcher", "Пылеуловитель");
        b("cast_house_machine", "Cast House Machine", "Машина литейного двора");
        b("iron_runner", "Iron Runner", "Чугунный жёлоб");
        b("ladle", "Hot Metal Ladle", "Чугуновозный ковш");
        b("slag_granulator", "Slag Granulator", "Установка грануляции шлака");
        b("document_lectern", "Document Lectern", "Пюпитр для документов");
        b("canary_cage", "Canary Cage", "Клетка для канарейки");
        b("pyrite_ore", "Pyrite Ore", "Пиритовая руда");
        b("deepslate_pyrite_ore", "Deepslate Pyrite Ore", "Пиритовая руда в глубинном сланце");
        b("galena_ore", "Galena Ore", "Галенитовая руда");
        b("deepslate_galena_ore", "Deepslate Galena Ore", "Галенитовая руда в глубинном сланце");
        b("fluorite_ore", "Fluorite Ore", "Флюоритовая руда");
        b("multiblock_part", "Structure Part", "Часть конструкции");
        b("kinetic_port", "Shaft Coupling", "Муфта вала");
        b("instrument_pressure", "Pressure Gauge", "Манометр");
        // электросеть I1 (07 §7.22)
        b("shaft_generator", "Shaft Generator", "Генератор на валу");
        b("grid_dispatch_center", "Grid Dispatch Center", "Пульт диспетчера энергосистемы");
        b("generator_exciter", "Generator Exciter", "Возбудитель генератора");
        b("instrument_synchroscope", "Synchroscope", "Синхроноскоп");
        b("electric_motor", "Electric Motor", "Электродвигатель");
        b("station_battery", "Station Battery", "Аккумуляторная батарея");
        b("capacitor_bank", "Capacitor Bank", "Батарея конденсаторов");
        b("electric_arc_furnace", "Electric Arc Furnace", "Дуговая сталеплавильная печь");
        b("electric_arc_furnace_real", "Héroult Arc Furnace", "Дуговая печь Эру");
        b("busbar", "Busbar", "Шина");
        b("disconnector", "Disconnector", "Разъединитель");
        b("switchgear_cell", "Switchgear Cell 6 kV", "Ячейка КРУ 6 кВ");
        b("lv_switchboard", "LV Switchboard 0.4 kV", "Щит 0,4 кВ");
        b("relay_cabinet", "Relay Protection Cabinet", "Шкаф релейной защиты");
        b("distribution_transformer", "Distribution Transformer 6/0.4 kV", "Трансформатор 6/0,4 кВ");
        b("wooden_pole", "Wooden Pole", "Деревянная опора");
        b("concrete_pole", "Concrete Pole", "Железобетонная опора");
        b("power_cable", "Power Cable", "Силовой кабель");
        b("grid_consumer", "Settlement Load", "Посёлок-потребитель");
        b("grid_fe_interface_small", "Grid FE Interface, 100 kW", "Интерфейс сети и FE, 100 кВт");
        b("grid_fe_interface", "Grid FE Interface, 1 MW", "Интерфейс сети и FE, 1 МВт");
        b("grid_fe_interface_large", "Grid FE Interface, 10 MW", "Интерфейс сети и FE, 10 МВт");
        b("energy_meter", "Energy Meter", "Счётчик электроэнергии");
        b("wire_connector_lv", "LV Wire Connector", "Коннектор провода 0,4 кВ");
        b("wire_connector_mv", "Pin Insulator Head", "Изолятор ВЛ 6 кВ");
        // автоматика A1–A2 и пульт (03d, 03c, этап 9 M3)
        b("relay", "Control Relay", "Промежуточное реле");
        b("relay_latching", "Latching Relay", "Бистабильное реле");
        b("time_relay", "Time Relay", "Реле времени");
        b("stepping_switch", "Stepping Switch", "Шаговый искатель");
        b("cam_controller", "Cam Controller", "Командоаппарат");
        b("process_switch", "Process Switch", "Реле процесса");
        b("relay_power_supply", "24 V DC Power Supply", "Блок питания 24 В");
        b("relay_rack", "Relay Rack", "Релейный шкаф");
        b("redstone_interface", "Relay–Redstone Interface", "Переходник реле — редстоун");
        b("pushbutton", "Pushbutton", "Кнопка");
        b("selector_switch", "Selector Switch", "Переключатель");
        b("key_switch", "Control Key", "Ключ управления");
        b("indicator_lamp", "Indicator Lamp", "Сигнальная лампа");
        b("gas_detector", "Fixed Gas Detector", "Стационарный газоанализатор");
        b("analog_summer", "Summing Amplifier", "Сумматор");
        b("analog_integrator", "Integrator", "Интегратор");
        b("analog_differentiator", "Differentiator", "Дифференциатор");
        b("analog_multiplier", "Multiplier", "Множительный блок");
        b("analog_function", "Diode Function Generator", "Функциональный преобразователь");
        b("analog_comparator", "Comparator", "Компаратор");
        b("manual_station", "Manual/Auto Station", "Станция РУЧ/АВТ");
        b("signal_converter", "Signal Converter", "Нормирующий преобразователь");
        b("plc", "Programmable Logic Controller", "Программируемый логический контроллер");
        b("sis_logic_solver", "Safety Logic Solver (SIS)", "Логический блок ПАЗ");
        b("lv_distribution_box", "Fuse Box", "Распределительный ящик с предохранителями");
        b("lighting_board", "Lighting Board", "Щиток освещения");
        b("power_socket", "Socket 16 A", "Розетка 16 А");
        b("power_socket_3p", "Three-Phase Socket 32 A", "Трёхфазная розетка 32 А");
        b("power_board", "Power Board", "Силовой щит");
        b("ups_cabinet", "UPS Cabinet", "Шкаф ИБП");
        b("solar_inverter", "Solar Inverter", "Солнечный инвертор");
        b("solar_tracker_drive", "Solar Tracker Drive", "Привод солнечного трекера");
        b("battery_inverter", "Battery Inverter", "Батарейный инвертор");

        i("fusible_plug", "Fusible Plug", "Легкоплавкая пробка");
        i("governor_dashpot", "Governor Dashpot", "Катаракт регулятора");
        i("boiler_slag", "Boiler Slag", "Котельный шлак");
        i("raw_coal", "Run-of-Mine Coal", "Рядовой уголь");
        i("washed_coal", "Washed Coal", "Мытый уголь");
        i("coal_middlings", "Coal Middlings", "Промпродукт");
        i("coal_refuse", "Coal Refuse", "Породные отходы");
        i("raw_magnetite", "Raw Magnetite", "Магнетит");
        i("raw_hematite", "Raw Hematite", "Гематит");
        i("raw_pyrolusite", "Raw Pyrolusite", "Пиролюзит");
        i("raw_pentlandite", "Raw Pentlandite", "Пентландит");
        i("raw_chalcopyrite", "Raw Chalcopyrite", "Халькопирит");
        i("raw_sperrylite", "Raw Sperrylite", "Сперрилит");
        i("raw_molybdenite", "Raw Molybdenite", "Молибденит");
        i("raw_sphalerite", "Raw Sphalerite", "Сфалерит");
        i("raw_chromite", "Raw Chromite", "Хромит");
        i("raw_cassiterite", "Raw Cassiterite", "Касситерит");
        i("raw_bauxite", "Raw Bauxite", "Бокситовая руда");
        i("stone_dust", "Stone Dust", "Инертная пыль");
        i("pig_iron", "Pig Iron", "Чугун");
        i("foundry_sand", "Foundry Sand", "Формовочная смесь");
        i("bronze_ingot", "Tin Bronze Ingot", "Слиток оловянной бронзы");
        i("casting_pattern", "Casting Pattern", "Модель отливки");
        i("casting", "Casting", "Отливка");
        i("refractory_brick", "Refractory Brick", "Огнеупорный кирпич");
        i("workpiece", "Workpiece", "Поковка");
        i("workpiece.bloom", "Bloom", "Крица");
        i("smith_hammer", "Smith's Hammer", "Кузнечный молот");
        i("shaft", "Shaft", "Вал");
        i("tongs", "Smith's Tongs", "Кузнечные клещи");
        i("wire", "Wire", "Проволока");
        i("electrical_sheet", "Electrical Steel Sheet", "Лист электротехнической стали");
        i("lamination", "Stator Lamination Bundle", "Пачка пластин статора");
        i("stator_core", "Stator Core", "Пакет статора");
        i("stator", "Wound Stator", "Намотанный статор");
        i("wire.rod", "Wire Rod", "Пруток");
        i("cutting_tool", "Cutting Tool", "Резец");
        i("milling_cutter", "Disc Milling Cutter", "Дисковая фреза");
        i("grinding_wheel", "Grinding Wheel", "Шлифовальный круг");
        i("spur_gear", "Spur Gear", "Зубчатое колесо");
        i("keyed_shaft", "Keyed Shaft", "Вал со шпоночным пазом");
        i("machine_slideway", "Machine Slideway", "Направляющая станины");
        i("planed_plate", "Planed Plate", "Строганая плита");
        i("ground_plate", "Ground Plate", "Шлифованная плита");
        i("flame_safety_lamp", "Flame Safety Lamp", "Бензиновая лампа");
        i("polyethylene", "Polyethylene Pellets (25 kg bag)", "Гранулы полиэтилена (мешок 25 кг)");
        i("uf6_cylinder_48y", "UF₆ Cylinder 48Y", "Баллон UF₆ 48Y");
        i("fuel_pellet_tray", "Fuel Pellet Tray (one rod)", "Поддон топливных таблеток (на твэл)");
        i("uo2_grinding_sludge", "UO₂ Grinding Sludge and Rejects (1 kg U)", "Шлам шлифования и брак UO₂ (1 кг U)");
        i("fuel_rod", "Fuel Rod", "Твэл");
        i("fuel_assembly", "Fuel Assembly", "Тепловыделяющая сборка (ТВС)");
        i("zircon_concentrate", "Zircon Concentrate", "Цирконовый концентрат");
        i("zirconium_sponge", "Reactor-Grade Zirconium Sponge", "Губка циркония реакторной чистоты");
        i("triga_fuel_element", "TRIGA Fuel Element (U-ZrH)", "Твэл TRIGA (U-ZrH)");
        i("hafnium_ingot", "Hafnium Ingot", "Слиток гафния");
        i("zircaloy_ingot", "Zirconium Alloy Ingot", "Слиток циркониевого сплава");
        i("cladding_tube", "Zirconium Cladding Tube", "Оболочечная труба из циркониевого сплава");
        i("spacer_grid", "Spacer Grid", "Дистанционирующая решётка");
        i("dosimeter_radiometer", "Dosimeter-Radiometer DKS-04", "Дозиметр-радиометр ДКС-04");
        b("lead_block", "Lead Shielding Block", "Свинцовый блок защиты");
        b("helium_recovery_unit", "Helium Recovery Unit", "Гелиевая установка");
        b("fuel_rack", "Spent Fuel Pool Rack", "Стеллаж бассейна выдержки");
        b("ultrasonic_bath", "Ultrasonic Decontamination Bath", "Ультразвуковая ванна дезактивации");
        b("decon_shower", "Decontamination Shower", "Душ санпропускника");
        b("hand_foot_monitor", "Hand and Foot Contamination Monitor", "Установка контроля загрязнённости рук и ног");
        b("portal_monitor", "Portal Gamma Monitor", "Портальный гамма-монитор");
        i("contamination_meter", "Surface Contamination Meter", "Радиометр загрязнённости поверхностей");
        i("strippable_coating", "Strippable Decontamination Coating", "Снимаемое дезактивирующее покрытие");
        i("stripped_coating", "Peeled Coating Film", "Содранная плёнка покрытия");
        i("decon_sprayer", "Decontamination Sprayer", "Распылитель дезактивирующих растворов");
        MISC.put("item.promptcritical.decon_sprayer.tip", new String[] {
                "Right-click a tank: hand over the collected waste and fill up · right-click a surface: treat it. KMnO₄ first, then acid",
                "ПКМ по баку — сдать смыв и заправиться · ПКМ по поверхности — обработать. Сначала KMnO₄, потом кислота"});
        MISC.put("item.promptcritical.contamination_meter.tip", new String[] {
                "Right-click a block: its surface · in the air: your hands and shoes, or the item in your other hand",
                "ПКМ по блоку — его поверхность · в воздух — руки и обувь или предмет во второй руке"});
        MISC.put("item.promptcritical.strippable_coating.tip", new String[] {
                "Spray on a surface, let it dry for 4 h, then peel it off with an empty hand while sneaking",
                "Нанести на поверхность, дать высохнуть 4 ч, затем содрать пустой рукой с Shift"});
        b("purex_cell", "PUREX Canyon Control Cell", "Каньон PUREX (горячая камера)");
        b("hlw_vault", "Vitrified Waste Vault", "Хранилище остеклованных отходов");
        b("shearing_machine", "Fuel Shear", "Рубильная машина");
        b("dissolver", "Fuel Dissolver", "Растворитель топлива");
        b("mixer_settler", "Mixer-Settler Bank", "Батарея смесителей-отстойников");
        b("oxalate_precipitator", "Plutonium Oxalate Precipitator", "Осадитель оксалата плутония");
        b("denitration_reactor", "Uranium Denitration Reactor", "Аппарат денитрации урана");
        b("vitrification_melter", "Vitrification Melter", "Печь остекловывания");
        i("puo2_powder", "Plutonium Dioxide", "Диоксид плутония");
        i("hlw_glass_canister", "Vitrified High-Level Waste Canister", "Стеклоблок высокоактивных отходов");
        b("lrw_evaporator", "Liquid Radwaste Evaporator", "Выпарная установка ЖРО");
        b("cementation_mixer", "Cementation Mixer", "Смеситель цементирования");
        b("bitumenizer", "Rotary Bitumenizer", "Роторный битуматор");
        b("waste_compactor", "Waste Supercompactor (2000 t)", "Суперпресс РАО (2000 т)");
        b("near_surface_repository", "Near-Surface Disposal Vault", "Приповерхностный пункт захоронения РАО");
        b("deep_geological_repository", "Deep Geological Repository Drift", "Штрек пункта глубинного захоронения");
        b("reactor_water_cleanup", "Reactor Water Cleanup", "Система очистки теплоносителя");
        i("waste_drum", "200 l Waste Drum", "Бочка 200 л");
        i("portland_cement", "Portland Cement (50 kg)", "Портландцемент (50 кг)");
        i("cemented_waste_drum", "Cemented Waste Drum", "Бочка цементного компаунда");
        i("bitumen_waste_drum", "Bitumen Waste Drum", "Бочка битумного компаунда");
        i("compacted_waste_drum", "Supercompacted Waste Overpack", "Контейнер прессованных отходов");
        i("copper_canister", "Copper Disposal Canister", "Медный контейнер захоронения");
        i("filter_cartridge", "Filter Cartridge", "Фильтрующий патрон");
        i("spent_ion_exchange_resin", "Spent Ion Exchange Resin (100 l)", "Отработанный ионит (100 л)");
        MISC.put("message.promptcritical.dosimeter.reading", new String[] {"DKS-04: %s · total %s", "ДКС-04: %s · накоплено %s"});
        MISC.put("message.promptcritical.dosimeter.idle", new String[] {"No reading yet: carry it to measure",
                "Показаний пока нет: носите при себе"});
        i("uf6_cylinder_30b", "UF₆ Cylinder 30B", "Баллон UF₆ 30B");
        i("uf6_cylinder_5a", "UF₆ Cylinder 5A", "Баллон UF₆ 5A");
        i("pvc", "PVC Resin (25 kg bag)", "Смола ПВХ (мешок 25 кг)");
        i("uo3_powder", "Uranium Trioxide (1 kg U)", "Трёхокись урана (1 кг U)");
        i("uf4_powder", "Uranium Tetrafluoride, Green Salt (1 kg U)", "Тетрафторид урана, зелёная соль (1 кг U)");
        i("ion_exchange_resin_sba", "Strong-Base Anion Exchange Resin (100 l)", "Сильноосновный анионит (100 л)");
        i("catalyst_steam_reforming", "Nickel Reforming Catalyst", "Никелевый катализатор конверсии метана");
        i("catalyst_shift_ht", "Iron-Chromium Shift Catalyst", "Железохромовый катализатор конверсии CO");
        i("catalyst_shift_lt", "Copper-Zinc Shift Catalyst", "Медноцинковый катализатор конверсии CO");
        i("catalyst_methanol", "Methanol Synthesis Catalyst", "Катализатор синтеза метанола");
        i("catalyst_hydrotreating", "Hydrotreating Catalyst (CoMo)", "Катализатор гидроочистки (CoMo)");
        i("catalyst_reforming", "Reforming Catalyst (Pt-Re)", "Катализатор риформинга (Pt-Re)");
        i("catalyst_fcc", "Cracking Catalyst (Zeolite Y), 100 kg", "Катализатор крекинга (цеолит Y), 100 кг");
        i("sample_bottle", "Sample Bottle", "Пробоотборник");
        i("batch_certificate", "Gasoline Batch Certificate", "Паспорт партии бензина");
        i("sulfur", "Sulphur", "Сера");
        i("pt_rh_gauze", "Platinum-Rhodium Gauze Pack", "Пакет платинородиевых сеток");
        i("catalyst_denox", "Tail Gas Reduction Catalyst", "Катализатор очистки хвостовых газов");
        i("cap_lamp", "Miner's Cap Lamp", "Головной шахтный светильник");
        i("powered_respirator", "Powered Air-Purifying Respirator", "Респиратор с принудительной подачей воздуха");
        i("heated_suit", "Electrically Heated Suit", "Электрообогреваемый костюм");
        b("lamp_rack", "Lamp Room Charging Rack", "Зарядный стеллаж ламповой");
        b("cap_lamp_light", "Cap Lamp Light", "Свет фары");
        i("self_rescuer", "Self-Rescuer", "Самоспасатель");
        i("stone_duster", "Stone Duster", "Осланцеватель");
        i("wire_rope", "Wire Rope", "Стальной канат");
        i("ventilation_regulator", "Ventilation Regulator", "Вентиляционный регулятор");
        i("canary", "Canary", "Канарейка");
        i("methanometer", "Methanometer", "Метанометр");
        i("engineering_handbook", "Engineering Handbook", "Справочник инженера");
        i("operating_procedure", "Operating Procedure", "Инструкция по эксплуатации");
        i("operator_license", "Operator License", "Удостоверение оператора");
        i("shift_log", "Shift Log", "Оперативный журнал");
        i("work_permit", "Work Permit Form", "Бланк наряда-допуска");
        i("geological_map", "Geological Map", "Геологическая карта");
        // нефть и газ I2
        b("reservoir_sandstone", "Oil-Bearing Sandstone", "Нефтенасыщенный песчаник");
        b("reservoir_sandstone_gas", "Gas-Bearing Sandstone", "Газонасыщенный песчаник");
        b("reservoir_sandstone_water", "Water-Bearing Sandstone", "Водонасыщенный песчаник");
        b("cap_rock_shale", "Cap Rock Shale", "Глинистая покрышка");
        b("barite_ore", "Bedded Barite", "Пластовый барит");
        b("oil_seep", "Oil Seep", "Нефтепроявление");
        b("natural_bitumen", "Natural Bitumen", "Природный битум");
        b("gas_seep_vent", "Gas Seep Vent", "Выход газа");
        b("oklo_uraninite_ore", "Deepslate Pitchblende Ore", "Урановая смолка в глубинном сланце");
        b("assay_station", "Assay Station", "Станция пробирного анализа");
        b("seismic_recorder", "Seismic Recorder", "Сейсмостанция");
        b("geophone", "Geophone", "Сейсмоприёмник");
        b("drillers_console", "Driller's Console", "Пульт бурильщика");
        b("rotary_table", "Rotary Table", "Ротор");
        b("mud_pump", "Triplex Mud Pump", "Буровой насос");
        b("drawworks", "Drawworks", "Буровая лебёдка");
        b("derrick_segment", "Derrick Section", "Секция вышки");
        b("bop_annular", "Annular Preventer", "Кольцевой превентор");
        b("bop_ram", "Ram Preventer", "Плашечный превентор");
        b("logging_unit", "Logging Unit", "Каротажный подъёмник");
        b("core_analysis_station", "Core Analysis Station", "Станция анализа керна");
        b("bop_accumulator", "BOP Accumulator Unit", "Аккумуляторная установка ПВО");
        b("choke_manifold", "Choke Manifold", "Манифольд дросселирования");
        b("mud_tank", "Mud Tank", "Ёмкость бурового раствора");
        b("mud_mixing_hopper", "Mud Mixing Hopper", "Гидроворонка");
        i("barite_powder", "Barite Sack (50 kg)", "Мешок барита (50 кг)");
        b("gas_compressor", "Gas Compressor", "Газовый компрессор");
        b("centrifugal_compressor", "Centrifugal Compressor", "Центробежный компрессор");
        b("glycol_regenerator", "Glycol Regenerator", "Регенератор гликоля");
        b("low_temperature_separator", "Low-Temperature Separation Unit", "Установка НТС");
        b("ngl_fractionator", "NGL Fractionation Unit", "Газофракционирующая установка");
        b("rtu_station", "Telemetry Gateway (RTU)", "Шлюз телемеханики (КП)");
        b("historian_server", "Historian Server", "Сервер историана");
        b("annunciator_panel", "Annunciator Panel", "Светосигнальное табло");
        b("alarm_horn", "Alarm Horn", "Сирена сигнализации");
        b("control_desk", "Control Desk", "Секция пульта");
        b("control_room_core", "Control Room Core", "Ядро щитовой");
        b("watchdog_relay", "Watchdog Relay", "Сторожевое реле");
        b("fire_water_pump", "Fire Water Pump", "Пожарный насос");
        b("gas_storage_bullet", "LPG Bullet", "Ёмкость СУГ");
        b("oil_boom", "Oil Boom", "Боновое заграждение");
        b("boom_anchor", "Boom Anchor", "Якорь бона");
        b("oil_skimmer", "Oil Skimmer", "Нефтесборщик");
        b("oiled_sand", "Oiled Sand", "Замазученный песок");
        b("oiled_gravel", "Oiled Gravel", "Замазученный гравий");
        b("tank_gauge", "Tank Gauge", "Уровнемер резервуара");
        b("pig_launcher", "Pig Launcher", "Камера запуска СОД");
        b("pig_receiver", "Pig Receiver", "Камера приёма СОД");
        b("cp_rectifier", "Cathodic Protection Rectifier", "Станция катодной защиты");
        b("cp_test_post", "CP Test Post", "Контрольно-измерительный пункт");
        b("fire_hydrant", "Fire Hydrant", "Пожарный гидрант");
        b("foam_proportioner", "Foam Proportioner", "Дозатор пенообразователя");
        b("foam_chamber", "Foam Chamber", "Пенокамера");
        b("simulation_workstation", "Simulation Workstation", "Инженерная станция");
        b("training_simulator", "Training Simulator", "Шкаф тренажёра");
        b("simulator_console", "Simulator Console", "Пульт тренажёра");
        b("instructor_station", "Instructor Station", "Место инструктора");
        b("mes_server", "Plant Server (MES)", "Сервер завода (MES)");
        b("pyrolysis_furnace", "Pyrolysis Furnace", "Печь пиролиза");
        b("cracked_gas_unit", "Cracked Gas Separation Unit", "Установка газоразделения пирогаза");
        b("ldpe_reactor", "LDPE Unit", "Установка ПЭВД");
        b("brine_treater", "Brine Treatment Plant", "Рассольное отделение");
        b("chlor_alkali_cell", "Diaphragm Chlorine Cell Series", "Серия диафрагменных электролизёров");
        b("rectifier_unit", "Electrolysis Rectifier Unit", "Выпрямительный агрегат электролиза");
        b("chlorine_liquefier", "Chlorine Drying and Liquefaction", "Осушка и сжижение хлора");
        b("edc_unit", "EDC Unit (Chlorination and Oxychlorination)", "Установка ДХЭ (хлорирование и оксихлорирование)");
        b("edc_cracker", "EDC Cracking Furnace", "Печь пиролиза ДХЭ");
        b("hcl_synthesis_furnace", "HCl Synthesis Furnace", "Печь синтеза хлороводорода");
        b("pvc_reactor", "PVC Suspension Autoclave", "Автоклав суспензионного ПВХ");
        b("acid_plant", "Contact Sulphuric Acid Plant", "Сернокислотная установка");
        b("hydrotreater", "Diesel Hydrotreater", "Установка гидроочистки дизельного топлива");
        b("claus_unit", "Claus Sulphur Recovery Unit", "Установка Клауса");
        b("catalytic_reformer", "Catalytic Reformer", "Установка каталитического риформинга");
        b("delayed_coker", "Delayed Coker", "Установка замедленного коксования");
        // подготовка и углубление переработки НПЗ, ГПЗ (09 §9.7.3, §9.9–9.10)
        b("electrostatic_desalter", "Electrostatic Desalter", "Электрообессоливающая установка (ЭЛОУ)");
        b("vacuum_column", "Vacuum Distillation Column", "Вакуумная колонна");
        b("steam_ejector", "Steam Ejector", "Пароэжектор");
        b("fcc_unit", "Fluid Catalytic Cracking Unit", "Установка каталитического крекинга");
        b("amine_unit", "Amine Gas Treating Unit", "Установка аминовой очистки газа");
        b("gasoline_blender", "Gasoline Blending Station", "Станция смешения бензина");
        b("quality_lab", "Fuel Quality Laboratory", "Лаборатория качества топлива");
        b("quality_analyzer", "Online Gasoline Analyzer", "Поточный анализатор бензина");
        b("hf_kiln", "Hydrogen Fluoride Kiln", "Печь фтористого водорода");
        b("leach_tank", "Uranium Leach and CCD Train", "Выщелачивание урана с противоточной декантацией");
        b("uranium_ion_exchange", "Uranium Ion Exchange Unit", "Ионообменная установка урана");
        b("precipitation_tank", "Ammonium Diuranate Precipitator", "Осадитель диураната аммония");
        b("rotary_calciner", "Rotary Calciner (ADU to UO3)", "Прокалочная печь (ДУА в UO3)");
        b("reduction_furnace", "Hydrogen Reduction Furnace (UO3 to UO2)", "Печь восстановления (UO3 в UO2)");
        b("hydrofluorination_reactor", "Hydrofluorination Reactor (UO2 to UF4)", "Аппарат гидрофторирования (UO2 в UF4)");
        b("fluorination_reactor", "Flame Fluorination Reactor (UF4 to UF6)", "Пламенный реактор фторирования (UF4 в UF6)");
        b("fluorine_cell", "Fluorine Cell Series", "Серия фторных электролизёров");
        b("steam_reformer", "Steam Reformer", "Печь паровой конверсии метана");
        b("co2_absorber", "CO₂ Removal and Methanation Column", "Колонна очистки от CO₂ с метанатором");
        b("ammonia_converter", "Ammonia Synthesis Loop", "Колонна синтеза аммиака");
        b("nitric_acid_unit", "Nitric Acid Unit", "Агрегат азотной кислоты");
        b("acid_concentrator", "Nitric Acid Concentrator", "Колонна концентрирования азотной кислоты");
        b("methanol_converter", "Methanol Synthesis Unit", "Агрегат синтеза метанола");
        b("methanol_column", "Methanol Distillation Columns", "Колонны ректификации метанола");
        b("gas_centrifuge", "Gas Centrifuge Section", "Секция газовых центрифуг");
        b("centrifuge_cascade", "Centrifuge Cascade Control", "Пульт каскада газовых центрифуг");
        b("uo2_conversion", "UF₆ to UO₂ Conversion Line (ADU)", "Линия конверсии UF₆ в UO₂ (АДУ)");
        b("pellet_press", "Fuel Pellet Press", "Пресс топливных таблеток");
        b("sintering_furnace", "Pellet Sintering Furnace", "Печь спекания таблеток");
        b("rod_loading_station", "Fuel Rod Loading and Welding Station", "Участок снаряжения и заварки твэлов");
        b("assembly_jig", "Fuel Assembly Jig", "Стапель сборки ТВС");
        b("power_reactor", "Channel Power Reactor (AM-1 type, legacy)", "Канальный энергетический реактор (типа АМ-1, старая серия)");
        b("am1_reactor", "AM-1 Channel Power Reactor", "Канальный энергетический реактор АМ-1");
        b("irradiation_channel", "Irradiation Channel", "Канал облучения");
        b("rtg_thermoelectric", "Thermoelectric RTG", "Термоэлектрический РИТЭГ");
        b("transient_rod_drive", "TRIGA Transient Rod Drive", "Привод импульсного стержня TRIGA");
        b("submerged_arc_furnace", "Submerged Arc Furnace", "Рудно-термическая печь");
        b("trichlorosilane_reactor", "Trichlorosilane Fluidised-Bed Reactor", "Реактор синтеза трихлорсилана");
        b("siemens_reactor", "Siemens Polysilicon Reactor", "Реактор Сименса");
        b("czochralski_puller", "Czochralski Crystal Puller", "Установка Чохральского");
        b("wafer_saw", "Wafer Saw and Polisher", "Станок резки и полировки пластин");
        b("cleanroom_airlock", "Clean Room Airlock and Process Console", "Шлюз и пульт чистой комнаты");
        b("cleanroom_panel", "Clean Room Wall Panel", "Стеновая панель чистой комнаты");
        b("cleanroom_window", "Clean Room Window", "Окно чистой комнаты");
        b("hepa_filter", "HEPA Fan Filter Unit", "Вентиляторный модуль с HEPA-фильтром");
        i("vacuum_tube", "Vacuum Tube (Triode)", "Электровакуумная лампа (триод)");
        i("metallurgical_silicon", "Metallurgical Silicon", "Технический кремний");
        i("polysilicon", "Polysilicon Rod", "Стержень поликремния");
        i("silicon_ingot", "Silicon Ingot", "Слиток кремния");
        i("silicon_wafer", "Silicon Wafer", "Кремниевая пластина");
        i("solar_cell", "Solar Cell", "Солнечный элемент");
        i("transistor", "Transistor", "Транзистор");
        i("integrated_circuit", "Logic Integrated Circuit", "Логическая микросхема");
        i("memory_chip", "Memory Chip", "Микросхема памяти");
        i("microprocessor", "Microprocessor", "Микропроцессор");
        i("copper_foil", "Copper Foil", "Медная фольга");
        i("resistor", "Resistor", "Резистор");
        i("capacitor", "Capacitor", "Конденсатор");
        i("printed_circuit_board", "Printed Circuit Board", "Печатная плата");
        i("phenolic_resin", "Phenolic Resin", "Фенолоформальдегидная смола");
        i("cathode_ray_tube", "Cathode-Ray Tube", "Электронно-лучевая трубка");
        i("stepper_lens", "Stepper Projection Lens", "Объектив степпера");
        b("electrostatic_precipitator", "Electrostatic Precipitator", "Электрофильтр");
        b("fgd_absorber", "Limestone Flue Gas Desulfurisation Absorber", "Абсорбер мокрой известняковой сероочистки");
        b("cems_analyzer", "Continuous Emission Monitoring Analyzer", "Анализатор САКВ");
        b("market_terminal", "Exchange Terminal", "Биржевой терминал");
        i("cobalt_ingot", "Cobalt Ingot", "Слиток кобальта");
        i("iridium_ingot", "Iridium Ingot", "Слиток иридия");
        i("npo2_powder", "Neptunium Dioxide (Np-237)", "Диоксид нептуния (Np-237)");
        i("irradiation_capsule", "Irradiation Capsule", "Капсула облучения");
        i("sealed_source", "Sealed Radioactive Source", "Закрытый радионуклидный источник");
        i("radiography_camera", "Gamma Radiography Camera", "Гамма-дефектоскоп");
        i("radiography_crank", "Radiography Crank and Guide Tube", "Привод и шланг-ампулопровод дефектоскопа");
        i("radiography_film", "Radiography Film", "Радиографическая плёнка");
        i("radiography_film_exposed", "Developed Radiograph", "Проявленная радиограмма");
        i("pu238_heat_source", "Pu-238 Heat Source", "Источник тепла Pu-238");
        i("sr90_heat_source", "Sr-90 Heat Source", "Источник тепла Sr-90");
        b("primary_pump", "Primary Circulation Pump", "Главный циркуляционный насос");
        b("steam_generator", "Steam Generator", "Парогенератор");
        b("pwr_reactor", "Pressurized Water Reactor (VVER type, legacy)", "Водо-водяной энергетический реактор (типа ВВЭР, старая серия)");
        b("containment_wall", "Containment Wall", "Стена гермооболочки");
        b("corium", "Corium", "Кориум");
        b("containment_hatch", "Containment Personnel Hatch", "Шлюзовой люк гермооболочки");
        b("hydrogen_recombiner", "Passive Hydrogen Recombiner", "Пассивный каталитический рекомбинатор водорода");
        b("eccs_pump", "ECCS Pump Train", "Насос САОЗ");
        b("eccs_accumulator", "ECCS Hydroaccumulator", "Гидроёмкость САОЗ");
        b("pressurizer", "Pressurizer (legacy)", "Компенсатор давления (старая серия)");
        b("vver_reactor", "VVER-1000 Reactor", "Реактор ВВЭР-1000");
        b("vver_steam_generator", "PGV-1000 Steam Generator", "Парогенератор ПГВ-1000");
        b("vver_coolant_pump", "GCN-195 Reactor Coolant Pump", "Главный циркуляционный насос ГЦН-195");
        b("vver_pressurizer", "VVER-1000 Pressurizer", "Компенсатор давления ВВЭР-1000");
        b("candu_reactor", "Heavy Water Reactor (CANDU type, legacy)", "Тяжеловодный реактор (типа CANDU, старая серия)");
        b("candu6_reactor", "CANDU 6 Reactor Assembly", "Реакторная сборка CANDU 6");
        b("gs_exchange_tower", "Girdler Sulfide Heavy Water Plant", "Сероводородный завод тяжёлой воды");
        b("heavy_water_column", "Heavy Water Finishing Column", "Колонна доводки тяжёлой воды");
        i("heavy_water_bucket", "Heavy Water Bucket", "Ведро тяжёлой воды");
        b("bwr_reactor", "Boiling Water Reactor (legacy)", "Кипящий корпусной реактор (старая серия)");
        b("bwr240_reactor", "BWR-240 Boiling Water Reactor", "Кипящий реактор BWR-240");
        b("sfr_reactor", "Sodium-Cooled Fast Reactor (legacy)", "Реактор на быстрых нейтронах с натрием (старая серия)");
        b("bn600_reactor", "BN-600 Sodium Fast Reactor", "Реактор на быстрых нейтронах БН-600");
        b("wellhead", "Wellhead", "Колонная головка");
        b("christmas_tree", "Christmas Tree", "Фонтанная арматура");
        b("pumpjack", "Pumpjack", "Станок-качалка");
        b("three_phase_separator", "Three-Phase Separator", "Трёхфазный сепаратор");
        b("flare_stack", "Flare Stack", "Факельный ствол");
        b("flare_tip", "Flare Tip", "Факельный оголовок");
        b("tube_furnace", "Crude Heater", "Трубчатая печь");
        b("crude_column", "Atmospheric Column Bottom", "Куб атмосферной колонны");
        b("column_tray_section", "Column Tray Section", "Тарельчатая секция колонны");
        b("column_top", "Column Top", "Верх колонны");
        i("seismic_charge", "Seismic Charge", "Сейсмический заряд");
        i("sucker_rod", "Sucker Rod", "Насосная штанга");
        i("downhole_rod_pump", "Downhole Rod Pump", "Штанговый глубинный насос");
        i("gas_lift_valve", "Gas Lift Valve", "Газлифтный клапан");
        i("gamma_logging_sonde", "Gamma Ray Sonde", "Зонд гамма-каротажа");
        i("neutron_logging_sonde", "Neutron Sonde (Am-Be)", "Зонд нейтронного каротажа (Am-Be)");
        i("resistivity_logging_sonde", "Resistivity Sonde", "Зонд электрокаротажа");
        i("core_barrel", "Core Barrel", "Керноотборник");
        i("reservoir_core", "Reservoir Core", "Керн");
        i("seismic_section", "Seismic Section", "Сейсмический разрез");
        i("copper_wire", "Copper Wire", "Медная проволока");
        i("generator_coupling", "Shear-Pin Coupling", "Муфта со срезным штифтом");
        i("jacket_heater", "Jacket Water Heater", "Подогреватель рубашки");
        i("lockout_padlock", "Lockout Padlock", "Замок блокировки");
        i("am241_oxide", "Americium-241 Oxide", "Оксид америция-241");
        i("fire_extinguisher", "Powder Fire Extinguisher (ABCE)", "Огнетушитель порошковый (ABCE)");
        i("fire_extinguisher_water", "Water Fire Extinguisher", "Огнетушитель водный");
        i("fire_extinguisher_foam", "Foam Fire Extinguisher", "Огнетушитель пенный");
        i("fire_extinguisher_co2", "CO₂ Fire Extinguisher", "Огнетушитель углекислотный");
        i("fire_extinguisher_metal", "Metal Fire Extinguisher (D)", "Огнетушитель для металлов (D)");
        i("lockout_tag", "Lockout Tag", "Бирка «Не включать!»");
        i("lockout_hasp", "Lockout Hasp", "Многозамковая накладка");
        i("voltage_tester", "Voltage Tester", "Указатель напряжения");
        i("load_sequencer", "Load Sequencer", "Секвенсор нагрузки");
        i("lv_fuse", "LV Fuse Link", "Плавкая вставка");
        i("interlock_module", "Interlock Module", "Модуль блокировки");
        i("insulator_pin", "Pin Insulator", "Штыревой изолятор");
        i("wire_coil_lv", "Insulated Wire Coil", "Бухта самонесущего провода");
        i("wiring_tool", "Wiring Tool", "Монтажный инструмент");
        i("relay_coil", "Relay Coil", "Катушка реле");
        i("overhead_conductor", "Bare Overhead Conductor", "Голый провод ВЛ");
        i("relay_overcurrent", "Overcurrent Relay", "Реле максимального тока");
        i("relay_sync_check", "Sync-Check Relay", "Реле контроля синхронизма");
        i("relay_reverse_power", "Reverse Power Relay", "Реле обратной мощности");
        i("relay_frequency", "Underfrequency Relay", "Реле понижения частоты");
        i("relay_voltage", "Undervoltage Relay", "Реле минимального напряжения");
        i("relay_thermal", "Thermal Overload Relay", "Тепловое реле");
        i("star_delta_starter", "Star-Delta Starter", "Пускатель звезда–треугольник");
        i("soft_starter", "Soft Starter", "Устройство плавного пуска");
        i("variable_frequency_drive", "Variable Frequency Drive", "Частотный преобразователь");
        i("relay_ufls", "Load-Shedding Relay (UFLS)", "Реле АЧР");
        i("relay_autoreclose", "Auto-Reclose Relay", "Реле АПВ");
        i("relay_breaker_failure", "Breaker Failure Relay", "Реле УРОВ");
        i("coke", "Coke", "Кокс");
        i("coke_breeze", "Coke Breeze", "Коксовая мелочь");
        i("pitch_coke", "Pitch Coke", "Пековый кокс");
        i("petroleum_coke", "Petroleum Coke", "Нефтяной кокс");
        i("coal_tar_pitch", "Coal Tar Pitch", "Каменноугольный пек");
        i("green_electrode", "Carbon Electrode", "Угольный электрод");
        i("ammonium_sulfate", "Ammonium Sulfate", "Сульфат аммония");
        i("iron_ore_concentrate", "Iron Ore Concentrate", "Железорудный концентрат");
        i("sinter", "Sinter", "Агломерат");
        i("taphole_clay", "Taphole Clay", "Лёточная масса");
        i("silica_brick", "Silica Brick", "Динасовый кирпич");
        i("tuyere_spare", "Spare Tuyere", "Запасная фурма");
        i("granulated_slag", "Granulated Slag", "Гранулированный шлак");
        b("bof_vessel", "Basic Oxygen Converter", "Кислородный конвертер");
        b("oxygen_lance", "Oxygen Lance", "Кислородная фурма");
        b("ladle_station", "Ladle Treatment Station", "Установка доводки в ковше");
        b("ladle_station_real", "Ladle Treatment Station", "Установка доводки стали в ковше");
        b("ingot_mold", "Ingot Mold", "Изложница");
        b("ingot_mold_real", "Big-end-down Ingot Mould", "Изложница, уширенная книзу");
        b("reheating_furnace", "Reheating Furnace", "Нагревательная печь");
        b("bof_vessel_real", "LD Converter", "Кислородный конвертер LD");
        b("reheating_furnace_real", "Continuous Reheating Furnace", "Методическая нагревательная печь");
        b("heat_treatment_furnace", "Heat Treatment Furnace", "Термическая печь");
        b("heat_treatment_furnace_real", "Car-bottom Heat Treatment Furnace", "Термическая печь с выкатным подом");
        b("quench_tank", "Quench Tank", "Закалочный бак");
        b("roller_table", "Roller Table", "Рольганг");
        b("rolling_mill_stand", "Rolling Mill Stand", "Прокатная клеть");
        b("piercing_mill", "Piercing Mill", "Прошивной стан");
        b("piercing_mill_real", "Mannesmann Piercing Mill", "Прошивной стан Маннесмана");
        b("pipe_welder", "Induction Pipe Welder", "Трубосварочный стан ТВЧ");
        b("pipe_welder_real", "HF Induction Tube Mill", "Трубоэлектросварочный стан ТВЧ");
        b("hydrotest_bench", "Hydrostatic Test Bench", "Стенд гидроиспытаний");
        b("air_separator", "Air Separation Unit", "Воздухоразделительная установка");
        b("asu_main_compressor", "ASU Main Air Compressor", "Воздушный компрессор ВРУ");
        b("asu_molsieve", "Molecular Sieve Adsorber", "Блок осушки на молекулярных ситах");
        b("asu_expander", "Turboexpander", "Турбодетандер");
        b("cryogenic_tank", "Cryogenic Tank", "Криогенная ёмкость");
        // эксплуатация и водоподготовка (этап 11а M3)
        b("maintenance_panel", "Maintenance Panel", "Щит обслуживания");
        b("hydrotest_pump", "Hand Test Pump", "Ручной опрессовочный насос");
        b("recording_gauge", "Recording Gauge", "Самопишущий прибор");
        b("water_clarifier", "Lime Clarifier", "Осветлитель с известкованием");
        b("sand_filter", "Sand Filter", "Механический фильтр");
        b("ion_exchange_column", "Sodium Ion Exchange Column", "Na-катионитный фильтр");
        // котёл I1 и деаэратор (этап 11б M3)
        b("water_tube_boiler", "Water-Tube Boiler Panel (BMS)", "Щит водотрубного котла (BMS)");
        b("boiler_wall", "Water Wall", "Экран топки");
        b("boiler_burner", "Boiler Burner", "Горелка котла");
        b("steam_drum", "Steam Drum", "Барабан котла");
        b("boiler_superheater", "Superheater", "Пароперегреватель");
        b("boiler_economizer", "Economizer", "Экономайзер");
        b("air_preheater", "Air Preheater", "Воздухоподогреватель");
        b("forced_draft_fan", "Forced Draft Fan", "Дутьевой вентилятор");
        b("forced_draft_fan_real", "Forced Draught Blower", "Дутьевой вентилятор ВДН");
        b("steam_turbine", "Steam Turbine", "Паровая турбина");
        b("hydro_turbine", "Hydro Turbine", "Гидротурбина");
        b("tidal_unit", "Tidal Bulb Unit", "Капсульный агрегат ПЭС");
        b("tide_gauge", "Tide Gauge", "Мареограф");
        b("pumped_storage_unit", "Pumped Storage Unit", "Обратимый агрегат ГАЭС");
        b("electrode_boiler", "Electrode Boiler", "Электродный котёл");
        b("electrode_boiler_real", "6.3 kV Electrode Boiler", "Электродный котёл 6,3 кВ");
        b("wind_turbine", "Wind Power Unit D-30", "Ветроэлектростанция Д-30");
        b("dam_gate", "Dam Intake Gate", "Затвор водозабора");
        b("gas_turbine", "Gas Turbine Unit", "Газотурбинная установка");
        b("diesel_generator", "Emergency Diesel Generator", "Аварийный дизель-генератор");
        b("line_blind", "Line Blind", "Поворотная заглушка");
        b("smoke_detector", "Smoke Detector", "Дымовой извещатель");
        b("heat_detector", "Heat Detector", "Тепловой извещатель");
        b("flame_detector", "Flame Detector", "Извещатель пламени");
        b("manual_call_point", "Manual Call Point", "Ручной пожарный извещатель");
        b("fire_alarm_panel", "Fire Alarm Panel", "Прибор приёмно-контрольный пожарный");
        b("sprinkler_head", "Sprinkler Head", "Спринклерный ороситель");
        b("deluge_nozzle", "Deluge Nozzle", "Дренчерный ороситель");
        b("gas_suppression_cylinder", "Gas Suppression Cylinder", "Модуль газового пожаротушения");
        b("fire_monitor", "Fire Monitor", "Лафетный ствол");
        b("lockout_box", "Lockout Box", "Ящик групповой блокировки");
        b("permit_board", "Permit Board", "Стенд нарядов-допусков");
        i("portable_gas_detector", "Portable Gas Detector", "Переносной газоанализатор");
        i("sorbent_pad", "Sorbent Pad", "Сорбирующий мат");
        i("oiled_sorbent", "Oiled Sorbent", "Отработанный сорбент");
        i("pig", "Cleaning Pig", "Очистной скребок");
        i("smart_pig", "Smart Pig (MFL)", "Внутритрубный дефектоскоп");
        i("sacrificial_anode", "Magnesium Sacrificial Anode", "Магниевый протектор");
        i("calibration_gas", "Calibration Gas", "Поверочная газовая смесь");
        b("mechanical_disconnect", "Mechanical Disconnect", "Расцепная муфта");
        b("hrsg", "Heat Recovery Steam Generator", "Котёл-утилизатор");
        b("turbine_gearbox", "Turbine Gearbox", "Редуктор турбины");
        b("condenser", "Steam Condenser", "Конденсатор");
        b("cooling_tower_cell", "Cooling Tower Cell", "Секция градирни");
        b("induced_draft_fan", "Induced Draft Fan", "Дымосос");
        b("induced_draft_fan_real", "Induced Draught Smoke Exhauster", "Дымосос ДН");
        b("deaerator", "Deaerator", "Деаэратор");
        i("mechanic_stethoscope", "Mechanic's Stethoscope", "Слуховая трубка механика");
        i("torque_wrench", "Torque Wrench", "Динамометрический ключ");
        i("test_pressure_gauge", "Test Pressure Gauge", "Контрольный манометр");
        i("caulking_hammer", "Caulking Hammer", "Чеканочный молоток");
        i("oil_can", "Oil Can", "Маслёнка");
        i("boiler_scraper", "Boiler Scale Scraper", "Скребок для накипи");
        i("safety_valve", "Safety Valve", "Предохранительный клапан");
        i("bronze_bearing_shell", "Bronze Bearing Shell", "Бронзовый вкладыш подшипника");
        i("pump_impeller", "Pump Impeller", "Рабочее колесо насоса");
        i("gland_packing", "Gland Packing", "Сальниковая набивка");
        i("tallow", "Tallow", "Технический жир");
        i("chart_paper", "Chart Paper", "Диаграммная лента");
        i("recorded_chart", "Recorded Chart", "Записанная диаграмма");
        i("salt", "Salt", "Соль");
        i("coal_dust", "Coal Dust", "Угольная пыль");
        i("low_nox_burner_kit", "Low-NOx Burner Kit", "Комплект малотоксичной горелки");
        i("steel_ingot", "Steel Ingot", "Стальной слиток");
        i("steel_billet", "Steel Billet", "Стальная заготовка");
        i("steel_plate", "Steel Plate", "Стальной лист");
        i("steel_rod", "Steel Rod", "Стальной пруток");
        i("steel_beam", "Steel Beam", "Стальная балка");
        i("steel_scrap", "Steel Scrap", "Стальной лом");
        i("burnt_lime", "Burnt Lime", "Обожжённая известь");
        i("ferroalloy", "Ferroalloy", "Ферросплав");
        i("seamless_pipe", "Seamless Pipe", "Бесшовная труба");
        i("welded_pipe", "Welded Pipe", "Сварная труба");

        FLUIDS.put("flue_gas", new String[] {"Flue Gas", "Дымовые газы"});
        FLUIDS.put("crude_oil", new String[] {"Crude Oil", "Сырая нефть"});
        FLUIDS.put("recovered_oil", new String[] {"Recovered Oil (emulsion)", "Собранная нефть (эмульсия)"});
        FLUIDS.put("foam_concentrate", new String[] {"Foam Concentrate", "Пенообразователь"});
        FLUIDS.put("sales_gas", new String[] {"Sales Gas", "Товарный газ"});
        FLUIDS.put("methanol", new String[] {"Methanol", "Метанол"});
        FLUIDS.put("ngl", new String[] {"NGL (Natural Gas Liquids)", "ШФЛУ"});
        FLUIDS.put("propane", new String[] {"Propane", "Пропан"});
        FLUIDS.put("butane", new String[] {"Butane", "Бутан"});
        FLUIDS.put("natural_gasoline", new String[] {"Natural Gasoline", "Газовый бензин"});
        FLUIDS.put("ethylene", new String[] {"Ethylene", "Этилен"});
        FLUIDS.put("propylene", new String[] {"Propylene", "Пропилен"});
        FLUIDS.put("c4_fraction", new String[] {"C4 Cut", "C4-фракция пиролиза"});
        FLUIDS.put("pyrolysis_gasoline", new String[] {"Pyrolysis Gasoline", "Пиробензин"});
        FLUIDS.put("pyrolysis_fuel_oil", new String[] {"Pyrolysis Fuel Oil", "Тяжёлая смола пиролиза"});
        FLUIDS.put("brine", new String[] {"Treated Brine", "Очищенный рассол"});
        FLUIDS.put("raw_brine", new String[] {"Raw Brine", "Сырой рассол"});
        FLUIDS.put("chlorine", new String[] {"Chlorine", "Хлор"});
        FLUIDS.put("liquid_chlorine", new String[] {"Liquid Chlorine", "Жидкий хлор"});
        FLUIDS.put("hydrogen", new String[] {"Hydrogen", "Водород"});
        FLUIDS.put("sodium_hydroxide_solution", new String[] {"Cell Liquor (Caustic Soda)", "Электролитическая щёлочь"});
        FLUIDS.put("hydrogen_chloride", new String[] {"Hydrogen Chloride", "Хлористый водород"});
        FLUIDS.put("hydrochloric_acid", new String[] {"Hydrochloric Acid", "Соляная кислота"});
        FLUIDS.put("trichlorosilane", new String[] {"Trichlorosilane", "Трихлорсилан"});
        FLUIDS.put("ethylene_dichloride", new String[] {"Ethylene Dichloride", "Дихлорэтан"});
        FLUIDS.put("vinyl_chloride", new String[] {"Vinyl Chloride", "Винилхлорид"});
        FLUIDS.put("sulfuric_acid", new String[] {"Sulphuric Acid (98 %)", "Серная кислота (98 %)"});
        FLUIDS.put("hydrogen_fluoride", new String[] {"Anhydrous Hydrogen Fluoride", "Безводный фтористый водород"});
        FLUIDS.put("fluorine", new String[] {"Fluorine", "Фтор"});
        FLUIDS.put("pregnant_leach_solution", new String[] {"Pregnant Leach Solution", "Продуктивный раствор"});
        FLUIDS.put("raffinate", new String[] {"Barren Solution", "Маточник сорбции"});
        FLUIDS.put("decon_solution", new String[] {"Decontamination Solution (citric + oxalic acid)", "Дезактивирующий раствор (лимонная + щавелевая кислота)"});
        FLUIDS.put("decon_permanganate", new String[] {"Alkaline Permanganate Solution", "Щелочной раствор перманганата"});
        FLUIDS.put("contaminated_decon_solution", new String[] {"Spent Decontamination Solution (liquid radwaste)", "Отработанный дезактивирующий раствор (ЖРО)"});
        FLUIDS.put("uranium_tailings", new String[] {"Uranium Mill Tailings", "Хвосты урановой фабрики"});
        FLUIDS.put("uranium_eluate", new String[] {"Uranium Eluate", "Урановый элюат"});
        FLUIDS.put("liquid_radioactive_waste", new String[] {"Liquid Radioactive Waste", "Жидкие радиоактивные отходы"});
        FLUIDS.put("syngas", new String[] {"Ammonia Syngas (3 H₂ + N₂)", "Азотоводородная смесь"});
        FLUIDS.put("ammonia", new String[] {"Liquid Ammonia", "Жидкий аммиак"});
        FLUIDS.put("nitric_acid", new String[] {"Nitric Acid (56 %)", "Азотная кислота 56 %"});
        FLUIDS.put("concentrated_nitric_acid", new String[] {"Concentrated Nitric Acid (98 %)", "Концентрированная азотная кислота 98 %"});
        FLUIDS.put("helium", new String[] {"Helium", "Гелий"});
        FLUIDS.put("produced_water", new String[] {"Produced Water", "Пластовая вода"});
        FLUIDS.put("injection_water", new String[] {"Injection Water", "Вода для ППД"});
        FLUIDS.put("associated_gas", new String[] {"Associated Gas", "Попутный нефтяной газ"});
        FLUIDS.put("gasoline", new String[] {"Straight-Run Gasoline", "Прямогонный бензин"});
        FLUIDS.put("kerosene", new String[] {"Kerosene", "Керосин"});
        FLUIDS.put("diesel_fuel", new String[] {"Diesel Fuel", "Дизельное топливо"});
        FLUIDS.put("fuel_oil", new String[] {"Fuel Oil (Mazut)", "Мазут"});
        FLUIDS.put("vacuum_gas_oil", new String[] {"Vacuum Gas Oil", "Вакуумный газойль"});
        FLUIDS.put("vacuum_residue", new String[] {"Vacuum Residue (Tar)", "Гудрон"});
        FLUIDS.put("fcc_gasoline", new String[] {"Cracked Gasoline", "Бензин каткрекинга"});
        FLUIDS.put("light_cycle_oil", new String[] {"Light Cycle Oil", "Лёгкий газойль каткрекинга"});
        FLUIDS.put("decant_oil", new String[] {"Decant Oil", "Тяжёлый газойль каткрекинга"});
        FLUIDS.put("lpg", new String[] {"Liquefied Petroleum Gas", "Сжиженный углеводородный газ"});
        FLUIDS.put("reformate", new String[] {"Reformate", "Риформат"});
        FLUIDS.put("motor_gasoline", new String[] {"Motor Gasoline", "Товарный бензин"});
        FLUIDS.put("mine_methane", new String[] {"Mine Methane", "Шахтный метан"});
        FLUIDS.put("raw_water", new String[] {"Raw Water", "Сырая вода"});
        FLUIDS.put("mine_water", new String[] {"Mine Water", "Шахтная вода"});
        FLUIDS.put("heavy_water", new String[] {"Heavy Water (99.75 % D₂O)", "Тяжёлая вода (99,75 % D₂O)"});
        FLUIDS.put("downgraded_heavy_water", new String[] {"Downgraded Heavy Water (90 % D)", "Разбавленная тяжёлая вода (90 % D)"});
        FLUIDS.put("deuterium_enriched_water", new String[] {"Deuterium-Enriched Water (15 % D)", "Вода, обогащённая дейтерием (15 % D)"});
        FLUIDS.put("coke_oven_gas", new String[] {"Coke Oven Gas", "Коксовый газ"});
        FLUIDS.put("blast_furnace_gas", new String[] {"Blast Furnace Gas", "Доменный газ"});
        FLUIDS.put("coal_tar", new String[] {"Coal Tar", "Каменноугольная смола"});
        FLUIDS.put("crude_benzol", new String[] {"Crude Benzol", "Сырой бензол"});
        FLUIDS.put("converter_gas", new String[] {"Converter Gas", "Конвертерный газ"});
        FLUIDS.put("oxygen", new String[] {"Oxygen", "Кислород"});
        FLUIDS.put("nitrogen", new String[] {"Nitrogen", "Азот"});
        FLUIDS.put("argon", new String[] {"Argon", "Аргон"});
        FLUIDS.put("liquid_oxygen", new String[] {"Liquid Oxygen", "Жидкий кислород"});
        FLUIDS.put("liquid_nitrogen", new String[] {"Liquid Nitrogen", "Жидкий азот"});
        FLUIDS.put("clarified_water", new String[] {"Clarified Water", "Осветлённая вода"});
        FLUIDS.put("filtered_water", new String[] {"Filtered Water", "Фильтрованная вода"});
        FLUIDS.put("softened_water", new String[] {"Softened Water", "Умягчённая вода"});
        FLUIDS.put("boiler_feedwater", new String[] {"Boiler Feedwater", "Питательная вода"});
        MISC.put("block.promptcritical.mine_water", new String[] {"Mine Water", "Шахтная вода"});
        MISC.put("block.promptcritical.heavy_water", new String[] {"Heavy Water", "Тяжёлая вода"});
        MISC.put("block.promptcritical.crude_oil", new String[] {"Spilt Crude Oil", "Разлитая нефть"});
        MISC.put("block.promptcritical.oil_slick", new String[] {"Oil Slick", "Нефтяная плёнка"});

        MISC.put("itemGroup." + PromptCritical.MOD_ID, new String[] {"Prompt Critical", "Prompt Critical"});
        MISC.put("commands.promptcritical.debug.hash", new String[] {"State hash %s at tick %s", "Хеш состояния %s на тике %s"});
        MISC.put("commands.promptcritical.debug.devices", new String[] {"Devices: %s", "Устройств: %s"});
        MISC.put("commands.promptcritical.profile.header", new String[] {
                "Simulation profile of %s: degradation %s, tick p95 %s ms", "Профиль симуляции %s: деградация %s, p95 тика %s мс"});
        MISC.put("commands.promptcritical.profile.damage", new String[] {
                "Damage budget: %s blocks applied, %s queued, %s denied", "Бюджет разрушений: применено %s, в очереди %s, отказано %s"});
        MISC.put("commands.promptcritical.mine.count", new String[] {"Mines: %s", "Шахт: %s"});
        MISC.put("commands.promptcritical.mine.ignited", new String[] {"Methane ignited", "Метан воспламенился"});
        MISC.put("commands.promptcritical.mine.not_ignited", new String[] {"Mixture outside explosive limits", "Смесь вне взрывных пределов"});
        MISC.put("commands.promptcritical.boiler.preset", new String[] {"Boiler preset: %s MPa, level %s m", "Котёл: %s МПа, уровень %s м"});
        MISC.put("commands.promptcritical.boiler.no_boiler", new String[] {"Look at a fire-tube boiler", "Посмотрите на жаротрубный котёл"});
        MISC.put("promptcritical.mine.not_in_mine", new String[] {"Not in a mine working", "Вы не в горной выработке"});
        MISC.put("promptcritical.mine.lamp.normal", new String[] {"Lamp: steady flame", "Лампа: пламя ровное"});
        MISC.put("promptcritical.mine.cap_lamp.broken", new String[] {"The cap lamp glass is broken: the lamp is out and no longer flameproof",
                "Стекло фары разбито: лампа погасла и больше не взрывобезопасна"});
        MISC.put("item.promptcritical.cap_lamp.charge", new String[] {"Charge %s %% of %s Ah · %s cycles",
                "Заряд %s %% от %s А·ч · циклов %s"});
        MISC.put("item.promptcritical.cap_lamp.broken", new String[] {"Glass broken: put it on the lamp room rack",
                "Стекло разбито: сдайте в ламповую на стеллаж"});
        MISC.put("item.promptcritical.electric_gear.battery", new String[] {"NiCd battery: %s cells, %s V, %s Ah (%s Wh)",
                "Батарея НК: %s эл., %s В, %s А·ч (%s Вт·ч)"});
        MISC.put("item.promptcritical.electric_gear.charge", new String[] {"Charge %s %% · load %s W · %s cycles",
                "Заряд %s %% · нагрузка %s Вт · %s циклов"});
        MISC.put("item.promptcritical.powered_respirator.filter", new String[] {"Acid gas filter (EN 14387 B1): %s %% used",
                "Фильтр кислых газов (EN 14387 B1): израсходован на %s %%"});
        MISC.put("item.promptcritical.powered_respirator.filter_spent", new String[] {"Filter spent (%s %%): gas breaks through, change it on the lamp room rack",
                "Фильтр отработан (%s %%): газ проходит, замените на стеллаже ламповой"});
        MISC.put("item.promptcritical.powered_respirator.no_co", new String[] {"Does not stop carbon monoxide or add oxygen: in a mine fire use the self-rescuer",
                "Не задерживает угарный газ и не даёт кислорода: при пожаре в шахте — самоспасатель"});
        MISC.put("item.promptcritical.heated_suit.heater", new String[] {"Thermostatic heater up to %s W, insulation %s clo",
                "Обогрев с терморегулятором до %s Вт, изоляция %s кло"});
        MISC.put("promptcritical.gear.cold.stage1", new String[] {"You are shivering: core temperature below 35 °C",
                "Вас бьёт озноб: температура тела ниже 35 °C"});
        MISC.put("promptcritical.gear.cold.stage2", new String[] {"Moderate hypothermia: core below 32 °C, get warm",
                "Умеренное переохлаждение: ниже 32 °C, срочно в тепло"});
        MISC.put("promptcritical.gear.cold.stage3", new String[] {"Severe hypothermia: core below 28 °C",
                "Тяжёлое переохлаждение: ниже 28 °C"});
        MISC.put("item.promptcritical.cap_lamp.proof", new String[] {"Flameproof mine design", "Рудничное взрывобезопасное исполнение"});
        MISC.put("promptcritical.mine.lamp.halo", new String[] {"Lamp: blue cap over the flame, %s %% of full height",
                "Лампа: голубой ореол над пламенем, %s %% высоты"});
        MISC.put("promptcritical.mine.lamp.flame_in_gauze", new String[] {"Lamp: gas burning inside the gauze, get out!",
                "Лампа: смесь горит внутри сетки, уходи!"});
        MISC.put("promptcritical.mine.lamp.ignites", new String[] {"Lamp: flame passed the gauze!", "Лампа: пламя вышло за сетку!"});
        MISC.put("promptcritical.mine.lamp.extinguished", new String[] {"Lamp: went out (oxygen below 17 %%)",
                "Лампа: погасла (кислорода меньше 17 %%)"});
        MISC.put("promptcritical.mine.support.lowering", new String[] {"Support: lowering the legs", "Крепь: разгрузка стоек"});
        MISC.put("promptcritical.mine.support.setting", new String[] {"Support: setting against the roof", "Крепь: распор в кровлю"});
        MISC.put("promptcritical.mine.methanometer", new String[] {"CH4: %s %%", "CH4: %s %%"});
        MISC.put("promptcritical.mine.self_rescuer.on", new String[] {"Self-rescuer on: breathing from the apparatus",
                "Самоспасатель включён: дыхание из аппарата"});
        MISC.put("promptcritical.mine.self_rescuer.spent", new String[] {"Self-rescuer spent", "Самоспасатель отработал"});
        MISC.put("promptcritical.body.title_button", new String[] {"Character body", "Тело персонажа"});
        MISC.put("promptcritical.body.title", new String[] {"Character body", "Тело персонажа"});
        MISC.put("promptcritical.body.hint_title", new String[] {"Saved for every new world; you confirm it on first join",
                "Сохранится для новых миров; при первом входе вы его подтвердите"});
        MISC.put("promptcritical.body.hint_world", new String[] {"Chosen once per world: only an operator can reset it",
                "Выбирается один раз на мир: сбросить может только оператор"});
        MISC.put("promptcritical.body.confirm", new String[] {"Confirm", "Подтвердить"});
        MISC.put("promptcritical.body.sex", new String[] {"Sex", "Пол"});
        MISC.put("promptcritical.body.sex.male", new String[] {"Male", "Мужской"});
        MISC.put("promptcritical.body.sex.female", new String[] {"Female", "Женский"});
        MISC.put("promptcritical.body.height", new String[] {"Height: %s cm", "Рост: %s см"});
        MISC.put("promptcritical.body.mass", new String[] {"Mass: %s kg", "Масса: %s кг"});
        MISC.put("promptcritical.body.age", new String[] {"Age: %s", "Возраст: %s"});
        MISC.put("promptcritical.body.info.bmi", new String[] {"Body mass index: %s", "Индекс массы тела: %s"});
        MISC.put("promptcritical.body.info.blood", new String[] {"Blood volume: %s L", "Объём крови: %s л"});
        MISC.put("promptcritical.body.info.bmr", new String[] {"Basal metabolism: %s W", "Основной обмен: %s Вт"});
        MISC.put("promptcritical.body.info.hitbox", new String[] {"Hitbox height: %s blocks", "Высота хитбокса: %s блока"});
        MISC.put("promptcritical.body.info.stride", new String[] {"Walking speed: %s %%", "Скорость шага: %s %%"});
        MISC.put("promptcritical.body.info.gas_rest", new String[] {"Mine gas at rest: ×%s", "Газ в шахте стоя: ×%s"});
        MISC.put("promptcritical.body.info.gas_sprint", new String[] {"Mine gas running: ×%s", "Газ в шахте на бегу: ×%s"});
        MISC.put("promptcritical.body.info.knockback", new String[] {"Knockback resistance: %s %%",
                "Сопротивление отбрасыванию: %s %%"});
        MISC.put("promptcritical.body.info.cage", new String[] {"In the hoist cage: %s kg", "В клети подъёма: %s кг"});
        MISC.put("promptcritical.body.info.let_go", new String[] {"Let-go current: %s mA", "Ток отпускания: %s мА"});
        MISC.put("promptcritical.body.shock.felt", new String[] {"Electric shock: %s mA through the body",
                "Бьёт током: %s мА через тело"});
        MISC.put("promptcritical.body.shock.held", new String[] {"%s mA: the hand will not let go!",
                "%s мА: руку свело, не отпустить!"});
        MISC.put("death.attack.promptcritical.electrocution", new String[] {"%1$s was electrocuted", "%1$s убит током"});
        MISC.put("death.attack.promptcritical.electrocution.player", new String[] {"%1$s was electrocuted while fighting %2$s",
                "%1$s убит током в схватке с %2$s"});
        MISC.put("message.promptcritical.station_battery", new String[] {"Battery 24 V: charge %s %%, %s V open circuit, %s A",
                "Батарея 24 В: заряд %s %%, %s В без нагрузки, %s А"});
        MISC.put("message.promptcritical.capacitor_bank", new String[] {"Capacitor bank: %s of %s steps, %s kvar at 6.3 kV",
                "Батарея конденсаторов: ступеней %s из %s, %s квар при 6,3 кВ"});
        MISC.put("message.promptcritical.eaf.no_ladle", new String[] {"No ladle in front of the spout", "Перед носком печи нет ковша"});
        MISC.put("message.promptcritical.eaf.tapped", new String[] {"Tapped %s kg of steel at %s °C", "Выпущено %s кг стали при %s °C"});
        MISC.put("message.promptcritical.eaf.full", new String[] {"The furnace is full (6 t)", "Печь полна (6 т)"});
        MISC.put("message.promptcritical.eaf.not_molten", new String[] {"Not all melted yet", "Ещё не всё расплавилось"});
        MISC.put("message.promptcritical.eaf.empty", new String[] {"Charge steel scrap first", "Сначала загрузите стальной лом"});
        MISC.put("message.promptcritical.eaf.arc_on", new String[] {"Arc struck · %s kg, %s %% molten, %s °C, %s MW",
                "Дуга зажжена · %s кг, расплав %s %%, %s °C, %s МВт"});
        MISC.put("message.promptcritical.eaf.arc_off", new String[] {"Electrodes raised · %s kg, %s %% molten, %s °C, %s MW",
                "Электроды подняты · %s кг, расплав %s %%, %s °C, %s МВт"});
        MISC.put("promptcritical.mine.duster.done", new String[] {"Stone dust applied: inert share %s %%",
                "Выработка осланцована: инертная доля %s %%"});
        MISC.put("promptcritical.mine.duster.empty", new String[] {"No stone dust in the inventory", "Нет инертной пыли"});
        MISC.put("promptcritical.mine.report.none", new String[] {"No mine workings connected", "Выработки не подключены"});
        MISC.put("promptcritical.mine.report.header", new String[] {"Mine %s: %s airway nodes, intake %s m3/s",
                "Шахта %s: узлов выработок %s, приток воздуха %s м³/с"});
        MISC.put("promptcritical.mine.report.gas", new String[] {"CH4 max %s %%, CO max %s ppm, O2 min %s %%, methane emission %s m3/min",
                "CH4 макс. %s %%, CO макс. %s ppm, O2 мин. %s %%, метановыделение %s м³/мин"});
        MISC.put("promptcritical.mine.report.explosions", new String[] {"Explosions: %s", "Взрывов: %s"});
        MISC.put("death.attack.promptcritical.mine_gas", new String[] {"%1$s was overcome by afterdamp",
                "%1$s задохнулся рудничным газом"});
        MISC.put("death.attack.promptcritical.mine_gas.player", new String[] {"%1$s was overcome by afterdamp while fighting %2$s",
                "%1$s задохнулся рудничным газом, спасаясь от %2$s"});
        MISC.put("commands.promptcritical.profile.reset", new String[] {"Simulation profile reset", "Профиль симуляции сброшен"});
        MISC.putAll(FoundryText.LANG);
        MISC.putAll(ManufacturingText.LANG);
        MISC.putAll(dev.denfry.promptcritical.mod.paperwork.PaperworkText.LANG);
        MISC.putAll(dev.denfry.promptcritical.mod.site.SiteText.LANG);
        MISC.putAll(dev.denfry.promptcritical.mod.ironworks.IronworksText.LANG);
        MISC.putAll(dev.denfry.promptcritical.mod.steelworks.SteelworksText.LANG);
        MISC.putAll(dev.denfry.promptcritical.mod.ops.OpsText.LANG);
        MISC.putAll(dev.denfry.promptcritical.mod.worldgen.WorldFinds.LANG);
        MISC.putAll(dev.denfry.promptcritical.mod.ops.LockoutText.LANG);
        MISC.putAll(dev.denfry.promptcritical.mod.ops.PermitText.LANG);
        MISC.putAll(dev.denfry.promptcritical.mod.ops.OpsFireText.LANG);
        MISC.putAll(dev.denfry.promptcritical.mod.powerhouse.PowerhouseText.LANG);
        MISC.putAll(dev.denfry.promptcritical.mod.grid.GridText.LANG);
        MISC.putAll(dev.denfry.promptcritical.mod.automation.AutomationText.LANG);
        MISC.putAll(dev.denfry.promptcritical.mod.content.ContentText.LANG);
        MISC.putAll(dev.denfry.promptcritical.mod.compat.CompatText.LANG);
        for (dev.denfry.promptcritical.mod.pa.PaLine l : dev.denfry.promptcritical.mod.pa.PaLine.values())
            MISC.put(l.subtitleKey(), new String[] {l.en, l.ru});
        for (dev.denfry.promptcritical.mod.sound.PcSfx s : dev.denfry.promptcritical.mod.sound.PcSfx.values())
            MISC.put(s.subtitleKey(), new String[] {s.en, s.ru});
        PcAdvancements.LANG.forEach((id, t) -> {
            MISC.put("advancements.promptcritical." + id + ".title", new String[] {t[0], t[1]});
            MISC.put("advancements.promptcritical." + id + ".description", new String[] {t[2], t[3]});
        });
        // декор I0 (11 §11.6.2)
        String[][] decor = {
                {"tile_white", "White Tile", "Белая плитка"}, {"tile_blue", "Blue Tile", "Голубая плитка"},
                {"tile_green", "Green Tile", "Зелёная плитка"}, {"porcelain_tile", "Grey Porcelain Tile", "Керамогранит серый"},
                {"porcelain_tile_beige", "Beige Porcelain Tile", "Керамогранит бежевый"},
                {"painted_concrete", "Grey Painted Concrete", "Крашеный бетон, серый"},
                {"painted_concrete_yellow", "Yellow Painted Concrete", "Крашеный бетон, жёлтый"},
                {"painted_concrete_red", "Fire-Red Painted Concrete", "Крашеный бетон, красный противопожарный"},
                {"painted_concrete_two_tone", "Two-Tone Painted Wall, Green", "Стена в два цвета, зелёная"},
                {"painted_concrete_two_tone_blue", "Two-Tone Painted Wall, Blue", "Стена в два цвета, синяя"},
                {"painted_concrete_two_tone_beige", "Two-Tone Painted Wall, Beige", "Стена в два цвета, бежевая"},
                {"glass_block", "Glass Block", "Стеклоблок"}, {"glass_block_frosted", "Frosted Glass Block", "Матовый стеклоблок"},
                {"corrugated_steel", "Grey Corrugated Steel", "Профнастил серый"},
                {"corrugated_steel_blue", "Blue Corrugated Steel", "Профнастил синий"},
                {"corrugated_steel_green", "Green Corrugated Steel", "Профнастил зелёный"},
                {"corrugated_steel_red", "Red Corrugated Steel", "Профнастил красный"},
                {"corrugated_steel_white", "White Corrugated Steel", "Профнастил белый"},
                {"grating", "Steel Grating", "Решётчатый настил"}, {"grating_step", "Grating Stair", "Ступени из решётчатого настила"},
                {"handrail", "Yellow Handrail", "Перила жёлтые"}, {"handrail_galvanized", "Galvanized Handrail", "Перила оцинкованные"},
                {"cage_ladder", "Caged Ladder", "Лестница с ограждением"},
                {"steel_scaffold", "Steel Scaffold", "Стальные строительные леса"},
                {"industrial_light", "Industrial Pendant Light", "Промышленный светильник"},
                {"industrial_light_street", "Street Light", "Уличный консольный светильник"},
                {"industrial_light_floodlight", "Floodlight", "Прожектор"},
                {"industrial_light_exit", "Exit Sign Light", "Световой указатель «Выход»"},
                {"lamp_fluorescent", "Fluorescent Batten", "Люминесцентный светильник ЛСП"},
                {"lamp_bulkhead", "Bulkhead Light", "Светильник НПП «тарелка»"},
                {"lamp_explosion_proof", "Explosion-Proof Light", "Взрывозащищённый светильник"},
                {"lamp_emergency", "Emergency Light", "Аварийный светильник"},
                {"lamp_high_bay", "High-Bay Light", "Светильник «колокол»"},
                {"warning_beacon", "Warning Beacon", "Проблесковый маячок"},
                {"room_sign", "Room Sign", "Табличка помещения"}, {"room_sign_evac", "Evacuation Arrow", "Стрелка эвакуации"},
                {"wall_clock", "Wall Clock", "Настенные часы"}, {"safety_poster", "Safety Poster", "Плакат по технике безопасности"},
                {"safety_poster_large", "Large Safety Poster", "Большой плакат по технике безопасности"},
                {"curb", "Curb", "Бордюр"},
                // I1
                {"linoleum_control", "Grey Control Room Linoleum", "Линолеум серый"},
                {"linoleum_control_beige", "Beige Control Room Linoleum", "Линолеум бежевый"},
                {"linoleum_control_parquet", "Parquet-Pattern Linoleum", "Линолеум «под паркет»"},
                {"ceiling_tile", "Suspended Ceiling Tile", "Плита подвесного потолка"},
                {"ceiling_light_panel", "Ceiling Light Panel", "Светильник подвесного потолка"},
                {"cable_tray", "Cable Tray", "Кабельный лоток"}, {"cable_tray_cables", "Cable Tray with Cables", "Кабельный лоток с кабелями"},
                {"cable_tray_corner", "Cable Tray Bend", "Кабельный лоток, угол"},
                {"cable_tray_tee", "Cable Tray Tee", "Кабельный лоток, тройник"},
                {"cable_tray_vertical", "Vertical Cable Tray", "Кабельный лоток вертикальный"},
                {"room_sign_electrical", "Sign: Switchgear Room", "Табличка «Электрощитовая»"},
                {"room_sign_pump", "Sign: Pump Room", "Табличка «Насосная»"},
                {"room_sign_control", "Sign: Main Control Room", "Табличка «ГЩУ»"},
                {"electrical_cabinet", "Distribution Cabinet", "Распределительный шкаф"},
                {"junction_box", "Junction Box", "Распаечная коробка"},
                {"solar_panel", "Solar Panel", "Солнечная панель"},
                {"solar_panel_tracker", "Tracking Solar Panel", "Солнечная панель на трекере"},
                {"grid_battery_rack", "Grid Battery Rack (250 kWh)", "Стеллаж сетевой батареи (250 кВт·ч)"},
                {"wall_socket", "Industrial Sockets", "Силовые розетки"},
                {"knife_switch", "Knife Switch", "Рубильник"},
                {"voltage_warning_sign", "Sign: Danger, High Voltage", "Плакат «Стой! Напряжение»"},
                {"cable_drum", "Cable Drum", "Кабельный барабан"},
                {"dielectric_mat", "Dielectric Mat", "Диэлектрический коврик"},
                {"conduit", "Wiring Conduit", "Труба электропроводки"},
                {"conduit_vertical", "Vertical Wiring Conduit", "Труба электропроводки вертикальная"},
                {"workbench", "Fitter's Workbench", "Слесарный верстак"},
                {"locker", "Changing Room Locker", "Шкафчик раздевалки"},
                {"shelving", "Storage Shelving", "Стеллаж"},
                {"fire_extinguisher_wall", "Wall Fire Extinguisher", "Огнетушитель на стене"},
                {"fire_hose_cabinet", "Fire Hose Cabinet", "Пожарный шкаф"},
                {"first_aid_box", "First Aid Box", "Аптечка"},
                {"oil_drum", "Oil Drum", "Бочка с маслом"},
                {"gas_cylinders", "Gas Cylinder Rack", "Стойка с баллонами"},
                {"column_i_beam", "I-Beam Column", "Колонна из двутавра"},
                {"floor_marking", "Floor Marking Line", "Разметка пола"},
                {"floor_marking_hazard", "Hazard Floor Marking", "Разметка пола «опасная зона»"}};
        java.util.Map<String, String[]> names = new java.util.HashMap<>();
        for (String[] d : decor) {
            b(d[0], d[1], d[2]);
            names.put(d[0], new String[] {d[1], d[2]});
        }
        dev.denfry.promptcritical.mod.registry.PcBlocks.decorForms().forEach((db, form) -> {
            String[] n = names.get(form.base());
            String[] suffix = switch (form.kind()) {
                case "slab" -> new String[] {" Slab", "плита"};
                case "stairs" -> new String[] {" Stairs", "ступени"};
                default -> new String[] {" Wall", "стенка"};
            };
            b(db.getId().getPath(), n[0] + suffix[0], n[1] + " (" + suffix[1] + ")");
        });
        MISC.putAll(dev.denfry.promptcritical.mod.mine.HoistText.LANG);
        MISC.putAll(dev.denfry.promptcritical.mod.mine.ShaftSignalText.LANG);
        // сцены Ponder (11 §11.1.4)
        dev.denfry.promptcritical.mod.content.PonderLayouts.TEXTS.forEach((scene, t) -> {
            MISC.put(dev.denfry.promptcritical.mod.content.PonderLayouts.langKey(scene, "header"), t.header());
            for (int i = 0; i < t.lines().size(); i++)
                MISC.put(dev.denfry.promptcritical.mod.content.PonderLayouts.langKey(scene, "text_" + (i + 1)), t.lines().get(i));
        });
        // тексты щитов, объявленные в полях блок-сущностей, регистрируются при инициализации класса, а datagen эти
        // классы не грузит: без явной инициализации ключи twin.msg.* не попадали в языковые файлы
        for (String c : new String[] {"dev.denfry.promptcritical.mod.scada.SimulatorDeskBlockEntity",
                "dev.denfry.promptcritical.mod.scada.TrainingSimulatorBlockEntity",
                "dev.denfry.promptcritical.mod.scada.TwinWorkstationBlockEntity",
                "dev.denfry.promptcritical.mod.scada.OperatorLicenseItem"}) {
            try {
                Class.forName(c, true, PcLanguageProvider.class.getClassLoader());
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("panel text holder missing: " + c, e);
            }
        }
        MISC.putAll(dev.denfry.promptcritical.mod.panel.PanelText.lang());
        MISC.putAll(dev.denfry.promptcritical.mod.drilling.DrillingText.lang());
        MISC.putAll(dev.denfry.promptcritical.mod.production.ProductionText.lang());
        MISC.putAll(dev.denfry.promptcritical.mod.scada.ScadaText.lang());
        MISC.putAll(dev.denfry.promptcritical.mod.mes.MesText.lang());
        MISC.putAll(dev.denfry.promptcritical.mod.gas.GasText.lang());
        MISC.putAll(dev.denfry.promptcritical.mod.chem.ChemText.lang());
        MISC.putAll(dev.denfry.promptcritical.mod.heavywater.HeavyWaterText.lang());
        MISC.putAll(dev.denfry.promptcritical.mod.nonferrous.NonferrousText.lang());
        MISC.putAll(dev.denfry.promptcritical.mod.nuclear.NuclearText.lang());
        MISC.putAll(dev.denfry.promptcritical.mod.npp.NppText.lang());
        MISC.putAll(dev.denfry.promptcritical.mod.isotopes.IsotopeText.lang());
        MISC.putAll(dev.denfry.promptcritical.mod.electronics.ElectronicsText.lang());
        MISC.putAll(dev.denfry.promptcritical.mod.environment.AirQualityService.lang());
        MISC.putAll(dev.denfry.promptcritical.mod.market.MarketText.lang());
        MISC.putAll(dev.denfry.promptcritical.mod.chlor.ChlorText.lang());
        MISC.putAll(dev.denfry.promptcritical.mod.environment.ToxicReleaseService.lang());
        MISC.putAll(dev.denfry.promptcritical.mod.hazard.HazardText.LANG);
        MISC.putAll(dev.denfry.promptcritical.mod.environment.RadiationService.lang());
        i("vibrometer", "Vibration Analyser", "Виброанализатор");
        i("hearing_protection", "Ear Muffs", "Противошумные наушники");
        i("bearing_kit", "Rolling Bearing Kit", "Комплект подшипника качения");
        i("balancing_weights", "Balancing Weights", "Балансировочные грузы");
        i("alignment_tool", "Shaft Alignment Tool", "Приспособление для центровки валов");
        b("instrument_vibration", "Vibration Transmitter", "Вибродатчик");
        MISC.putAll(dev.denfry.promptcritical.mod.acoustics.Acoustics.lang());
        MISC.putAll(dev.denfry.promptcritical.mod.radiation.RadiationBlocksText.lang());
        MISC.putAll(dev.denfry.promptcritical.mod.radiation.ContaminationText.lang());
        MISC.putAll(dev.denfry.promptcritical.mod.reprocessing.ReprocessingText.lang());
        MISC.putAll(dev.denfry.promptcritical.mod.waste.WasteText.lang());
        MISC.putAll(dev.denfry.promptcritical.mod.uranium.UraniumText.lang());
        MISC.put("death.attack.promptcritical.chlorine", new String[] {"%1$s was gassed by chlorine", "%1$s отравлен хлором"});
        MISC.put("death.attack.promptcritical.chlorine.player", new String[] {"%1$s was gassed by chlorine while fighting %2$s",
                "%1$s отравлен хлором, спасаясь от %2$s"});
        MISC.putAll(dev.denfry.promptcritical.mod.nitrogen.NitrogenText.lang());
        MISC.putAll(dev.denfry.promptcritical.mod.methanol.MethanolText.lang());
        safety();
    }

    /** Промышленная безопасность 13: СИЗ §13.7, замкнутые пространства, обходы, факел, знаки. */
    private static void safety() {
        i("hard_hat", "Hard Hat", "Защитная каска");
        i("gas_mask", "Gas Mask", "Фильтрующий противогаз");
        i("gas_mask_filter", "Gas Mask Filter", "Фильтр противогаза");
        i("scba", "Self-Contained Breathing Apparatus", "Дыхательный аппарат на сжатом воздухе");
        i("scba_cylinder", "SCBA Air Cylinder", "Баллон дыхательного аппарата");
        i("fire_suit", "Fire Proximity Suit", "Огнезащитный костюм");
        i("safety_harness", "Safety Harness", "Страховочная привязь");
        i("lifeline", "Lifeline", "Страховочный фал");
        b("manway", "Manway", "Люк-лаз");
        b("rescue_tripod", "Rescue Tripod", "Спасательная тренога");
        b("portable_ventilator", "Portable Ventilator", "Переносной вентилятор");
        b("patrol_checkpoint", "Patrol Checkpoint", "Метка обхода");
        i("patrol_reader", "Patrol Reader", "Считыватель обходчика");
        b("flare_ignition_panel", "Flare Ignition Panel", "Щит розжига факела");
        b("flare_knockout_drum", "Flare Knock-out Drum", "Факельный сепаратор капель");
        b("flare_seal_drum", "Flare Seal Drum", "Гидрозатвор факела");
        b("turbine_turning_gear", "Turbine Turning Gear", "Валоповоротное устройство");
        b("charcoal_filter", "Charcoal Iodine Filter", "Йодный угольный фильтр");
        b("vent_stack", "Ventilation Stack", "Вентиляционная труба");
        b("hazard_sign", "Safety Sign", "Знак безопасности");
        b("water_intake", "Water Intake", "Водозабор");
        b("hazard_diamond", "NFPA 704 Diamond", "Ромб NFPA 704");
        b("un_placard", "ADR Placard", "Табличка ДОПОГ");
        MISC.putAll(dev.denfry.promptcritical.mod.safety.SafetyText.LANG);
    }

    @Override
    protected void addTranslations() {
        int k = russian ? 1 : 0;
        BLOCKS.forEach((id, n) -> add("block." + PromptCritical.MOD_ID + "." + id, n[k]));
        ITEMS.forEach((id, n) -> add("item." + PromptCritical.MOD_ID + "." + id, n[k]));
        FLUIDS.forEach((id, n) -> add("fluid_type." + PromptCritical.MOD_ID + "." + id, n[k]));
        MISC.forEach((key, n) -> add(key, n[k]));
        dev.denfry.promptcritical.mod.boiler.BoilerText.all().forEach((key, n) -> add(key, n[k]));
    }
}
