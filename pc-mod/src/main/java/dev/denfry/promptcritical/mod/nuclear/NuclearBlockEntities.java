package dev.denfry.promptcritical.mod.nuclear;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Типы BE ядерного топливного цикла: пульт каскада газовых центрифуг и его слоты баллонов для воронок. */
public final class NuclearBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, PromptCritical.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CascadeBlockEntity>> CENTRIFUGE_CASCADE =
            BLOCK_ENTITIES.register("centrifuge_cascade",
                    () -> BlockEntityType.Builder.of(CascadeBlockEntity::new, PcBlocks.CENTRIFUGE_CASCADE.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<Uo2ConversionBlockEntity>> UO2_CONVERSION =
            BLOCK_ENTITIES.register("uo2_conversion",
                    () -> BlockEntityType.Builder.of(Uo2ConversionBlockEntity::new, PcBlocks.UO2_CONVERSION.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PelletPressBlockEntity>> PELLET_PRESS =
            BLOCK_ENTITIES.register("pellet_press",
                    () -> BlockEntityType.Builder.of(PelletPressBlockEntity::new, PcBlocks.PELLET_PRESS.get(), PcBlocks.PELLET_PRESS_REAL.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SinteringFurnaceBlockEntity>> SINTERING_FURNACE =
            BLOCK_ENTITIES.register("sintering_furnace",
                    () -> BlockEntityType.Builder.of(SinteringFurnaceBlockEntity::new, PcBlocks.SINTERING_FURNACE.get(),
                            PcBlocks.SINTERING_FURNACE_REAL.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RodStationBlockEntity>> ROD_LOADING_STATION =
            BLOCK_ENTITIES.register("rod_loading_station",
                    () -> BlockEntityType.Builder.of(RodStationBlockEntity::new, PcBlocks.ROD_LOADING_STATION.get(),
                            PcBlocks.FUEL_ROD_WELDER_REAL.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AssemblyJigBlockEntity>> ASSEMBLY_JIG =
            BLOCK_ENTITIES.register("assembly_jig",
                    () -> BlockEntityType.Builder.of(AssemblyJigBlockEntity::new, PcBlocks.ASSEMBLY_JIG.get(), PcBlocks.FUEL_ASSEMBLY_STAND_REAL.get()).build(null));

    /** Газовая центрифуга и секция каскада реального масштаба: их считает пульт каскада. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CentrifugeRealBlockEntity>> CENTRIFUGE_REAL =
            BLOCK_ENTITIES.register("gas_centrifuge_real", () -> BlockEntityType.Builder.of(CentrifugeRealBlockEntity::new,
                    PcBlocks.GAS_CENTRIFUGE_REAL.get(), PcBlocks.CENTRIFUGE_CASCADE_SECTION_REAL.get()).build(null));
    /** Положенный баллон 30B и поставленная ТВС: блок хранит свой предмет. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PlacedItemBlockEntity>> PLACED_ITEM =
            BLOCK_ENTITIES.register("placed_fuel_item", () -> BlockEntityType.Builder.of(PlacedItemBlockEntity::new,
                    PcBlocks.UF6_CYLINDER_30B_REAL.get(), PcBlocks.VVER440_FUEL_ASSEMBLY_REAL.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<Uf6AutoclaveBlockEntity>> UF6_AUTOCLAVE =
            BLOCK_ENTITIES.register("uf6_autoclave",
                    () -> BlockEntityType.Builder.of(Uf6AutoclaveBlockEntity::new, PcBlocks.UF6_AUTOCLAVE_REAL.get()).build(null));

    private NuclearBlockEntities() {
    }

    public static void register(IEventBus modBus) {
        BLOCK_ENTITIES.register(modBus);
        modBus.addListener(NuclearBlockEntities::capabilities);
        // баллон 30B и ТВС кладут в мир Shift + ПКМ по верхней грани
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(PlacedItemBlockEntity::onRightClick);
    }

    private static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, CENTRIFUGE_CASCADE.get(), FoundryDevice::items);
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, UO2_CONVERSION.get(), FoundryDevice::items);
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, UO2_CONVERSION.get(), FoundryDevice::fluids);
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, PELLET_PRESS.get(), FoundryDevice::items);
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, SINTERING_FURNACE.get(), FoundryDevice::items);
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, SINTERING_FURNACE.get(), FoundryDevice::fluids);
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ROD_LOADING_STATION.get(), FoundryDevice::items);
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, ROD_LOADING_STATION.get(), FoundryDevice::fluids);
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ASSEMBLY_JIG.get(), FoundryDevice::items);
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, UF6_AUTOCLAVE.get(), FoundryDevice::items);
    }
}
