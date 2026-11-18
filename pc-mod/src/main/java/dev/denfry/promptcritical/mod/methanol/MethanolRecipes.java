package dev.denfry.promptcritical.mod.methanol;

import com.simibubi.create.AllBlocks;
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
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.world.item.Items;

/** Рецепты метанольного производства: агрегат синтеза, колонны ректификации и медный катализатор. */
public final class MethanolRecipes extends RecipeProvider {
    public MethanolRecipes(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries);
    }

    public static DataProvider provider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        DataProvider inner = new MethanolRecipes(output, registries);
        return new DataProvider() {
            @Override
            public CompletableFuture<?> run(CachedOutput cache) {
                return inner.run(cache);
            }

            @Override
            public String getName() {
                return "Recipes: methanol";
            }
        };
    }

    private static String id(String name) {
        return PromptCritical.id("methanol/" + name).toString();
    }

    @Override
    protected void buildRecipes(RecipeOutput out) {
        var sheet = AllItems.IRON_SHEET.get();
        var pipe = PcItems.SEAMLESS_PIPE.get();
        // агрегат: трубчатая печь, компрессор синтез-газа, трубчатый реактор в толстостенном корпусе
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.METHANOL_CONVERTER.get())
                .pattern("IPI").pattern("TGT").pattern("IHI").define('I', Items.IRON_BLOCK).define('P', pipe)
                .define('T', PcBlocks.TUBE_FURNACE.get()).define('G', PcBlocks.GAS_COMPRESSOR.get()).define('H', AllBlocks.SHAFT.get())
                .unlockedBy("has_tube_furnace", has(PcBlocks.TUBE_FURNACE.get())).save(out, id("methanol_converter"));
        // колонны: две тарельчатые колонны с кипятильниками
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.METHANOL_COLUMN.get())
                .pattern("SPS").pattern("STS").pattern("SVS").define('S', sheet).define('P', pipe)
                .define('T', AllBlocks.FLUID_TANK.get()).define('V', AllBlocks.FLUID_VALVE.get())
                .unlockedBy("has_methanol_converter", has(PcBlocks.METHANOL_CONVERTER.get())).save(out, id("methanol_column"));
        // Cu/ZnO/Al₂O₃: медь, цинк (сфалерит), глинозём
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, PcItems.CATALYST_METHANOL.get())
                .requires(Items.COPPER_INGOT, 3).requires(PcItems.RAW_SPHALERITE.get()).requires(Items.CLAY_BALL)
                .unlockedBy("has_sphalerite", has(PcItems.RAW_SPHALERITE.get())).save(out, id("catalyst_methanol"));
    }
}
