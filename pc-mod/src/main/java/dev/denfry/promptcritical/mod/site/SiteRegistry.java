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
    public static final DeferredItem<DesignDrawingItem> VVER70_FUEL_POOL_REAL_DRAWING = drawing("vver70_fuel_pool_real");
    public static final DeferredItem<DesignDrawingItem> VVER70_REFUELLING_MACHINE_REAL_DRAWING = drawing("vver70_refuelling_machine_real");
    public static final DeferredItem<DesignDrawingItem> NPD_REACTOR_REAL_DRAWING = drawing("candu_npd_reactor_real");
    public static final DeferredItem<DesignDrawingItem> NPD_FUELLING_MACHINE_REAL_DRAWING = drawing("candu_npd_fuelling_machine_real");
    public static final DeferredItem<DesignDrawingItem> NPD_BOILER_REAL_DRAWING = drawing("candu_npd_boiler_real");
    public static final DeferredItem<DesignDrawingItem> NPD_PUMP_REAL_DRAWING = drawing("candu_npd_pump_real");
    public static final DeferredItem<DesignDrawingItem> BOR60_REACTOR_REAL_DRAWING = drawing("bor60_reactor_real");
    public static final DeferredItem<DesignDrawingItem> BOR60_IHX_REAL_DRAWING = drawing("bor60_ihx_real");
    public static final DeferredItem<DesignDrawingItem> BOR60_PRIMARY_PUMP_REAL_DRAWING = drawing("bor60_primary_pump_real");
    public static final DeferredItem<DesignDrawingItem> BOR60_STEAM_GENERATOR_REAL_DRAWING = drawing("bor60_steam_generator_real");
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
    // водородная станция
    public static final DeferredItem<DesignDrawingItem> H2_ELECTROLYSER_REAL_DRAWING = drawing("h2_electrolyser_real");
    public static final DeferredItem<DesignDrawingItem> H2_COMPRESSOR_REAL_DRAWING = drawing("h2_compressor_real");
    public static final DeferredItem<DesignDrawingItem> H2_TUBE_BANK_REAL_DRAWING = drawing("h2_tube_bank_real");
    public static final DeferredItem<DesignDrawingItem> H2_GASHOLDER_REAL_DRAWING = drawing("h2_gasholder_real");
    public static final DeferredItem<DesignDrawingItem> H2_FUEL_CELL_REAL_DRAWING = drawing("h2_fuel_cell_real");
    // кузница и машиностроение реального масштаба (18 §18.6–18.7)
    public static final DeferredItem<DesignDrawingItem> POWER_HAMMER_REAL_DRAWING = drawing("power_hammer_real");
    public static final DeferredItem<DesignDrawingItem> BLOOMERY_REAL_DRAWING = drawing("bloomery_real");
    public static final DeferredItem<DesignDrawingItem> FORGE_HEARTH_REAL_DRAWING = drawing("forge_hearth_real");
    public static final DeferredItem<DesignDrawingItem> ASSEMBLY_STAND_DRAWING = drawing("assembly_stand");
    // I3 реального масштаба: ПГУ, азот, метанол, пиролиз, хлор и ПВХ
    public static final DeferredItem<DesignDrawingItem> GAS_TURBINE_HEAVY_REAL_DRAWING = drawing("gas_turbine_heavy_real");
    public static final DeferredItem<DesignDrawingItem> HRSG_REAL_DRAWING = drawing("hrsg_real");
    public static final DeferredItem<DesignDrawingItem> STEAM_REFORMER_REAL_DRAWING = drawing("steam_reformer_real");
    public static final DeferredItem<DesignDrawingItem> AMMONIA_CONVERTER_REAL_DRAWING = drawing("ammonia_converter_real");
    public static final DeferredItem<DesignDrawingItem> METHANOL_CONVERTER_REAL_DRAWING = drawing("methanol_converter_real");
    public static final DeferredItem<DesignDrawingItem> PYROLYSIS_FURNACE_REAL_DRAWING = drawing("pyrolysis_furnace_real");
    public static final DeferredItem<DesignDrawingItem> CHLOR_ALKALI_ELECTROLYSER_REAL_DRAWING = drawing("chlor_alkali_electrolyser_real");
    public static final DeferredItem<DesignDrawingItem> PVC_REACTOR_REAL_DRAWING = drawing("pvc_reactor_real");
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
    // радзащита реального масштаба (04b §4b.4, §4b.7, 02 §2.7.4–2.7.5)
    public static final DeferredItem<DesignDrawingItem> PORTAL_MONITOR_REAL_DRAWING = drawing("portal_monitor_real");
    public static final DeferredItem<DesignDrawingItem> HAND_FOOT_MONITOR_REAL_DRAWING = drawing("hand_foot_monitor_real");
    public static final DeferredItem<DesignDrawingItem> HOT_CELL_REAL_DRAWING = drawing("hot_cell_real");
    public static final DeferredItem<DesignDrawingItem> SPENT_FUEL_CASK_REAL_DRAWING = drawing("spent_fuel_cask_real");
    public static final DeferredItem<DesignDrawingItem> CASK_TRANSPORT_CART_REAL_DRAWING = drawing("cask_transport_cart_real");
    public static final DeferredItem<DesignDrawingItem> DRY_STORAGE_MODULE_REAL_DRAWING = drawing("dry_storage_module_real");
    // топливный цикл реального масштаба (fuelcycle-dimensions)
    public static final DeferredItem<DesignDrawingItem> GAS_CENTRIFUGE_REAL_DRAWING = drawing("gas_centrifuge_real");
    public static final DeferredItem<DesignDrawingItem> CENTRIFUGE_CASCADE_SECTION_REAL_DRAWING = drawing("centrifuge_cascade_section_real");
    public static final DeferredItem<DesignDrawingItem> FLUORINATOR_REAL_DRAWING = drawing("fluorinator_real");
    public static final DeferredItem<DesignDrawingItem> UF6_AUTOCLAVE_REAL_DRAWING = drawing("uf6_autoclave_real");
    public static final DeferredItem<DesignDrawingItem> PELLET_PRESS_REAL_DRAWING = drawing("pellet_press_real");
    public static final DeferredItem<DesignDrawingItem> SINTERING_FURNACE_REAL_DRAWING = drawing("sintering_furnace_real");
    public static final DeferredItem<DesignDrawingItem> FUEL_ROD_WELDER_REAL_DRAWING = drawing("fuel_rod_welder_real");
    public static final DeferredItem<DesignDrawingItem> FUEL_ASSEMBLY_STAND_REAL_DRAWING = drawing("fuel_assembly_stand_real");
    // буровая и промысел I2 реального масштаба (09 §9.5, §9.6)
    public static final DeferredItem<DesignDrawingItem> DERRICK_SEGMENT_REAL_DRAWING = drawing("derrick_segment_real");
    public static final DeferredItem<DesignDrawingItem> ROTARY_TABLE_REAL_DRAWING = drawing("rotary_table_real");
    public static final DeferredItem<DesignDrawingItem> DRAWWORKS_REAL_DRAWING = drawing("drawworks_real");
    public static final DeferredItem<DesignDrawingItem> MUD_PUMP_REAL_DRAWING = drawing("mud_pump_real");
    public static final DeferredItem<DesignDrawingItem> PUMPJACK_REAL_DRAWING = drawing("pumpjack_real");
    public static final DeferredItem<DesignDrawingItem> THREE_PHASE_SEPARATOR_REAL_DRAWING = drawing("three_phase_separator_real");
    // транспорт нефти I2 (09 §9.8)
    public static final DeferredItem<DesignDrawingItem> MAINLINE_PUMP_REAL_DRAWING = drawing("mainline_pump_real");
    public static final DeferredItem<DesignDrawingItem> LINE_HEATER_REAL_DRAWING = drawing("line_heater_real");
    public static final DeferredItem<DesignDrawingItem> OIL_TANK_REAL_DRAWING = drawing("oil_tank_real");
    public static final DeferredItem<DesignDrawingItem> OIL_TANK_FLOATING_REAL_DRAWING = drawing("oil_tank_floating_real");

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
