package dev.denfry.promptcritical.mod.radiation;

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

/** Типы BE радиационной части: стеллаж бассейна выдержки. */
public final class RadiationBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, PromptCritical.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FuelRackBlockEntity>> FUEL_RACK =
            BLOCK_ENTITIES.register("fuel_rack", () -> BlockEntityType.Builder.of(FuelRackBlockEntity::new, PcBlocks.FUEL_RACK.get()).build(null));

    private RadiationBlockEntities() {
    }

    public static void register(IEventBus modBus) {
        BLOCK_ENTITIES.register(modBus);
        modBus.addListener((RegisterCapabilitiesEvent e) -> e.registerBlockEntity(Capabilities.ItemHandler.BLOCK, FUEL_RACK.get(),
                FoundryDevice::items));
    }
}
