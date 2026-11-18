package dev.denfry.promptcritical.mod.powerhouse;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Типы BE котла I1 и деаэратора (07 §7.9.5, 08 §8.12). */
public final class PowerhouseBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, PromptCritical.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WaterTubeBoilerBlockEntity>> WATER_TUBE_BOILER =
            type("water_tube_boiler", WaterTubeBoilerBlockEntity::new, PcBlocks.WATER_TUBE_BOILER);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DraftFanBlockEntity>> FORCED_DRAFT_FAN =
            type("forced_draft_fan", DraftFanBlockEntity::forced, PcBlocks.FORCED_DRAFT_FAN);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DraftFanBlockEntity>> INDUCED_DRAFT_FAN =
            type("induced_draft_fan", DraftFanBlockEntity::induced, PcBlocks.INDUCED_DRAFT_FAN);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DeaeratorBlockEntity>> DEAERATOR =
            type("deaerator", DeaeratorBlockEntity::new, PcBlocks.DEAERATOR);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SteamTurbineBlockEntity>> STEAM_TURBINE =
            type("steam_turbine", SteamTurbineBlockEntity::new, PcBlocks.STEAM_TURBINE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HydroTurbineBlockEntity>> HYDRO_TURBINE =
            type("hydro_turbine", HydroTurbineBlockEntity::new, PcBlocks.HYDRO_TURBINE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GasTurbineBlockEntity>> GAS_TURBINE =
            type("gas_turbine", GasTurbineBlockEntity::new, PcBlocks.GAS_TURBINE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DieselGeneratorBlockEntity>> DIESEL_GENERATOR =
            type("diesel_generator", DieselGeneratorBlockEntity::new, PcBlocks.DIESEL_GENERATOR);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HrsgBlockEntity>> HRSG =
            type("hrsg", HrsgBlockEntity::new, PcBlocks.HRSG);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TurbineGearboxBlockEntity>> TURBINE_GEARBOX =
            type("turbine_gearbox", TurbineGearboxBlockEntity::new, PcBlocks.TURBINE_GEARBOX);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WindTurbineBlockEntity>> WIND_TURBINE =
            type("wind_turbine", WindTurbineBlockEntity::new, PcBlocks.WIND_TURBINE);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TidalUnitBlockEntity>> TIDAL_UNIT =
            type("tidal_unit", TidalUnitBlockEntity::new, PcBlocks.TIDAL_UNIT);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TideGaugeBlockEntity>> TIDE_GAUGE =
            type("tide_gauge", TideGaugeBlockEntity::new, PcBlocks.TIDE_GAUGE);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PumpedStorageBlockEntity>> PUMPED_STORAGE =
            type("pumped_storage_unit", PumpedStorageBlockEntity::new, PcBlocks.PUMPED_STORAGE_UNIT);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ElectrodeBoilerBlockEntity>> ELECTRODE_BOILER =
            type("electrode_boiler", ElectrodeBoilerBlockEntity::new, PcBlocks.ELECTRODE_BOILER);

    private PowerhouseBlockEntities() {
    }

    private static <T extends BlockEntity> DeferredHolder<BlockEntityType<?>, BlockEntityType<T>> type(String name,
            BlockEntityType.BlockEntitySupplier<T> factory, Supplier<? extends Block> block) {
        return BLOCK_ENTITIES.register(name, () -> BlockEntityType.Builder.of(factory, block.get()).build(null));
    }
}
