package dev.denfry.promptcritical.mod.registry;

import dev.denfry.promptcritical.mod.ops.OpsItem;
import dev.denfry.promptcritical.mod.ops.RecordedChartItem;
import dev.denfry.promptcritical.mod.grid.RelayModuleItem;
import dev.denfry.promptcritical.mod.grid.WireCoilItem;
import dev.denfry.promptcritical.sim.grid.RelaySetting;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.block.PcBlock;
import dev.denfry.promptcritical.mod.content.EngineeringHandbookItem;
import dev.denfry.promptcritical.mod.content.GeologicalMapItem;
import dev.denfry.promptcritical.mod.content.OperatingProcedureItem;
import dev.denfry.promptcritical.mod.foundry.CastingPatternItem;
import dev.denfry.promptcritical.mod.item.MineInstrumentItem;
import dev.denfry.promptcritical.mod.item.VentilationRegulatorItem;
import dev.denfry.promptcritical.mod.item.WireRopeItem;
import dev.denfry.promptcritical.mod.steelworks.FerroalloyItem;
import dev.denfry.promptcritical.mod.steelworks.SteelStockItem;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Предметы вехи M2: реестры 07 §7.22, 08 §8.20, 11; предметы блоков. Поведение добавляют этапы 6–9. */
public final class PcItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(PromptCritical.MOD_ID);

    private static final List<DeferredItem<? extends Item>> PLAIN = new ArrayList<>();

    // котельная (07)
    public static final DeferredItem<Item> FUSIBLE_PLUG = item("fusible_plug", 64);
    public static final DeferredItem<Item> GOVERNOR_DASHPOT = item("governor_dashpot", 16);
    public static final DeferredItem<Item> BOILER_SLAG = item("boiler_slag", 64);
    // уголь и руды (08 §8.1, §8.3)
    public static final DeferredItem<Item> RAW_COAL = item("raw_coal", 64);
    public static final DeferredItem<Item> WASHED_COAL = item("washed_coal", 64);
    public static final DeferredItem<Item> COAL_MIDDLINGS = item("coal_middlings", 64);
    public static final DeferredItem<Item> COAL_REFUSE = item("coal_refuse", 64);
    public static final DeferredItem<Item> RAW_MAGNETITE = item("raw_magnetite", 64);
    public static final DeferredItem<Item> RAW_HEMATITE = item("raw_hematite", 64);
    public static final DeferredItem<Item> RAW_PYROLUSITE = item("raw_pyrolusite", 64);
    public static final DeferredItem<Item> RAW_PENTLANDITE = item("raw_pentlandite", 64);
    public static final DeferredItem<Item> RAW_CHALCOPYRITE = item("raw_chalcopyrite", 64);
    public static final DeferredItem<Item> RAW_SPERRYLITE = item("raw_sperrylite", 64);
    public static final DeferredItem<Item> RAW_MOLYBDENITE = item("raw_molybdenite", 64);
    public static final DeferredItem<Item> RAW_SPHALERITE = item("raw_sphalerite", 64);
    public static final DeferredItem<Item> RAW_CHROMITE = item("raw_chromite", 64);
    public static final DeferredItem<Item> RAW_CASSITERITE = item("raw_cassiterite", 64);
    public static final DeferredItem<Item> RAW_BAUXITE = item("raw_bauxite", 64);
    // шахта, литьё, цех (08 §8.2, §8.9, §8.10)
    public static final DeferredItem<Item> STONE_DUST = item("stone_dust", 64);
    public static final DeferredItem<Item> PIG_IRON = item("pig_iron", 64);
    public static final DeferredItem<Item> FOUNDRY_SAND = item("foundry_sand", 64);
    /** Оловянная бронза для тигля: медь, сплавленная с касситеритом под углём (вкладыши подшипников I0). */
    public static final DeferredItem<Item> BRONZE_INGOT = item("bronze_ingot", 64);
    public static final DeferredItem<CastingPatternItem> CASTING_PATTERN = registered(ITEMS.registerItem("casting_pattern",
            CastingPatternItem::new, new Item.Properties().stacksTo(16)));
    public static final DeferredItem<Item> CASTING = item("casting", 16);
    public static final DeferredItem<Item> REFRACTORY_BRICK = item("refractory_brick", 64);
    /** Быстрорежущий резец: прочность — износ по Тейлору (08 §8.10). */
    public static final DeferredItem<Item> CUTTING_TOOL = registered(ITEMS.registerSimpleItem("cutting_tool",
            new Item.Properties().durability(100)));
    /** Дисковая фреза Р6М5 Ø80 для фрезерного станка: прочность — износ по Тейлору. */
    public static final DeferredItem<Item> MILLING_CUTTER = registered(ITEMS.registerSimpleItem("milling_cutter",
            new Item.Properties().durability(100)));
    /** Шлифовальный круг ПП 250×20×76: прочность — засаливание до правки. */
    public static final DeferredItem<Item> GRINDING_WHEEL = registered(ITEMS.registerSimpleItem("grinding_wheel",
            new Item.Properties().durability(100)));
    /** Детали станочного парка (компонент {@code machined_finish}: Ra и квалитет). */
    public static final DeferredItem<Item> SPUR_GEAR = item("spur_gear", 16);
    public static final DeferredItem<Item> KEYED_SHAFT = item("keyed_shaft", 16);
    public static final DeferredItem<Item> MACHINE_SLIDEWAY = item("machine_slideway", 16);
    public static final DeferredItem<Item> PLANED_PLATE = item("planed_plate", 16);
    public static final DeferredItem<Item> GROUND_PLATE = item("ground_plate", 16);
    public static final DeferredItem<Item> FLAME_SAFETY_LAMP = item("flame_safety_lamp", 1);
    /** Полиэтилен высокого давления в гранулах: мешок 25 кг. */
    public static final DeferredItem<Item> POLYETHYLENE = item("polyethylene", 64);
    /** Суспензионный ПВХ: мешок 25 кг. */
    public static final DeferredItem<Item> PVC = item("pvc", 64);
    /** Загрузки катализаторов азотного производства: никелевый риформинга, железохромовый и медноцинковый конверсии CO. */
    public static final DeferredItem<Item> CATALYST_STEAM_REFORMING = item("catalyst_steam_reforming", 16);
    public static final DeferredItem<Item> CATALYST_SHIFT_HT = item("catalyst_shift_ht", 16);
    public static final DeferredItem<Item> CATALYST_SHIFT_LT = item("catalyst_shift_lt", 16);
    /** Медь–оксид цинка–глинозём синтеза метанола (СНМ-1). */
    public static final DeferredItem<Item> CATALYST_METHANOL = item("catalyst_methanol", 16);
    /** CoMo/Al₂O₃ гидроочистки (GDD 09 §9.9.11). */
    public static final DeferredItem<Item> CATALYST_HYDROTREATING = item("catalyst_hydrotreating", 16);
    /** Pt-Re/Al₂O₃ каталитического риформинга (0,3 % Pt, 0,3 % Re, хлорированный глинозём). */
    public static final DeferredItem<Item> CATALYST_REFORMING = item("catalyst_reforming", 16);
    /** Комовая сера установки Клауса, 10 кг — сырьё сернокислотной установки. */
    public static final DeferredItem<Item> SULFUR = item("sulfur", 64);
    /** Пакет платинородиевых сеток (Pt 92,5 %, Rh 7,5 %) и катализатор очистки хвостовых газов. */
    public static final DeferredItem<Item> PT_RH_GAUZE = item("pt_rh_gauze", 4);
    public static final DeferredItem<Item> CATALYST_DENOX = item("catalyst_denox", 16);
    /** Баллоны UF₆ (ANSI N14.1): содержимое — компонент {@code uf6}. */
    /** Топливо: партия — компонент {@code fuel_lot} (порошок UO₂ — тот же {@link #UO2_POWDER}, 1 кг U). */
    public static final DeferredItem<Item> FUEL_PELLET_TRAY = item("fuel_pellet_tray", 16);
    public static final DeferredItem<Item> UO2_GRINDING_SLUDGE = item("uo2_grinding_sludge", 64);
    public static final DeferredItem<Item> FUEL_ROD = item("fuel_rod", 64);
    public static final DeferredItem<Item> FUEL_ASSEMBLY = item("fuel_assembly", 1);
    /** Цирконий: циркон из тяжёлой фракции песка, губка (с отделением гафния), сплав, оболочечные трубы, решётки. */
    public static final DeferredItem<Item> ZIRCON_CONCENTRATE = item("zircon_concentrate", 64);
    public static final DeferredItem<Item> ZIRCONIUM_SPONGE = item("zirconium_sponge", 64);
    public static final DeferredItem<Item> HAFNIUM_INGOT = item("hafnium_ingot", 64);
    public static final DeferredItem<Item> ZIRCALOY_INGOT = item("zircaloy_ingot", 64);
    public static final DeferredItem<Item> CLADDING_TUBE = item("cladding_tube", 64);
    public static final DeferredItem<Item> SPACER_GRID = item("spacer_grid", 64);
    public static final DeferredItem<dev.denfry.promptcritical.mod.nuclear.Uf6CylinderItem> UF6_CYLINDER_48Y = uf6("uf6_cylinder_48y",
            dev.denfry.promptcritical.mod.nuclear.Uf6CylinderItem.Type.Y48);
    public static final DeferredItem<dev.denfry.promptcritical.mod.nuclear.Uf6CylinderItem> UF6_CYLINDER_30B = uf6("uf6_cylinder_30b",
            dev.denfry.promptcritical.mod.nuclear.Uf6CylinderItem.Type.B30);
    public static final DeferredItem<dev.denfry.promptcritical.mod.nuclear.Uf6CylinderItem> UF6_CYLINDER_5A = uf6("uf6_cylinder_5a",
            dev.denfry.promptcritical.mod.nuclear.Uf6CylinderItem.Type.A5);
    /** Головной шахтный светильник: щелочная батарея на поясе, фара на каске (слот головы). */
    public static final DeferredItem<dev.denfry.promptcritical.mod.mine.CapLampItem> CAP_LAMP = registered(ITEMS.registerItem("cap_lamp",
            dev.denfry.promptcritical.mod.mine.CapLampItem::new, new Item.Properties()));
    /** Респиратор с принудительной подачей воздуха: поясной блок на NiCd, маска (слот груди). */
    public static final DeferredItem<dev.denfry.promptcritical.mod.gear.ElectricGearItem> POWERED_RESPIRATOR = registered(ITEMS.registerItem(
            "powered_respirator", p -> new dev.denfry.promptcritical.mod.gear.ElectricGearItem(p,
                    dev.denfry.promptcritical.mod.gear.ElectricGearItem.Kind.RESPIRATOR), new Item.Properties()));
    /** Электрообогреваемый костюм на 24 В (слот ног). */
    public static final DeferredItem<dev.denfry.promptcritical.mod.gear.ElectricGearItem> HEATED_SUIT = registered(ITEMS.registerItem(
            "heated_suit", p -> new dev.denfry.promptcritical.mod.gear.ElectricGearItem(p,
                    dev.denfry.promptcritical.mod.gear.ElectricGearItem.Kind.HEATED_SUIT), new Item.Properties()));
    public static final DeferredItem<MineInstrumentItem> SELF_RESCUER = instrument("self_rescuer", MineInstrumentItem.Kind.SELF_RESCUER, 16);
    public static final DeferredItem<MineInstrumentItem> STONE_DUSTER = instrument("stone_duster", MineInstrumentItem.Kind.STONE_DUSTER, 1);
    public static final DeferredItem<MineInstrumentItem> METHANOMETER = instrument("methanometer", MineInstrumentItem.Kind.METHANOMETER, 1);
    /** Без компонента {@code rope_state} — канат {@link dev.denfry.promptcritical.mod.item.RopeStateData#DEFAULT}. */
    public static final DeferredItem<WireRopeItem> WIRE_ROPE = registered(ITEMS.registerItem("wire_rope", WireRopeItem::new,
            new Item.Properties().stacksTo(16)));
    public static final DeferredItem<Item> CANARY = item("canary", 16);

    // --- электросеть I1 (07 §7.22) ---
    public static final DeferredItem<Item> COPPER_WIRE = item("copper_wire", 64);
    public static final DeferredItem<Item> GENERATOR_COUPLING = item("generator_coupling", 16);
    /** Подогреватель рубашки ДГ (07 §7.11.3): без него на морозе отказ пуска вчетверо вероятнее. */
    public static final DeferredItem<Item> JACKET_HEATER = item("jacket_heater", 16);
    /** Секвенсор нагрузки ДГ (07 §7.11.4): ступени через 5 с после включения на обесточенную шину. */
    public static final DeferredItem<Item> LOAD_SEQUENCER = item("load_sequencer", 16);
    public static final DeferredItem<Item> LV_FUSE = item("lv_fuse", 64);
    public static final DeferredItem<Item> INTERLOCK_MODULE = item("interlock_module", 16);
    public static final DeferredItem<Item> INSULATOR_PIN = item("insulator_pin", 64);
    public static final DeferredItem<WireCoilItem> WIRE_COIL_LV = registered(ITEMS.registerItem("wire_coil_lv",
            p -> new WireCoilItem(p, true), new Item.Properties().stacksTo(16)));
    public static final DeferredItem<WireCoilItem> OVERHEAD_CONDUCTOR = registered(ITEMS.registerItem("overhead_conductor",
            p -> new WireCoilItem(p, false), new Item.Properties().stacksTo(16)));
    public static final DeferredItem<RelayModuleItem> RELAY_OVERCURRENT = relay("relay_overcurrent", RelaySetting.Kind.OVERCURRENT);
    public static final DeferredItem<RelayModuleItem> RELAY_SYNC_CHECK = relay("relay_sync_check", RelaySetting.Kind.SYNC_CHECK);
    public static final DeferredItem<RelayModuleItem> RELAY_REVERSE_POWER = relay("relay_reverse_power", RelaySetting.Kind.REVERSE_POWER);
    public static final DeferredItem<RelayModuleItem> RELAY_FREQUENCY = relay("relay_frequency", RelaySetting.Kind.UNDERFREQUENCY);
    public static final DeferredItem<RelayModuleItem> RELAY_VOLTAGE = relay("relay_voltage", RelaySetting.Kind.UNDERVOLTAGE);
    public static final DeferredItem<RelayModuleItem> RELAY_THERMAL = relay("relay_thermal", RelaySetting.Kind.THERMAL);
    public static final DeferredItem<RelayModuleItem> RELAY_UFLS = relay("relay_ufls", RelaySetting.Kind.UFLS);
    public static final DeferredItem<RelayModuleItem> RELAY_AUTORECLOSE = relay("relay_autoreclose", RelaySetting.Kind.AUTORECLOSE);
    public static final DeferredItem<RelayModuleItem> RELAY_BREAKER_FAILURE = relay("relay_breaker_failure",
            RelaySetting.Kind.BREAKER_FAILURE);
    // --- автоматика A1–A2 (03d, этап 9 M3) ---
    public static final DeferredItem<dev.denfry.promptcritical.mod.automation.WiringToolItem> WIRING_TOOL = registered(ITEMS.registerItem(
            "wiring_tool", dev.denfry.promptcritical.mod.automation.WiringToolItem::new, new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> RELAY_COIL = item("relay_coil", 64);
    // --- коксохим и домна (08 §8.4–8.5, этап 10 M3) ---
    /** Кокс: компонент {@code coke_assay} (зола, сера, M₂₅, влага; горячий — до тушения). */
    public static final DeferredItem<Item> COKE = item("coke", 64);
    public static final DeferredItem<Item> COKE_BREEZE = item("coke_breeze", 64);
    public static final DeferredItem<Item> PITCH_COKE = item("pitch_coke", 64);
    public static final DeferredItem<Item> COAL_TAR_PITCH = item("coal_tar_pitch", 64);
    /** Электрод: компонент {@code electrode_state} (сырой или обожжённый, трещины). */
    public static final DeferredItem<Item> GREEN_ELECTRODE = item("green_electrode", 16);
    /** Графитированный электрод из печи Ачесона: сырьё реакторного графита. */
    public static final DeferredItem<Item> GRAPHITE_ELECTRODE = item("graphite_electrode", 16);
    /**
     * Нефтяной кокс замедленного коксования: топливо (теплота сгорания ~33 МДж/кг против ~29 у угля — 1800 тиков в
     * печи) и наполнитель электродной массы наравне с пековым коксом.
     */
    public static final DeferredItem<Item> PETROLEUM_COKE = registered(ITEMS.registerItem("petroleum_coke", p -> new Item(p) {
        @Override
        public int getBurnTime(net.minecraft.world.item.ItemStack stack, @org.jetbrains.annotations.Nullable net.minecraft.world.item.crafting.RecipeType<?> type) {
            return 1800;
        }
    }, new Item.Properties().stacksTo(64)));

    // --- уран для графитового котла T0 (I1) ---
    public static final DeferredItem<Item> RAW_URANINITE = item("raw_uraninite", 64);
    public static final DeferredItem<Item> CRUSHED_URANIUM_ORE = item("crushed_uranium_ore", 64);
    public static final DeferredItem<Item> GROUND_URANIUM_ORE = item("ground_uranium_ore", 64);
    public static final DeferredItem<Item> YELLOWCAKE = item("yellowcake", 64);
    public static final DeferredItem<Item> UO2_POWDER = item("uo2_powder", 64);
    /** Диоксид плутония после PUREX: партия с компонентом {@code pu_lot}. */
    public static final DeferredItem<Item> PUO2_POWDER = item("puo2_powder", 1);
    /** Стеклоблок высокоактивных отходов: 50 кг боросиликатного стекла с компонентом активности. */
    public static final DeferredItem<Item> HLW_GLASS_CANISTER = item("hlw_glass_canister", 1);
    // --- радиоактивные отходы: тара, вяжущие, упаковки, отработанные смолы и фильтры ---
    /** Бочка 200 л из оцинкованной стали: пустая — тара для цементирования, битумирования и прессования. */
    public static final DeferredItem<Item> WASTE_DRUM = item("waste_drum", 16);
    /** Портландцемент: мешок 50 кг. */
    public static final DeferredItem<Item> PORTLAND_CEMENT = item("portland_cement", 64);
    /** Бочка цементного компаунда: компоненты активности и упаковки. */
    public static final DeferredItem<Item> CEMENTED_WASTE_DRUM = item("cemented_waste_drum", 1);
    /** Бочка битумного компаунда. */
    public static final DeferredItem<Item> BITUMEN_WASTE_DRUM = item("bitumen_waste_drum", 1);
    /** Бочка-контейнер с прессованными таблетками суперпресса. */
    public static final DeferredItem<Item> COMPACTED_WASTE_DRUM = item("compacted_waste_drum", 1);
    /** Медный контейнер для стеклоблоков в пункте глубинного захоронения (KBS-3: медь 5 см на чугунной вставке). */
    public static final DeferredItem<Item> COPPER_CANISTER = item("copper_canister", 4);
    /** Фильтрующий патрон механических фильтров СВО; отработанный несёт активность. */
    public static final DeferredItem<Item> FILTER_CARTRIDGE = item("filter_cartridge", 16);
    /** Отработанный ионит СВО: мешок 100 л с активностью. */
    public static final DeferredItem<Item> SPENT_ION_EXCHANGE_RESIN = item("spent_ion_exchange_resin", 1);
    // --- изотопы и РИТЭГ (12): мишени, капсулы, закрытые источники, дефектоскопия, источники тепла ---
    /** Металлический кобальт (таблетки мишени Co-60). */
    public static final DeferredItem<Item> COBALT_INGOT = item("cobalt_ingot", 64);
    /** Иридий из концентрата платиновых металлов (мишень Ir-192, оболочка источника Pu-238). */
    public static final DeferredItem<Item> IRIDIUM_INGOT = item("iridium_ingot", 64);
    /** Диоксид нептуния-237 (мишень Pu-238). */
    public static final DeferredItem<Item> NPO2_POWDER = item("npo2_powder", 16);
    /** Капсула облучения: пустая или с компонентом {@code irradiation_target}. */
    public static final DeferredItem<Item> IRRADIATION_CAPSULE = item("irradiation_capsule", 16);
    /** Закрытый источник особой формы: активность — компонентом {@code activity}. */
    public static final DeferredItem<Item> SEALED_SOURCE = item("sealed_source", 1);
    public static final DeferredItem<dev.denfry.promptcritical.mod.isotopes.RadiographyCameraItem> RADIOGRAPHY_CAMERA = registered(
            ITEMS.registerItem("radiography_camera", dev.denfry.promptcritical.mod.isotopes.RadiographyCameraItem::new, new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> RADIOGRAPHY_CRANK = item("radiography_crank", 16);
    public static final DeferredItem<Item> RADIOGRAPHY_FILM = item("radiography_film", 64);
    /** Проявленная плёнка: компонент {@code film_result}. */
    public static final DeferredItem<Item> RADIOGRAPHY_FILM_EXPOSED = item("radiography_film_exposed", 1);
    public static final DeferredItem<Item> PU238_HEAT_SOURCE = item("pu238_heat_source", 1);
    public static final DeferredItem<Item> SR90_HEAT_SOURCE = item("sr90_heat_source", 1);
    // --- электроника (11 §11.5, 15 §15.6): лампы A2, кремний, кристаллы, платы, ЭЛТ ---
    public static final DeferredItem<Item> VACUUM_TUBE = item("vacuum_tube", 64);
    public static final DeferredItem<Item> METALLURGICAL_SILICON = item("metallurgical_silicon", 64);
    public static final DeferredItem<Item> POLYSILICON = item("polysilicon", 64);
    /** Слиток Чохральского: компонент {@code silicon_ingot}. */
    public static final DeferredItem<Item> SILICON_INGOT = item("silicon_ingot", 1);
    public static final DeferredItem<Item> SILICON_WAFER = item("silicon_wafer", 64);
    /** Кристаллы с компонентом {@code chip_spec}. */
    public static final DeferredItem<Item> TRANSISTOR = item("transistor", 64);
    public static final DeferredItem<Item> INTEGRATED_CIRCUIT = item("integrated_circuit", 64);
    public static final DeferredItem<Item> MEMORY_CHIP = item("memory_chip", 64);
    public static final DeferredItem<Item> MICROPROCESSOR = item("microprocessor", 64);
    public static final DeferredItem<Item> COPPER_FOIL = item("copper_foil", 64);
    public static final DeferredItem<Item> RESISTOR = item("resistor", 64);
    public static final DeferredItem<Item> CAPACITOR = item("capacitor", 64);
    public static final DeferredItem<Item> PRINTED_CIRCUIT_BOARD = item("printed_circuit_board", 64);
    /** Резольная фенолоформальдегидная смола: связующее гетинакса плат (фенол из каменноугольной смолы + формалин). */
    public static final DeferredItem<Item> PHENOLIC_RESIN = item("phenolic_resin", 64);
    public static final DeferredItem<Item> CATHODE_RAY_TUBE = item("cathode_ray_tube", 16);
    /** Объектив проекционного степпера g-line из плавленого кварца. */
    public static final DeferredItem<Item> STEPPER_LENS = item("stepper_lens", 1);
    /** Трёхокись урана после прокалки ДУА: мешок на 1 кг урана. */
    public static final DeferredItem<Item> UO3_POWDER = item("uo3_powder", 64);
    /** Тетрафторид урана, «зелёная соль»: мешок на 1 кг урана. */
    public static final DeferredItem<Item> UF4_POWDER = item("uf4_powder", 64);
    /** Сильноосновный анионит: мешок 100 л. */
    public static final DeferredItem<Item> ION_EXCHANGE_RESIN_SBA = item("ion_exchange_resin_sba", 16);
    public static final DeferredItem<Item> MAGNESIUM_INGOT = item("magnesium_ingot", 64);
    /** Блочок природного металлического урана: кассета топливного канала котла. */
    public static final DeferredItem<Item> URANIUM_SLUG = item("uranium_slug", 16);
    public static final DeferredItem<Item> AMMONIUM_SULFATE = item("ammonium_sulfate", 64);
    public static final DeferredItem<Item> IRON_ORE_CONCENTRATE = item("iron_ore_concentrate", 64);
    /** Агломерат: компонент {@code sinter_assay} (Fe, SiO₂, CaO). */
    public static final DeferredItem<Item> SINTER = item("sinter", 64);
    public static final DeferredItem<Item> TAPHOLE_CLAY = item("taphole_clay", 64);
    public static final DeferredItem<Item> SILICA_BRICK = item("silica_brick", 64);
    public static final DeferredItem<Item> TUYERE_SPARE = item("tuyere_spare", 16);
    public static final DeferredItem<Item> GRANULATED_SLAG = item("granulated_slag", 64);
    // --- сталь, прокат, трубы (08 §8.6–8.7, этап 10б M3): компонент steel_stock, у труб ещё pipe_spec ---
    public static final DeferredItem<SteelStockItem> STEEL_INGOT = stock("steel_ingot", 16);
    public static final DeferredItem<SteelStockItem> STEEL_BILLET = stock("steel_billet", 16);
    public static final DeferredItem<SteelStockItem> STEEL_PLATE = stock("steel_plate", 16);
    public static final DeferredItem<SteelStockItem> STEEL_ROD = stock("steel_rod", 16);
    public static final DeferredItem<SteelStockItem> STEEL_BEAM = stock("steel_beam", 16);
    /** Труба: 1 предмет = 1 м (08 §8.0.2). */
    public static final DeferredItem<SteelStockItem> SEAMLESS_PIPE = stock("seamless_pipe", 64);
    public static final DeferredItem<SteelStockItem> WELDED_PIPE = stock("welded_pipe", 64);
    public static final DeferredItem<Item> STEEL_SCRAP = item("steel_scrap", 64);
    public static final DeferredItem<Item> BURNT_LIME = item("burnt_lime", 64);
    /** Ферросплав: вид — компонентом {@code ferroalloy}. */
    public static final DeferredItem<FerroalloyItem> FERROALLOY = registered(ITEMS.registerItem("ferroalloy", FerroalloyItem::new,
            new Item.Properties().stacksTo(64)));
    // --- эксплуатация (13 §13.6.5–13.6.7, этап 11а M3): инструменты, запчасти, расходники ---
    /** LOTO 13 §13.1.2: личный замок, бирка, многозамковая накладка, указатель напряжения. */
    public static final DeferredItem<dev.denfry.promptcritical.mod.ops.LockoutItem> LOCKOUT_PADLOCK =
            lockout("lockout_padlock", dev.denfry.promptcritical.mod.ops.LockoutItem.Kind.PADLOCK, 8);
    public static final DeferredItem<dev.denfry.promptcritical.mod.ops.LockoutItem> LOCKOUT_TAG =
            lockout("lockout_tag", dev.denfry.promptcritical.mod.ops.LockoutItem.Kind.TAG, 16);
    public static final DeferredItem<dev.denfry.promptcritical.mod.ops.LockoutItem> LOCKOUT_HASP =
            lockout("lockout_hasp", dev.denfry.promptcritical.mod.ops.LockoutItem.Kind.HASP, 8);
    public static final DeferredItem<dev.denfry.promptcritical.mod.ops.LockoutItem> VOLTAGE_TESTER =
            lockout("voltage_tester", dev.denfry.promptcritical.mod.ops.LockoutItem.Kind.VOLTAGE_TESTER, 1);
    public static final DeferredItem<OpsItem> MECHANIC_STETHOSCOPE = ops("mechanic_stethoscope", OpsItem.Kind.STETHOSCOPE, 1);
    public static final DeferredItem<OpsItem> TORQUE_WRENCH = ops("torque_wrench", OpsItem.Kind.TORQUE_WRENCH, 1);
    public static final DeferredItem<OpsItem> TEST_PRESSURE_GAUGE = ops("test_pressure_gauge", OpsItem.Kind.TEST_GAUGE, 1);
    public static final DeferredItem<OpsItem> CAULKING_HAMMER = ops("caulking_hammer", OpsItem.Kind.CAULKING_HAMMER, 1);
    public static final DeferredItem<OpsItem> OIL_CAN = ops("oil_can", OpsItem.Kind.OIL_CAN, 1);
    public static final DeferredItem<OpsItem> BOILER_SCRAPER = ops("boiler_scraper", OpsItem.Kind.BOILER_SCRAPER, 1);
    public static final DeferredItem<OpsItem> SAFETY_VALVE = ops("safety_valve", OpsItem.Kind.SAFETY_VALVE, 4);
    public static final DeferredItem<OpsItem> BRONZE_BEARING_SHELL = ops("bronze_bearing_shell", OpsItem.Kind.BEARING_SHELL, 16);
    public static final DeferredItem<OpsItem> PUMP_IMPELLER = ops("pump_impeller", OpsItem.Kind.IMPELLER, 4);
    public static final DeferredItem<OpsItem> GLAND_PACKING = ops("gland_packing", OpsItem.Kind.GLAND_PACKING, 16);
    public static final DeferredItem<Item> TALLOW = item("tallow", 64);
    public static final DeferredItem<Item> CHART_PAPER = item("chart_paper", 64);
    public static final DeferredItem<RecordedChartItem> RECORDED_CHART = registered(ITEMS.registerItem("recorded_chart",
            RecordedChartItem::new, new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> SALT = item("salt", 64);
    /** Угольная пыль для пылеугольных горелок котла I1 (помол угля жерновами Create). */
    public static final DeferredItem<Item> COAL_DUST = item("coal_dust", 64);
    // справочник (11)
    public static final DeferredItem<EngineeringHandbookItem> ENGINEERING_HANDBOOK = registered(ITEMS.registerItem("engineering_handbook",
            EngineeringHandbookItem::new, new Item.Properties().stacksTo(1)));
    /** Оперативный журнал обхода и сдачи смены; бланк наряда-допуска (заполняется кликом по аппарату). */
    public static final DeferredItem<dev.denfry.promptcritical.mod.paperwork.DocumentItem> SHIFT_LOG = registered(ITEMS.registerItem(
            "shift_log", dev.denfry.promptcritical.mod.paperwork.DocumentItem::new, new Item.Properties().stacksTo(1)));
    public static final DeferredItem<dev.denfry.promptcritical.mod.paperwork.DocumentItem> WORK_PERMIT = registered(ITEMS.registerItem(
            "work_permit", dev.denfry.promptcritical.mod.paperwork.DocumentItem::new, new Item.Properties().stacksTo(16)));
    public static final DeferredItem<OperatingProcedureItem> OPERATING_PROCEDURE = registered(ITEMS.registerItem("operating_procedure",
            OperatingProcedureItem::new, new Item.Properties().stacksTo(1)));
    // нефть и газ I2: утяжелитель (мешок 50 кг, API 13A), заряд для шурфа, сейсмический разрез
    public static final DeferredItem<Item> BARITE_POWDER = item("barite_powder", 64);
    public static final DeferredItem<Item> SEISMIC_CHARGE = item("seismic_charge", 16);
    public static final DeferredItem<dev.denfry.promptcritical.mod.drilling.SeismicSectionItem> SEISMIC_SECTION = registered(
            ITEMS.registerItem("seismic_section", dev.denfry.promptcritical.mod.drilling.SeismicSectionItem::new,
                    new Item.Properties().stacksTo(1)));
    public static final DeferredItem<GeologicalMapItem> GEOLOGICAL_MAP = registered(ITEMS.registerItem("geological_map",
            GeologicalMapItem::new, new Item.Properties().stacksTo(1)));

    /** Регулятор проветривания — второй предмет блока {@code ventilation_door} (08 §8.2.7). */
    public static final DeferredItem<VentilationRegulatorItem> VENTILATION_REGULATOR = registered(ITEMS.registerItem("ventilation_regulator",
            p -> new VentilationRegulatorItem(PcBlocks.VENTILATION_DOOR.get(), p)));

    static {
        for (DeferredBlock<? extends Block> b : PcBlocks.withItems()) {
            ITEMS.register(b.getId().getPath(), () -> new PcBlockItem(b.get(), new Item.Properties()));
        }
    }

    private PcItems() {
    }

    private static DeferredItem<dev.denfry.promptcritical.mod.nuclear.Uf6CylinderItem> uf6(String name,
            dev.denfry.promptcritical.mod.nuclear.Uf6CylinderItem.Type type) {
        return registered(ITEMS.registerItem(name, p -> new dev.denfry.promptcritical.mod.nuclear.Uf6CylinderItem(p, type),
                new Item.Properties()));
    }

    /** Дозиметр-радиометр ДКС-04: мощность дозы и накопленная доза, щелчки счётчика. */
    public static final DeferredItem<dev.denfry.promptcritical.mod.environment.DosimeterItem> DOSIMETER = registered(ITEMS.registerItem(
            "dosimeter_radiometer", dev.denfry.promptcritical.mod.environment.DosimeterItem::new, new Item.Properties()));
    /** Виброметр-анализатор: спектр, зона ISO 10816, температура подшипника, шум (04a §4a.8.1). */
    public static final DeferredItem<dev.denfry.promptcritical.mod.acoustics.VibrometerItem> VIBROMETER = registered(ITEMS.registerItem(
            "vibrometer", dev.denfry.promptcritical.mod.acoustics.VibrometerItem::new, new Item.Properties()));
    /** Противошумные наушники (13 §13.7). */
    public static final DeferredItem<dev.denfry.promptcritical.mod.acoustics.HearingProtectionItem> HEARING_PROTECTION = registered(
            ITEMS.registerItem("hearing_protection", dev.denfry.promptcritical.mod.acoustics.HearingProtectionItem::new, new Item.Properties()));
    /** Обслуживание вращающихся машин (04a §4a.7.5): подшипник, балансировка, центровка. */
    public static final DeferredItem<dev.denfry.promptcritical.mod.acoustics.MachineServiceItem> BEARING_KIT = service("bearing_kit",
            dev.denfry.promptcritical.mod.acoustics.MachineServiceItem.Kind.BEARING_KIT);
    public static final DeferredItem<dev.denfry.promptcritical.mod.acoustics.MachineServiceItem> BALANCING_WEIGHTS = service(
            "balancing_weights", dev.denfry.promptcritical.mod.acoustics.MachineServiceItem.Kind.BALANCING_WEIGHTS);
    public static final DeferredItem<dev.denfry.promptcritical.mod.acoustics.MachineServiceItem> ALIGNMENT_TOOL = service(
            "alignment_tool", dev.denfry.promptcritical.mod.acoustics.MachineServiceItem.Kind.ALIGNMENT_TOOL);

    private static DeferredItem<dev.denfry.promptcritical.mod.acoustics.MachineServiceItem> service(String name,
            dev.denfry.promptcritical.mod.acoustics.MachineServiceItem.Kind kind) {
        return registered(ITEMS.registerItem(name, p -> new dev.denfry.promptcritical.mod.acoustics.MachineServiceItem(p, kind),
                new Item.Properties()));
    }

    /** Радиометр загрязнённости поверхностей с α/β-зондом. */
    public static final DeferredItem<dev.denfry.promptcritical.mod.radiation.ContaminationMeterItem> CONTAMINATION_METER = registered(
            ITEMS.registerItem("contamination_meter", dev.denfry.promptcritical.mod.radiation.ContaminationMeterItem::new, new Item.Properties()));
    /** Баллон снимаемого дезактивирующего покрытия. */
    public static final DeferredItem<dev.denfry.promptcritical.mod.radiation.StrippableCoatingItem> STRIPPABLE_COATING = registered(
            ITEMS.registerItem("strippable_coating", dev.denfry.promptcritical.mod.radiation.StrippableCoatingItem::new, new Item.Properties()));
    /** Распылитель дезактивирующих растворов со сборником смыва. */
    public static final DeferredItem<dev.denfry.promptcritical.mod.radiation.DeconSprayerItem> DECON_SPRAYER = registered(
            ITEMS.registerItem("decon_sprayer", dev.denfry.promptcritical.mod.radiation.DeconSprayerItem::new, new Item.Properties()));
    /** Содранная плёнка покрытия с загрязнением: твёрдые РАО. */
    public static final DeferredItem<Item> STRIPPED_COATING = item("stripped_coating", 16);

    /** Ведро реакторной тяжёлой воды: 1000 мБ = 1 м³, 1,1 т D₂O; льётся в баки, трубы и в мир, как вода. */
    public static final DeferredItem<net.minecraft.world.item.BucketItem> HEAVY_WATER_BUCKET = registered(ITEMS.registerItem(
            "heavy_water_bucket", p -> new net.minecraft.world.item.BucketItem(PcFluids.HEAVY_WATER.source.get(), p),
            new Item.Properties().craftRemainder(net.minecraft.world.item.Items.BUCKET).stacksTo(1)));

    private static <I extends Item> DeferredItem<I> registered(DeferredItem<I> i) {
        PLAIN.add(i);
        return i;
    }

    private static DeferredItem<Item> item(String name, int stack) {
        DeferredItem<Item> i = ITEMS.registerSimpleItem(name, new Item.Properties().stacksTo(stack));
        PLAIN.add(i);
        return i;
    }

    private static DeferredItem<dev.denfry.promptcritical.mod.ops.LockoutItem> lockout(String name,
            dev.denfry.promptcritical.mod.ops.LockoutItem.Kind kind, int stack) {
        return registered(ITEMS.registerItem(name, p -> new dev.denfry.promptcritical.mod.ops.LockoutItem(p, kind),
                new Item.Properties().stacksTo(stack)));
    }

    private static DeferredItem<OpsItem> ops(String name, OpsItem.Kind kind, int stack) {
        return registered(ITEMS.registerItem(name, p -> new OpsItem(p, kind), new Item.Properties().stacksTo(stack)));
    }

    private static DeferredItem<SteelStockItem> stock(String name, int stack) {
        return registered(ITEMS.registerItem(name, SteelStockItem::new, new Item.Properties().stacksTo(stack)));
    }

    private static DeferredItem<RelayModuleItem> relay(String name, RelaySetting.Kind kind) {
        return registered(ITEMS.registerItem(name, p -> new RelayModuleItem(p, kind), new Item.Properties().stacksTo(1)));
    }

    private static DeferredItem<MineInstrumentItem> instrument(String name, MineInstrumentItem.Kind kind, int stack) {
        DeferredItem<MineInstrumentItem> i = ITEMS.registerItem(name, p -> new MineInstrumentItem(p, kind), new Item.Properties().stacksTo(stack));
        PLAIN.add(i);
        return i;
    }

    public static List<DeferredItem<? extends Item>> plainItems() {
        return Collections.unmodifiableList(PLAIN);
    }

    /** Предмет блока; многоклеточный блок с клетками ниже якоря ставится так, чтобы нижний слой лёг на землю. */
    public static class PcBlockItem extends BlockItem {
        public PcBlockItem(Block block, Properties props) {
            super(block, props);
        }

        @Override
        public BlockPlaceContext updatePlacementContext(BlockPlaceContext ctx) {
            if (getBlock() instanceof PcBlock b) {
                int lift = -b.spec().footprint().minY();
                if (lift > 0) {
                    BlockPos pos = ctx.getClickedPos().above(lift);
                    return BlockPlaceContext.at(ctx, pos, ctx.getClickedFace());
                }
            }
            return ctx;
        }
    }
}
