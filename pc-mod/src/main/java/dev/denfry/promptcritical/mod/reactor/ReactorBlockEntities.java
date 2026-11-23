package dev.denfry.promptcritical.mod.reactor;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Блок-сущности графитового котла и печи Ачесона. */
public final class ReactorBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, PromptCritical.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PileControllerBlockEntity>> REACTOR_CONTROLLER =
            type("reactor_controller", PileControllerBlockEntity::new, PcBlocks.REACTOR_CONTROLLER);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FuelChannelBlockEntity>> FUEL_CHANNEL =
            type("fuel_channel", FuelChannelBlockEntity::new, PcBlocks.FUEL_CHANNEL);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AchesonFurnaceBlockEntity>> ACHESON_FURNACE =
            BLOCK_ENTITIES.register("acheson_furnace", () -> BlockEntityType.Builder.of(AchesonFurnaceBlockEntity::new,
                    PcBlocks.ACHESON_FURNACE.get(), PcBlocks.ACHESON_FURNACE_REAL.get()).build(null));

    private ReactorBlockEntities() {
    }

    @SuppressWarnings("ConstantConditions")
    private static <T extends BlockEntity> DeferredHolder<BlockEntityType<?>, BlockEntityType<T>> type(String name,
            BlockEntityType.BlockEntitySupplier<T> factory, Supplier<? extends Block> block) {
        return BLOCK_ENTITIES.register(name, () -> BlockEntityType.Builder.of(factory, block.get()).build(null));
    }
}
