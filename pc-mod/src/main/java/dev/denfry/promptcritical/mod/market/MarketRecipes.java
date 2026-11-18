package dev.denfry.promptcritical.mod.market;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.world.item.Items;

/** Рецепт биржевого терминала (14 §14.14): экран, две электронные лампы Create, корпус и обшивка. */
public final class MarketRecipes extends RecipeProvider {
    public MarketRecipes(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries);
    }

    public static DataProvider provider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        DataProvider inner = new MarketRecipes(output, registries);
        return new DataProvider() {
            @Override
            public CompletableFuture<?> run(CachedOutput cache) {
                return inner.run(cache);
            }

            @Override
            public String getName() {
                return "Recipes: exchange";
            }
        };
    }

    @Override
    protected void buildRecipes(RecipeOutput out) {
        var tube = AllItems.ELECTRON_TUBE.get();
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.MARKET_TERMINAL.get())
                .pattern(" G ").pattern("TCT").pattern("SSS")
                .define('G', Items.GLASS_PANE).define('T', tube).define('C', AllBlocks.ANDESITE_CASING.get())
                .define('S', AllItems.IRON_SHEET.get())
                .unlockedBy("has_electron_tube", has(tube))
                .save(out, PromptCritical.id("market/market_terminal").toString());
    }
}
