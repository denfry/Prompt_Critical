package dev.denfry.promptcritical.mod.data;

import dev.denfry.promptcritical.mod.PromptCritical;
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
        b("document_lectern", "Document Lectern", "Пюпитр для документов");
        b("canary_cage", "Canary Cage", "Клетка для канарейки");
        b("multiblock_part", "Structure Part", "Часть конструкции");

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
        i("canary", "Canary", "Канарейка");
        i("engineering_handbook", "Engineering Handbook", "Справочник инженера");
        i("operating_procedure", "Operating Procedure", "Инструкция по эксплуатации");
        i("geological_map", "Geological Map", "Геологическая карта");

        FLUIDS.put("flue_gas", new String[] {"Flue Gas", "Дымовые газы"});
        FLUIDS.put("mine_methane", new String[] {"Mine Methane", "Шахтный метан"});
        FLUIDS.put("raw_water", new String[] {"Raw Water", "Сырая вода"});
        FLUIDS.put("mine_water", new String[] {"Mine Water", "Шахтная вода"});

        MISC.put("itemGroup." + PromptCritical.MOD_ID, new String[] {"Prompt Critical", "Prompt Critical"});
        MISC.put("commands.promptcritical.debug.hash", new String[] {"State hash %s at tick %s", "Хеш состояния %s на тике %s"});
        MISC.put("commands.promptcritical.debug.devices", new String[] {"Devices: %s", "Устройств: %s"});
        MISC.put("commands.promptcritical.profile.header", new String[] {
                "Simulation profile of %s: degradation %s, tick p95 %s ms", "Профиль симуляции %s: деградация %s, p95 тика %s мс"});
        MISC.put("commands.promptcritical.profile.damage", new String[] {
                "Damage budget: %s blocks applied, %s queued, %s denied", "Бюджет разрушений: применено %s, в очереди %s, отказано %s"});
        MISC.put("commands.promptcritical.profile.reset", new String[] {"Simulation profile reset", "Профиль симуляции сброшен"});
    }

    @Override
    protected void addTranslations() {
        int k = russian ? 1 : 0;
        BLOCKS.forEach((id, n) -> add("block." + PromptCritical.MOD_ID + "." + id, n[k]));
        ITEMS.forEach((id, n) -> add("item." + PromptCritical.MOD_ID + "." + id, n[k]));
        FLUIDS.forEach((id, n) -> add("fluid_type." + PromptCritical.MOD_ID + "." + id, n[k]));
        MISC.forEach((key, n) -> add(key, n[k]));
    }
}
