package dev.denfry.promptcritical.mod.reprocessing;

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

/** Типы BE радиохимии: каньон PUREX и хранилище стекла. */
public final class ReprocessingBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, PromptCritical.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PurexCellBlockEntity>> PUREX_CELL =
            BLOCK_ENTITIES.register("purex_cell", () -> BlockEntityType.Builder.of(PurexCellBlockEntity::new, PcBlocks.PUREX_CELL.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HlwVaultBlockEntity>> HLW_VAULT =
            BLOCK_ENTITIES.register("hlw_vault", () -> BlockEntityType.Builder.of(HlwVaultBlockEntity::new, PcBlocks.HLW_VAULT.get()).build(null));

    private ReprocessingBlockEntities() {
    }

    public static void register(IEventBus modBus) {
        BLOCK_ENTITIES.register(modBus);
        PuLot.register(modBus);
        modBus.addListener((RegisterCapabilitiesEvent e) -> {
            e.registerBlockEntity(Capabilities.FluidHandler.BLOCK, PUREX_CELL.get(), FoundryDevice::fluids);
            e.registerBlockEntity(Capabilities.ItemHandler.BLOCK, PUREX_CELL.get(), FoundryDevice::items);
            e.registerBlockEntity(Capabilities.ItemHandler.BLOCK, HLW_VAULT.get(), FoundryDevice::items);
        });
    }
}
