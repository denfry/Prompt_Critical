package dev.denfry.promptcritical.mod.methanol;

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

/** Типы BE метанольного производства (агрегат синтеза, ректификация) и их патрубки. */
public final class MethanolBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, PromptCritical.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MethanolConverterBlockEntity>> METHANOL_CONVERTER =
            type("methanol_converter", MethanolConverterBlockEntity::new, PcBlocks.METHANOL_CONVERTER);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MethanolColumnBlockEntity>> METHANOL_COLUMN =
            type("methanol_column", MethanolColumnBlockEntity::new, PcBlocks.METHANOL_COLUMN);

    private MethanolBlockEntities() {
    }

    public static void register(IEventBus modBus) {
        BLOCK_ENTITIES.register(modBus);
        modBus.addListener(MethanolBlockEntities::capabilities);
    }

    private static void capabilities(RegisterCapabilitiesEvent event) {
        fluids(event, METHANOL_CONVERTER.get());
        fluids(event, METHANOL_COLUMN.get());
    }

    private static <T extends BlockEntity & FoundryDevice> void fluids(RegisterCapabilitiesEvent event, BlockEntityType<T> type) {
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, type, FoundryDevice::fluids);
    }

    private static <T extends BlockEntity> DeferredHolder<BlockEntityType<?>, BlockEntityType<T>> type(String name,
            BlockEntityType.BlockEntitySupplier<T> factory, Supplier<? extends Block> block) {
        return BLOCK_ENTITIES.register(name, () -> BlockEntityType.Builder.of(factory, block.get()).build(null));
    }
}
