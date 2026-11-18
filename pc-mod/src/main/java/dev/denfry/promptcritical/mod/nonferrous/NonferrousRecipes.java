package dev.denfry.promptcritical.mod.nonferrous;

import com.simibubi.create.AllBlocks;
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
 * Рецепты цветной металлургии (08 §8.8). Вехи (06: M3 — эра I1, M8 — I2) держат ингредиенты: стальной лист и
 * бесшовная труба проката I1, механизмы Create, огнеупор.
 */
public final class NonferrousRecipes extends RecipeProvider {
    public NonferrousRecipes(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries);
    }

    public static DataProvider provider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        DataProvider inner = new NonferrousRecipes(output, registries);
        return new DataProvider() {
            @Override
            public CompletableFuture<?> run(CachedOutput cache) {
                return inner.run(cache);
            }

            @Override
            public String getName() {
                return "Recipes: nonferrous metallurgy";
            }
        };
    }

    private static String id(String name) {
        return PromptCritical.id("nonferrous/" + name).toString();
    }

    @Override
    protected void buildRecipes(RecipeOutput out) {
        var plate = PcItems.STEEL_PLATE.get();
        var pipe = PcItems.SEAMLESS_PIPE.get();
        var tank = AllBlocks.FLUID_TANK.get();
        var brick = PcItems.REFRACTORY_BRICK.get();
        // Байер: автоклав — толстостенный сосуд на насосе, декомпозёр — бак с мешалкой, печь — футерованная труба на валу
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.BAYER_DIGESTER.get())
                .pattern("PTP").pattern("SFS").pattern("PMP").define('P', plate).define('T', tank).define('S', pipe)
                .define('F', Items.FURNACE).define('M', AllBlocks.MECHANICAL_PUMP.get())
                .unlockedBy("has_raw_bauxite", has(PcItems.RAW_BAUXITE.get())).save(out, id("bayer_digester"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.ALUMINA_PRECIPITATOR.get())
                .pattern("PXP").pattern("PTP").pattern("PTP").define('P', plate).define('T', tank)
                .define('X', AllBlocks.MECHANICAL_MIXER.get())
                .unlockedBy("has_bayer_digester", has(PcBlocks.BAYER_DIGESTER.get())).save(out, id("alumina_precipitator"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.ALUMINA_CALCINER.get())
                .pattern("BPB").pattern("SFS").pattern("BPB").define('B', brick).define('P', plate)
                .define('S', AllBlocks.SHAFT.get()).define('F', Items.BLAST_FURNACE)
                .unlockedBy("has_aluminium_hydroxide", has(PcItems.ALUMINIUM_HYDROXIDE.get())).save(out, id("alumina_calciner"));
    }
}
