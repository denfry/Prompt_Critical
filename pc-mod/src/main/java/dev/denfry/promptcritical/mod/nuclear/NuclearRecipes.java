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
        var valve = AllBlocks.FLUID_VALVE.get();
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
        // топливный цех: линия АДУ (испаритель UF₆, осадитель, печь восстановления), пресс, печь спекания, сварка, стапель
        var zr = PcItems.ZIRCALOY_INGOT.get();
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.UO2_CONVERSION.get())
                .pattern("PTP").pattern("SFS").pattern("PVP").define('P', AllBlocks.FLUID_PIPE.get()).define('T', AllBlocks.FLUID_TANK.get())
                .define('S', sheet).define('F', net.minecraft.world.item.Items.BLAST_FURNACE).define('V', valve)
                .unlockedBy("has_uf6_cylinder", has(PcItems.UF6_CYLINDER_30B.get())).save(out, id("uo2_conversion"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.PELLET_PRESS.get())
                .pattern("SMS").pattern("SPS").pattern("SHS").define('S', sheet).define('M', mechanism)
                .define('P', AllBlocks.MECHANICAL_PRESS.get()).define('H', AllBlocks.SHAFT.get())
                .unlockedBy("has_uo2_powder", has(PcItems.UO2_POWDER.get())).save(out, id("pellet_press"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.SINTERING_FURNACE.get())
                .pattern("RRR").pattern("RFR").pattern("SGS").define('R', PcItems.REFRACTORY_BRICK.get())
                .define('F', net.minecraft.world.item.Items.BLAST_FURNACE).define('S', sheet).define('G', AllBlocks.MECHANICAL_SAW.get())
                .unlockedBy("has_pellet_press", has(PcBlocks.PELLET_PRESS.get())).save(out, id("sintering_furnace"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.ROD_LOADING_STATION.get())
                .pattern("SMS").pattern("WTW").pattern("SVS").define('S', sheet).define('M', mechanism)
                .define('W', PcItems.COPPER_WIRE.get()).define('T', AllBlocks.FLUID_TANK.get()).define('V', valve)
                .unlockedBy("has_cladding_tube", has(PcItems.CLADDING_TUBE.get())).save(out, id("rod_loading_station"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.ASSEMBLY_JIG.get())
                .pattern("SSS").pattern("ZMZ").pattern("SSS").define('S', PcItems.STEEL_BEAM.get()).define('Z', zr)
                .define('M', mechanism).unlockedBy("has_fuel_rod", has(PcItems.FUEL_ROD.get())).save(out, id("assembly_jig"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcItems.SPACER_GRID.get(), 2)
                .pattern("Z Z").pattern(" Z ").pattern("Z Z").define('Z', zr)
                .unlockedBy("has_zircaloy", has(zr)).save(out, id("spacer_grid"));
        // баллоны: стальные сосуды с вентилем
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcItems.UF6_CYLINDER_48Y.get())
                .pattern("PVP").pattern("P P").pattern("PPP").define('P', plate).define('V', valve)
                .unlockedBy("has_steel_plate", has(plate)).save(out, id("uf6_cylinder_48y"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcItems.UF6_CYLINDER_30B.get())
                .pattern("PVP").pattern("P P").pattern(" P ").define('P', plate).define('V', valve)
                .unlockedBy("has_steel_plate", has(plate)).save(out, id("uf6_cylinder_30b"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcItems.UF6_CYLINDER_5A.get())
                .pattern(" V ").pattern("S S").pattern(" S ").define('S', sheet).define('V', valve)
                .unlockedBy("has_iron_sheet", has(sheet)).save(out, id("uf6_cylinder_5a"));
        // чертежи топливного цикла реального масштаба: бумага вокруг машины старой серии
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, dev.denfry.promptcritical.mod.site.SiteRegistry.GAS_CENTRIFUGE_REAL_DRAWING.get()).pattern("PPP")
                .pattern("PMP").pattern("PPP").define('P', net.minecraft.world.item.Items.PAPER).define('M', PcBlocks.GAS_CENTRIFUGE.get())
                .unlockedBy("has_core", has(PcBlocks.GAS_CENTRIFUGE.get())).save(out, id("gas_centrifuge_real_drawing"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, dev.denfry.promptcritical.mod.site.SiteRegistry.CENTRIFUGE_CASCADE_SECTION_REAL_DRAWING.get()).pattern("PPP")
                .pattern("PMP").pattern("PPP").define('P', net.minecraft.world.item.Items.PAPER).define('M', PcBlocks.GAS_CENTRIFUGE.get())
                .unlockedBy("has_core", has(PcBlocks.GAS_CENTRIFUGE.get())).save(out, id("centrifuge_cascade_section_real_drawing"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, dev.denfry.promptcritical.mod.site.SiteRegistry.FLUORINATOR_REAL_DRAWING.get()).pattern("PPP")
                .pattern("PMP").pattern("PPP").define('P', net.minecraft.world.item.Items.PAPER).define('M', PcBlocks.FLUORINATION_REACTOR.get())
                .unlockedBy("has_core", has(PcBlocks.FLUORINATION_REACTOR.get())).save(out, id("fluorinator_real_drawing"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, dev.denfry.promptcritical.mod.site.SiteRegistry.UF6_AUTOCLAVE_REAL_DRAWING.get()).pattern("PPP")
                .pattern("PMP").pattern("PPP").define('P', net.minecraft.world.item.Items.PAPER).define('M', PcItems.UF6_CYLINDER_30B.get())
                .unlockedBy("has_core", has(PcItems.UF6_CYLINDER_30B.get())).save(out, id("uf6_autoclave_real_drawing"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, dev.denfry.promptcritical.mod.site.SiteRegistry.PELLET_PRESS_REAL_DRAWING.get()).pattern("PPP")
                .pattern("PMP").pattern("PPP").define('P', net.minecraft.world.item.Items.PAPER).define('M', PcBlocks.PELLET_PRESS.get())
                .unlockedBy("has_core", has(PcBlocks.PELLET_PRESS.get())).save(out, id("pellet_press_real_drawing"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, dev.denfry.promptcritical.mod.site.SiteRegistry.SINTERING_FURNACE_REAL_DRAWING.get()).pattern("PPP")
                .pattern("PMP").pattern("PPP").define('P', net.minecraft.world.item.Items.PAPER).define('M', PcBlocks.SINTERING_FURNACE.get())
                .unlockedBy("has_core", has(PcBlocks.SINTERING_FURNACE.get())).save(out, id("sintering_furnace_real_drawing"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, dev.denfry.promptcritical.mod.site.SiteRegistry.FUEL_ROD_WELDER_REAL_DRAWING.get()).pattern("PPP")
                .pattern("PMP").pattern("PPP").define('P', net.minecraft.world.item.Items.PAPER).define('M', PcBlocks.ROD_LOADING_STATION.get())
                .unlockedBy("has_core", has(PcBlocks.ROD_LOADING_STATION.get())).save(out, id("fuel_rod_welder_real_drawing"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, dev.denfry.promptcritical.mod.site.SiteRegistry.FUEL_ASSEMBLY_STAND_REAL_DRAWING.get()).pattern("PPP")
                .pattern("PMP").pattern("PPP").define('P', net.minecraft.world.item.Items.PAPER).define('M', PcBlocks.ASSEMBLY_JIG.get())
                .unlockedBy("has_core", has(PcBlocks.ASSEMBLY_JIG.get())).save(out, id("fuel_assembly_stand_real_drawing"));
    }
}
