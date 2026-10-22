package dev.denfry.promptcritical.mod.ops;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.binding.SimConfigs;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.sim.ops.OpsSubsystem;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Привязка эксплуатации (этап 11а M3): подсистема {@link OpsSubsystem} (пусконаладка, износ, щиты обслуживания),
 * BE щита, опрессовочного насоса, самописца и водоподготовки, ПКМ инструментами и запчастями по машинам.
 */
public final class OpsBinding {
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, PromptCritical.MOD_ID);
    public static final DeferredHolder<RecipeSerializer<?>, SimpleCraftingRecipeSerializer<CastingPartRecipe>> CASTING_PART =
            RECIPE_SERIALIZERS.register("casting_part", () -> new SimpleCraftingRecipeSerializer<>(CastingPartRecipe::new));

    private OpsBinding() {
    }

    public static void register(IEventBus modBus) {
        OpsComponents.COMPONENTS.register(modBus);
        OpsBlockEntities.BLOCK_ENTITIES.register(modBus);
        RECIPE_SERIALIZERS.register(modBus);
        modBus.addListener(OpsBinding::registerCapabilities);
        NeoForge.EVENT_BUS.addListener(OpsActions::onRightClick);
        SimBindings.registerSubsystem(level -> new OpsSubsystem(SimConfigs.ops(), level.getSeed()));
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, OpsBlockEntities.WATER_TREATMENT.get(), FoundryDevice::items);
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, OpsBlockEntities.WATER_TREATMENT.get(), FoundryDevice::fluids);
    }
}
