package dev.denfry.promptcritical.mod.reprocessing;

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
import net.minecraft.world.level.ItemLike;

/** Рецепты радиохимии: каньон, аппараты линии PUREX, хранилище стекла. */
public final class ReprocessingRecipes extends RecipeProvider {
    public ReprocessingRecipes(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries);
    }

    public static DataProvider provider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        DataProvider inner = new ReprocessingRecipes(output, registries);
        return new DataProvider() {
            @Override
            public CompletableFuture<?> run(CachedOutput cache) {
                return inner.run(cache);
            }

            @Override
            public String getName() {
                return "Recipes: reprocessing";
            }
        };
    }

    private static String id(String name) {
        return PromptCritical.id("reprocessing/" + name).toString();
    }

    /** Аппарат из нержавеющей стали: листы, труба и сердцевина. */
    private static void apparatus(RecipeOutput out, ItemLike result, ItemLike core, String name) {
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, result)
                .pattern("SPS").pattern("SCS").pattern("SPS").define('S', AllItems.IRON_SHEET.get()).define('P', PcItems.SEAMLESS_PIPE.get())
                .define('C', core).unlockedBy("has_lead_block", has(PcBlocks.LEAD_BLOCK.get())).save(out, id(name));
    }

    @Override
    protected void buildRecipes(RecipeOutput out) {
        // каньон: свинцовое стекло смотрового окна, манипуляторы — механические руки Create
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.PUREX_CELL.get())
                .pattern("LGL").pattern("ADA").pattern("LCL").define('L', PcBlocks.LEAD_BLOCK.get()).define('G', Items.GLASS)
                .define('A', AllBlocks.MECHANICAL_ARM.get()).define('D', PcItems.DOSIMETER.get()).define('C', Items.SMOOTH_STONE)
                .unlockedBy("has_lead_block", has(PcBlocks.LEAD_BLOCK.get())).save(out, id("purex_cell"));
        apparatus(out, PcBlocks.SHEARING_MACHINE.get(), AllBlocks.MECHANICAL_SAW.get(), "shearing_machine");
        apparatus(out, PcBlocks.DISSOLVER.get(), AllBlocks.FLUID_TANK.get(), "dissolver");
        apparatus(out, PcBlocks.MIXER_SETTLER.get(), AllBlocks.MECHANICAL_MIXER.get(), "mixer_settler");
        apparatus(out, PcBlocks.OXALATE_PRECIPITATOR.get(), AllBlocks.BASIN.get(), "oxalate_precipitator");
        apparatus(out, PcBlocks.DENITRATION_REACTOR.get(), Items.BLAST_FURNACE, "denitration_reactor");
        apparatus(out, PcBlocks.VITRIFICATION_MELTER.get(), Items.GLASS, "vitrification_melter");
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.HLW_VAULT.get())
                .pattern("CCC").pattern("CBC").pattern("CCC").define('C', Items.SMOOTH_STONE).define('B', AllBlocks.ENCASED_FAN.get())
                .unlockedBy("has_vitrification_melter", has(PcBlocks.VITRIFICATION_MELTER.get())).save(out, id("hlw_vault"));
    }
}
