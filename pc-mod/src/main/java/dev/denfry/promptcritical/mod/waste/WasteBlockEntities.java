package dev.denfry.promptcritical.mod.waste;

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

/** Типы BE обращения с РАО, патрубки и склады; заодно компонент паспорта упаковки. */
public final class WasteBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, PromptCritical.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LrwEvaporatorBlockEntity>> LRW_EVAPORATOR =
            type("lrw_evaporator", LrwEvaporatorBlockEntity::new, PcBlocks.LRW_EVAPORATOR);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WasteCompactorBlockEntity>> WASTE_COMPACTOR =
            type("waste_compactor", WasteCompactorBlockEntity::new, PcBlocks.WASTE_COMPACTOR);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RepositoryBlockEntity>> NEAR_SURFACE_REPOSITORY =
            type("near_surface_repository", RepositoryBlockEntity::nearSurface, PcBlocks.NEAR_SURFACE_REPOSITORY);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RepositoryBlockEntity>> DEEP_REPOSITORY =
            type("deep_geological_repository", RepositoryBlockEntity::deep, PcBlocks.DEEP_GEOLOGICAL_REPOSITORY);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ReactorWaterCleanupBlockEntity>> REACTOR_WATER_CLEANUP =
            type("reactor_water_cleanup", ReactorWaterCleanupBlockEntity::new, PcBlocks.REACTOR_WATER_CLEANUP);

    private WasteBlockEntities() {
    }

    public static void register(IEventBus modBus) {
        BLOCK_ENTITIES.register(modBus);
        WastePackage.register(modBus);
        modBus.addListener(WasteBlockEntities::capabilities);
    }

    private static void capabilities(RegisterCapabilitiesEvent event) {
        both(event, LRW_EVAPORATOR.get());
        both(event, REACTOR_WATER_CLEANUP.get());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, WASTE_COMPACTOR.get(), FoundryDevice::items);
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, NEAR_SURFACE_REPOSITORY.get(), FoundryDevice::items);
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, DEEP_REPOSITORY.get(), FoundryDevice::items);
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
