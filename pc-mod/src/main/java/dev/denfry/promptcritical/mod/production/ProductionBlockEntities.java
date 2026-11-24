package dev.denfry.promptcritical.mod.production;

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

/**
 * Типы BE добычи и НПЗ и баки для труб Create: сепаратор, печь и куб колонны — на любой клетке (клетки конструкции
 * пересылает {@code FoundryBinding}), отборы колонны — на нижней клетке секций и на верхе.
 */
public final class ProductionBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, PromptCritical.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WellheadBlockEntity>> WELLHEAD =
            type("wellhead", WellheadBlockEntity::new, PcBlocks.WELLHEAD);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PumpjackBlockEntity>> PUMPJACK =
            type("pumpjack", PumpjackBlockEntity::new, PcBlocks.PUMPJACK, PcBlocks.PUMPJACK_REAL);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SeparatorBlockEntity>> SEPARATOR =
            type("three_phase_separator", SeparatorBlockEntity::new, PcBlocks.THREE_PHASE_SEPARATOR, PcBlocks.THREE_PHASE_SEPARATOR_REAL);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TubeFurnaceBlockEntity>> TUBE_FURNACE =
            type("tube_furnace", TubeFurnaceBlockEntity::new, PcBlocks.TUBE_FURNACE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CrudeColumnBlockEntity>> CRUDE_COLUMN =
            type("crude_column", CrudeColumnBlockEntity::new, PcBlocks.CRUDE_COLUMN);

    private ProductionBlockEntities() {
    }

    public static void register(IEventBus modBus) {
        BLOCK_ENTITIES.register(modBus);
        modBus.addListener(ProductionBlockEntities::capabilities);
    }

    private static void capabilities(RegisterCapabilitiesEvent event) {
        fluids(event, SEPARATOR.get());
        fluids(event, TUBE_FURNACE.get());
        fluids(event, CRUDE_COLUMN.get());
        event.registerBlock(Capabilities.FluidHandler.BLOCK, (level, pos, state, be, side) -> {
            CrudeColumnBlockEntity c = CrudeColumnBlockEntity.below(level, pos);
            return c == null ? null : c.drawAt(pos);
        }, PcBlocks.COLUMN_TRAY_SECTION.get(), PcBlocks.COLUMN_TOP.get());
    }

    private static <T extends BlockEntity & FoundryDevice> void fluids(RegisterCapabilitiesEvent event, BlockEntityType<T> type) {
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, type, FoundryDevice::fluids);
    }

    @SafeVarargs
    private static <T extends BlockEntity> DeferredHolder<BlockEntityType<?>, BlockEntityType<T>> type(String name,
            BlockEntityType.BlockEntitySupplier<T> factory, Supplier<? extends Block>... blocks) {
        return BLOCK_ENTITIES.register(name, () -> BlockEntityType.Builder.of(factory,
                java.util.Arrays.stream(blocks).map(Supplier::get).toArray(Block[]::new)).build(null));
    }
}
