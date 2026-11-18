package dev.denfry.promptcritical.mod.nuclear;

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

/** Рецепты обогащения: секции центрифуг, пульт каскада и баллоны UF₆. */
public final class NuclearRecipes extends RecipeProvider {
    public NuclearRecipes(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries);
    }

    public static DataProvider provider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        DataProvider inner = new NuclearRecipes(output, registries);
        return new DataProvider() {
            @Override
            public CompletableFuture<?> run(CachedOutput cache) {
                return inner.run(cache);
            }

            @Override
            public String getName() {
                return "Recipes: nuclear";
            }
        };
    }

    private static String id(String name) {
        return PromptCritical.id("nuclear/" + name).toString();
    }

    @Override
    protected void buildRecipes(RecipeOutput out) {
        var sheet = AllItems.IRON_SHEET.get();
        var plate = PcItems.STEEL_PLATE.get();
        var mechanism = AllItems.PRECISION_MECHANISM.get();
        // секция центрифуг: вакуумный кожух, ротор на игольчатой опоре и магнитном подвесе, двигатель
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.GAS_CENTRIFUGE.get())
                .pattern("SMS").pattern("SRS").pattern("SWS").define('S', sheet).define('M', mechanism)
                .define('R', PcItems.STEEL_ROD.get()).define('W', PcItems.COPPER_WIRE.get())
                .unlockedBy("has_precision_mechanism", has(mechanism)).save(out, id("gas_centrifuge"));
        // пульт: преобразователь частоты, коллекторы питания, отбора и отвала
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.CENTRIFUGE_CASCADE.get())
                .pattern("WMW").pattern("PTP").pattern("WHW").define('W', PcItems.COPPER_WIRE.get()).define('M', mechanism)
                .define('P', AllBlocks.FLUID_PIPE.get()).define('T', AllBlocks.FLUID_TANK.get()).define('H', AllBlocks.SHAFT.get())
                .unlockedBy("has_gas_centrifuge", has(PcBlocks.GAS_CENTRIFUGE.get())).save(out, id("centrifuge_cascade"));
        // баллоны: стальные сосуды с вентилем
        var valve = AllBlocks.FLUID_VALVE.get();
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcItems.UF6_CYLINDER_48Y.get())
                .pattern("PVP").pattern("P P").pattern("PPP").define('P', plate).define('V', valve)
                .unlockedBy("has_steel_plate", has(plate)).save(out, id("uf6_cylinder_48y"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcItems.UF6_CYLINDER_30B.get())
                .pattern("PVP").pattern("P P").pattern(" P ").define('P', plate).define('V', valve)
                .unlockedBy("has_steel_plate", has(plate)).save(out, id("uf6_cylinder_30b"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcItems.UF6_CYLINDER_5A.get())
                .pattern(" V ").pattern("S S").pattern(" S ").define('S', sheet).define('V', valve)
                .unlockedBy("has_iron_sheet", has(sheet)).save(out, id("uf6_cylinder_5a"));
    }
}
