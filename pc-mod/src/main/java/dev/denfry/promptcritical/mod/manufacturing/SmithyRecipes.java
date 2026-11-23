package dev.denfry.promptcritical.mod.manufacturing;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.world.item.Items;

/**
 * Рецепты кузницы ручной ступени (18 §18.7). Пока из ванильного: глиняная шахта горна, горн с кожаными мехами,
 * наковальня и молот. Перевод самих этих рецептов на поковки — вехи MF5–MF7 (сборочный стенд и подмена рецептов).
 */
public final class SmithyRecipes extends RecipeProvider {
    public SmithyRecipes(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries);
    }

    public static net.minecraft.data.DataProvider provider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        net.minecraft.data.DataProvider inner = new SmithyRecipes(output, registries);
        return new net.minecraft.data.DataProvider() {
            @Override
            public CompletableFuture<?> run(net.minecraft.data.CachedOutput cache) {
                return inner.run(cache);
            }

            @Override
            public String getName() {
                return "Recipes: smithy";
            }
        };
    }

    private static String id(String name) {
        return PromptCritical.id("smithy/" + name).toString();
    }

    @Override
    protected void buildRecipes(RecipeOutput out) {
        // сыродутный горн: глиняная обмазка на кирпичной шахте
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.BLOOMERY.get())
                .pattern("CBC").pattern("B B").pattern("BBB").define('C', Items.CLAY_BALL).define('B', Items.BRICKS)
                .unlockedBy("has_raw_iron", has(Items.RAW_IRON)).save(out, id("bloomery"));
        // кузнечный горн: каменный стол с огнём и кожаными мехами
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.FORGE_HEARTH.get())
                .pattern(" L ").pattern("SCS").pattern("SSS").define('L', Items.LEATHER).define('C', Items.CAMPFIRE)
                .define('S', Items.STONE_BRICKS)
                .unlockedBy("has_campfire", has(Items.CAMPFIRE)).save(out, id("forge_hearth"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.SMITHING_ANVIL.get())
                .pattern("BB ").pattern(" S ").pattern("SSS").define('B', Items.IRON_BLOCK).define('S', Items.STONE)
                .unlockedBy("has_iron_block", has(Items.IRON_BLOCK)).save(out, id("smithing_anvil"));
        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, ManufacturingItems.SMITH_HAMMER.get())
                .pattern("II ").pattern("IS ").pattern(" S ").define('I', Items.IRON_INGOT).define('S', Items.STICK)
                .unlockedBy("has_iron", has(Items.IRON_INGOT)).save(out, id("smith_hammer"));
    }
}
