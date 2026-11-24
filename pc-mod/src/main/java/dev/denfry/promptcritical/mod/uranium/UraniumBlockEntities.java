package dev.denfry.promptcritical.mod.uranium;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Типы BE передела урана, патрубки и склады. */
public final class UraniumBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, PromptCritical.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AcidPlantBlockEntity>> ACID_PLANT =
            type("acid_plant", AcidPlantBlockEntity::new, PcBlocks.ACID_PLANT);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HfKilnBlockEntity>> HF_KILN =
            type("hf_kiln", HfKilnBlockEntity::new, PcBlocks.HF_KILN);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LeachTankBlockEntity>> LEACH_TANK =
            type("leach_tank", LeachTankBlockEntity::new, PcBlocks.LEACH_TANK);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<IonExchangeBlockEntity>> ION_EXCHANGE =
            type("uranium_ion_exchange", IonExchangeBlockEntity::new, PcBlocks.URANIUM_ION_EXCHANGE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PrecipitationBlockEntity>> PRECIPITATION =
            type("precipitation_tank", PrecipitationBlockEntity::new, PcBlocks.PRECIPITATION_TANK);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ConversionBlockEntity>> CALCINER =
            type("rotary_calciner", ConversionBlockEntity::calciner, PcBlocks.ROTARY_CALCINER);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ConversionBlockEntity>> REDUCTION =
            type("reduction_furnace", ConversionBlockEntity::reduction, PcBlocks.REDUCTION_FURNACE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ConversionBlockEntity>> HYDROFLUORINATION =
            type("hydrofluorination_reactor", ConversionBlockEntity::hydrofluorination, PcBlocks.HYDROFLUORINATION_REACTOR);
    /** Реактор фторирования старой серии и пламенная башня реального масштаба — один BE. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FluorinationBlockEntity>> FLUORINATION =
            BLOCK_ENTITIES.register("fluorination_reactor", () -> BlockEntityType.Builder.of(FluorinationBlockEntity::new,
                    PcBlocks.FLUORINATION_REACTOR.get(), PcBlocks.FLUORINATOR_REAL.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FluorineCellBlockEntity>> FLUORINE_CELL =
            type("fluorine_cell", FluorineCellBlockEntity::new, PcBlocks.FLUORINE_CELL);

    private UraniumBlockEntities() {
    }

    public static void register(IEventBus modBus) {
        BLOCK_ENTITIES.register(modBus);
        modBus.addListener(UraniumBlockEntities::capabilities);
    }

    private static void capabilities(RegisterCapabilitiesEvent event) {
        both(event, ACID_PLANT.get());
        both(event, HF_KILN.get());
        both(event, LEACH_TANK.get());
        both(event, ION_EXCHANGE.get());
        both(event, PRECIPITATION.get());
        both(event, CALCINER.get());
        both(event, REDUCTION.get());
        both(event, HYDROFLUORINATION.get());
        both(event, FLUORINATION.get());
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, FLUORINE_CELL.get(), FoundryDevice::fluids);
    }

    private static <T extends BlockEntity & FoundryDevice> void both(RegisterCapabilitiesEvent event, BlockEntityType<T> type) {
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, type, FoundryDevice::fluids);
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, type, FoundryDevice::items);
    }

    private static <T extends BlockEntity> DeferredHolder<BlockEntityType<?>, BlockEntityType<T>> type(String name,
            BlockEntityType.BlockEntitySupplier<T> factory, Supplier<? extends Block> block) {
        return BLOCK_ENTITIES.register(name, () -> BlockEntityType.Builder.of(factory, block.get()).build(null));
    }
}
