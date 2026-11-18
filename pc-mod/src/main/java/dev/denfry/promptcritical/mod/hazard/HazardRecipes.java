package dev.denfry.promptcritical.mod.hazard;

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

/** Рецепты хранения СУГ, ликвидации разливов, трубопроводов и резервуарного парка (09 §9.8, 14 §14.5). */
public final class HazardRecipes extends RecipeProvider {
    public HazardRecipes(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries);
    }

    public static DataProvider provider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        DataProvider inner = new HazardRecipes(output, registries);
        return new DataProvider() {
            @Override
            public CompletableFuture<?> run(CachedOutput cache) {
                return inner.run(cache);
            }

            @Override
            public String getName() {
                return "Recipes: oil and gas hazards";
            }
        };
    }

    private static String id(String name) {
        return PromptCritical.id("hazard/" + name).toString();
    }

    @Override
    protected void buildRecipes(RecipeOutput out) {
        var plate = PcItems.STEEL_PLATE.get();
        var pipe = PcItems.SEAMLESS_PIPE.get();
        // сосуд под давлением: толстый лист, предохранительный клапан, штуцер
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.GAS_STORAGE_BULLET.get())
                .pattern("SVS").pattern("STS").pattern("SPS").define('S', plate).define('V', AllBlocks.FLUID_VALVE.get())
                .define('T', AllBlocks.FLUID_TANK.get()).define('P', pipe)
                .unlockedBy("has_steel_plate", has(plate)).save(out, id("gas_storage_bullet"));
        // боны: поплавок и юбка, якорь — бетонный груз на цепи
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.OIL_BOOM.get(), 4)
                .pattern("WWW").pattern("SSS").define('W', Items.ORANGE_WOOL).define('S', Items.STRING)
                .unlockedBy("has_wool", has(Items.ORANGE_WOOL)).save(out, id("oil_boom"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.BOOM_ANCHOR.get())
                .pattern(" C ").pattern("SSS").define('C', Items.CHAIN).define('S', Items.STONE)
                .unlockedBy("has_chain", has(Items.CHAIN)).save(out, id("boom_anchor"));
        // олеофильный барабан на валу, поплавки, бак
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.OIL_SKIMMER.get())
                .pattern("ISI").pattern("PTP").pattern("I I").define('I', AllItems.IRON_SHEET.get()).define('S', AllBlocks.SHAFT.get())
                .define('P', PcItems.POLYETHYLENE.get()).define('T', AllBlocks.FLUID_TANK.get())
                .unlockedBy("has_polyethylene", has(PcItems.POLYETHYLENE.get())).save(out, id("oil_skimmer"));
        // сорбент — нетканый полиолефин (в GDD — полипропилен; в моде пока есть полиэтилен)
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcItems.SORBENT_PAD.get(), 8)
                .pattern("PP").pattern("PP").define('P', PcItems.POLYETHYLENE.get())
                .unlockedBy("has_polyethylene", has(PcItems.POLYETHYLENE.get())).save(out, id("sorbent_pad"));
    }
}
