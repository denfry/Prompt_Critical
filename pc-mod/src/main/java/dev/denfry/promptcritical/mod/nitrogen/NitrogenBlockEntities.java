package dev.denfry.promptcritical.mod.nitrogen;

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

/** Типы BE азотного производства (риформинг, очистка, синтез NH₃, азотная кислота) и их патрубки. */
public final class NitrogenBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, PromptCritical.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SteamReformerBlockEntity>> STEAM_REFORMER =
            type("steam_reformer", SteamReformerBlockEntity::new, PcBlocks.STEAM_REFORMER, PcBlocks.STEAM_REFORMER_REAL);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<Co2AbsorberBlockEntity>> CO2_ABSORBER =
            type("co2_absorber", Co2AbsorberBlockEntity::new, PcBlocks.CO2_ABSORBER);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AmmoniaConverterBlockEntity>> AMMONIA_CONVERTER =
            type("ammonia_converter", AmmoniaConverterBlockEntity::new, PcBlocks.AMMONIA_CONVERTER, PcBlocks.AMMONIA_CONVERTER_REAL);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<NitricAcidBlockEntity>> NITRIC_ACID_UNIT =
            type("nitric_acid_unit", NitricAcidBlockEntity::new, PcBlocks.NITRIC_ACID_UNIT);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AcidConcentratorBlockEntity>> ACID_CONCENTRATOR =
            type("acid_concentrator", AcidConcentratorBlockEntity::new, PcBlocks.ACID_CONCENTRATOR);

    private NitrogenBlockEntities() {
    }

    public static void register(IEventBus modBus) {
        BLOCK_ENTITIES.register(modBus);
        modBus.addListener(NitrogenBlockEntities::capabilities);
    }

    private static void capabilities(RegisterCapabilitiesEvent event) {
        fluids(event, STEAM_REFORMER.get());
        fluids(event, CO2_ABSORBER.get());
        fluids(event, AMMONIA_CONVERTER.get());
        fluids(event, NITRIC_ACID_UNIT.get());
        fluids(event, ACID_CONCENTRATOR.get());
    }

    private static <T extends BlockEntity & FoundryDevice> void fluids(RegisterCapabilitiesEvent event, BlockEntityType<T> type) {
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, type, FoundryDevice::fluids);
    }

    @SafeVarargs
    private static <T extends BlockEntity> DeferredHolder<BlockEntityType<?>, BlockEntityType<T>> type(String name,
            BlockEntityType.BlockEntitySupplier<T> factory, Supplier<? extends Block>... blocks) {
        return BLOCK_ENTITIES.register(name, () -> BlockEntityType.Builder.of(factory,
                java.util.Arrays.stream(blocks).map(Supplier::get).toArray(Block[]::new)).build(null));
    }
}
