package dev.denfry.promptcritical.mod.refinery;

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

/** Рецепты подготовки и углубления переработки НПЗ и ГПЗ (09 §9.7.3, §9.9–9.10). */
public final class ConversionRecipes extends RecipeProvider {
    public ConversionRecipes(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries);
    }

    public static DataProvider provider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        DataProvider inner = new ConversionRecipes(output, registries);
        return new DataProvider() {
            @Override
            public CompletableFuture<?> run(CachedOutput cache) {
                return inner.run(cache);
            }

            @Override
            public String getName() {
                return "Recipes: refinery conversion";
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
        // горизонтальный сосуд с электродами на изоляторах, трансформатор на крыше, смесительный клапан
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.ELECTROSTATIC_DESALTER.get())
                .pattern("IWI").pattern("PTP").pattern("SVS").define('I', PcItems.INSULATOR_PIN.get()).define('W', PcItems.COPPER_WIRE.get())
                .define('P', pipe).define('T', AllBlocks.FLUID_TANK.get()).define('S', plate).define('V', AllBlocks.FLUID_VALVE.get())
                .unlockedBy("has_crude_column", has(PcBlocks.CRUDE_COLUMN.get())).save(out, id("electrostatic_desalter"));
    }
}
