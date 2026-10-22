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
        b("coke_oven", "Coke Oven", "Коксовая камера");
        b("coke_battery_wall", "Coke Battery Flue Wall", "Простенок коксовой батареи");
        b("gas_collector_main", "Gas Collector Main", "Газосборник");
        b("coke_pusher_ram", "Coke Pusher Ram", "Штанга коксовыталкивателя");
        b("gas_exhauster", "Coke Oven Gas Exhauster", "Эксгаустер коксового газа");
        b("byproduct_plant", "By-Product Recovery Unit", "Аппарат улавливания");
        b("quench_tower", "Coke Quench Tower", "Тушильная башня");
        b("electrode_extruder", "Electrode Extrusion Press", "Электродный пресс");
        b("baking_furnace", "Electrode Baking Furnace", "Обжиговая печь электродов");
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
        b("lv_distribution_box", "Fuse Box", "Распределительный ящик с предохранителями");

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
        i("casting_pattern", "Casting Pattern", "Модель отливки");
        i("casting", "Casting", "Отливка");
        i("refractory_brick", "Refractory Brick", "Огнеупорный кирпич");
        i("cutting_tool", "Cutting Tool", "Резец");
        i("flame_safety_lamp", "Flame Safety Lamp", "Бензиновая лампа");
        i("self_rescuer", "Self-Rescuer", "Самоспасатель");
        i("stone_duster", "Stone Duster", "Осланцеватель");
        i("wire_rope", "Wire Rope", "Стальной канат");
        i("ventilation_regulator", "Ventilation Regulator", "Вентиляционный регулятор");
        i("canary", "Canary", "Канарейка");
        i("methanometer", "Methanometer", "Метанометр");
        i("engineering_handbook", "Engineering Handbook", "Справочник инженера");
        i("operating_procedure", "Operating Procedure", "Инструкция по эксплуатации");
        i("geological_map", "Geological Map", "Геологическая карта");
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
        MISC.putAll(dev.denfry.promptcritical.mod.ironworks.IronworksText.LANG);
        MISC.putAll(dev.denfry.promptcritical.mod.steelworks.SteelworksText.LANG);
        MISC.putAll(dev.denfry.promptcritical.mod.ops.OpsText.LANG);
        MISC.putAll(dev.denfry.promptcritical.mod.powerhouse.PowerhouseText.LANG);
        MISC.putAll(dev.denfry.promptcritical.mod.grid.GridText.LANG);
        MISC.putAll(dev.denfry.promptcritical.mod.automation.AutomationText.LANG);
        MISC.putAll(dev.denfry.promptcritical.mod.content.ContentText.LANG);
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
                {"room_sign_control", "Sign: Main Control Room", "Табличка «ГЩУ»"}};
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
