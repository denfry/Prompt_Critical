package dev.denfry.promptcritical.mod.isotopes;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.mod.steelworks.SteelworksComponents;
import dev.denfry.promptcritical.sim.metallurgy.Ferroalloy;
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
import net.minecraft.data.recipes.SpecialRecipeBuilder;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.crafting.DataComponentIngredient;

/**
 * Рецепты изотопов (12): канал облучения, капсулы, кобальт из ферросплава, иридий из концентрата сперрилита,
 * дефектоскоп, плёнка, РИТЭГ и изотопные операции по компонентам ({@link IsotopeCraftRecipe}). Диоксид нептуния
 * рецепта не имеет: его даёт стадия извлечения нептуния PUREX (02 §2.8, {@code np_recovery}), которой пока нет.
 */
public final class IsotopeRecipes extends RecipeProvider {
    public IsotopeRecipes(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries);
    }

    public static DataProvider provider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        DataProvider inner = new IsotopeRecipes(output, registries);
        return new DataProvider() {
            @Override
            public CompletableFuture<?> run(CachedOutput cache) {
                return inner.run(cache);
            }

            @Override
            public String getName() {
                return "Recipes: isotopes and RTG";
            }
        };
    }

    private static String id(String name) {
        return PromptCritical.id("isotopes/" + name).toString();
    }

    @Override
    protected void buildRecipes(RecipeOutput out) {
        var graphite = PcBlocks.MODERATOR_BLOCK_GRAPHITE.get();
        var sheet = AllItems.IRON_SHEET.get();
        // канал: графитовый блок со сквозной гильзой и пробкой
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.IRRADIATION_CHANNEL.get())
                .pattern(" S ").pattern("GPG").pattern(" S ").define('S', sheet).define('G', graphite).define('P', AllBlocks.FLUID_PIPE.get())
                .unlockedBy("has_graphite", has(graphite)).save(out, id("irradiation_channel"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcItems.IRRADIATION_CAPSULE.get(), 4)
                .pattern(" S ").pattern("S S").pattern(" S ").define('S', sheet)
                .unlockedBy("has_sheet", has(sheet)).save(out, id("irradiation_capsule"));
        // кобальт — из ферросплава кобальта сталеплавильного цеха (08)
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, PcItems.COBALT_INGOT.get())
                .requires(DataComponentIngredient.of(false, SteelworksComponents.FERROALLOY, Ferroalloy.COBALT.id, PcItems.FERROALLOY.get()))
                .unlockedBy("has_ferroalloy", has(PcItems.FERROALLOY.get())).save(out, id("cobalt_ingot"));
        // иридий — платиноид концентрата сперрилита: в рудах его в десятки раз меньше платины
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, PcItems.IRIDIUM_INGOT.get())
                .requires(PcItems.RAW_SPERRYLITE.get(), 9)
                .unlockedBy("has_sperrylite", has(PcItems.RAW_SPERRYLITE.get())).save(out, id("iridium_ingot"));
        // дефектоскопия
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, PcItems.RADIOGRAPHY_FILM.get(), 3)
                .requires(Items.PAPER, 3).requires(Items.GLOWSTONE_DUST)
                .unlockedBy("has_paper", has(Items.PAPER)).save(out, id("radiography_film"));
        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, PcItems.RADIOGRAPHY_CRANK.get())
                .pattern("SH").pattern("S ").define('S', sheet).define('H', AllBlocks.HAND_CRANK.get())
                .unlockedBy("has_sheet", has(sheet)).save(out, id("radiography_crank"));
        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, PcItems.RADIOGRAPHY_CAMERA.get())
                .pattern("LSL").pattern("LCL").pattern("LLL").define('L', PcBlocks.LEAD_BLOCK.get()).define('S', sheet)
                .define('C', PcItems.RADIOGRAPHY_CRANK.get())
                .unlockedBy("has_crank", has(PcItems.RADIOGRAPHY_CRANK.get())).save(out, id("radiography_camera"));
        // РИТЭГ: корпус с рёбрами, термобатарея (медные коммутационные шины), защитный вкладыш
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.RTG_THERMOELECTRIC.get())
                .pattern("BSB").pattern("CLC").pattern("BSB").define('B', Items.IRON_BARS).define('S', sheet).define('C', Items.COPPER_BLOCK)
                .define('L', PcBlocks.LEAD_BLOCK.get())
                .unlockedBy("has_lead", has(PcBlocks.LEAD_BLOCK.get())).save(out, id("rtg_thermoelectric"));
        SpecialRecipeBuilder.special(IsotopeCraftRecipe::new).save(out, id("isotope_craft"));
    }
}
