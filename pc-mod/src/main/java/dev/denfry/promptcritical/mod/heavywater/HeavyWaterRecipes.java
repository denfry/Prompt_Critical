package dev.denfry.promptcritical.mod.heavywater;

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

/** Рецепты тяжёлой воды: сероводородный завод и колонна доводки. */
public final class HeavyWaterRecipes extends RecipeProvider {
    public HeavyWaterRecipes(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries);
    }

    public static DataProvider provider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        DataProvider inner = new HeavyWaterRecipes(output, registries);
        return new DataProvider() {
            @Override
            public CompletableFuture<?> run(CachedOutput cache) {
                return inner.run(cache);
            }

            @Override
            public String getName() {
                return "Recipes: heavy water";
            }
        };
    }

    private static String id(String name) {
        return PromptCritical.id("heavy_water/" + name).toString();
    }

    @Override
    protected void buildRecipes(RecipeOutput out) {
        var plate = PcItems.STEEL_PLATE.get();
        // GS: две колонны с ситчатыми тарелками (абсорбер), газодувка сероводорода, теплообменники
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.GS_EXCHANGE_TOWER.get())
                .pattern("PAP").pattern("CHC").pattern("PAP").define('P', plate).define('A', PcBlocks.CO2_ABSORBER.get())
                .define('C', PcBlocks.GAS_COMPRESSOR.get()).define('H', AllBlocks.FLUID_TANK.get())
                .unlockedBy("has_co2_absorber", has(PcBlocks.CO2_ABSORBER.get())).save(out, id("gs_exchange_tower"));
        // доводка: ректификационная колонна на вакуум-насосе, куб — бак
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.HEAVY_WATER_COLUMN.get())
                .pattern("PMP").pattern("PVP").pattern("PHP").define('P', plate).define('M', PcBlocks.METHANOL_COLUMN.get())
                .define('V', AllBlocks.MECHANICAL_PUMP.get()).define('H', AllBlocks.FLUID_TANK.get())
                .unlockedBy("has_methanol_column", has(PcBlocks.METHANOL_COLUMN.get())).save(out, id("heavy_water_column"));
    }
}
