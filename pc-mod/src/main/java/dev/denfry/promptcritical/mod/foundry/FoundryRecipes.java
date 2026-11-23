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
        // оловянная бронза: медь, сплавленная с касситеритом под углём (~10 % олова); единственный источник
        // бронзы в моде без других модов — нужна тиглю для корпуса подшипника и вкладышей
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, PcItems.BRONZE_INGOT.get(), 3)
                .requires(Items.COPPER_INGOT, 3).requires(PcItems.RAW_CASSITERITE.get()).requires(ItemTags.COALS)
                .unlockedBy("has_cassiterite", has(PcItems.RAW_CASSITERITE.get())).save(out, id("bronze_ingot"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcItems.CASTING_PATTERN.get())
                .pattern(" P ").pattern("PSP").pattern(" P ").define('P', ItemTags.PLANKS).define('S', Items.STICK)
                .unlockedBy("has_planks", has(ItemTags.PLANKS)).save(out, id("casting_pattern"));
        ShapelessRecipeBuilder.shapeless(RecipeCategory.TOOLS, PcItems.CUTTING_TOOL.get())
                .requires(Items.IRON_INGOT, 2).requires(Items.FLINT)
                .unlockedBy("has_iron", has(Items.IRON_INGOT)).save(out, id("cutting_tool"));
        // дисковая фреза: быстрорежущая пластина на стальном диске; круг — абразив (кремень) на керамической связке (глина),
        // обожжённой вместе с песком-наполнителем
        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, PcItems.MILLING_CUTTER.get())
                .pattern(" T ").pattern("TIT").pattern(" T ").define('T', PcItems.CUTTING_TOOL.get()).define('I', Items.IRON_INGOT)
                .unlockedBy("has_cutting_tool", has(PcItems.CUTTING_TOOL.get())).save(out, id("milling_cutter"));
        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, PcItems.GRINDING_WHEEL.get())
                .pattern("FCF").pattern("CSC").pattern("FCF").define('F', Items.FLINT).define('C', Items.CLAY_BALL)
                .define('S', Items.SAND)
                .unlockedBy("has_flint", has(Items.FLINT)).save(out, id("grinding_wheel"));
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
        // чертёж вагранки реального масштаба: вагранка строится на стройплощадке по массе прототипа (SitePlans)
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, dev.denfry.promptcritical.mod.site.SiteRegistry.CUPOLA_FURNACE_REAL_DRAWING.get())
                .pattern("PPP").pattern("IRI").pattern("SFS").define('P', Items.PAPER).define('I', AllItems.IRON_SHEET.get())
                .define('R', PcItems.REFRACTORY_BRICK.get()).define('S', AllBlocks.SHAFT.get()).define('F', Items.BLAST_FURNACE)
                .unlockedBy("has_refractory_brick", has(PcItems.REFRACTORY_BRICK.get())).save(out, id("cupola_furnace_real_drawing"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.CRUCIBLE_FURNACE.get())
                .pattern("RCR").pattern("BFB").define('R', PcItems.REFRACTORY_BRICK.get()).define('C', Items.CAULDRON)
                .define('B', Items.BRICKS).define('F', Items.FURNACE)
                .unlockedBy("has_refractory_brick", has(PcItems.REFRACTORY_BRICK.get())).save(out, id("crucible_furnace"));
        // чертёж тигельной ямы: печь строится на стройплощадке по массе прототипа (SitePlans)
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, dev.denfry.promptcritical.mod.site.SiteRegistry.CRUCIBLE_FURNACE_REAL_DRAWING.get())
                .pattern("PPP").pattern("RCR").pattern("BBB").define('P', Items.PAPER).define('R', PcItems.REFRACTORY_BRICK.get())
                .define('C', Items.CAULDRON).define('B', Items.BRICKS)
                .unlockedBy("has_refractory_brick", has(PcItems.REFRACTORY_BRICK.get())).save(out, id("crucible_furnace_real_drawing"));
        // чертежи формовочной машины и выбивной решётки реального масштаба: строятся на стройплощадке (SitePlans)
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, dev.denfry.promptcritical.mod.site.SiteRegistry.MOLDING_STATION_REAL_DRAWING.get())
                .pattern("PPP").pattern("IHI").pattern("SXS").define('P', Items.PAPER).define('I', AllItems.IRON_SHEET.get())
                .define('H', Items.HOPPER).define('S', AllBlocks.SHAFT.get()).define('X', Items.PISTON)
                .unlockedBy("has_molding_station", has(PcBlocks.MOLDING_STATION.get())).save(out, id("molding_station_real_drawing"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, dev.denfry.promptcritical.mod.site.SiteRegistry.SHAKEOUT_GRATE_REAL_DRAWING.get())
                .pattern("PPP").pattern("BBB").pattern("SXS").define('P', Items.PAPER).define('B', Items.IRON_BARS)
                .define('S', AllBlocks.SHAFT.get()).define('X', Items.PISTON)
                .unlockedBy("has_shakeout_grate", has(PcBlocks.SHAKEOUT_GRATE.get())).save(out, id("shakeout_grate_real_drawing"));
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
        // станочный парк строит сам себя: строгальный — из деталей Create, его направляющие идут в фрезерный и
        // шлифовальный, зубчатые колёса фрезерного — в коробку шлифовального
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.SHAPING_MACHINE.get())
                .pattern("IPI").pattern("GCS").define('I', Items.IRON_INGOT).define('P', AllBlocks.MECHANICAL_PISTON.get())
                .define('G', AllBlocks.COGWHEEL.get()).define('C', AllBlocks.ANDESITE_CASING.get()).define('S', AllBlocks.SHAFT.get())
                .unlockedBy("has_lathe", has(PcBlocks.MACHINE_LATHE.get())).save(out, id("shaping_machine"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.MILLING_MACHINE.get())
                .pattern("IGI").pattern("WCW").pattern("ISI").define('I', Items.IRON_INGOT).define('G', AllBlocks.COGWHEEL.get())
                .define('W', PcItems.MACHINE_SLIDEWAY.get()).define('C', AllBlocks.ANDESITE_CASING.get()).define('S', AllBlocks.SHAFT.get())
                .unlockedBy("has_slideway", has(PcItems.MACHINE_SLIDEWAY.get())).save(out, id("milling_machine"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.SURFACE_GRINDER.get())
                .pattern("IBI").pattern("WCW").pattern("GSG").define('I', Items.IRON_INGOT).define('B', AllItems.BELT_CONNECTOR.get())
                .define('W', PcItems.MACHINE_SLIDEWAY.get()).define('C', AllBlocks.ANDESITE_CASING.get())
                .define('G', PcItems.SPUR_GEAR.get()).define('S', AllBlocks.SHAFT.get())
                .unlockedBy("has_spur_gear", has(PcItems.SPUR_GEAR.get())).save(out, id("surface_grinder"));
    }
}
