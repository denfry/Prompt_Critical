package dev.denfry.promptcritical.mod.chem;

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

/** Рецепты химии I3. */
public final class ChemRecipes extends RecipeProvider {
    public ChemRecipes(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries);
    }

    public static DataProvider provider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        DataProvider inner = new ChemRecipes(output, registries);
        return new DataProvider() {
            @Override
            public CompletableFuture<?> run(CachedOutput cache) {
                return inner.run(cache);
            }

            @Override
            public String getName() {
                return "Recipes: chemistry";
            }
        };
    }

    private static String id(String name) {
        return PromptCritical.id("chem/" + name).toString();
    }

    @Override
    protected void buildRecipes(RecipeOutput out) {
        var sheet = AllItems.IRON_SHEET.get();
        var pipe = PcItems.SEAMLESS_PIPE.get();
        // печь пиролиза: трубчатая печь, змеевики из жаропрочных труб, закалочный аппарат
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.PYROLYSIS_FURNACE.get())
                .pattern("PBP").pattern("PTP").pattern("BHB").define('P', pipe).define('B', Items.BRICKS)
                .define('T', PcBlocks.TUBE_FURNACE.get()).define('H', AllBlocks.FLUID_TANK.get())
                .unlockedBy("has_tube_furnace", has(PcBlocks.TUBE_FURNACE.get())).save(out, id("pyrolysis_furnace"));
        // газоразделение: колонны (ГФУ), компрессор, холод
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.CRACKED_GAS_UNIT.get())
                .pattern("SNS").pattern("CIC").pattern("SVS").define('S', sheet).define('N', PcBlocks.NGL_FRACTIONATOR.get())
                .define('C', PcBlocks.GAS_COMPRESSOR.get()).define('I', Items.PACKED_ICE).define('V', AllBlocks.FLUID_VALVE.get())
                .unlockedBy("has_ngl_fractionator", has(PcBlocks.NGL_FRACTIONATOR.get())).save(out, id("cracked_gas_unit"));
        // ПЭВД: гиперкомпрессор (поршни на коленвале) и толстостенная труба реактора
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.LDPE_REACTOR.get())
                .pattern("MMM").pattern("CPC").pattern("IHI").define('M', AllBlocks.MECHANICAL_PISTON.get())
                .define('C', AllBlocks.COGWHEEL.get()).define('P', pipe).define('I', Items.IRON_BLOCK).define('H', AllBlocks.SHAFT.get())
                .unlockedBy("has_seamless_pipe", has(pipe)).save(out, id("ldpe_reactor"));
    }
}
