package dev.denfry.promptcritical.mod.greenhouse;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Теплица на сбросном тепле (17 §17.3.6): тип BE регистра и подписка на рост растений. */
public final class GreenhouseBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, PromptCritical.MOD_ID);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GreenhouseCoilBlockEntity>> GREENHOUSE_COIL =
            BLOCK_ENTITIES.register("greenhouse_coil",
                    () -> BlockEntityType.Builder.of(GreenhouseCoilBlockEntity::new, PcBlocks.GREENHOUSE_COIL.get()).build(null));

    private GreenhouseBlockEntities() {
    }

    public static void register(IEventBus modBus) {
        BLOCK_ENTITIES.register(modBus);
        GreenhouseGrowth.register();
    }
}
