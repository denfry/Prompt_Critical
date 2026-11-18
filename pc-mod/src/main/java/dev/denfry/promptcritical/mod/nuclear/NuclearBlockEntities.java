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
                    () -> BlockEntityType.Builder.of(PelletPressBlockEntity::new, PcBlocks.PELLET_PRESS.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SinteringFurnaceBlockEntity>> SINTERING_FURNACE =
            BLOCK_ENTITIES.register("sintering_furnace",
                    () -> BlockEntityType.Builder.of(SinteringFurnaceBlockEntity::new, PcBlocks.SINTERING_FURNACE.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RodStationBlockEntity>> ROD_LOADING_STATION =
            BLOCK_ENTITIES.register("rod_loading_station",
                    () -> BlockEntityType.Builder.of(RodStationBlockEntity::new, PcBlocks.ROD_LOADING_STATION.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AssemblyJigBlockEntity>> ASSEMBLY_JIG =
            BLOCK_ENTITIES.register("assembly_jig",
                    () -> BlockEntityType.Builder.of(AssemblyJigBlockEntity::new, PcBlocks.ASSEMBLY_JIG.get()).build(null));

    private NuclearBlockEntities() {
    }

    public static void register(IEventBus modBus) {
        BLOCK_ENTITIES.register(modBus);
        modBus.addListener(NuclearBlockEntities::capabilities);
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
    }
}
