package dev.denfry.promptcritical.mod.data;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.content.SimAchievements;
import dev.denfry.promptcritical.mod.content.SimEventTrigger;
import dev.denfry.promptcritical.sim.reactor.ReactorSubsystem;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.automation.AutomationSubsystem;
import dev.denfry.promptcritical.sim.grid.GridSubsystem;
import dev.denfry.promptcritical.sim.plant.SteamPlant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.advancements.critereon.MinMaxBounds;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.common.data.AdvancementProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

/**
 * Достижения каталога 11 §11.2.2 (производство, эксплуатация, автоматизация, безопасность, мир): ванильные, условия — через {@code promptcritical:sim_event} или предмет в инвентаре, награда — только
 * опыт (C2, T11.5). Остальные ветки каталога добавляются в своих вехах.
 */
public final class PcAdvancements implements AdvancementProvider.AdvancementGenerator {
    /** id → {название en, ru, описание en, ru}. */
    public static final Map<String, String[]> LANG = new LinkedHashMap<>();

    static {
        LANG.put("root", new String[] {"Prompt Critical", "Prompt Critical", "Open the Engineering Handbook: the plant starts with coal and steam",
                "Откройте справочник инженера: комплекс начинается с угля и пара"});
        LANG.put("first_steam", new String[] {"First Steam", "Первый пар", "A steam engine turns a Create shaft",
                "Паровая машина отдаёт момент на вал Create"});
        LANG.put("canary", new String[] {"Canary in a Cage", "Канарейка в клетке", "Hang a canary cage in a mine working",
                "Поставьте клетку с канарейкой в шахте"});
        LANG.put("canary_saved", new String[] {"The Bird Lives", "Птичка жива", "A canary that fell from its perch came round in fresh air",
                "Упавшая с жёрдочки канарейка очнулась на свежем воздухе — её вынесли вовремя"});
        LANG.put("coke", new String[] {"Coke and Gas", "Кокс и газ", "Push the first coke out of a coke oven battery",
                "Выдайте первую партию кокса из коксовой батареи"});
        LANG.put("pig_iron", new String[] {"Pig Iron", "Чугун", "Tap the first iron from a blast furnace",
                "Первый выпуск чугуна из домны"});
        LANG.put("steel", new String[] {"Steel", "Сталь", "Tap the first heat from a converter",
                "Первая плавка в конвертере"});
        LANG.put("seamless", new String[] {"No Seam", "Без шва", "Pierce the first seamless pipe",
                "Первая бесшовная труба"});
        LANG.put("synchronized", new String[] {"On the Grid", "В сеть",
                "Close a generator onto the grid with a torque shock of no more than 10 %",
                "Генератор включён в сеть без броска момента больше 10 %"});
        LANG.put("first_criticality", new String[] {"Prompt Is Not Yet Critical", "Пока не мгновенно",
                "Hold a graphite pile self-sustaining for five seconds without help from the source",
                "Удержите графитовый котёл на самоподдерживающейся реакции пять секунд без помощи источника"});
        LANG.put("pile_scram", new String[] {"Zip!", "Зип!",
                "See the pile's zip rods fall on a scram", "Увидеть, как падают аварийные стержни котла"});
        LANG.put("relay_logic", new String[] {"Click-Clack", "Щёлк-щёлк", "A circuit of at least five relays drives a mechanism",
                "Схема из пяти и более реле управляет механизмом"});
        LANG.put("analog_pid", new String[] {"No Computer Needed", "Без компьютера",
                "A PI controller built from analog blocks holds its setpoint for a whole day",
                "ПИ-регулятор на аналоговых блоках держит параметр целые сутки"});
        LANG.put("first_oil", new String[] {"Gusher", "Фонтан", "The first crude oil from a well", "Первая нефть из скважины"});
        LANG.put("helium", new String[] {"Light as Helium", "Лёгкий как гелий", "Separate helium from natural gas", "Выделите гелий из природного газа"});
        LANG.put("ethylene", new String[] {"Ethylene", "Этилен", "The first ethylene from pyrolysis", "Первый этилен пиролиза"});
        LANG.put("sulfur", new String[] {"Yellow Mountain", "Жёлтая гора", "Hold 64 sulfur", "Соберите 64 серы"});
        LANG.put("bakelite", new String[] {"Age of Plastics", "Эпоха пластмасс", "Make the first phenolic resin", "Получите первую фенолформальдегидную смолу"});
        LANG.put("yellowcake", new String[] {"Yellowcake", "Жёлтый кек", "The first yellowcake", "Первый жёлтый кек"});
        LANG.put("uf6", new String[] {"Hexafluoride", "Гексафторид", "Fill the first UF6 cylinder", "Наполните первый баллон UF₆"});
        LANG.put("enriched", new String[] {"Three Percent", "Три процента", "Enrich UF6 to 3 % U-235 or more", "Обогатите UF₆ до 3 % U-235 и выше"});
        LANG.put("first_fa", new String[] {"First Assembly", "Первая сборка", "Assemble the first fuel assembly", "Соберите первую ТВС"});
        LANG.put("closed_cycle", new String[] {"Closed Cycle", "Замкнутый цикл", "Load MOX fuel from your own plutonium into a reactor and hold a vitrified HLW canister", "Загрузите МОКС-топливо из своего плутония в реактор и получите канистру остеклованных ВАО"});
        LANG.put("scram_clean", new String[] {"Clean Scram", "Чистая АЗ", "Scram a power reactor from above 50 % with no fuel failing in the minute after", "Аварийная защита с мощности выше 50 % без разгерметизации ТВС в следующую минуту"});
        LANG.put("pumpjack", new String[] {"Nodding Donkey", "Качалка", "A pumpjack lifts oil for a whole day",
                "Станок-качалка качает нефть целые сутки"});
        LANG.put("first_cut", new String[] {"First Cut", "Первая фракция",
                "A crude column makes on-spec gasoline: no more than 30 K overlap with kerosene",
                "Ректификационная колонна выдаёт бензин в спецификации: перекрытие с керосином не больше 30 К"});
        LANG.put("load_follow", new String[] {"Load Following", "Под нагрузку",
                "A power reactor follows the dispatcher's daily load curve for a whole day within 5 %",
                "Реактор целые сутки следует суточному графику диспетчера с отклонением меньше 5 %"});
        LANG.put("hundred_days", new String[] {"Hundred Days", "Сто суток",
                "Run a power reactor for 100 days with no automatic scram (manual ones allowed)",
                "Энергетический реактор проработал 100 суток без автоматической АЗ (ручные не в счёт)"});
        LANG.put("industrial_complex", new String[] {"Industrial Complex", "Промышленный комплекс",
                "A blast furnace, a crude unit, a gas plant and a reactor above 50 % all running at once",
                "Одновременно работают домна, НПЗ, ГПЗ и реактор на мощности выше 50 %"});
        LANG.put("black_start", new String[] {"From Scratch", "С нуля",
                "After a blackout, start a stopped generator and bring the dead network back under load on your own",
                "После блэкаута запустите остановленный генератор и сами поднимите обесточенную сеть под нагрузку"});
        LANG.put("one_over_m", new String[] {"One over M", "Метод 1/M",
                "Bring a pile critical after counting it at three or more different loadings",
                "Выведите котёл на критичность, перед этим сняв счёт при трёх и более разных загрузках"});
        LANG.put("xenon_pit", new String[] {"Iodine Pit", "Йодная яма",
                "Bring a T1 power reactor back to power out of the xenon pit without dropping below 15 rods of reactivity margin",
                "Выведите реактор T1 на мощность из йодной ямы, не опуская ОЗР ниже 15 стержней"});
        LANG.put("kill_well", new String[] {"Well Killed", "Скважина заглушена", "Kill a well kick with the driller's method, without a blowout", "Ликвидируйте ГНВП методом бурильщика, без открытого фонтана"});
        LANG.put("plc_first", new String[] {"First Scan", "Первый скан", "A PLC program runs in RUN", "Программа ПЛК работает в режиме RUN"});
        LANG.put("all_languages", new String[] {"IEC Polyglot", "Полиглот МЭК", "One PLC has run programs in LD, FBD, SFC and ST", "Один ПЛК работал на программах LD, FBD, SFC и ST"});
        LANG.put("lua_first", new String[] {"Hello, Reactor", "Hello, reactor", "A Lua program reads a machine through a pc_rtu telemetry gateway", "Lua-программа читает машину через шлюз телемеханики pc_rtu"});
        LANG.put("hmi", new String[] {"High-Performance HMI", "Высокоэффективный HMI", "A pcui overview screen (ISA-101 level L1) shows 20 tags or more",
                "Экран обзора pcui (уровень L1 по ISA-101) показывает 20 тегов и больше"});
        LANG.put("stuck_valve", new String[] {"Don't Trust the Lamp", "Не верь лампе",
                "Pass the simulator lesson where a stuck relief valve shows up on its position sensor, not on the 'command sent' lamp",
                "Пройдите урок тренажёра, где заклинивший клапан виден по датчику положения, а не по лампе «команда подана»"});
        LANG.put("historian", new String[] {"Plant Memory", "Память завода", "A historian keeps 100 tags, each for a game day", "Историан хранит 100 тегов, каждый — за игровые сутки"});
        LANG.put("telemetry", new String[] {"Telemechanics", "Телемеханика", "Ten telemetry gateways report to one historian", "Десять шлюзов телемеханики у одного историана"});
        LANG.put("dosimeter", new String[] {"Count the Dose", "Считай дозу", "Carry a dosimeter for a game day", "Носите дозиметр игровые сутки"});
        LANG.put("lockout", new String[] {"Padlock", "Замок", "Hang your first LOTO padlock", "Повесьте первый замок LOTO"});
        LANG.put("permit", new String[] {"By the Permit", "По наряду", "Do hot work under a work permit with a gas test",
                "Проведите огневые работы по наряду-допуску с газоанализом"});
        LANG.put("turnaround", new String[] {"Turnaround", "Остановочный ремонт", "Shut a plant down, repair it under permits and start it again",
                "Установка остановлена, отремонтирована по нарядам и пущена"});
        LANG.put("alara", new String[] {"ALARA", "ALARA", "Repair a main coolant pump with a crew collective dose under 10 mSv",
                "Отремонтируйте ГЦН с коллективной дозой бригады меньше 10 мЗв"});
        LANG.put("proof_test", new String[] {"Proof Tested", "Поверено", "Proof-test a SIS function before it is overdue",
                "Выполните периодическую проверку ПАЗ до просрочки"});
        LANG.put("sis_matrix", new String[] {"The Matrix", "Матрица", "A SIS cause and effect matrix with ten causes or more and 2oo3 voting",
                "МПС ПАЗ с десятью и более причинами и голосованием 2oo3"});
        LANG.put("own_sun", new String[] {"Own Sun", "Своё солнце", "Get the first sealed radioactive source", "Получите первый закрытый радионуклидный источник"});
        LANG.put("through_steel", new String[] {"Through Steel", "Сквозь сталь", "Find a weld defect on a radiograph", "Найдите дефект шва на рентгенограмме"});
        LANG.put("pitch_lake", new String[] {"Asphalt Lake", "Асфальтовое озеро", "Find a natural bitumen lake",
                "Найдите естественный выход битума"});
        LANG.put("gas_crater", new String[] {"Door to Hell", "Врата ада", "Find a burning gas crater", "Найдите горящий газовый кратер"});
        LANG.put("oklo", new String[] {"The Planet Was First", "Планета успела первой",
                "An assay shows depleted U-235 in the ore of a natural reactor",
                "Анализ показал обеднённый U-235 в руде природного реактора"});
        LANG.put("orphan_found", new String[] {"Nobody's Source", "Ничей источник", "Find an orphan radioactive source",
                "Найдите бесхозный радиоактивный источник"});
        LANG.put("digital_twin", new String[] {"Twin First", "Сначала на двойнике",
                "A PLC program passed its acceptance test on the digital twin, then went into the real PLC",
                "Программа прошла испытания на цифровом двойнике, затем загружена в реальный ПЛК"});
        LANG.put("operator_license", new String[] {"Licensed", "Допуск",
                "Pass the simulator lessons of a plant and get an operator license",
                "Сдайте уроки тренажёра по установке и получите удостоверение оператора"});
    }

