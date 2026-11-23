package dev.denfry.promptcritical.mod.compat.jei;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.compat.MachineProcesses;
import dev.denfry.promptcritical.mod.compat.MachineProcesses.Plant;
import dev.denfry.promptcritical.mod.compat.MachineProcesses.Process;
import dev.denfry.promptcritical.mod.foundry.FoundryComponents;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.mod.steelworks.SteelworksComponents;
import java.util.List;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.ingredients.subtypes.ISubtypeInterpreter;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.ISubtypeRegistration;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Плагин JEI (EMI читает его сам): категории цехов из {@link MachineProcesses}, подтипы предметов с видом в
 * компоненте (ферросплав, литейная модель), пояснения к предметам, которые не крафтятся. Загружается только при
 * установленном JEI — по аннотации, остальной код мода на него не ссылается.
 */
@JeiPlugin
public final class PcJeiPlugin implements IModPlugin {
    private static final ResourceLocation UID = PromptCritical.id("jei");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerItemSubtypes(ISubtypeRegistration registration) {
        registration.registerSubtypeInterpreter(PcItems.FERROALLOY.get(), new ISubtypeInterpreter<>() {
            @Override
            public @Nullable Object getSubtypeData(ItemStack stack, UidContext context) {
                return stack.get(SteelworksComponents.FERROALLOY.get());
            }

            @Override
            public String getLegacyStringSubtypeInfo(ItemStack stack, UidContext context) {
                String id = stack.get(SteelworksComponents.FERROALLOY.get());
                return id == null ? "" : id;
            }
        });
        registration.registerSubtypeInterpreter(PcItems.CASTING_PATTERN.get(), new ISubtypeInterpreter<>() {
            @Override
            public @Nullable Object getSubtypeData(ItemStack stack, UidContext context) {
                return stack.get(FoundryComponents.CASTING_PATTERN.get());
            }

            @Override
            public String getLegacyStringSubtypeInfo(ItemStack stack, UidContext context) {
                FoundryComponents.Pattern p = stack.get(FoundryComponents.CASTING_PATTERN.get());
                return p == null ? "" : p.kind().name() + (p.riser() ? "+riser" : "");
            }
        });
    }

    /** Старая серия машин (спецификация real-scale machines, M4) не крафтится и скрыта. */
    @Override
    public void onRuntimeAvailable(mezz.jei.api.runtime.IJeiRuntime runtime) {
        var legacy = new java.util.ArrayList<net.minecraft.world.item.ItemStack>();
        for (var b : dev.denfry.promptcritical.mod.registry.PcBlocks.legacy()) legacy.add(new net.minecraft.world.item.ItemStack(b.get()));
        runtime.getIngredientManager().removeIngredientsAtRuntime(mezz.jei.api.constants.VanillaTypes.ITEM_STACK, legacy);
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        for (Plant plant : Plant.values()) {
            registration.addRecipeCategories(new ProcessCategory(plant, registration.getJeiHelpers().getGuiHelper()));
        }
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        for (Plant plant : Plant.values()) {
            List<Process> list = MachineProcesses.all().stream().filter(p -> p.plant() == plant).toList();
            registration.addRecipes(ProcessCategory.type(plant), list);
        }
        // добывается в мире, без рецепта
        info(registration, PcItems.RAW_COAL.get().getDefaultInstance(), "raw_coal");
    }

    private static void info(IRecipeRegistration registration, ItemStack stack, String key) {
        registration.addItemStackInfo(stack, Component.translatable("jei." + PromptCritical.MOD_ID + ".info." + key));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        for (Plant plant : Plant.values()) {
            for (ItemStack machine : MachineProcesses.machines(plant)) {
                registration.addRecipeCatalyst(machine, ProcessCategory.type(plant));
            }
        }
    }
}
