package dev.denfry.promptcritical.mod.pa;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Блок-сущности ГГС (13 §13.3.4). */
public final class PaBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, PromptCritical.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<IntercomStationBlockEntity>> INTERCOM_STATION =
            BLOCK_ENTITIES.register("intercom_station",
                    () -> BlockEntityType.Builder.of(IntercomStationBlockEntity::new, PcBlocks.INTERCOM_STATION.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PlantLoudspeakerBlockEntity>> PLANT_LOUDSPEAKER =
            BLOCK_ENTITIES.register("plant_loudspeaker",
                    () -> BlockEntityType.Builder.of(PlantLoudspeakerBlockEntity::new, PcBlocks.PLANT_LOUDSPEAKER.get()).build(null));

    private PaBlockEntities() {
    }

    public static void register(IEventBus modBus) {
        BLOCK_ENTITIES.register(modBus);
        PaSystem.register();
    }
}
