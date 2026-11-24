package dev.denfry.promptcritical.mod.site;

import dev.denfry.promptcritical.mod.PromptCritical;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Регистрация стройплощадки и чертежей (спецификация real-scale machines, §3a). */
public final class SiteRegistry {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(PromptCritical.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(PromptCritical.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, PromptCritical.MOD_ID);

    public static final DeferredBlock<ConstructionSiteBlock> SITE = BLOCKS.registerBlock("construction_site", ConstructionSiteBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(1.5f).sound(SoundType.SCAFFOLDING).noOcclusion());
    public static final DeferredItem<BlockItem> SITE_ITEM = ITEMS.registerSimpleBlockItem(SITE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ConstructionSiteBlockEntity>> SITE_BE =
            BLOCK_ENTITIES.register("construction_site", () -> BlockEntityType.Builder.of(ConstructionSiteBlockEntity::new, SITE.get()).build(null));

    public static final DeferredItem<DesignDrawingItem> VVER_REACTOR_DRAWING = drawing("vver_reactor");
    public static final DeferredItem<DesignDrawingItem> VVER_STEAM_GENERATOR_DRAWING = drawing("vver_steam_generator");
    public static final DeferredItem<DesignDrawingItem> VVER_COOLANT_PUMP_DRAWING = drawing("vver_coolant_pump");
    public static final DeferredItem<DesignDrawingItem> VVER_PRESSURIZER_DRAWING = drawing("vver_pressurizer");
    public static final DeferredItem<DesignDrawingItem> VVER70_REACTOR_REAL_DRAWING = drawing("vver70_reactor_real");
    public static final DeferredItem<DesignDrawingItem> VVER70_STEAM_GENERATOR_REAL_DRAWING = drawing("vver70_steam_generator_real");
    public static final DeferredItem<DesignDrawingItem> VVER70_COOLANT_PUMP_REAL_DRAWING = drawing("vver70_coolant_pump_real");
    public static final DeferredItem<DesignDrawingItem> VVER70_PRESSURIZER_REAL_DRAWING = drawing("vver70_pressurizer_real");
    public static final DeferredItem<DesignDrawingItem> BWR240_REACTOR_DRAWING = drawing("bwr240_reactor");
    public static final DeferredItem<DesignDrawingItem> CANDU6_REACTOR_DRAWING = drawing("candu6_reactor");
    public static final DeferredItem<DesignDrawingItem> BN600_REACTOR_DRAWING = drawing("bn600_reactor");
    public static final DeferredItem<DesignDrawingItem> AM1_REACTOR_DRAWING = drawing("am1_reactor");
    public static final DeferredItem<DesignDrawingItem> LANCASHIRE_BOILER_DRAWING = drawing("lancashire_boiler");
    public static final DeferredItem<DesignDrawingItem> HEADFRAME_STEEL_DRAWING = drawing("headframe_steel");
    public static final DeferredItem<DesignDrawingItem> MILL_ENGINE_DRAWING = drawing("mill_engine");
    public static final DeferredItem<DesignDrawingItem> WINDING_ENGINE_DRAWING = drawing("winding_engine");
    public static final DeferredItem<DesignDrawingItem> GUIBAL_FAN_DRAWING = drawing("guibal_fan");
    public static final DeferredItem<DesignDrawingItem> CUPOLA_FURNACE_REAL_DRAWING = drawing("cupola_furnace_real");
    public static final DeferredItem<DesignDrawingItem> MINE_DRAINAGE_PUMP_GEARED_DRAWING = drawing("mine_drainage_pump_geared");
    public static final DeferredItem<DesignDrawingItem> LANCASHIRE_STOKER_DRAWING = drawing("lancashire_stoker");
    public static final DeferredItem<DesignDrawingItem> CRUCIBLE_FURNACE_REAL_DRAWING = drawing("crucible_furnace_real");
    public static final DeferredItem<DesignDrawingItem> HOT_BLAST_STOVE_REAL_DRAWING = drawing("hot_blast_stove_real");
    public static final DeferredItem<DesignDrawingItem> BLAST_BLOWER_ENGINE_DRAWING = drawing("blast_blower_engine");
    public static final DeferredItem<DesignDrawingItem> SINTER_STRAND_REAL_DRAWING = drawing("sinter_strand_real");
    public static final DeferredItem<DesignDrawingItem> DUST_CATCHER_REAL_DRAWING = drawing("dust_catcher_real");
    public static final DeferredItem<DesignDrawingItem> GAS_EXHAUSTER_REAL_DRAWING = drawing("gas_exhauster_real");
    public static final DeferredItem<DesignDrawingItem> BYPRODUCT_PLANT_COOLER_REAL_DRAWING = drawing("byproduct_plant_cooler_real");
    public static final DeferredItem<DesignDrawingItem> BYPRODUCT_PLANT_PRECIPITATOR_REAL_DRAWING = drawing("byproduct_plant_precipitator_real");
    public static final DeferredItem<DesignDrawingItem> BYPRODUCT_PLANT_SATURATOR_REAL_DRAWING = drawing("byproduct_plant_saturator_real");
    public static final DeferredItem<DesignDrawingItem> BYPRODUCT_PLANT_SCRUBBER_REAL_DRAWING = drawing("byproduct_plant_scrubber_real");
    public static final DeferredItem<DesignDrawingItem> CAST_HOUSE_MUD_GUN_REAL_DRAWING = drawing("cast_house_mud_gun_real");
    public static final DeferredItem<DesignDrawingItem> CAST_HOUSE_DRILL_REAL_DRAWING = drawing("cast_house_drill_real");
    public static final DeferredItem<DesignDrawingItem> LADLE_REAL_DRAWING = drawing("ladle_real");
    public static final DeferredItem<DesignDrawingItem> SLAG_GRANULATOR_REAL_DRAWING = drawing("slag_granulator_real");
    public static final DeferredItem<DesignDrawingItem> DEAERATOR_REAL_DRAWING = drawing("deaerator_real");
    public static final DeferredItem<DesignDrawingItem> ION_EXCHANGE_COLUMN_REAL_DRAWING = drawing("ion_exchange_column_real");
    public static final DeferredItem<DesignDrawingItem> SHAFT_GENERATOR_REAL_DRAWING = drawing("shaft_generator_real");
    public static final DeferredItem<DesignDrawingItem> ELECTRIC_MOTOR_MV_REAL_DRAWING = drawing("electric_motor_mv_real");
    // добыча, обогащение и коксохим реального масштаба
    public static final DeferredItem<DesignDrawingItem> LONGWALL_SHEARER_REAL_DRAWING = drawing("longwall_shearer_real");
    public static final DeferredItem<DesignDrawingItem> COAL_JIG_REAL_DRAWING = drawing("coal_jig_real");
    public static final DeferredItem<DesignDrawingItem> QUENCH_TOWER_REAL_DRAWING = drawing("quench_tower_real");
    public static final DeferredItem<DesignDrawingItem> POWERED_ROOF_SUPPORT_REAL_DRAWING = drawing("powered_roof_support_real");
    // углеродная линия реального масштаба
    public static final DeferredItem<DesignDrawingItem> ACHESON_FURNACE_REAL_DRAWING = drawing("acheson_furnace_real");
    public static final DeferredItem<DesignDrawingItem> ELECTRODE_EXTRUDER_REAL_DRAWING = drawing("electrode_extruder_real");
    public static final DeferredItem<DesignDrawingItem> BAKING_FURNACE_REAL_DRAWING = drawing("baking_furnace_real");
    public static final DeferredItem<DesignDrawingItem> BOF_VESSEL_REAL_DRAWING = drawing("bof_vessel_real");
    public static final DeferredItem<DesignDrawingItem> REHEATING_FURNACE_REAL_DRAWING = drawing("reheating_furnace_real");
    public static final DeferredItem<DesignDrawingItem> ELECTRIC_ARC_FURNACE_REAL_DRAWING = drawing("electric_arc_furnace_real");
    public static final DeferredItem<DesignDrawingItem> LADLE_STATION_REAL_DRAWING = drawing("ladle_station_real");
    public static final DeferredItem<DesignDrawingItem> INGOT_MOLD_REAL_DRAWING = drawing("ingot_mold_real");
    public static final DeferredItem<DesignDrawingItem> HEAT_TREATMENT_FURNACE_REAL_DRAWING = drawing("heat_treatment_furnace_real");
    public static final DeferredItem<DesignDrawingItem> PIPE_WELDER_REAL_DRAWING = drawing("pipe_welder_real");
    public static final DeferredItem<DesignDrawingItem> PIERCING_MILL_REAL_DRAWING = drawing("piercing_mill_real");
    public static final DeferredItem<DesignDrawingItem> MOLDING_STATION_REAL_DRAWING = drawing("molding_station_real");
    public static final DeferredItem<DesignDrawingItem> SHAKEOUT_GRATE_REAL_DRAWING = drawing("shakeout_grate_real");
    public static final DeferredItem<DesignDrawingItem> MACHINE_LATHE_REAL_DRAWING = drawing("machine_lathe_real");
    public static final DeferredItem<DesignDrawingItem> DRILL_PRESS_REAL_DRAWING = drawing("drill_press_real");
    public static final DeferredItem<DesignDrawingItem> MILLING_MACHINE_REAL_DRAWING = drawing("milling_machine_real");
    public static final DeferredItem<DesignDrawingItem> SHAPING_MACHINE_REAL_DRAWING = drawing("shaping_machine_real");
    public static final DeferredItem<DesignDrawingItem> SURFACE_GRINDER_REAL_DRAWING = drawing("surface_grinder_real");
    public static final DeferredItem<DesignDrawingItem> DEGASIFICATION_PUMP_REAL_DRAWING = drawing("degasification_pump_real");
    public static final DeferredItem<DesignDrawingItem> FORCED_DRAFT_FAN_REAL_DRAWING = drawing("forced_draft_fan_real");
    public static final DeferredItem<DesignDrawingItem> INDUCED_DRAFT_FAN_REAL_DRAWING = drawing("induced_draft_fan_real");
    public static final DeferredItem<DesignDrawingItem> MAGNETIC_SEPARATOR_REAL_DRAWING = drawing("magnetic_separator_real");
    public static final DeferredItem<DesignDrawingItem> FLOTATION_CELL_REAL_DRAWING = drawing("flotation_cell_real");
    public static final DeferredItem<DesignDrawingItem> ELECTRODE_BOILER_REAL_DRAWING = drawing("electrode_boiler_real");
    // кузница и машиностроение реального масштаба (18 §18.6–18.7)
    public static final DeferredItem<DesignDrawingItem> POWER_HAMMER_REAL_DRAWING = drawing("power_hammer_real");
    public static final DeferredItem<DesignDrawingItem> BLOOMERY_REAL_DRAWING = drawing("bloomery_real");
    public static final DeferredItem<DesignDrawingItem> FORGE_HEARTH_REAL_DRAWING = drawing("forge_hearth_real");
    public static final DeferredItem<DesignDrawingItem> ASSEMBLY_STAND_DRAWING = drawing("assembly_stand");
    public static final DeferredItem<DesignDrawingItem> CRANK_PRESS_REAL_DRAWING = drawing("crank_press_real");
    public static final DeferredItem<DesignDrawingItem> WIRE_MILL_REAL_DRAWING = drawing("wire_mill_real");
    public static final DeferredItem<DesignDrawingItem> SHEET_MILL_REAL_DRAWING = drawing("sheet_mill_real");
    public static final DeferredItem<DesignDrawingItem> NOTCHING_PRESS_REAL_DRAWING = drawing("notching_press_real");
    public static final DeferredItem<DesignDrawingItem> WINDING_MACHINE_REAL_DRAWING = drawing("winding_machine_real");
    public static final DeferredItem<DesignDrawingItem> HEATING_FURNACE_REAL_DRAWING = drawing("heating_furnace_real");
    public static final DeferredItem<DesignDrawingItem> HARDENING_FURNACE_REAL_DRAWING = drawing("hardening_furnace_real");
    // цветная металлургия реального масштаба (08 §8.8)
    public static final DeferredItem<DesignDrawingItem> ELECTROREFINING_CELL_REAL_DRAWING = drawing("electrorefining_cell_real");
    public static final DeferredItem<DesignDrawingItem> PEIRCE_SMITH_CONVERTER_REAL_DRAWING = drawing("peirce_smith_converter_real");
    public static final DeferredItem<DesignDrawingItem> REDUCTION_POT_REAL_DRAWING = drawing("reduction_pot_real");
    public static final DeferredItem<DesignDrawingItem> BAYER_DIGESTER_REAL_DRAWING = drawing("bayer_digester_real");
    public static final DeferredItem<DesignDrawingItem> ALUMINA_PRECIPITATOR_REAL_DRAWING = drawing("alumina_precipitator_real");
    public static final DeferredItem<DesignDrawingItem> MATTE_SMELTER_REAL_DRAWING = drawing("matte_smelter_real");

    private SiteRegistry() {}

    private static DeferredItem<DesignDrawingItem> drawing(String plan) {
        return ITEMS.registerItem(plan + "_drawing", p -> new DesignDrawingItem(p, plan), new Item.Properties().stacksTo(1));
    }

    /** Предметы для вкладки творческого режима. */
    public static List<DeferredItem<? extends Item>> items() {
        List<DeferredItem<? extends Item>> out = new java.util.ArrayList<>();
        out.add(SITE_ITEM);
        out.addAll(drawings());
        return out;
    }

    /** Все чертежи машин реального масштаба, в порядке регистрации. */
    @SuppressWarnings("unchecked")
    public static List<DeferredItem<? extends Item>> drawings() {
        List<DeferredItem<? extends Item>> out = new java.util.ArrayList<>();
        for (var e : ITEMS.getEntries())
            if (e.getId().getPath().endsWith("_drawing")) out.add((DeferredItem<? extends Item>) e);
        return out;
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        modBus.addListener(SiteRegistry::capabilities);
    }

    private static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, SITE_BE.get(), (be, side) -> be.items());
    }
}
