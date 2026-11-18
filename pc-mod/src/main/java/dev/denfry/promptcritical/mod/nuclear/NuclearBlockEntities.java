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

    private NuclearBlockEntities() {
    }

    public static void register(IEventBus modBus) {
        BLOCK_ENTITIES.register(modBus);
        modBus.addListener(NuclearBlockEntities::capabilities);
    }

    private static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, CENTRIFUGE_CASCADE.get(), FoundryDevice::items);
    }
}
