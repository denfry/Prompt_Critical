package dev.denfry.promptcritical.mod.waste;

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
import net.minecraft.world.level.ItemLike;

/** Рецепты обращения с РАО: аппараты, тара, вяжущее, медные контейнеры. */
public final class WasteRecipes extends RecipeProvider {
    public WasteRecipes(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries);
    }

    public static DataProvider provider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        DataProvider inner = new WasteRecipes(output, registries);
        return new DataProvider() {
            @Override
            public CompletableFuture<?> run(CachedOutput cache) {
                return inner.run(cache);
            }

            @Override
            public String getName() {
                return "Recipes: radioactive waste";
            }
        };
    }

    private static String id(String name) {
        return PromptCritical.id("waste/" + name).toString();
    }

    /** Аппарат из стального листа с трубами и сердцевиной. */
    private static void apparatus(RecipeOutput out, ItemLike result, ItemLike core, String name) {
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, result)
                .pattern("SPS").pattern("SCS").pattern("SPS").define('S', AllItems.IRON_SHEET.get()).define('P', PcItems.SEAMLESS_PIPE.get())
                .define('C', core).unlockedBy("has_iron_sheet", has(AllItems.IRON_SHEET.get())).save(out, id(name));
    }

    @Override
    protected void buildRecipes(RecipeOutput out) {
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcItems.WASTE_DRUM.get(), 2)
                .pattern("S S").pattern("S S").pattern("SSS").define('S', AllItems.IRON_SHEET.get())
                .unlockedBy("has_iron_sheet", has(AllItems.IRON_SHEET.get())).save(out, id("waste_drum"));
        // клинкерная печь свёрнута в рецепт: сырьевая смесь 3:1 известняк и глина
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, PcItems.PORTLAND_CEMENT.get(), 4)
                .requires(Items.CALCITE, 3).requires(Items.CLAY_BALL)
                .unlockedBy("has_calcite", has(Items.CALCITE)).save(out, id("portland_cement"));
        // KBS-3: медная оболочка на чугунной вставке
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcItems.COPPER_CANISTER.get())
                .pattern("CCC").pattern("CIC").pattern("CCC").define('C', Items.COPPER_BLOCK).define('I', Items.IRON_BLOCK)
                .unlockedBy("has_copper_block", has(Items.COPPER_BLOCK)).save(out, id("copper_canister"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcItems.FILTER_CARTRIDGE.get(), 2)
                .pattern("P").pattern("B").pattern("P").define('P', Items.PAPER).define('B', Items.IRON_BARS)
                .unlockedBy("has_paper", has(Items.PAPER)).save(out, id("filter_cartridge"));
        apparatus(out, PcBlocks.LRW_EVAPORATOR.get(), AllBlocks.FLUID_TANK.get(), "lrw_evaporator");
        apparatus(out, PcBlocks.CEMENTATION_MIXER.get(), AllBlocks.MECHANICAL_MIXER.get(), "cementation_mixer");
        apparatus(out, PcBlocks.BITUMENIZER.get(), Items.CAULDRON, "bitumenizer");
        apparatus(out, PcBlocks.WASTE_COMPACTOR.get(), AllBlocks.MECHANICAL_PRESS.get(), "waste_compactor");
        apparatus(out, PcBlocks.REACTOR_WATER_CLEANUP.get(), PcItems.FILTER_CARTRIDGE.get(), "reactor_water_cleanup");
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.NEAR_SURFACE_REPOSITORY.get())
                .pattern("CCC").pattern("CDC").pattern("CCC").define('C', Items.SMOOTH_STONE).define('D', PcItems.WASTE_DRUM.get())
                .unlockedBy("has_waste_drum", has(PcItems.WASTE_DRUM.get())).save(out, id("near_surface_repository"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.DEEP_GEOLOGICAL_REPOSITORY.get())
                .pattern("DDD").pattern("DKD").pattern("DDD").define('D', Items.DEEPSLATE_BRICKS).define('K', PcItems.COPPER_CANISTER.get())
                .unlockedBy("has_copper_canister", has(PcItems.COPPER_CANISTER.get())).save(out, id("deep_geological_repository"));
    }
}
