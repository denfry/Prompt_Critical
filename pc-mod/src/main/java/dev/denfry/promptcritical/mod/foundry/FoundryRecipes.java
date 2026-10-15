package dev.denfry.promptcritical.mod.foundry;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcItems;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.data.recipes.SimpleCookingRecipeBuilder;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

/** Рецепты литейки, цеха и углеобогащения I0 (08 §8.3, §8.9–8.10): машины из деталей Create, расходники. */
public final class FoundryRecipes extends RecipeProvider {
    public FoundryRecipes(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries);
    }

    private static String id(String name) {
        return PromptCritical.id("foundry/" + name).toString();
    }

    @Override
    protected void buildRecipes(RecipeOutput out) {
        // формовочная смесь: кварцевый песок и глина-связка (бентонит 02 — позже)
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, PcItems.FOUNDRY_SAND.get(), 4)
                .requires(Items.SAND, 3).requires(Items.CLAY_BALL)
                .unlockedBy("has_sand", has(Items.SAND)).save(out, id("foundry_sand"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcItems.CASTING_PATTERN.get())
                .pattern(" P ").pattern("PSP").pattern(" P ").define('P', ItemTags.PLANKS).define('S', Items.STICK)
                .unlockedBy("has_planks", has(ItemTags.PLANKS)).save(out, id("casting_pattern"));
        ShapelessRecipeBuilder.shapeless(RecipeCategory.TOOLS, PcItems.CUTTING_TOOL.get())
                .requires(Items.IRON_INGOT, 2).requires(Items.FLINT)
                .unlockedBy("has_iron", has(Items.IRON_INGOT)).save(out, id("cutting_tool"));
        SimpleCookingRecipeBuilder.smelting(Ingredient.of(PcBlocks.FIRECLAY.get()), RecipeCategory.MISC,
                PcItems.REFRACTORY_BRICK.get(), 0.1f, 200)
                .unlockedBy("has_fireclay", has(PcBlocks.FIRECLAY.get())).save(out, id("refractory_brick"));

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.SAND_MOLD.get())
                .pattern(" P ").pattern("PFP").pattern(" P ").define('P', ItemTags.PLANKS).define('F', PcItems.FOUNDRY_SAND.get())
                .unlockedBy("has_foundry_sand", has(PcItems.FOUNDRY_SAND.get())).save(out, id("sand_mold"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.COAL_JIG.get())
                .pattern("SBS").pattern("ICI").pattern("I I").define('S', AllBlocks.SHAFT.get()).define('B', Items.BUCKET)
                .define('I', AllItems.IRON_SHEET.get()).define('C', AllBlocks.ANDESITE_CASING.get())
                .unlockedBy("has_casing", has(AllBlocks.ANDESITE_CASING.get())).save(out, id("coal_jig"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.CUPOLA_FURNACE.get())
                .pattern("IRI").pattern("IRI").pattern("IFI").define('I', AllItems.IRON_SHEET.get())
                .define('R', PcItems.REFRACTORY_BRICK.get()).define('F', Items.BLAST_FURNACE)
                .unlockedBy("has_refractory_brick", has(PcItems.REFRACTORY_BRICK.get())).save(out, id("cupola_furnace"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.CRUCIBLE_FURNACE.get())
                .pattern("RCR").pattern("BFB").define('R', PcItems.REFRACTORY_BRICK.get()).define('C', Items.CAULDRON)
                .define('B', Items.BRICKS).define('F', Items.FURNACE)
                .unlockedBy("has_refractory_brick", has(PcItems.REFRACTORY_BRICK.get())).save(out, id("crucible_furnace"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.MOLDING_STATION.get())
                .pattern("IHI").pattern("SCS").define('I', AllItems.IRON_SHEET.get()).define('H', Items.HOPPER)
                .define('S', AllBlocks.SHAFT.get()).define('C', AllBlocks.ANDESITE_CASING.get())
                .unlockedBy("has_casing", has(AllBlocks.ANDESITE_CASING.get())).save(out, id("molding_station"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.SHAKEOUT_GRATE.get())
                .pattern("BBB").pattern("SCS").define('B', Items.IRON_BARS).define('S', AllBlocks.SHAFT.get())
                .define('C', AllBlocks.ANDESITE_CASING.get())
                .unlockedBy("has_casing", has(AllBlocks.ANDESITE_CASING.get())).save(out, id("shakeout_grate"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.MACHINE_LATHE.get())
                .pattern("IGI").pattern("SCS").define('I', Items.IRON_INGOT).define('G', AllBlocks.COGWHEEL.get())
                .define('S', AllBlocks.SHAFT.get()).define('C', AllBlocks.ANDESITE_CASING.get())
                .unlockedBy("has_casing", has(AllBlocks.ANDESITE_CASING.get())).save(out, id("machine_lathe"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.DRILL_PRESS.get())
                .pattern("IGI").pattern(" C ").pattern(" S ").define('I', Items.IRON_INGOT).define('G', AllBlocks.COGWHEEL.get())
                .define('S', AllBlocks.SHAFT.get()).define('C', AllBlocks.ANDESITE_CASING.get())
                .unlockedBy("has_casing", has(AllBlocks.ANDESITE_CASING.get())).save(out, id("drill_press"));
    }
}
