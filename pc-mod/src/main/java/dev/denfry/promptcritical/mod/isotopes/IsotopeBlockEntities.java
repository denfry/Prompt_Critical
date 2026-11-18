package dev.denfry.promptcritical.mod.isotopes;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Изотопы (раздел 12): типы BE канала облучения и РИТЭГ, компоненты, рецепт изотопных операций, подсказки. */
public final class IsotopeBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, PromptCritical.MOD_ID);
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, PromptCritical.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<IrradiationChannelBlockEntity>> IRRADIATION_CHANNEL =
            type("irradiation_channel", IrradiationChannelBlockEntity::new, PcBlocks.IRRADIATION_CHANNEL);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RtgBlockEntity>> RTG =
            type("rtg_thermoelectric", RtgBlockEntity::new, PcBlocks.RTG_THERMOELECTRIC);
    public static final DeferredHolder<RecipeSerializer<?>, SimpleCraftingRecipeSerializer<IsotopeCraftRecipe>> CRAFT =
            RECIPE_SERIALIZERS.register("isotope_craft", () -> new SimpleCraftingRecipeSerializer<>(IsotopeCraftRecipe::new));

    private IsotopeBlockEntities() {
    }

    public static void register(IEventBus modBus) {
        IsotopeComponents.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        RECIPE_SERIALIZERS.register(modBus);
        modBus.addListener(IsotopeBlockEntities::capabilities);
        IsotopeText.registerTooltips();
    }

    private static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, IRRADIATION_CHANNEL.get(), FoundryDevice::items);
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, RTG.get(), FoundryDevice::items);
    }

    private static <T extends BlockEntity> DeferredHolder<BlockEntityType<?>, BlockEntityType<T>> type(String name,
            BlockEntityType.BlockEntitySupplier<T> factory, Supplier<? extends Block> block) {
        return BLOCK_ENTITIES.register(name, () -> BlockEntityType.Builder.of(factory, block.get()).build(null));
    }
}
