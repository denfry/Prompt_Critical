package dev.denfry.promptcritical.mod.chem;

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

/** Типы BE химии I3 (пиролиз, газоразделение, ПЭВД) и их патрубки и склады для труб и воронок. */
public final class ChemBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, PromptCritical.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PyrolysisFurnaceBlockEntity>> PYROLYSIS_FURNACE =
            type("pyrolysis_furnace", PyrolysisFurnaceBlockEntity::new, PcBlocks.PYROLYSIS_FURNACE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CrackedGasUnitBlockEntity>> CRACKED_GAS_UNIT =
            type("cracked_gas_unit", CrackedGasUnitBlockEntity::new, PcBlocks.CRACKED_GAS_UNIT);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LdpeReactorBlockEntity>> LDPE_REACTOR =
            type("ldpe_reactor", LdpeReactorBlockEntity::new, PcBlocks.LDPE_REACTOR);

    private ChemBlockEntities() {
    }

    public static void register(IEventBus modBus) {
        BLOCK_ENTITIES.register(modBus);
        modBus.addListener(ChemBlockEntities::capabilities);
    }

    private static void capabilities(RegisterCapabilitiesEvent event) {
        fluids(event, PYROLYSIS_FURNACE.get());
        fluids(event, CRACKED_GAS_UNIT.get());
        fluids(event, LDPE_REACTOR.get());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, LDPE_REACTOR.get(), FoundryDevice::items);
    }

    private static <T extends BlockEntity & FoundryDevice> void fluids(RegisterCapabilitiesEvent event, BlockEntityType<T> type) {
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, type, FoundryDevice::fluids);
    }

    private static <T extends BlockEntity> DeferredHolder<BlockEntityType<?>, BlockEntityType<T>> type(String name,
            BlockEntityType.BlockEntitySupplier<T> factory, Supplier<? extends Block> block) {
        return BLOCK_ENTITIES.register(name, () -> BlockEntityType.Builder.of(factory, block.get()).build(null));
    }
}
