package dev.denfry.promptcritical.mod.gas;

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

/** Типы BE газопереработки и патрубки для труб Create (клетки конструкций пересылает {@code FoundryBinding}). */
public final class GasBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, PromptCritical.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GasCompressorBlockEntity>> GAS_COMPRESSOR =
            type("gas_compressor", GasCompressorBlockEntity::new, PcBlocks.GAS_COMPRESSOR);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CentrifugalCompressorBlockEntity>> CENTRIFUGAL_COMPRESSOR =
            type("centrifugal_compressor", CentrifugalCompressorBlockEntity::new, PcBlocks.CENTRIFUGAL_COMPRESSOR);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GasPlantBlockEntity>> GAS_PLANT =
            type("low_temperature_separator", GasPlantBlockEntity::new, PcBlocks.LOW_TEMPERATURE_SEPARATOR);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<NglFractionatorBlockEntity>> NGL_FRACTIONATOR =
            type("ngl_fractionator", NglFractionatorBlockEntity::new, PcBlocks.NGL_FRACTIONATOR);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HeliumRecoveryBlockEntity>> HELIUM_RECOVERY =
            type("helium_recovery_unit", HeliumRecoveryBlockEntity::new, PcBlocks.HELIUM_RECOVERY_UNIT);

    private GasBlockEntities() {
    }

    public static void register(IEventBus modBus) {
        BLOCK_ENTITIES.register(modBus);
        modBus.addListener(GasBlockEntities::capabilities);
    }

    private static void capabilities(RegisterCapabilitiesEvent event) {
        fluids(event, GAS_PLANT.get());
        fluids(event, NGL_FRACTIONATOR.get());
        fluids(event, HELIUM_RECOVERY.get());
    }

    private static <T extends BlockEntity & FoundryDevice> void fluids(RegisterCapabilitiesEvent event, BlockEntityType<T> type) {
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, type, FoundryDevice::fluids);
    }

    private static <T extends BlockEntity> DeferredHolder<BlockEntityType<?>, BlockEntityType<T>> type(String name,
            BlockEntityType.BlockEntitySupplier<T> factory, Supplier<? extends Block> block) {
        return BLOCK_ENTITIES.register(name, () -> BlockEntityType.Builder.of(factory, block.get()).build(null));
    }
}
