package dev.denfry.promptcritical.mod.nonferrous;

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

/** Типы BE цветной металлургии (08 §8.8), их патрубки и склады. */
public final class NonferrousBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, PromptCritical.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BayerDigesterBlockEntity>> BAYER_DIGESTER =
            type("bayer_digester", BayerDigesterBlockEntity::new, PcBlocks.BAYER_DIGESTER);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AluminaPrecipitatorBlockEntity>> ALUMINA_PRECIPITATOR =
            type("alumina_precipitator", AluminaPrecipitatorBlockEntity::new, PcBlocks.ALUMINA_PRECIPITATOR);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AluminaCalcinerBlockEntity>> ALUMINA_CALCINER =
            type("alumina_calciner", AluminaCalcinerBlockEntity::new, PcBlocks.ALUMINA_CALCINER);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ReductionPotBlockEntity>> REDUCTION_POT =
            type("reduction_pot", ReductionPotBlockEntity::new, PcBlocks.REDUCTION_POT);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FlotationCellBlockEntity>> FLOTATION_CELL =
            type("flotation_cell", FlotationCellBlockEntity::new, PcBlocks.FLOTATION_CELL);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MatteSmelterBlockEntity>> MATTE_SMELTER =
            type("matte_smelter", MatteSmelterBlockEntity::new, PcBlocks.MATTE_SMELTER);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ConverterBlockEntity>> PEIRCE_SMITH_CONVERTER =
            type("peirce_smith_converter", ConverterBlockEntity::new, PcBlocks.PEIRCE_SMITH_CONVERTER);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ElectrorefiningCellBlockEntity>> ELECTROREFINING_CELL =
            type("electrorefining_cell", ElectrorefiningCellBlockEntity::new, PcBlocks.ELECTROREFINING_CELL);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PgmRefineryBlockEntity>> PGM_REFINERY =
            type("pgm_refinery", PgmRefineryBlockEntity::new, PcBlocks.PGM_REFINERY);

    private NonferrousBlockEntities() {
    }

    public static void register(IEventBus modBus) {
        BLOCK_ENTITIES.register(modBus);
        NonferrousComponents.COMPONENTS.register(modBus);
        modBus.addListener(NonferrousBlockEntities::capabilities);
    }

    private static void capabilities(RegisterCapabilitiesEvent event) {
        both(event, BAYER_DIGESTER.get());
        both(event, ALUMINA_PRECIPITATOR.get());
        both(event, ALUMINA_CALCINER.get());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, REDUCTION_POT.get(), FoundryDevice::items);
        both(event, FLOTATION_CELL.get());
        both(event, MATTE_SMELTER.get());
        both(event, PEIRCE_SMITH_CONVERTER.get());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ELECTROREFINING_CELL.get(), FoundryDevice::items);
        both(event, PGM_REFINERY.get());
    }

    private static <T extends BlockEntity & FoundryDevice> void both(RegisterCapabilitiesEvent event, BlockEntityType<T> type) {
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, type, FoundryDevice::fluids);
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, type, FoundryDevice::items);
    }

    private static <T extends BlockEntity> DeferredHolder<BlockEntityType<?>, BlockEntityType<T>> type(String name,
            BlockEntityType.BlockEntitySupplier<T> factory, Supplier<? extends Block> block) {
        return BLOCK_ENTITIES.register(name, () -> BlockEntityType.Builder.of(factory, block.get()).build(null));
    }
}
