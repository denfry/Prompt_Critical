package dev.denfry.promptcritical.mod.reactor;

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
import net.minecraft.data.recipes.SimpleCookingRecipeBuilder;
import net.minecraft.data.recipes.SingleItemRecipeBuilder;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * Рецепты графитового котла T0. Крафт и печи — здесь; дробление, помол и смешивание Create — JSON в
 * {@code data/promptcritical/recipe/reactor/}. Графит — из графитированных электродов печи Ачесона, каналы
 * фрезеруются из графитового блока камнерезом (и пилой Create).
 */
public final class ReactorRecipes extends RecipeProvider {
    public ReactorRecipes(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries);
    }

    public static DataProvider provider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        DataProvider inner = new ReactorRecipes(output, registries);
        return new DataProvider() {
            @Override
            public CompletableFuture<?> run(CachedOutput cache) {
                return inner.run(cache);
            }

            @Override
            public String getName() {
                return "Recipes: graphite pile";
            }
        };
    }

    private static String id(String name) {
        return PromptCritical.id("reactor/" + name).toString();
    }

    @Override
    protected void buildRecipes(RecipeOutput out) {
        var plate = PcItems.STEEL_PLATE.get();
        var rod = PcItems.STEEL_ROD.get();
        var wire = PcItems.COPPER_WIRE.get();
        var tube = AllItems.ELECTRON_TUBE.get();
        var shaft = AllBlocks.SHAFT.get();
        var graphite = PcBlocks.MODERATOR_BLOCK_GRAPHITE.get();
        var hasElectrode = has(PcItems.GRAPHITE_ELECTRODE.get());

        // уран: прокалка и восстановление жёлтого кека водородом из коксового газа сведены к доменной печи
        SimpleCookingRecipeBuilder.blasting(Ingredient.of(PcItems.YELLOWCAKE.get()), RecipeCategory.MISC, PcItems.UO2_POWDER.get(),
                0.2f, 200).unlockedBy("has_yellowcake", has(PcItems.YELLOWCAKE.get())).save(out, id("uo2_powder"));

        // кладка
        ShapelessRecipeBuilder.shapeless(RecipeCategory.BUILDING_BLOCKS, graphite)
                .requires(PcItems.GRAPHITE_ELECTRODE.get(), 2)
                .unlockedBy("has_graphite_electrode", hasElectrode).save(out, id("moderator_block_graphite"));
        SingleItemRecipeBuilder.stonecutting(Ingredient.of(graphite), RecipeCategory.BUILDING_BLOCKS, PcBlocks.FUEL_CHANNEL.get())
                .unlockedBy("has_graphite", has(graphite)).save(out, id("fuel_channel_cutting"));
        SingleItemRecipeBuilder.stonecutting(Ingredient.of(graphite), RecipeCategory.BUILDING_BLOCKS,
                PcBlocks.CONTROL_ROD_CHANNEL.get()).unlockedBy("has_graphite", has(graphite)).save(out, id("control_rod_channel_cutting"));

        // источник: радий из остатков переработки руды, бериллий — в капсуле
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, PcBlocks.STARTUP_SOURCE.get())
                .requires(PcItems.GROUND_URANIUM_ORE.get(), 8).requires(plate)
                .unlockedBy("has_ground_uranium_ore", has(PcItems.GROUND_URANIUM_ORE.get())).save(out, id("startup_source"));

        // стержни: кадмий — попутчик цинка, сфалерит
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.CONTROL_ROD_DRIVE.get())
                .pattern("PSP").pattern("RXR").pattern("PWP")
                .define('P', plate).define('S', shaft).define('R', rod).define('X', PcItems.RAW_SPHALERITE.get()).define('W', wire)
                .unlockedBy("has_graphite", has(graphite)).save(out, id("control_rod_drive"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.ZIP_ROD_WINCH.get())
                .pattern("PSP").pattern("RXR").pattern("PCP")
                .define('P', plate).define('S', shaft).define('R', rod).define('X', PcItems.RAW_SPHALERITE.get())
                .define('C', PcItems.WIRE_ROPE.get())
                .unlockedBy("has_graphite", has(graphite)).save(out, id("zip_rod_winch"));

        // приборы и пульт
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.NEUTRON_DETECTOR.get())
                .pattern("PTP").pattern("PGP").pattern("PWP")
                .define('P', plate).define('T', tube).define('G', Items.GLASS).define('W', wire)
                .unlockedBy("has_graphite", has(graphite)).save(out, id("neutron_detector"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.REACTOR_CONTROLLER.get())
                .pattern("PTP").pattern("TCT").pattern("PWP")
                .define('P', plate).define('T', tube).define('C', AllBlocks.ANDESITE_CASING.get()).define('W', wire)
                .unlockedBy("has_graphite", has(graphite)).save(out, id("reactor_controller"));
        ShapelessRecipeBuilder.shapeless(RecipeCategory.REDSTONE, PcBlocks.SCRAM_BUTTON.get())
                .requires(plate).requires(Items.RED_DYE).requires(Items.STONE_BUTTON)
                .unlockedBy("has_controller", has(PcBlocks.REACTOR_CONTROLLER.get())).save(out, id("scram_button"));

        // печь Ачесона: засыпка коксовой мелочи между токоподводами
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.ACHESON_FURNACE.get())
                .pattern("BWB").pattern("KKK").pattern("BWB")
                .define('B', PcItems.REFRACTORY_BRICK.get()).define('W', wire).define('K', PcItems.COKE_BREEZE.get())
                .unlockedBy("has_green_electrode", has(PcItems.GREEN_ELECTRODE.get())).save(out, id("acheson_furnace"));
        // чертёж: машина реального масштаба строится на стройплощадке по массе прототипа (SitePlans)
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, dev.denfry.promptcritical.mod.site.SiteRegistry.ACHESON_FURNACE_REAL_DRAWING.get())
                .pattern("PPP").pattern(" M ").define('P', net.minecraft.world.item.Items.PAPER).define('M', PcBlocks.ACHESON_FURNACE.get())
                .unlockedBy("has_acheson_furnace", has(PcBlocks.ACHESON_FURNACE.get())).save(out, id("acheson_furnace_real_drawing"));
    }
}
