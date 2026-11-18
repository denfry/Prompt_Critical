package dev.denfry.promptcritical.mod.environment;

import com.simibubi.create.AllItems;
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
import net.minecraft.data.recipes.SimpleCookingRecipeBuilder;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

/** Рецепты радиационной защиты: дозиметр-радиометр и свинцовые блоки из галенита. */
public final class RadiationRecipes extends RecipeProvider {
    public RadiationRecipes(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries);
    }

    public static DataProvider provider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        DataProvider inner = new RadiationRecipes(output, registries);
        return new DataProvider() {
            @Override
            public CompletableFuture<?> run(CachedOutput cache) {
                return inner.run(cache);
            }

            @Override
            public String getName() {
                return "Recipes: radiation protection";
            }
        };
    }

    private static String id(String name) {
        return PromptCritical.id("radiation/" + name).toString();
    }

    @Override
    protected void buildRecipes(RecipeOutput out) {
        // счётчик Гейгера — стеклянная трубка с газом, высокое напряжение — электронная лампа, корпус — латунь
        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, PcItems.DOSIMETER.get())
                .pattern("BGB").pattern("BEB").pattern("BRB").define('B', AllItems.BRASS_SHEET.get()).define('G', Items.GLASS)
                .define('E', AllItems.ELECTRON_TUBE.get()).define('R', Items.REDSTONE)
                .unlockedBy("has_electron_tube", has(AllItems.ELECTRON_TUBE.get())).save(out, id("dosimeter_radiometer"));
        // свинец выплавляют из галенита (обжиг PbS и восстановление — свёрнуты в доменную плавку)
        SimpleCookingRecipeBuilder.blasting(Ingredient.of(PcBlocks.GALENA_ORE.get(), PcBlocks.DEEPSLATE_GALENA_ORE.get()), RecipeCategory.BUILDING_BLOCKS,
                        PcBlocks.LEAD_BLOCK.get(), 0.3f, 200)
                .unlockedBy("has_galena", has(PcBlocks.GALENA_ORE.get())).save(out, id("lead_block_from_galena"));
    }
}
