package dev.denfry.promptcritical.mod.powerhouse;

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

/**
 * Рецепты котла I1 и деаэратора (07 §7.9.5, 08 §8.12): экраны — из бесшовной трубы с огнеупором, барабан и щит — из
 * стального листа, вентиляторы — на валу Create. Угольная пыль — помолом угля жерновами Create (статический рецепт).
 */
public final class PowerhouseRecipes extends RecipeProvider {
    public PowerhouseRecipes(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries);
    }

    public static DataProvider provider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        DataProvider inner = new PowerhouseRecipes(output, registries);
        return new DataProvider() {
            @Override
            public CompletableFuture<?> run(CachedOutput cache) {
                return inner.run(cache);
            }

            @Override
            public String getName() {
                return "Recipes: water-tube boiler and deaerator";
            }
        };
    }

    private static String id(String name) {
        return PromptCritical.id("powerhouse/" + name).toString();
    }

    @Override
    protected void buildRecipes(RecipeOutput out) {
        var plate = PcItems.STEEL_PLATE.get();
        var seamless = PcItems.SEAMLESS_PIPE.get();
        var welded = PcItems.WELDED_PIPE.get();
        var brick = PcItems.REFRACTORY_BRICK.get();
        var shaft = AllBlocks.SHAFT.get();
        var pipe = AllBlocks.FLUID_PIPE.get();
        var hasSeamless = has(seamless);
        var wall = PcBlocks.BOILER_WALL.get();

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, wall, 6)
                .pattern("PRP").pattern("PRP").pattern("PRP").define('P', seamless).define('R', brick)
                .unlockedBy("has_seamless_pipe", hasSeamless).save(out, id("boiler_wall"));
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, PcBlocks.BOILER_BURNER.get())
                .requires(wall).requires(AllBlocks.FLUID_VALVE.get()).requires(Items.FLINT_AND_STEEL).requires(plate)
                .unlockedBy("has_boiler_wall", has(wall)).save(out, id("boiler_burner"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.STEAM_DRUM.get())
                .pattern("PPP").pattern("PTP").pattern("PPP").define('P', plate).define('T', AllBlocks.FLUID_TANK.get())
                .unlockedBy("has_seamless_pipe", hasSeamless).save(out, id("steam_drum"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.BOILER_SUPERHEATER.get())
                .pattern("SSS").pattern(" P ").define('S', seamless).define('P', plate)
                .unlockedBy("has_seamless_pipe", hasSeamless).save(out, id("boiler_superheater"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.BOILER_ECONOMIZER.get())
                .pattern("SFS").pattern(" P ").define('S', seamless).define('F', pipe).define('P', plate)
                .unlockedBy("has_seamless_pipe", hasSeamless).save(out, id("boiler_economizer"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.AIR_PREHEATER.get())
                .pattern("WWW").pattern("WPW").define('W', welded).define('P', plate)
                .unlockedBy("has_welded_pipe", has(welded)).save(out, id("air_preheater"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.FORCED_DRAFT_FAN.get())
                .pattern("PFP").pattern("S P").define('P', plate).define('F', AllItems.PROPELLER.get()).define('S', shaft)
                .unlockedBy("has_steel_plate", has(plate)).save(out, id("forced_draft_fan"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.INDUCED_DRAFT_FAN.get())
                .pattern("PFP").pattern("SRP").define('P', plate).define('F', AllItems.PROPELLER.get()).define('S', shaft)
                .define('R', brick).unlockedBy("has_steel_plate", has(plate)).save(out, id("induced_draft_fan"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.WATER_TUBE_BOILER.get())
                .pattern("PKP").pattern("RCR").define('P', plate).define('K', Items.COMPARATOR).define('R', Items.REDSTONE)
                .define('C', AllBlocks.BRASS_CASING.get()).unlockedBy("has_boiler_wall", has(wall)).save(out, id("water_tube_boiler"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.DEAERATOR.get())
                .pattern("PTP").pattern("FVF").define('P', plate).define('T', AllBlocks.FLUID_TANK.get()).define('F', pipe)
                .define('V', AllBlocks.FLUID_VALVE.get()).unlockedBy("has_steel_plate", has(plate)).save(out, id("deaerator"));
    }
}
