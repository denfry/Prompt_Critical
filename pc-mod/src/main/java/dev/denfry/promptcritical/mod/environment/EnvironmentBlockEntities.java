package dev.denfry.promptcritical.mod.environment;

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

/** Очистка дымовых газов (14 §14.2): типы BE электрофильтра и сероочистки. */
public final class EnvironmentBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, PromptCritical.MOD_ID);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ElectrostaticPrecipitatorBlockEntity>> ELECTROSTATIC_PRECIPITATOR =
            type("electrostatic_precipitator", ElectrostaticPrecipitatorBlockEntity::new, PcBlocks.ELECTROSTATIC_PRECIPITATOR);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FgdAbsorberBlockEntity>> FGD_ABSORBER =
            type("fgd_absorber", FgdAbsorberBlockEntity::new, PcBlocks.FGD_ABSORBER);

    private EnvironmentBlockEntities() {
    }

    public static void register(IEventBus modBus) {
        BLOCK_ENTITIES.register(modBus);
        modBus.addListener((RegisterCapabilitiesEvent e) -> e.registerBlockEntity(Capabilities.ItemHandler.BLOCK, FGD_ABSORBER.get(),
                FoundryDevice::items));
    }

    private static <T extends BlockEntity> DeferredHolder<BlockEntityType<?>, BlockEntityType<T>> type(String name,
            BlockEntityType.BlockEntitySupplier<T> factory, Supplier<? extends Block> block) {
        return BLOCK_ENTITIES.register(name, () -> BlockEntityType.Builder.of(factory, block.get()).build(null));
    }
}
