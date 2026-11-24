package dev.denfry.promptcritical.mod.production;

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
import net.minecraft.world.item.Items;

/**
 * Рецепты добычи и прямой перегонки: устьевое оборудование и колонна — из бесшовных труб проката, сталь — листы и
 * блоки, задвижки и трубы — Create. Эксплуатационную колонну спускают теми же бесшовными трубами, перфорируют
 * сейсмическими зарядами.
 */
public final class ProductionRecipes extends RecipeProvider {
    public ProductionRecipes(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries);
    }

    public static DataProvider provider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        DataProvider inner = new ProductionRecipes(output, registries);
        return new DataProvider() {
            @Override
            public CompletableFuture<?> run(CachedOutput cache) {
                return inner.run(cache);
            }

            @Override
            public String getName() {
                return "Recipes: oil production and crude distillation";
            }
        };
    }

    private static String id(String name) {
        return PromptCritical.id("production/" + name).toString();
    }

    @Override
    protected void buildRecipes(RecipeOutput out) {
        var sheet = AllItems.IRON_SHEET.get();
        var pipe = PcItems.SEAMLESS_PIPE.get();
        var valve = AllBlocks.FLUID_VALVE.get();
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.WELLHEAD.get())
                .pattern("SVS").pattern("PIP").pattern("SPS").define('S', sheet).define('V', valve).define('P', pipe)
                .define('I', Items.IRON_BLOCK).unlockedBy("has_seamless_pipe", has(pipe)).save(out, id("wellhead"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.CHRISTMAS_TREE.get())
                .pattern(" V ").pattern("VPV").pattern(" V ").define('V', valve).define('P', pipe)
                .unlockedBy("has_seamless_pipe", has(pipe)).save(out, id("christmas_tree"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.PUMPJACK.get())
                .pattern("III").pattern("CLH").pattern("AAA").define('I', Items.IRON_BLOCK).define('C', Items.CHAIN)
                .define('L', AllBlocks.LARGE_COGWHEEL.get()).define('H', AllBlocks.SHAFT.get()).define('A', AllBlocks.ANDESITE_CASING.get())
                .unlockedBy("has_large_cogwheel", has(AllBlocks.LARGE_COGWHEEL.get())).save(out, id("pumpjack"));
        // чертежи промысла реального масштаба: строятся на стройплощадке по массе прототипа (SitePlans), старый блок — образец
        dev.denfry.promptcritical.mod.drilling.DrillingRecipes.drawing(out, dev.denfry.promptcritical.mod.site.SiteRegistry.PUMPJACK_REAL_DRAWING.get(),
                PcBlocks.PUMPJACK.get(), "pumpjack_real_drawing");
        dev.denfry.promptcritical.mod.drilling.DrillingRecipes.drawing(out,
                dev.denfry.promptcritical.mod.site.SiteRegistry.THREE_PHASE_SEPARATOR_REAL_DRAWING.get(), PcBlocks.THREE_PHASE_SEPARATOR.get(),
                "three_phase_separator_real_drawing");
        // транспорт нефти: старых блоков нет, образец — насос и бак Create, трубчатая печь
        dev.denfry.promptcritical.mod.drilling.DrillingRecipes.drawing(out, dev.denfry.promptcritical.mod.site.SiteRegistry.MAINLINE_PUMP_REAL_DRAWING.get(),
                AllBlocks.MECHANICAL_PUMP.get(), "mainline_pump_real_drawing");
        dev.denfry.promptcritical.mod.drilling.DrillingRecipes.drawing(out, dev.denfry.promptcritical.mod.site.SiteRegistry.LINE_HEATER_REAL_DRAWING.get(),
                PcBlocks.TUBE_FURNACE.get(), "line_heater_real_drawing");
        dev.denfry.promptcritical.mod.drilling.DrillingRecipes.drawing(out, dev.denfry.promptcritical.mod.site.SiteRegistry.OIL_TANK_REAL_DRAWING.get(),
                AllBlocks.FLUID_TANK.get(), "oil_tank_real_drawing");
        dev.denfry.promptcritical.mod.drilling.DrillingRecipes.drawing(out, dev.denfry.promptcritical.mod.site.SiteRegistry.OIL_TANK_FLOATING_REAL_DRAWING.get(),
                dev.denfry.promptcritical.mod.site.SiteRegistry.OIL_TANK_REAL_DRAWING.get(), "oil_tank_floating_real_drawing");
        // штанга — пруток с муфтой; глубинный насос — цилиндр из трубы, плунжер и два шаровых клапана
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcItems.SUCKER_ROD.get(), 4)
                .pattern("  I").pattern(" I ").pattern("N  ").define('I', Items.IRON_INGOT).define('N', Items.IRON_NUGGET)
                .unlockedBy("has_pumpjack", has(PcBlocks.PUMPJACK.get())).save(out, id("sucker_rod"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcItems.DOWNHOLE_ROD_PUMP.get())
                .pattern(" P ").pattern("VIV").pattern(" P ").define('P', pipe).define('V', valve).define('I', Items.IRON_INGOT)
                .unlockedBy("has_pumpjack", has(PcBlocks.PUMPJACK.get())).save(out, id("downhole_rod_pump"));
        // газлифтный клапан: корпус из трубы, сильфон (медь) и седло с шаром
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcItems.GAS_LIFT_VALVE.get(), 2)
                .pattern(" P ").pattern("CVC").pattern(" P ").define('P', pipe).define('C', Items.COPPER_INGOT).define('V', valve)
                .unlockedBy("has_seamless_pipe", has(pipe)).save(out, id("gas_lift_valve"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.THREE_PHASE_SEPARATOR.get())
                .pattern("SPS").pattern("GTG").pattern("SVS").define('S', sheet).define('P', AllBlocks.FLUID_PIPE.get())
                .define('G', Items.GLASS_PANE).define('T', AllBlocks.FLUID_TANK.get()).define('V', valve)
                .unlockedBy("has_fluid_tank", has(AllBlocks.FLUID_TANK.get())).save(out, id("three_phase_separator"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.FLARE_STACK.get(), 4)
                .pattern("P").pattern("B").pattern("P").define('P', pipe).define('B', Items.IRON_BARS)
                .unlockedBy("has_seamless_pipe", has(pipe)).save(out, id("flare_stack"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.FLARE_TIP.get())
                .pattern("SFS").pattern(" P ").define('S', sheet).define('F', Items.FLINT_AND_STEEL).define('P', pipe)
                .unlockedBy("has_seamless_pipe", has(pipe)).save(out, id("flare_tip"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.TUBE_FURNACE.get())
                .pattern("BBB").pattern("PFP").pattern("BSB").define('B', Items.BRICKS).define('P', pipe)
                .define('F', Items.BLAST_FURNACE).define('S', sheet).unlockedBy("has_seamless_pipe", has(pipe)).save(out, id("tube_furnace"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.CRUDE_COLUMN.get())
                .pattern("SPS").pattern("STS").pattern("IVI").define('S', sheet).define('P', pipe).define('T', AllBlocks.FLUID_TANK.get())
                .define('I', Items.IRON_BLOCK).define('V', valve).unlockedBy("has_fluid_tank", has(AllBlocks.FLUID_TANK.get()))
                .save(out, id("crude_column"));
        // тарелки — железные люки: колпачковые и клапанные тарелки и есть перфорированные листы с затворами
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.COLUMN_TRAY_SECTION.get(), 2)
                .pattern("STS").pattern("STS").pattern("STS").define('S', sheet).define('T', Items.IRON_TRAPDOOR)
                .unlockedBy("has_iron_sheet", has(sheet)).save(out, id("column_tray_section"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.COLUMN_TOP.get())
                .pattern(" P ").pattern("SVS").pattern("SSS").define('P', AllBlocks.FLUID_PIPE.get()).define('S', sheet).define('V', valve)
                .unlockedBy("has_iron_sheet", has(sheet)).save(out, id("column_top"));
    }
}
