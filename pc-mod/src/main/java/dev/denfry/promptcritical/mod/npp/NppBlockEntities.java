package dev.denfry.promptcritical.mod.npp;

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

/** Типы BE АЭС: реакторы T1 и T2a, ГЦН, парогенератор, компенсатор давления. */
public final class NppBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, PromptCritical.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PowerReactorBlockEntity>> POWER_REACTOR =
            type("power_reactor", PowerReactorBlockEntity::new, PcBlocks.POWER_REACTOR);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PrimaryPumpBlockEntity>> PRIMARY_PUMP =
            type("primary_pump", PrimaryPumpBlockEntity::new, PcBlocks.PRIMARY_PUMP);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SteamGeneratorBlockEntity>> STEAM_GENERATOR =
            type("steam_generator", SteamGeneratorBlockEntity::new, PcBlocks.STEAM_GENERATOR);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PwrReactorBlockEntity>> PWR_REACTOR =
            type("pwr_reactor", PwrReactorBlockEntity::new, PcBlocks.PWR_REACTOR);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PressurizerBlockEntity>> PRESSURIZER =
            type("pressurizer", PressurizerBlockEntity::new, PcBlocks.PRESSURIZER);

    private NppBlockEntities() {
    }

    public static void register(IEventBus modBus) {
        BLOCK_ENTITIES.register(modBus);
        modBus.addListener(NppBlockEntities::capabilities);
    }

    private static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, POWER_REACTOR.get(), FoundryDevice::items);
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, PWR_REACTOR.get(), FoundryDevice::items);
    }

    private static <T extends BlockEntity> DeferredHolder<BlockEntityType<?>, BlockEntityType<T>> type(String name,
            BlockEntityType.BlockEntitySupplier<T> factory, Supplier<? extends Block> block) {
        return BLOCK_ENTITIES.register(name, () -> BlockEntityType.Builder.of(factory, block.get()).build(null));
    }
}
