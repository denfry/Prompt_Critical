package dev.denfry.promptcritical.mod.data;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.foundry.FoundryText;
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
        b("grate_stoker", "Chain Grate Stoker", "Цепная колосниковая решётка");
        b("chimney", "Chimney", "Дымовая труба");
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
        b("mine_dispatch_cabinet", "Mine Gas Monitoring Cabinet", "Шкаф аппаратуры газовой защиты");
        b("mine_hoist", "Mine Hoist", "Шахтная подъёмная машина");
        b("headframe", "Headframe", "Копёр");
        b("mine_cage", "Mine Cage", "Шахтная клеть");
        b("mine_drainage_pump", "Mine Drainage Pump", "Насос главного водоотлива");
        b("coal_jig", "Coal Jig", "Отсадочная машина");
        b("cupola_furnace", "Cupola Furnace", "Вагранка");
        b("crucible_furnace", "Crucible Furnace", "Тигельная печь");
        b("sand_mold", "Sand Mould", "Песчаная форма");
        b("molding_station", "Moulding Machine", "Формовочная машина");
        b("shakeout_grate", "Shakeout Grate", "Выбивная решётка");
        b("machine_lathe", "Lathe", "Токарный станок");
        b("drill_press", "Drill Press", "Сверлильный станок");
        b("milling_machine", "Knee Milling Machine", "Консольно-фрезерный станок");
        b("shaping_machine", "Shaping Machine", "Поперечно-строгальный станок");
        b("surface_grinder", "Surface Grinder", "Плоскошлифовальный станок");
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
        i("graphite_electrode", "Graphite Electrode", "Графитированный электрод");
        i("raw_uraninite", "Raw Pitchblende", "Урановая смолка (сырьё)");
        i("crushed_uranium_ore", "Crushed Uranium Ore", "Дроблёная урановая руда");
        i("ground_uranium_ore", "Ground Uranium Ore", "Молотая урановая руда");
        i("yellowcake", "Yellowcake", "Жёлтый кек");
        i("uo2_powder", "Uranium Dioxide Powder", "Порошок диоксида урана");
        i("magnesium_ingot", "Magnesium Ingot", "Слиток магния");
        i("uranium_slug", "Natural Uranium Slug Cassette", "Кассета блочков природного урана");
        b("magnetic_separator", "Magnetic Separator", "Магнитный сепаратор");
        b("sinter_strand", "Sinter Strand", "Агломашина");
        b("blast_furnace_shell", "Blast Furnace Shell", "Кожух домны");
        b("blast_furnace_tuyere", "Blast Furnace Tuyere", "Фурма домны");
        b("blast_furnace_taphole", "Blast Furnace Taphole", "Лётка домны");
        b("blast_furnace_controller", "Blast Furnace Control Panel", "Пульт домны");
        b("blast_furnace_top", "Blast Furnace Top", "Засыпной аппарат");
        b("hot_blast_stove", "Hot Blast Stove", "Воздухонагреватель");
        b("blast_blower", "Blast Blower", "Воздуходувка");
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
        b("generator_exciter", "Generator Exciter", "Возбудитель генератора");
        b("instrument_synchroscope", "Synchroscope", "Синхроноскоп");
        b("electric_motor", "Electric Motor", "Электродвигатель");
        b("station_battery", "Station Battery", "Аккумуляторная батарея");
        b("capacitor_bank", "Capacitor Bank", "Батарея конденсаторов");
        b("electric_arc_furnace", "Electric Arc Furnace", "Дуговая сталеплавильная печь");
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
        i("hafnium_ingot", "Hafnium Ingot", "Слиток гафния");
        i("zircaloy_ingot", "Zirconium Alloy Ingot", "Слиток циркониевого сплава");
        i("cladding_tube", "Zirconium Cladding Tube", "Оболочечная труба из циркониевого сплава");
        i("spacer_grid", "Spacer Grid", "Дистанционирующая решётка");
        i("dosimeter_radiometer", "Dosimeter-Radiometer DKS-04", "Дозиметр-радиометр ДКС-04");
        b("lead_block", "Lead Shielding Block", "Свинцовый блок защиты");
        b("helium_recovery_unit", "Helium Recovery Unit", "Гелиевая установка");
        b("fuel_rack", "Spent Fuel Pool Rack", "Стеллаж бассейна выдержки");
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
        i("pt_rh_gauze", "Platinum-Rhodium Gauze Pack", "Пакет платинородиевых сеток");
        i("catalyst_denox", "Tail Gas Reduction Catalyst", "Катализатор очистки хвостовых газов");
        i("cap_lamp", "Miner's Cap Lamp", "Головной шахтный светильник");
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
        b("seismic_recorder", "Seismic Recorder", "Сейсмостанция");
        b("geophone", "Geophone", "Сейсмоприёмник");
        b("drillers_console", "Driller's Console", "Пульт бурильщика");
        b("rotary_table", "Rotary Table", "Ротор");
        b("mud_pump", "Triplex Mud Pump", "Буровой насос");
        b("drawworks", "Drawworks", "Буровая лебёдка");
        b("derrick_segment", "Derrick Section", "Секция вышки");
        b("bop_annular", "Annular Preventer", "Кольцевой превентор");
        b("choke_manifold", "Choke Manifold", "Манифольд дросселирования");
        b("mud_tank", "Mud Tank", "Ёмкость бурового раствора");
        b("mud_mixing_hopper", "Mud Mixing Hopper", "Гидроворонка");
        i("barite_powder", "Barite Sack (50 kg)", "Мешок барита (50 кг)");
        b("gas_compressor", "Gas Compressor", "Газовый компрессор");
        b("glycol_regenerator", "Glycol Regenerator", "Регенератор гликоля");
        b("low_temperature_separator", "Low-Temperature Separation Unit", "Установка НТС");
        b("ngl_fractionator", "NGL Fractionation Unit", "Газофракционирующая установка");
        b("rtu_station", "Telemetry Gateway (RTU)", "Шлюз телемеханики (КП)");
        b("pyrolysis_furnace", "Pyrolysis Furnace", "Печь пиролиза");
        b("cracked_gas_unit", "Cracked Gas Separation Unit", "Установка газоразделения пирогаза");
        b("ldpe_reactor", "LDPE Unit", "Установка ПЭВД");
        b("brine_treater", "Brine Treatment Plant", "Рассольное отделение");
        b("chlor_alkali_cell", "Diaphragm Chlorine Cell Series", "Серия диафрагменных электролизёров");
        b("rectifier_unit", "Electrolysis Rectifier Unit", "Выпрямительный агрегат электролиза");
        b("chlorine_liquefier", "Chlorine Drying and Liquefaction", "Осушка и сжижение хлора");
        b("edc_unit", "EDC Unit (Chlorination and Oxychlorination)", "Установка ДХЭ (хлорирование и оксихлорирование)");
        b("edc_cracker", "EDC Cracking Furnace", "Печь пиролиза ДХЭ");
        b("pvc_reactor", "PVC Suspension Autoclave", "Автоклав суспензионного ПВХ");
        b("acid_plant", "Contact Sulphuric Acid Plant", "Сернокислотная установка");
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
        b("power_reactor", "Channel Power Reactor (AM-1 type)", "Канальный энергетический реактор (типа АМ-1)");
        b("irradiation_channel", "Irradiation Channel", "Канал облучения");
        b("rtg_thermoelectric", "Thermoelectric RTG", "Термоэлектрический РИТЭГ");
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
        i("transistor", "Transistor", "Транзистор");
        i("integrated_circuit", "Logic Integrated Circuit", "Логическая микросхема");
        i("memory_chip", "Memory Chip", "Микросхема памяти");
        i("microprocessor", "Microprocessor", "Микропроцессор");
        i("copper_foil", "Copper Foil", "Медная фольга");
        i("resistor", "Resistor", "Резистор");
        i("capacitor", "Capacitor", "Конденсатор");
        i("printed_circuit_board", "Printed Circuit Board", "Печатная плата");
        i("cathode_ray_tube", "Cathode-Ray Tube", "Электронно-лучевая трубка");
        i("stepper_lens", "Stepper Projection Lens", "Объектив степпера");
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
        b("pwr_reactor", "Pressurized Water Reactor (VVER type)", "Водо-водяной энергетический реактор (типа ВВЭР)");
        b("pressurizer", "Pressurizer", "Компенсатор давления");
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
        i("seismic_section", "Seismic Section", "Сейсмический разрез");
        i("copper_wire", "Copper Wire", "Медная проволока");
        i("generator_coupling", "Shear-Pin Coupling", "Муфта со срезным штифтом");
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
        i("relay_ufls", "Load-Shedding Relay (UFLS)", "Реле АЧР");
        i("relay_autoreclose", "Auto-Reclose Relay", "Реле АПВ");
        i("relay_breaker_failure", "Breaker Failure Relay", "Реле УРОВ");
        i("coke", "Coke", "Кокс");
        i("coke_breeze", "Coke Breeze", "Коксовая мелочь");
        i("pitch_coke", "Pitch Coke", "Пековый кокс");
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
        b("ingot_mold", "Ingot Mold", "Изложница");
        b("reheating_furnace", "Reheating Furnace", "Нагревательная печь");
        b("heat_treatment_furnace", "Heat Treatment Furnace", "Термическая печь");
        b("quench_tank", "Quench Tank", "Закалочный бак");
        b("roller_table", "Roller Table", "Рольганг");
        b("rolling_mill_stand", "Rolling Mill Stand", "Прокатная клеть");
        b("piercing_mill", "Piercing Mill", "Прошивной стан");
        b("pipe_welder", "Induction Pipe Welder", "Трубосварочный стан ТВЧ");
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
        b("steam_turbine", "Steam Turbine", "Паровая турбина");
        b("hydro_turbine", "Hydro Turbine", "Гидротурбина");
        b("tidal_unit", "Tidal Bulb Unit", "Капсульный агрегат ПЭС");
        b("tide_gauge", "Tide Gauge", "Мареограф");
        b("pumped_storage_unit", "Pumped Storage Unit", "Обратимый агрегат ГАЭС");
        b("electrode_boiler", "Electrode Boiler", "Электродный котёл");
        b("wind_turbine", "Wind Power Unit D-30", "Ветроэлектростанция Д-30");
        b("dam_gate", "Dam Intake Gate", "Затвор водозабора");
        b("gas_turbine", "Gas Turbine Unit", "Газотурбинная установка");
        b("hrsg", "Heat Recovery Steam Generator", "Котёл-утилизатор");
        b("turbine_gearbox", "Turbine Gearbox", "Редуктор турбины");
        b("condenser", "Steam Condenser", "Конденсатор");
        b("cooling_tower_cell", "Cooling Tower Cell", "Секция градирни");
        b("induced_draft_fan", "Induced Draft Fan", "Дымосос");
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
        FLUIDS.put("trichlorosilane", new String[] {"Trichlorosilane", "Трихлорсилан"});
        FLUIDS.put("ethylene_dichloride", new String[] {"Ethylene Dichloride", "Дихлорэтан"});
        FLUIDS.put("vinyl_chloride", new String[] {"Vinyl Chloride", "Винилхлорид"});
        FLUIDS.put("sulfuric_acid", new String[] {"Sulphuric Acid (98 %)", "Серная кислота (98 %)"});
        FLUIDS.put("hydrogen_fluoride", new String[] {"Anhydrous Hydrogen Fluoride", "Безводный фтористый водород"});
        FLUIDS.put("fluorine", new String[] {"Fluorine", "Фтор"});
        FLUIDS.put("pregnant_leach_solution", new String[] {"Pregnant Leach Solution", "Продуктивный раствор"});
        FLUIDS.put("raffinate", new String[] {"Barren Solution", "Маточник сорбции"});
        FLUIDS.put("uranium_tailings", new String[] {"Uranium Mill Tailings", "Хвосты урановой фабрики"});
        FLUIDS.put("uranium_eluate", new String[] {"Uranium Eluate", "Урановый элюат"});
        FLUIDS.put("syngas", new String[] {"Ammonia Syngas (3 H₂ + N₂)", "Азотоводородная смесь"});
        FLUIDS.put("ammonia", new String[] {"Liquid Ammonia", "Жидкий аммиак"});
        FLUIDS.put("nitric_acid", new String[] {"Nitric Acid (56 %)", "Азотная кислота 56 %"});
        FLUIDS.put("concentrated_nitric_acid", new String[] {"Concentrated Nitric Acid (98 %)", "Концентрированная азотная кислота 98 %"});
        FLUIDS.put("helium", new String[] {"Helium", "Гелий"});
        FLUIDS.put("produced_water", new String[] {"Produced Water", "Пластовая вода"});
        FLUIDS.put("associated_gas", new String[] {"Associated Gas", "Попутный нефтяной газ"});
        FLUIDS.put("gasoline", new String[] {"Straight-Run Gasoline", "Прямогонный бензин"});
        FLUIDS.put("kerosene", new String[] {"Kerosene", "Керосин"});
        FLUIDS.put("diesel_fuel", new String[] {"Diesel Fuel", "Дизельное топливо"});
        FLUIDS.put("fuel_oil", new String[] {"Fuel Oil (Mazut)", "Мазут"});
        FLUIDS.put("mine_methane", new String[] {"Mine Methane", "Шахтный метан"});
        FLUIDS.put("raw_water", new String[] {"Raw Water", "Сырая вода"});
        FLUIDS.put("mine_water", new String[] {"Mine Water", "Шахтная вода"});
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
        MISC.putAll(dev.denfry.promptcritical.mod.paperwork.PaperworkText.LANG);
        MISC.putAll(dev.denfry.promptcritical.mod.ironworks.IronworksText.LANG);
        MISC.putAll(dev.denfry.promptcritical.mod.steelworks.SteelworksText.LANG);
        MISC.putAll(dev.denfry.promptcritical.mod.ops.OpsText.LANG);
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
        // сцены Ponder (11 §11.1.4)
        dev.denfry.promptcritical.mod.content.PonderLayouts.TEXTS.forEach((scene, t) -> {
            MISC.put(dev.denfry.promptcritical.mod.content.PonderLayouts.langKey(scene, "header"), t.header());
            for (int i = 0; i < t.lines().size(); i++)
                MISC.put(dev.denfry.promptcritical.mod.content.PonderLayouts.langKey(scene, "text_" + (i + 1)), t.lines().get(i));
        });
        MISC.putAll(dev.denfry.promptcritical.mod.panel.PanelText.lang());
        MISC.putAll(dev.denfry.promptcritical.mod.drilling.DrillingText.lang());
        MISC.putAll(dev.denfry.promptcritical.mod.production.ProductionText.lang());
        MISC.putAll(dev.denfry.promptcritical.mod.scada.ScadaText.lang());
        MISC.putAll(dev.denfry.promptcritical.mod.gas.GasText.lang());
        MISC.putAll(dev.denfry.promptcritical.mod.chem.ChemText.lang());
        MISC.putAll(dev.denfry.promptcritical.mod.nuclear.NuclearText.lang());
        MISC.putAll(dev.denfry.promptcritical.mod.npp.NppText.lang());
        MISC.putAll(dev.denfry.promptcritical.mod.isotopes.IsotopeText.lang());
        MISC.putAll(dev.denfry.promptcritical.mod.electronics.ElectronicsText.lang());
        MISC.putAll(dev.denfry.promptcritical.mod.chlor.ChlorText.lang());
        MISC.putAll(dev.denfry.promptcritical.mod.environment.ToxicReleaseService.lang());
        MISC.putAll(dev.denfry.promptcritical.mod.environment.RadiationService.lang());
        MISC.putAll(dev.denfry.promptcritical.mod.radiation.RadiationBlocksText.lang());
        MISC.putAll(dev.denfry.promptcritical.mod.reprocessing.ReprocessingText.lang());
        MISC.putAll(dev.denfry.promptcritical.mod.uranium.UraniumText.lang());
        MISC.put("death.attack.promptcritical.chlorine", new String[] {"%1$s was gassed by chlorine", "%1$s отравлен хлором"});
        MISC.put("death.attack.promptcritical.chlorine.player", new String[] {"%1$s was gassed by chlorine while fighting %2$s",
                "%1$s отравлен хлором, спасаясь от %2$s"});
        MISC.putAll(dev.denfry.promptcritical.mod.nitrogen.NitrogenText.lang());
        MISC.putAll(dev.denfry.promptcritical.mod.methanol.MethanolText.lang());
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
