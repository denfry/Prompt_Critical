package dev.denfry.promptcritical.mod.electronics;

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
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.crafting.DataComponentIngredient;

/**
 * Рецепты электроники (11 §11.5): электровакуумная лампа A2, медная фольга, пассивные элементы, печатная плата, ЭЛТ,
 * объектив степпера и установки кремниевой цепочки. Подмена рецептов компьютера и монитора CC: Tweaked — рукописные
 * рецепты {@code data/computercraft/recipe/*.json} с условиями {@code neoforge:mod_loaded} и
 * {@code promptcritical:cc_recipe_override}.
 */
public final class ElectronicsRecipes extends RecipeProvider {
    public ElectronicsRecipes(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries);
    }

    public static DataProvider provider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        DataProvider inner = new ElectronicsRecipes(output, registries);
        return new DataProvider() {
            @Override
            public CompletableFuture<?> run(CachedOutput cache) {
                return inner.run(cache);
            }

            @Override
            public String getName() {
                return "Recipes: electronics";
            }
        };
    }

    private static String id(String name) {
        return PromptCritical.id("electronics/" + name).toString();
    }

    @Override
    protected void buildRecipes(RecipeOutput out) {
        var sheet = AllItems.IRON_SHEET.get();
        var wire = PcItems.COPPER_WIRE.get();
        var foil = PcItems.COPPER_FOIL.get();
        // A2: триод — стеклянный баллон, оксидный катод на никелевом керне, никелевая сетка, магниевый газопоглотитель
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, PcItems.VACUUM_TUBE.get(), 2)
                .requires(Items.GLASS_PANE).requires(wire, 2)
                .requires(DataComponentIngredient.of(false, SteelworksComponents.FERROALLOY, Ferroalloy.NICKEL.id, PcItems.FERROALLOY.get()))
                .requires(PcItems.MAGNESIUM_INGOT.get())
                .unlockedBy("has_wire", has(wire)).save(out, id("vacuum_tube"));
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, foil, 2).requires(AllItems.COPPER_SHEET.get())
                .unlockedBy("has_copper_sheet", has(AllItems.COPPER_SHEET.get())).save(out, id("copper_foil"));
        // углеродные резисторы и бумажные конденсаторы
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, PcItems.RESISTOR.get(), 4).requires(wire).requires(Items.COAL).requires(Items.CLAY_BALL)
                .unlockedBy("has_wire", has(wire)).save(out, id("resistor"));
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, PcItems.CAPACITOR.get(), 4).requires(foil, 2).requires(Items.PAPER)
                .unlockedBy("has_foil", has(foil)).save(out, id("capacitor"));
        // плата: гетинакс (бумага на связующем) с медной фольгой, рисунок травлением
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcItems.PRINTED_CIRCUIT_BOARD.get(), 2).pattern("FFF").pattern("PBP")
                .define('F', foil).define('P', Items.PAPER).define('B', Items.SLIME_BALL)
                .unlockedBy("has_foil", has(foil)).save(out, id("printed_circuit_board"));
        // ЭЛТ: электронная пушка на лампе, люминофор, отклоняющие катушки, свинцовое стекло горловины
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcItems.CATHODE_RAY_TUBE.get()).pattern("GPG").pattern("GTG").pattern("WLW")
                .define('G', Items.GLASS).define('P', Items.GLOWSTONE_DUST).define('T', PcItems.VACUUM_TUBE.get()).define('W', wire)
                .define('L', PcBlocks.LEAD_BLOCK.get())
                .unlockedBy("has_vacuum_tube", has(PcItems.VACUUM_TUBE.get())).save(out, id("cathode_ray_tube"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcItems.STEPPER_LENS.get()).pattern("QSQ").pattern("QIQ")
                .define('Q', Items.QUARTZ_BLOCK).define('S', Items.SPYGLASS).define('I', sheet)
                .unlockedBy("has_quartz_block", has(Items.QUARTZ_BLOCK)).save(out, id("stepper_lens"));
        // установки
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.SUBMERGED_ARC_FURNACE.get()).pattern("EEE").pattern("BTB").pattern("BBB")
                .define('E', PcItems.GRAPHITE_ELECTRODE.get()).define('B', Items.BRICKS).define('T', PcBlocks.DISTRIBUTION_TRANSFORMER.get())
                .unlockedBy("has_graphite_electrode", has(PcItems.GRAPHITE_ELECTRODE.get())).save(out, id("submerged_arc_furnace"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.TRICHLOROSILANE_REACTOR.get()).pattern("SPS").pattern("SCS").pattern("SPS")
                .define('S', sheet).define('P', AllBlocks.FLUID_PIPE.get()).define('C', AllBlocks.FLUID_TANK.get())
                .unlockedBy("has_metallurgical_silicon", has(PcItems.METALLURGICAL_SILICON.get())).save(out, id("trichlorosilane_reactor"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.SIEMENS_REACTOR.get()).pattern("SSS").pattern("ETE").pattern("SPS")
                .define('S', sheet).define('E', PcItems.GRAPHITE_ELECTRODE.get()).define('T', PcBlocks.DISTRIBUTION_TRANSFORMER.get())
                .define('P', AllBlocks.FLUID_PIPE.get())
                .unlockedBy("has_metallurgical_silicon", has(PcItems.METALLURGICAL_SILICON.get())).save(out, id("siemens_reactor"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.CZOCHRALSKI_PULLER.get()).pattern("SGS").pattern("SQS").pattern("SHS")
                .define('S', sheet).define('G', Items.GLASS).define('Q', Items.QUARTZ_BLOCK).define('H', AllBlocks.SHAFT.get())
                .unlockedBy("has_polysilicon", has(PcItems.POLYSILICON.get())).save(out, id("czochralski_puller"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.WAFER_SAW.get()).pattern("SDS").pattern("SHS").pattern("SSS")
                .define('S', sheet).define('D', Items.DIAMOND).define('H', AllBlocks.SHAFT.get())
                .unlockedBy("has_silicon_ingot", has(PcItems.SILICON_INGOT.get())).save(out, id("wafer_saw"));
        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, PcBlocks.CLEANROOM_PANEL.get(), 8).pattern("SSS").pattern("SWS").pattern("SSS")
                .define('S', sheet).define('W', Items.WHITE_DYE)
                .unlockedBy("has_sheet", has(sheet)).save(out, id("cleanroom_panel"));
        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, PcBlocks.CLEANROOM_WINDOW.get(), 4).pattern(" G ").pattern("GSG").pattern(" G ")
                .define('G', Items.GLASS).define('S', sheet)
                .unlockedBy("has_sheet", has(sheet)).save(out, id("cleanroom_window"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.HEPA_FILTER.get()).pattern("SSS").pattern("PFP").pattern("SSS")
                .define('S', sheet).define('P', Items.PAPER).define('F', AllBlocks.ENCASED_FAN.get())
                .unlockedBy("has_cleanroom_panel", has(PcBlocks.CLEANROOM_PANEL.get())).save(out, id("hepa_filter"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.CLEANROOM_AIRLOCK.get()).pattern("PSP").pattern("PDP").pattern("PSP")
                .define('P', PcBlocks.CLEANROOM_PANEL.get()).define('S', sheet).define('D', Items.IRON_DOOR)
                .unlockedBy("has_cleanroom_panel", has(PcBlocks.CLEANROOM_PANEL.get())).save(out, id("cleanroom_airlock"));
    }
}
