package dev.denfry.promptcritical.mod.scada;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Тип BE шлюза телемеханики. Мост в CC: Tweaked ({@code mod.scada.cc.CcIntegration}) подключается, только если CC
 * загружен: класс с API CC до этого не загружается, и мод работает без CC.
 */
public final class ScadaBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, PromptCritical.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TelemetryGatewayBlockEntity>> TELEMETRY_GATEWAY =
            BLOCK_ENTITIES.register("rtu_station",
                    () -> BlockEntityType.Builder.of(TelemetryGatewayBlockEntity::new, PcBlocks.RTU_STATION.get()).build(null));

    private ScadaBlockEntities() {
    }

    public static void register(IEventBus modBus) {
        BLOCK_ENTITIES.register(modBus);
        if (ModList.get().isLoaded("computercraft")) dev.denfry.promptcritical.mod.scada.cc.CcIntegration.register(modBus);
    }
}
