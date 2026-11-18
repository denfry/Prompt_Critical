package dev.denfry.promptcritical.mod.gas;

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
 * Рецепты газопереработки: компрессор, регенератор гликоля (поставляется заправленным ТЭГ), установка НТС с пропановым
 * холодильником, газофракционирующая установка. Метанол — из сухой перегонки древесины ({@code create:mixing} в
 * ресурсах): древесный спирт был первым промышленным метанолом.
 */
public final class GasRecipes extends RecipeProvider {
    public GasRecipes(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries);
    }

    public static DataProvider provider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        DataProvider inner = new GasRecipes(output, registries);
        return new DataProvider() {
            @Override
            public CompletableFuture<?> run(CachedOutput cache) {
                return inner.run(cache);
            }

            @Override
            public String getName() {
                return "Recipes: gas processing";
            }
        };
    }

    private static String id(String name) {
        return PromptCritical.id("gas/" + name).toString();
    }

    @Override
    protected void buildRecipes(RecipeOutput out) {
        var sheet = AllItems.IRON_SHEET.get();
        var pipe = PcItems.SEAMLESS_PIPE.get();
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.GAS_COMPRESSOR.get())
                .pattern("PPP").pattern("CIC").pattern("AHA").define('P', AllBlocks.MECHANICAL_PISTON.get()).define('C', AllBlocks.COGWHEEL.get())
                .define('I', Items.IRON_BLOCK).define('A', AllBlocks.ANDESITE_CASING.get()).define('H', AllBlocks.SHAFT.get())
                .unlockedBy("has_mechanical_piston", has(AllBlocks.MECHANICAL_PISTON.get())).save(out, id("gas_compressor"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.GLYCOL_REGENERATOR.get())
                .pattern("SPS").pattern("TBT").pattern("SFS").define('S', sheet).define('P', pipe).define('T', AllBlocks.FLUID_TANK.get())
                .define('B', Items.BRICKS).define('F', Items.BLAST_FURNACE).unlockedBy("has_seamless_pipe", has(pipe))
                .save(out, id("glycol_regenerator"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.LOW_TEMPERATURE_SEPARATOR.get())
                .pattern("SVS").pattern("PTP").pattern("SIS").define('S', sheet).define('V', AllBlocks.FLUID_VALVE.get()).define('P', pipe)
                .define('T', AllBlocks.FLUID_TANK.get()).define('I', Items.PACKED_ICE)
                .unlockedBy("has_seamless_pipe", has(pipe)).save(out, id("low_temperature_separator"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.NGL_FRACTIONATOR.get())
                .pattern("STS").pattern("PCP").pattern("SVS").define('S', sheet).define('T', PcBlocks.COLUMN_TRAY_SECTION.get())
                .define('P', pipe).define('C', PcBlocks.CRUDE_COLUMN.get()).define('V', AllBlocks.FLUID_VALVE.get())
                .unlockedBy("has_crude_column", has(PcBlocks.CRUDE_COLUMN.get())).save(out, id("ngl_fractionator"));
    }
}
