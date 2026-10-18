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
        i("methanometer", "Methanometer", "Метанометр");
        i("engineering_handbook", "Engineering Handbook", "Справочник инженера");
        i("operating_procedure", "Operating Procedure", "Инструкция по эксплуатации");
        i("geological_map", "Geological Map", "Геологическая карта");

        FLUIDS.put("flue_gas", new String[] {"Flue Gas", "Дымовые газы"});
        FLUIDS.put("mine_methane", new String[] {"Mine Methane", "Шахтный метан"});
        FLUIDS.put("raw_water", new String[] {"Raw Water", "Сырая вода"});
        FLUIDS.put("mine_water", new String[] {"Mine Water", "Шахтная вода"});
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
                {"curb", "Curb", "Бордюр"}};
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
