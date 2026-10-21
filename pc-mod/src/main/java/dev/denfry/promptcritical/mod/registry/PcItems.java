package dev.denfry.promptcritical.mod.registry;

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
    public static final DeferredItem<CastingPatternItem> CASTING_PATTERN = registered(ITEMS.registerItem("casting_pattern",
            CastingPatternItem::new, new Item.Properties().stacksTo(16)));
    public static final DeferredItem<Item> CASTING = item("casting", 16);
    public static final DeferredItem<Item> REFRACTORY_BRICK = item("refractory_brick", 64);
    /** Быстрорежущий резец: прочность — износ по Тейлору (08 §8.10). */
    public static final DeferredItem<Item> CUTTING_TOOL = registered(ITEMS.registerSimpleItem("cutting_tool",
            new Item.Properties().durability(100)));
    public static final DeferredItem<Item> FLAME_SAFETY_LAMP = item("flame_safety_lamp", 1);
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
    public static final DeferredItem<Item> AMMONIUM_SULFATE = item("ammonium_sulfate", 64);
    public static final DeferredItem<Item> IRON_ORE_CONCENTRATE = item("iron_ore_concentrate", 64);
    /** Агломерат: компонент {@code sinter_assay} (Fe, SiO₂, CaO). */
    public static final DeferredItem<Item> SINTER = item("sinter", 64);
    public static final DeferredItem<Item> TAPHOLE_CLAY = item("taphole_clay", 64);
    public static final DeferredItem<Item> SILICA_BRICK = item("silica_brick", 64);
    public static final DeferredItem<Item> TUYERE_SPARE = item("tuyere_spare", 16);
    public static final DeferredItem<Item> GRANULATED_SLAG = item("granulated_slag", 64);
    // справочник (11)
    public static final DeferredItem<EngineeringHandbookItem> ENGINEERING_HANDBOOK = registered(ITEMS.registerItem("engineering_handbook",
            EngineeringHandbookItem::new, new Item.Properties().stacksTo(1)));
    public static final DeferredItem<OperatingProcedureItem> OPERATING_PROCEDURE = registered(ITEMS.registerItem("operating_procedure",
            OperatingProcedureItem::new, new Item.Properties().stacksTo(1)));
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

    private static <I extends Item> DeferredItem<I> registered(DeferredItem<I> i) {
        PLAIN.add(i);
        return i;
    }

    private static DeferredItem<Item> item(String name, int stack) {
        DeferredItem<Item> i = ITEMS.registerSimpleItem(name, new Item.Properties().stacksTo(stack));
        PLAIN.add(i);
        return i;
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
