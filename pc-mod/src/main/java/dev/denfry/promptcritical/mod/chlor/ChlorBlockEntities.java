package dev.denfry.promptcritical.mod.chlor;

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

/** Типы BE хлорного производства и ПВХ, их патрубки и склады. */
public final class ChlorBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, PromptCritical.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BrineTreaterBlockEntity>> BRINE_TREATER =
            type("brine_treater", BrineTreaterBlockEntity::new, PcBlocks.BRINE_TREATER);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CellRoomBlockEntity>> CELL_ROOM =
            type("chlor_alkali_cell", CellRoomBlockEntity::new, PcBlocks.CHLOR_ALKALI_CELL, PcBlocks.CHLOR_ALKALI_ELECTROLYSER_REAL);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RectifierBlockEntity>> RECTIFIER =
            type("rectifier_unit", RectifierBlockEntity::new, PcBlocks.RECTIFIER_UNIT);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LiquefierBlockEntity>> LIQUEFIER =
            type("chlorine_liquefier", LiquefierBlockEntity::new, PcBlocks.CHLORINE_LIQUEFIER);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EdcUnitBlockEntity>> EDC_UNIT =
            type("edc_unit", EdcUnitBlockEntity::new, PcBlocks.EDC_UNIT);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EdcCrackerBlockEntity>> EDC_CRACKER =
            type("edc_cracker", EdcCrackerBlockEntity::new, PcBlocks.EDC_CRACKER);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HclFurnaceBlockEntity>> HCL_FURNACE =
            type("hcl_synthesis_furnace", HclFurnaceBlockEntity::new, PcBlocks.HCL_SYNTHESIS_FURNACE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PvcReactorBlockEntity>> PVC_REACTOR =
            type("pvc_reactor", PvcReactorBlockEntity::new, PcBlocks.PVC_REACTOR, PcBlocks.PVC_REACTOR_REAL);

    private ChlorBlockEntities() {
    }

    public static void register(IEventBus modBus) {
        BLOCK_ENTITIES.register(modBus);
        modBus.addListener(ChlorBlockEntities::capabilities);
    }

    private static void capabilities(RegisterCapabilitiesEvent event) {
        fluids(event, BRINE_TREATER.get());
        fluids(event, CELL_ROOM.get());
        fluids(event, LIQUEFIER.get());
        fluids(event, EDC_UNIT.get());
        fluids(event, EDC_CRACKER.get());
        fluids(event, PVC_REACTOR.get());
        fluids(event, HCL_FURNACE.get());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, BRINE_TREATER.get(), FoundryDevice::items);
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, PVC_REACTOR.get(), FoundryDevice::items);
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
