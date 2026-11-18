package dev.denfry.promptcritical.mod.mes;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Тип BE сервера завода; периферию {@code pc_mes} регистрирует общий мост {@code mod.scada.cc.CcIntegration}. */
public final class MesBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, PromptCritical.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MesServerBlockEntity>> MES_SERVER =
            BLOCK_ENTITIES.register("mes_server",
                    () -> BlockEntityType.Builder.of(MesServerBlockEntity::new, PcBlocks.MES_SERVER.get()).build(null));

    private MesBlockEntities() {
    }

    public static void register(IEventBus modBus) {
        BLOCK_ENTITIES.register(modBus);
    }
}
