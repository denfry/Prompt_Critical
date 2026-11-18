package dev.denfry.promptcritical.mod.acoustics;

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
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Items;

/** Рецепты акустики: виброанализатор, вибродатчик, наушники, комплект подшипника, грузы и центровка. */
public final class AcousticsRecipes extends RecipeProvider {
    public AcousticsRecipes(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries);
    }

    public static DataProvider provider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        DataProvider inner = new AcousticsRecipes(output, registries);
        return new DataProvider() {
            @Override
            public CompletableFuture<?> run(CachedOutput cache) {
                return inner.run(cache);
            }

            @Override
            public String getName() {
                return "Recipes: acoustics";
            }
        };
    }

    private static String id(String name) {
        return PromptCritical.id("acoustics/" + name).toString();
    }

    @Override
    protected void buildRecipes(RecipeOutput out) {
        // щуп с пьезодатчиком на стальном жале, усилитель-интегратор на лампе, латунный корпус
        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, PcItems.VIBROMETER.get())
                .pattern(" N ").pattern("BEB").pattern("BRB").define('N', Items.IRON_NUGGET).define('B', AllItems.BRASS_SHEET.get())
                .define('E', AllItems.ELECTRON_TUBE.get()).define('R', Items.REDSTONE)
                .unlockedBy("has_electron_tube", has(AllItems.ELECTRON_TUBE.get())).save(out, id("vibrometer"));
        // стационарный датчик: тот же преобразователь на медной магнитной площадке
        ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, PcBlocks.INSTRUMENT_VIBRATION.get())
                .pattern("BEB").pattern("CRC").define('B', AllItems.BRASS_SHEET.get()).define('E', AllItems.ELECTRON_TUBE.get())
                .define('C', Items.COPPER_INGOT).define('R', Items.REDSTONE)
                .unlockedBy("has_electron_tube", has(AllItems.ELECTRON_TUBE.get())).save(out, id("instrument_vibration"));
        // оголовье из стальной полосы и чашки с шерстяным уплотнением
        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, PcItems.HEARING_PROTECTION.get())
                .pattern("III").pattern("W W").define('I', Items.IRON_INGOT).define('W', ItemTags.WOOL)
                .unlockedBy("has_wool", has(ItemTags.WOOL)).save(out, id("hearing_protection"));
        // кольца из листа, шарики, консистентная смазка
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, PcItems.BEARING_KIT.get())
                .requires(AllItems.IRON_SHEET.get(), 2).requires(Items.IRON_NUGGET, 4).requires(Items.SLIME_BALL)
                .unlockedBy("has_iron_sheet", has(AllItems.IRON_SHEET.get())).save(out, id("bearing_kit"));
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, PcItems.BALANCING_WEIGHTS.get(), 4)
                .requires(Items.IRON_NUGGET, 8)
                .unlockedBy("has_iron_nugget", has(Items.IRON_NUGGET)).save(out, id("balancing_weights"));
        // индикаторная головка на кронштейнах
        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, PcItems.ALIGNMENT_TOOL.get())
                .pattern(" P ").pattern("I I").define('P', AllItems.PRECISION_MECHANISM.get()).define('I', Items.IRON_INGOT)
                .unlockedBy("has_precision_mechanism", has(AllItems.PRECISION_MECHANISM.get())).save(out, id("alignment_tool"));
    }
}
