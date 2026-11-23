package dev.denfry.promptcritical.mod.manufacturing;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Типы BE кузницы ручной ступени (18 §18.7). */
public final class SmithyBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, PromptCritical.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BloomeryBlockEntity>> BLOOMERY =
            type("bloomery", BloomeryBlockEntity::new, PcBlocks.BLOOMERY);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ForgeHearthBlockEntity>> FORGE_HEARTH =
            type("forge_hearth", ForgeHearthBlockEntity::new, PcBlocks.FORGE_HEARTH);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SmithingAnvilBlockEntity>> SMITHING_ANVIL =
            type("smithing_anvil", SmithingAnvilBlockEntity::new, PcBlocks.SMITHING_ANVIL);

    private SmithyBlockEntities() {
    }

    /** Склад горнов открыт воронкам и Create — третий уровень (автоматика) подаёт руду, уголь и прокат сам. */
    static void capabilities(RegisterCapabilitiesEvent event) {
        for (var t : java.util.List.of(BLOOMERY.get(), FORGE_HEARTH.get(), SMITHING_ANVIL.get()))
            event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, t, (be, side) -> ((SmithyDevice) be).items(side));
    }

    private static <T extends BlockEntity> DeferredHolder<BlockEntityType<?>, BlockEntityType<T>> type(String name,
            BlockEntityType.BlockEntitySupplier<T> factory, Supplier<? extends Block> block) {
        return BLOCK_ENTITIES.register(name, () -> BlockEntityType.Builder.of(factory, block.get()).build(null));
    }
}
