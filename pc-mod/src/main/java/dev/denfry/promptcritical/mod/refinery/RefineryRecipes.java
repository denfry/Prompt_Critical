package dev.denfry.promptcritical.mod.refinery;

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

/** Рецепты облагораживания на НПЗ: гидроочистка, установка Клауса и катализатор CoMo. */
public final class RefineryRecipes extends RecipeProvider {
    public RefineryRecipes(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries);
    }

    public static DataProvider provider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        DataProvider inner = new RefineryRecipes(output, registries);
        return new DataProvider() {
            @Override
            public CompletableFuture<?> run(CachedOutput cache) {
                return inner.run(cache);
            }

            @Override
            public String getName() {
                return "Recipes: refinery";
            }
        };
    }

    private static String id(String name) {
        return PromptCritical.id("refinery/" + name).toString();
    }

    @Override
    protected void buildRecipes(RecipeOutput out) {
        var plate = PcItems.STEEL_PLATE.get();
        var pipe = PcItems.SEAMLESS_PIPE.get();
        // толстостенный реактор, печь сырья, циркуляционный компрессор водорода
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.HYDROTREATER.get())
                .pattern("SPS").pattern("TRG").pattern("SPS").define('S', plate).define('P', pipe)
                .define('T', PcBlocks.TUBE_FURNACE.get()).define('R', AllBlocks.FLUID_TANK.get()).define('G', PcBlocks.GAS_COMPRESSOR.get())
                .unlockedBy("has_crude_column", has(PcBlocks.CRUDE_COLUMN.get())).save(out, id("hydrotreater"));
        // печь с котлом-утилизатором, конверторы и конденсаторы серы
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.CLAUS_UNIT.get())
                .pattern("SPS").pattern("BTB").pattern("SVS").define('S', AllItems.IRON_SHEET.get()).define('P', pipe)
                .define('B', Items.BLAST_FURNACE).define('T', AllBlocks.FLUID_TANK.get()).define('V', AllBlocks.FLUID_VALVE.get())
                .unlockedBy("has_hydrotreater", has(PcBlocks.HYDROTREATER.get())).save(out, id("claus_unit"));
        // CoMo/Al₂O₃: кобальт, молибденит, боксит на глинозём
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, PcItems.CATALYST_HYDROTREATING.get())
                .requires(PcItems.COBALT_INGOT.get()).requires(PcItems.RAW_MOLYBDENITE.get(), 2).requires(PcItems.RAW_BAUXITE.get())
                .unlockedBy("has_molybdenite", has(PcItems.RAW_MOLYBDENITE.get())).save(out, id("catalyst_hydrotreating"));
    }
}
