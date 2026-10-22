package dev.denfry.promptcritical.mod.ops;

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
import net.minecraft.data.recipes.SpecialRecipeBuilder;
import net.minecraft.world.item.Items;

/**
 * Рецепты эксплуатации и водоподготовки (13 §13.6.5–13.6.7, 08 §8.12): инструменты среза, расходники, щит
 * обслуживания, опрессовочный насос, самописец, блоки водоподготовки; вкладыш и колесо — из отливки
 * ({@link CastingPartRecipe}). Технический жир и соль — смешиванием Create с нагревом (статические рецепты).
 */
public final class OpsRecipes extends RecipeProvider {
    public OpsRecipes(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries);
    }

    public static DataProvider provider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        DataProvider inner = new OpsRecipes(output, registries);
        return new DataProvider() {
            @Override
            public CompletableFuture<?> run(CachedOutput cache) {
                return inner.run(cache);
            }

            @Override
            public String getName() {
                return "Recipes: operations and water treatment";
            }
        };
    }

    private static String id(String name) {
        return PromptCritical.id("ops/" + name).toString();
    }

    @Override
    protected void buildRecipes(RecipeOutput out) {
        var sheet = AllItems.IRON_SHEET.get();
        var brass = AllItems.BRASS_INGOT.get();
        var pipe = AllBlocks.FLUID_PIPE.get();
        var hasSheet = has(sheet);

        // инструменты
        ShapelessRecipeBuilder.shapeless(RecipeCategory.TOOLS, PcItems.MECHANIC_STETHOSCOPE.get())
                .requires(Items.COPPER_INGOT).requires(Items.IRON_INGOT).requires(Items.STRING)
                .unlockedBy("has_copper", has(Items.COPPER_INGOT)).save(out, id("mechanic_stethoscope"));
        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, PcItems.TORQUE_WRENCH.get())
                .pattern(" I").pattern("IK").pattern("I ").define('I', Items.IRON_INGOT).define('K', Items.COMPARATOR)
                .unlockedBy("has_iron", has(Items.IRON_INGOT)).save(out, id("torque_wrench"));
        ShapelessRecipeBuilder.shapeless(RecipeCategory.TOOLS, PcItems.TEST_PRESSURE_GAUGE.get())
                .requires(Items.CLOCK).requires(Items.COPPER_INGOT).requires(Items.GLASS_PANE)
                .unlockedBy("has_clock", has(Items.CLOCK)).save(out, id("test_pressure_gauge"));
        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, PcItems.CAULKING_HAMMER.get())
                .pattern("II").pattern(" S").pattern(" S").define('I', Items.IRON_INGOT).define('S', Items.STICK)
                .unlockedBy("has_iron", has(Items.IRON_INGOT)).save(out, id("caulking_hammer"));
        ShapelessRecipeBuilder.shapeless(RecipeCategory.TOOLS, PcItems.OIL_CAN.get())
                .requires(sheet).requires(Items.COPPER_INGOT).requires(Items.BUCKET)
                .unlockedBy("has_sheet", hasSheet).save(out, id("oil_can"));
        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, PcItems.BOILER_SCRAPER.get())
                .pattern("P").pattern("S").pattern("S").define('P', sheet).define('S', Items.STICK)
                .unlockedBy("has_sheet", hasSheet).save(out, id("boiler_scraper"));

        // запчасти и расходники
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcItems.SAFETY_VALVE.get())
                .pattern(" B ").pattern("PVP").define('B', brass).define('P', pipe).define('V', AllBlocks.FLUID_VALVE.get())
                .unlockedBy("has_brass", has(brass)).save(out, id("safety_valve"));
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, PcItems.GLAND_PACKING.get(), 2)
                .requires(Items.STRING, 2).requires(PcItems.TALLOW.get())
                .unlockedBy("has_tallow", has(PcItems.TALLOW.get())).save(out, id("gland_packing"));
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, PcItems.CHART_PAPER.get(), 3)
                .requires(Items.PAPER, 3).requires(Items.BLACK_DYE)
                .unlockedBy("has_paper", has(Items.PAPER)).save(out, id("chart_paper"));
        SpecialRecipeBuilder.special(CastingPartRecipe::new).save(out, id("casting_part"));

        // щит, насос, самописец
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.MAINTENANCE_PANEL.get())
                .pattern("IGI").pattern("BBB").pattern("III").define('I', Items.IRON_INGOT).define('G', Items.GLASS_PANE)
                .define('B', brass).unlockedBy("has_brass", has(brass)).save(out, id("maintenance_panel"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.HYDROTEST_PUMP.get())
                .pattern("L  ").pattern("IPI").define('L', Items.LEVER).define('I', Items.IRON_INGOT).define('P', pipe)
                .unlockedBy("has_pipe", has(pipe)).save(out, id("hydrotest_pump"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.RECORDING_GAUGE.get())
                .pattern("IGI").pattern("CKC").pattern("III").define('I', Items.IRON_INGOT).define('G', Items.GLASS_PANE)
                .define('C', Items.COPPER_INGOT).define('K', Items.CLOCK)
                .unlockedBy("has_clock", has(Items.CLOCK)).save(out, id("recording_gauge"));

        // водоподготовка
        var tank = AllBlocks.FLUID_TANK.get();
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.WATER_CLARIFIER.get())
                .pattern("IPI").pattern("ITI").pattern("III").define('I', sheet).define('P', pipe).define('T', tank)
                .unlockedBy("has_sheet", hasSheet).save(out, id("water_clarifier"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.SAND_FILTER.get())
                .pattern("IPI").pattern("ISI").pattern("IPI").define('I', sheet).define('P', pipe).define('S', Items.SAND)
                .unlockedBy("has_sheet", hasSheet).save(out, id("sand_filter"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.ION_EXCHANGE_COLUMN.get())
                .pattern("IPI").pattern("ICI").pattern("IPI").define('I', sheet).define('P', pipe).define('C', Items.COAL)
                .unlockedBy("has_sheet", hasSheet).save(out, id("ion_exchange_column"));
    }
}