    @Override
    public void generate(HolderLookup.Provider registries, Consumer<AdvancementHolder> saver, ExistingFileHelper efh) {
        AdvancementHolder root = Advancement.Builder.advancement()
                .display(PcItems.ENGINEERING_HANDBOOK.get(), title("root"), description("root"),
                        ResourceLocation.fromNamespaceAndPath(PromptCritical.MOD_ID, "textures/block/decor/painted_concrete_two_tone.png"),
                        AdvancementType.TASK, false, false, false)
                .addCriterion("handbook", InventoryChangeTrigger.TriggerInstance.hasItems(PcItems.ENGINEERING_HANDBOOK.get()))
                .save(saver, id("root"), efh);
        AdvancementHolder steam = task(root, PcBlocks.STEAM_ENGINE_CYLINDER.get(), "first_steam", AdvancementType.TASK, false, 50)
                .addCriterion("engine_running", SimEventTrigger.TriggerInstance.of(ResourceLocation.parse(SteamPlant.ENGINE_RUNNING),
                        Map.of("omega_pu", MinMaxBounds.Doubles.atLeast(0.2)), true))
                .save(saver, id("first_steam"), efh);
        AdvancementHolder canary = task(steam, PcBlocks.CANARY_CAGE.get(), "canary", AdvancementType.TASK, false, 20)
                .addCriterion("canary_placed", SimEventTrigger.TriggerInstance.of(ResourceLocation.parse(SimAchievements.CANARY_PLACED),
                        Map.of(), false))
                .save(saver, id("canary"), efh);
        task(canary, PcItems.CANARY.get(), "canary_saved", AdvancementType.TASK, true, 30)
                .addCriterion("canary_revived", SimEventTrigger.TriggerInstance.of(ResourceLocation.parse(SimAchievements.CANARY_REVIVED),
                        Map.of(), true))
                .save(saver, id("canary_saved"), efh);
        // I1: сталь и ток
        AdvancementHolder coke = task(steam, PcItems.COKE.get(), "coke", AdvancementType.TASK, false, 30)
                .addCriterion("coke_pushed", event(SimAchievements.COKE_PUSHED, Map.of()))
                .save(saver, id("coke"), efh);
        AdvancementHolder iron = task(coke, PcItems.PIG_IRON.get(), "pig_iron", AdvancementType.TASK, false, 40)
                .addCriterion("iron_tapped", event(SimAchievements.IRON_TAPPED, Map.of()))
                .save(saver, id("pig_iron"), efh);
        AdvancementHolder steel = task(iron, PcItems.STEEL_INGOT.get(), "steel", AdvancementType.GOAL, false, 60)
                .addCriterion("steel_tapped", event(SimAchievements.STEEL_TAPPED, Map.of()))
                .save(saver, id("steel"), efh);
        task(steel, PcItems.SEAMLESS_PIPE.get(), "seamless", AdvancementType.TASK, false, 40)
                .addCriterion("seamless_pipe", event(SimAchievements.SEAMLESS_PIPE, Map.of()))
                .save(saver, id("seamless"), efh);
        AdvancementHolder sync = task(steam, PcBlocks.SHAFT_GENERATOR.get(), "synchronized", AdvancementType.GOAL, false, 50)
                .addCriterion("sync_closed", event(GridSubsystem.SYNC_CLOSED, Map.of("ok", MinMaxBounds.Doubles.atMost(0.10))))
                .save(saver, id("synchronized"), efh);
        // графитовый котёл T0
        AdvancementHolder pile = task(sync, PcBlocks.REACTOR_CONTROLLER.get(), "first_criticality", AdvancementType.CHALLENGE,
                false, 100)
                .addCriterion("critical", event(ReactorSubsystem.PILE_STATE, Map.of("critical", MinMaxBounds.Doubles.atLeast(0))))
                .save(saver, id("first_criticality"), efh);
        task(pile, PcBlocks.SCRAM_BUTTON.get(), "pile_scram", AdvancementType.TASK, true, 20)
                .addCriterion("scram", event(ReactorSubsystem.PILE_STATE, Map.of("scram", MinMaxBounds.Doubles.atLeast(1))))
                .save(saver, id("pile_scram"), efh);
        // автоматизация A1–A2
        AdvancementHolder relays = task(root, PcBlocks.RELAY.get(), "relay_logic", AdvancementType.TASK, false, 30)
                .addCriterion("relay_logic", event(AutomationSubsystem.RELAY_LOGIC,
                        Map.of("relays", MinMaxBounds.Doubles.atLeast(AutomationSubsystem.RELAY_LOGIC_MIN))))
                .save(saver, id("relay_logic"), efh);
        task(relays, PcBlocks.MANUAL_STATION.get(), "analog_pid", AdvancementType.GOAL, false, 60)
                .addCriterion("analog_loop_held", event(AutomationSubsystem.ANALOG_LOOP_HELD, Map.of()))
                .save(saver, id("analog_pid"), efh);
        // каталог 11 §11.2.2: производство, эксплуатация, автоматизация, безопасность
        AdvancementHolder oil = task(steam, PcBlocks.WELLHEAD.get(), "first_oil", AdvancementType.GOAL, false, 60)
                .addCriterion("oil", event(dev.denfry.promptcritical.mod.content.SimAchievements.FIRST_OIL, Map.of())).save(saver, id("first_oil"), efh);
        task(oil, PcBlocks.HELIUM_RECOVERY_UNIT.get(), "helium", AdvancementType.TASK, false, 40)
                .addCriterion("helium", event(dev.denfry.promptcritical.mod.content.SimAchievements.HELIUM, Map.of())).save(saver, id("helium"), efh);
        AdvancementHolder eth = task(oil, PcBlocks.PYROLYSIS_FURNACE.get(), "ethylene", AdvancementType.TASK, false, 40)
                .addCriterion("ethylene", event(dev.denfry.promptcritical.mod.content.SimAchievements.ETHYLENE, Map.of())).save(saver, id("ethylene"), efh);
        task(oil, PcItems.SULFUR.get(), "sulfur", AdvancementType.TASK, false, 30)
                .addCriterion("sulfur", InventoryChangeTrigger.TriggerInstance.hasItems(net.minecraft.advancements.critereon.ItemPredicate.Builder
                        .item().of(PcItems.SULFUR.get()).withCount(MinMaxBounds.Ints.atLeast(64)))).save(saver, id("sulfur"), efh);
        task(eth, PcItems.PHENOLIC_RESIN.get(), "bakelite", AdvancementType.TASK, false, 30)
                .addCriterion("resin", InventoryChangeTrigger.TriggerInstance.hasItems(PcItems.PHENOLIC_RESIN.get())).save(saver, id("bakelite"), efh);
        AdvancementHolder cake = task(pile, PcItems.YELLOWCAKE.get(), "yellowcake", AdvancementType.GOAL, false, 60)
                .addCriterion("cake", InventoryChangeTrigger.TriggerInstance.hasItems(PcItems.YELLOWCAKE.get())).save(saver, id("yellowcake"), efh);
        AdvancementHolder uf6 = task(cake, PcBlocks.FLUORINATION_REACTOR.get(), "uf6", AdvancementType.TASK, false, 40)
                .addCriterion("uf6", event(dev.denfry.promptcritical.mod.content.SimAchievements.UF6_FILLED, Map.of())).save(saver, id("uf6"), efh);
        AdvancementHolder enriched = task(uf6, PcBlocks.GAS_CENTRIFUGE.get(), "enriched", AdvancementType.GOAL, false, 80)
                .addCriterion("enriched", event(dev.denfry.promptcritical.mod.content.SimAchievements.ENRICHED, Map.of("u235_pct", MinMaxBounds.Doubles.atLeast(3)))).save(saver, id("enriched"), efh);
        AdvancementHolder fa = task(enriched, PcItems.FUEL_ASSEMBLY.get(), "first_fa", AdvancementType.TASK, false, 50)
                .addCriterion("fa", InventoryChangeTrigger.TriggerInstance.hasItems(PcItems.FUEL_ASSEMBLY.get())).save(saver, id("first_fa"), efh);
        task(fa, PcItems.HLW_GLASS_CANISTER.get(), "closed_cycle", AdvancementType.CHALLENGE, false, 200)
                .addCriterion("mox", event(dev.denfry.promptcritical.mod.content.SimAchievements.MOX_LOADED, Map.of()))
                .addCriterion("hlw", InventoryChangeTrigger.TriggerInstance.hasItems(PcItems.HLW_GLASS_CANISTER.get()))
                .save(saver, id("closed_cycle"), efh);
        task(fa, PcBlocks.PWR_REACTOR.get(), "scram_clean", AdvancementType.TASK, false, 50)
                .addCriterion("scram", event(dev.denfry.promptcritical.mod.content.SimAchievements.SCRAM_CLEAN, Map.of())).save(saver, id("scram_clean"), efh);
        task(oil, PcBlocks.PUMPJACK.get(), "pumpjack", AdvancementType.TASK, false, 40)
                .addCriterion("day", event(dev.denfry.promptcritical.mod.content.SimAchievements.PUMPJACK_DAY, Map.of())).save(saver, id("pumpjack"), efh);
        task(oil, PcBlocks.CRUDE_COLUMN.get(), "first_cut", AdvancementType.TASK, false, 40)
                .addCriterion("cut", event(dev.denfry.promptcritical.mod.content.SimAchievements.FIRST_CUT, Map.of())).save(saver, id("first_cut"), efh);
        task(pile, PcBlocks.GRID_DISPATCH_CENTER.get(), "load_follow", AdvancementType.GOAL, false, 100)
                .addCriterion("follow", event(dev.denfry.promptcritical.mod.content.SimAchievements.LOAD_FOLLOW, Map.of())).save(saver, id("load_follow"), efh);
        task(pile, PcBlocks.POWER_REACTOR.get(), "hundred_days", AdvancementType.CHALLENGE, false, 200)
                .addCriterion("run", event(dev.denfry.promptcritical.mod.content.SimAchievements.HUNDRED_DAYS, Map.of())).save(saver, id("hundred_days"), efh);
        task(oil, PcBlocks.BLAST_FURNACE_CONTROLLER.get(), "industrial_complex", AdvancementType.CHALLENGE, false, 200)
                .addCriterion("complex", event(dev.denfry.promptcritical.mod.content.SimAchievements.INDUSTRIAL_COMPLEX, Map.of()))
                .save(saver, id("industrial_complex"), efh);
        task(sync, PcBlocks.DIESEL_GENERATOR.get(), "black_start", AdvancementType.CHALLENGE, false, 100)
                .addCriterion("restored", event(GridSubsystem.BLACK_START, Map.of())).save(saver, id("black_start"), efh);
        task(pile, PcBlocks.NEUTRON_DETECTOR.get(), "one_over_m", AdvancementType.TASK, false, 50)
                .addCriterion("approach", event(ReactorSubsystem.PILE_STATE, Map.of("one_over_m", MinMaxBounds.Doubles.atLeast(3))))
                .save(saver, id("one_over_m"), efh);
        task(pile, PcBlocks.POWER_REACTOR.get(), "xenon_pit", AdvancementType.GOAL, false, 80)
                .addCriterion("pit", event(dev.denfry.promptcritical.mod.content.SimAchievements.XENON_PIT, Map.of())).save(saver, id("xenon_pit"), efh);
        task(oil, PcBlocks.DRILLERS_CONSOLE.get(), "kill_well", AdvancementType.GOAL, false, 80)
                .addCriterion("killed", event(dev.denfry.promptcritical.mod.content.SimAchievements.WELL_KILLED, Map.of())).save(saver, id("kill_well"), efh);
        AdvancementHolder plc = task(relays, PcBlocks.PLC.get(), "plc_first", AdvancementType.TASK, false, 30)
                .addCriterion("run", event(dev.denfry.promptcritical.mod.content.SimAchievements.PLC_RUN, Map.of())).save(saver, id("plc_first"), efh);
        task(plc, PcBlocks.PLC.get(), "all_languages", AdvancementType.CHALLENGE, false, 100)
                .addCriterion("languages", event(dev.denfry.promptcritical.mod.content.SimAchievements.PLC_LANGUAGES, Map.of("languages_4", MinMaxBounds.Doubles.atLeast(4))))
                .save(saver, id("all_languages"), efh);
        AdvancementHolder rtu = task(plc, PcBlocks.RTU_STATION.get(), "lua_first", AdvancementType.TASK, false, 30)
                .addCriterion("read", event(dev.denfry.promptcritical.mod.content.SimAchievements.LUA_READ, Map.of())).save(saver, id("lua_first"), efh);
        task(rtu, PcBlocks.HISTORIAN_SERVER.get(), "historian", AdvancementType.TASK, false, 50)
                .addCriterion("day", event(dev.denfry.promptcritical.mod.content.SimAchievements.HISTORIAN_DAY, Map.of())).save(saver, id("historian"), efh);
        // SCADA/HMI 03c: экран pcui уровня L1 с 20 тегами и больше (11 §11.2.2)
        task(rtu, PcBlocks.ANNUNCIATOR_PANEL.get(), "hmi", AdvancementType.TASK, false, 40)
                .addCriterion("screen", event(dev.denfry.promptcritical.mod.content.SimAchievements.HMI, Map.of("tags", MinMaxBounds.Doubles.atLeast(20))))
                .save(saver, id("hmi"), efh);
        task(rtu, PcBlocks.RTU_STATION.get(), "telemetry", AdvancementType.GOAL, false, 60)
                .addCriterion("rtus", event(dev.denfry.promptcritical.mod.content.SimAchievements.TELEMETRY, Map.of())).save(saver, id("telemetry"), efh);
        AdvancementHolder lock = task(root, PcItems.LOCKOUT_PADLOCK.get(), "lockout", AdvancementType.TASK, false, 20)
                .addCriterion("lock", event(dev.denfry.promptcritical.mod.content.SimAchievements.LOCKOUT, Map.of())).save(saver, id("lockout"), efh);
        // наряды-допуски, остановочный ремонт, ALARA (13 §13.2, §13.6.2; 04b), проверка и матрица ПАЗ (03d §3d.6)
        AdvancementHolder permit = task(lock, PcItems.WORK_PERMIT.get(), "permit", AdvancementType.TASK, false, 30)
                .addCriterion("hot_work", event(dev.denfry.promptcritical.mod.content.SimAchievements.HOT_WORK_PERMIT, Map.of()))
                .save(saver, id("permit"), efh);
        task(permit, PcBlocks.PERMIT_BOARD.get(), "turnaround", AdvancementType.GOAL, false, 100)
                .addCriterion("turnaround", event(dev.denfry.promptcritical.mod.content.SimAchievements.TURNAROUND,
                        Map.of("repairs", MinMaxBounds.Doubles.atLeast(1)))).save(saver, id("turnaround"), efh);
        task(permit, PcBlocks.PRIMARY_PUMP.get(), "alara", AdvancementType.CHALLENGE, false, 150)
                .addCriterion("alara", event(dev.denfry.promptcritical.mod.content.SimAchievements.ALARA,
                        Map.of("dose_msv", MinMaxBounds.Doubles.atMost(10)))).save(saver, id("alara"), efh);
        AdvancementHolder proof = task(relays, PcBlocks.SIS_LOGIC.get(), "proof_test", AdvancementType.TASK, false, 30)
                .addCriterion("proof_test", event(dev.denfry.promptcritical.mod.content.SimAchievements.PROOF_TEST, Map.of()))
                .save(saver, id("proof_test"), efh);
        task(proof, PcBlocks.SIS_LOGIC.get(), "sis_matrix", AdvancementType.TASK, false, 50)
                .addCriterion("matrix", event(dev.denfry.promptcritical.mod.content.SimAchievements.SIS_MATRIX,
                        Map.of("causes", MinMaxBounds.Doubles.atLeast(10)))).save(saver, id("sis_matrix"), efh);
        AdvancementHolder dose = task(lock, PcItems.DOSIMETER.get(), "dosimeter", AdvancementType.TASK, false, 30)
                .addCriterion("day", event(dev.denfry.promptcritical.mod.content.SimAchievements.DOSIMETER_DAY, Map.of())).save(saver, id("dosimeter"), efh);
        AdvancementHolder sun = task(dose, PcItems.SEALED_SOURCE.get(), "own_sun", AdvancementType.TASK, false, 40)
                .addCriterion("source", InventoryChangeTrigger.TriggerInstance.hasItems(PcItems.SEALED_SOURCE.get())).save(saver, id("own_sun"), efh);
        task(sun, PcItems.RADIOGRAPHY_FILM_EXPOSED.get(), "through_steel", AdvancementType.TASK, false, 40)
                .addCriterion("defect", event(dev.denfry.promptcritical.mod.content.SimAchievements.RADIOGRAPHY_DEFECT, Map.of())).save(saver, id("through_steel"), efh);
        // мир и скрытые (11 §11.2.2, §11.4)
        task(root, PcBlocks.NATURAL_BITUMEN.get(), "pitch_lake", AdvancementType.TASK, true, 20)
                .addCriterion("found", SimEventTrigger.TriggerInstance.of(
                        ResourceLocation.parse(dev.denfry.promptcritical.mod.worldgen.WorldBlocks.PITCH_LAKE), Map.of(), false))
                .save(saver, id("pitch_lake"), efh);
        task(root, PcBlocks.GAS_SEEP_VENT.get(), "gas_crater", AdvancementType.TASK, true, 20)
                .addCriterion("found", SimEventTrigger.TriggerInstance.of(
                        ResourceLocation.parse(dev.denfry.promptcritical.mod.worldgen.WorldBlocks.GAS_CRATER), Map.of(), false))
                .save(saver, id("gas_crater"), efh);
        task(root, PcBlocks.ASSAY_STATION.get(), "oklo", AdvancementType.CHALLENGE, true, 100)
                .addCriterion("assay", SimEventTrigger.TriggerInstance.of(
                        ResourceLocation.parse(dev.denfry.promptcritical.mod.worldgen.WorldFinds.OKLO), Map.of(), false))
                .save(saver, id("oklo"), efh);
        task(root, PcItems.SR90_HEAT_SOURCE.get(), "orphan_found", AdvancementType.TASK, true, 30)
                .addCriterion("orphan", InventoryChangeTrigger.TriggerInstance.hasItems(net.minecraft.advancements.critereon.ItemPredicate.Builder
                        .item().of(PcItems.SR90_HEAT_SOURCE.get()).hasComponents(net.minecraft.core.component.DataComponentPredicate.builder()
                                .expect(dev.denfry.promptcritical.mod.registry.PcDataComponents.ORPHAN_SOURCE.get(), true).build())))
                .save(saver, id("orphan_found"), efh);
        // A5 и тренажёр (03d §3d.9.1, 11 §11.3.5)
        task(relays, PcBlocks.SIMULATION_WORKSTATION.get(), "digital_twin", AdvancementType.GOAL, false, 80)
                .addCriterion("deployed", SimEventTrigger.TriggerInstance.of(
                        ResourceLocation.parse(dev.denfry.promptcritical.mod.scada.TwinWorkstationBlockEntity.DIGITAL_TWIN), Map.of(), false))
                .save(saver, id("digital_twin"), efh);
        task(root, PcItems.OPERATOR_LICENSE.get(), "operator_license", AdvancementType.GOAL, false, 60)
                .addCriterion("licensed", SimEventTrigger.TriggerInstance.of(
                        ResourceLocation.parse(dev.denfry.promptcritical.mod.scada.SimulatorDeskBlockEntity.OPERATOR_LICENSE),
                        Map.of("level", MinMaxBounds.Doubles.atLeast(1)), false))
                .save(saver, id("operator_license"), efh);
        // урок stuck_porv тренажёра (11 §11.2.2, §11.3.4): скрытое испытание
        task(root, PcBlocks.TRAINING_SIMULATOR.get(), "stuck_valve", AdvancementType.CHALLENGE, true, 80)
                .addCriterion("lesson", SimEventTrigger.TriggerInstance.of(
                        ResourceLocation.parse(dev.denfry.promptcritical.mod.scada.TrainingSimulatorBlockEntity.SCENARIO_PASSED),
                        Map.of("stuck_porv", MinMaxBounds.Doubles.atLeast(0)), false))
                .save(saver, id("stuck_valve"), efh);
    }

    /** Событие мира или домена у игрока в радиусе смены. */
    private static net.minecraft.advancements.Criterion<SimEventTrigger.TriggerInstance> event(String event,
            Map<String, MinMaxBounds.Doubles> predicate) {
        return SimEventTrigger.TriggerInstance.of(ResourceLocation.parse(event), predicate, true);
    }

    private static Advancement.Builder task(AdvancementHolder parent, ItemLike icon, String name, AdvancementType type,
            boolean hidden, int xp) {
        return Advancement.Builder.advancement().parent(parent)
                .display(icon, title(name), description(name), null, type, true, true, hidden)
                .rewards(AdvancementRewards.Builder.experience(xp));
    }

    private static Component title(String name) {
        return Component.translatable("advancements.promptcritical." + name + ".title");
    }

    private static Component description(String name) {
        return Component.translatable("advancements.promptcritical." + name + ".description");
    }

    private static ResourceLocation id(String name) {
        return PromptCritical.id(name);
    }
}
