package dev.denfry.promptcritical.mod.boiler;

import com.simibubi.create.AllBlocks;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcItems;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.world.item.Items;

/**
 * Рецепты котельной I0 (07 §7.9): только то, что есть в первые минуты — железо, кирпич, медь, вал и шестерня Create
 * (первый пар нужен раньше, чем пресс Create). Котёл ставится с легкоплавкой пробкой; запасные — медь с оловом
 * касситерита.
 */
public final class BoilerRecipes extends RecipeProvider {
    public BoilerRecipes(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries);
    }

    /** Провайдер под своим именем: {@code RecipeProvider#getName} финальный, а имена провайдеров уникальны. */
    public static DataProvider provider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        DataProvider inner = new BoilerRecipes(output, registries);
        return new DataProvider() {
            @Override
            public CompletableFuture<?> run(CachedOutput cache) {
                return inner.run(cache);
            }

            @Override
            public String getName() {
                return "Recipes: boiler room";
            }
        };
    }

    private static String id(String name) {
        return PromptCritical.id("boiler/" + name).toString();
    }

    @Override
    protected void buildRecipes(RecipeOutput out) {
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.FIRE_TUBE_BOILER.get())
                .pattern("KKK").pattern("IFI").pattern("BBB").define('K', Items.IRON_BLOCK).define('I', Items.IRON_INGOT)
                .define('F', Items.FURNACE).define('B', Items.BRICKS)
                .unlockedBy("has_iron_block", has(Items.IRON_BLOCK)).save(out, id("fire_tube_boiler"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.STEAM_ENGINE_CYLINDER.get())
                .pattern("IPI").pattern("SCS").pattern("III").define('I', Items.IRON_INGOT).define('P', Items.PISTON)
                .define('S', AllBlocks.SHAFT.get()).define('C', AllBlocks.COGWHEEL.get())
                .unlockedBy("has_piston", has(Items.PISTON)).save(out, id("steam_engine_cylinder"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.GRATE_STOKER.get())
                .pattern(" H ").pattern("ICI").pattern("III").define('H', Items.HOPPER).define('I', Items.IRON_INGOT)
                .define('C', AllBlocks.COGWHEEL.get())
                .unlockedBy("has_hopper", has(Items.HOPPER)).save(out, id("grate_stoker"));
        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, PcBlocks.CHIMNEY.get(), 4)
                .pattern("B B").pattern("B B").pattern("B B").define('B', Items.BRICK)
                .unlockedBy("has_brick", has(Items.BRICK)).save(out, id("chimney"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.INSTRUMENT_PRESSURE.get())
                .pattern(" G ").pattern("CNC").pattern(" C ").define('G', Items.GLASS_PANE).define('C', Items.COPPER_INGOT)
                .define('N', Items.IRON_NUGGET)
                .unlockedBy("has_copper", has(Items.COPPER_INGOT)).save(out, id("instrument_pressure"));
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, PcItems.FUSIBLE_PLUG.get(), 2)
                .requires(Items.COPPER_INGOT).requires(PcItems.RAW_CASSITERITE.get())
                .unlockedBy("has_cassiterite", has(PcItems.RAW_CASSITERITE.get())).save(out, id("fusible_plug"));
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, PcItems.GOVERNOR_DASHPOT.get())
                .requires(Items.COPPER_INGOT, 2).requires(Items.IRON_INGOT).requires(Items.HONEY_BOTTLE)
                .unlockedBy("has_engine", has(PcBlocks.STEAM_ENGINE_CYLINDER.get())).save(out, id("governor_dashpot"));
    }
}
