package dev.denfry.promptcritical.mod.drilling;

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

/**
 * Рецепты нефти и газа I2: сейсморазведка и буровая. Сталь — листы и блоки, гидравлика — трубы и вентили Create,
 * приводы — валы и шестерни. Барит из пластового барита мелют жернова Create (рецепт {@code create:milling} в
 * ресурсах).
 */
public final class DrillingRecipes extends RecipeProvider {
    public DrillingRecipes(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries);
    }

    public static DataProvider provider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        DataProvider inner = new DrillingRecipes(output, registries);
        return new DataProvider() {
            @Override
            public CompletableFuture<?> run(CachedOutput cache) {
                return inner.run(cache);
            }

            @Override
            public String getName() {
                return "Recipes: oil and gas drilling";
            }
        };
    }

    private static String id(String name) {
        return PromptCritical.id("drilling/" + name).toString();
    }

    /**
     * Чертёж машины реального масштаба: бумага, старый блок как образец и железные листы; саму машину строят на
     * стройплощадке по массе прототипа ({@link dev.denfry.promptcritical.mod.site.SitePlans}).
     */
    public static void drawing(RecipeOutput out, net.minecraft.world.item.Item drawing, net.minecraft.world.level.ItemLike sample, String name) {
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, drawing)
                .pattern("PPP").pattern("ISI").define('P', Items.PAPER).define('I', AllItems.IRON_SHEET.get()).define('S', sample)
                .unlockedBy("has_sample", has(sample)).save(out, id(name));
    }

    @Override
    protected void buildRecipes(RecipeOutput out) {
        var sheet = AllItems.IRON_SHEET.get();
        // сейсморазведка: заряд — порох в бумажной гильзе, сейсмоприёмник — катушка на пружине в корпусе с шипом
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, PcItems.SEISMIC_CHARGE.get(), 2).requires(Items.GUNPOWDER, 2)
                .requires(Items.PAPER).unlockedBy("has_gunpowder", has(Items.GUNPOWDER)).save(out, id("seismic_charge"));
        ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, PcBlocks.GEOPHONE.get(), 4)
                .pattern("C").pattern("R").pattern("I").define('C', Items.COPPER_INGOT).define('R', Items.REDSTONE)
                .define('I', Items.IRON_INGOT).unlockedBy("has_copper", has(Items.COPPER_INGOT)).save(out, id("geophone"));
        ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, PcBlocks.SEISMIC_RECORDER.get())
                .pattern("GPG").pattern("BTB").pattern("SRS").define('G', Items.GLASS_PANE).define('P', Items.PAPER)
                .define('B', AllItems.BRASS_INGOT.get()).define('T', AllItems.ELECTRON_TUBE.get()).define('S', sheet)
                .define('R', Items.REDSTONE).unlockedBy("has_electron_tube", has(AllItems.ELECTRON_TUBE.get())).save(out, id("seismic_recorder"));
        // буровая
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.DERRICK_SEGMENT.get(), 2)
                .pattern("I I").pattern("BIB").pattern("I I").define('I', Items.IRON_INGOT).define('B', Items.IRON_BARS)
                .unlockedBy("has_iron", has(Items.IRON_INGOT)).save(out, id("derrick_segment"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.ROTARY_TABLE.get())
                .pattern("SCS").pattern("IAI").pattern("SHS").define('S', sheet).define('C', AllBlocks.LARGE_COGWHEEL.get())
                .define('I', Items.IRON_BLOCK).define('A', AllBlocks.ANDESITE_CASING.get()).define('H', AllBlocks.SHAFT.get())
                .unlockedBy("has_large_cogwheel", has(AllBlocks.LARGE_COGWHEEL.get())).save(out, id("rotary_table"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.DRAWWORKS.get())
                .pattern("SCS").pattern("HIH").pattern("AGA").define('S', sheet).define('C', Items.CHAIN).define('H', AllBlocks.SHAFT.get())
                .define('I', Items.IRON_BLOCK).define('A', AllBlocks.ANDESITE_CASING.get()).define('G', AllBlocks.GEARBOX.get())
                .unlockedBy("has_gearbox", has(AllBlocks.GEARBOX.get())).save(out, id("drawworks"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.MUD_PUMP.get())
                .pattern("PVP").pattern("IMI").pattern("CAC").define('P', AllBlocks.FLUID_PIPE.get()).define('V', AllBlocks.FLUID_VALVE.get())
                .define('I', Items.IRON_BLOCK).define('M', AllBlocks.MECHANICAL_PUMP.get()).define('C', AllBlocks.COGWHEEL.get())
                .define('A', AllBlocks.ANDESITE_CASING.get()).unlockedBy("has_mechanical_pump", has(AllBlocks.MECHANICAL_PUMP.get()))
                .save(out, id("mud_pump"));
        // чертежи буровой реального масштаба: строятся на стройплощадке по массе прототипа (SitePlans), старый блок — образец
        drawing(out, dev.denfry.promptcritical.mod.site.SiteRegistry.DERRICK_SEGMENT_REAL_DRAWING.get(), PcBlocks.DERRICK_SEGMENT.get(), "derrick_segment_real_drawing");
        drawing(out, dev.denfry.promptcritical.mod.site.SiteRegistry.ROTARY_TABLE_REAL_DRAWING.get(), PcBlocks.ROTARY_TABLE.get(), "rotary_table_real_drawing");
        drawing(out, dev.denfry.promptcritical.mod.site.SiteRegistry.DRAWWORKS_REAL_DRAWING.get(), PcBlocks.DRAWWORKS.get(), "drawworks_real_drawing");
        drawing(out, dev.denfry.promptcritical.mod.site.SiteRegistry.MUD_PUMP_REAL_DRAWING.get(), PcBlocks.MUD_PUMP.get(), "mud_pump_real_drawing");
        // кольцевой превентор: стальной корпус и резиновое уплотнение (сухая ламинария вместо резины)
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.BOP_ANNULAR.get())
                .pattern("ISI").pattern("PRP").pattern("ISI").define('I', Items.IRON_BLOCK).define('S', sheet)
                .define('P', AllBlocks.FLUID_PIPE.get()).define('R', Items.DRIED_KELP_BLOCK)
                .unlockedBy("has_fluid_pipe", has(AllBlocks.FLUID_PIPE.get())).save(out, id("bop_annular"));
        // каротаж и керн: подъёмник — лебёдка с кабелем на шасси, зонды — трубки с датчиком, керноотборник — труба с коронкой
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.LOGGING_UNIT.get())
                .pattern("SCS").pattern("RTR").pattern("AAA").define('S', sheet).define('C', Items.CHAIN).define('R', Items.REDSTONE)
                .define('T', AllItems.ELECTRON_TUBE.get()).define('A', AllBlocks.ANDESITE_CASING.get())
                .unlockedBy("has_electron_tube", has(AllItems.ELECTRON_TUBE.get())).save(out, id("logging_unit"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.CORE_ANALYSIS_STATION.get())
                .pattern("GBG").pattern("SCS").define('G', Items.GLASS_BOTTLE).define('B', Items.BREWING_STAND).define('S', sheet)
                .define('C', Items.CRAFTING_TABLE).unlockedBy("has_brewing_stand", has(Items.BREWING_STAND)).save(out, id("core_analysis_station"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcItems.GAMMA_LOGGING_SONDE.get())
                .pattern("I").pattern("G").pattern("I").define('I', Items.IRON_INGOT).define('G', Items.GLASS)
                .unlockedBy("has_logging_unit", has(PcBlocks.LOGGING_UNIT.get())).save(out, id("gamma_logging_sonde"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcItems.NEUTRON_LOGGING_SONDE.get())
                .pattern("I").pattern("B").pattern("I").define('I', Items.IRON_INGOT).define('B', Items.LEAD)
                .unlockedBy("has_logging_unit", has(PcBlocks.LOGGING_UNIT.get())).save(out, id("neutron_logging_sonde"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcItems.RESISTIVITY_LOGGING_SONDE.get())
                .pattern("I").pattern("C").pattern("I").define('I', Items.IRON_INGOT).define('C', Items.COPPER_INGOT)
                .unlockedBy("has_logging_unit", has(PcBlocks.LOGGING_UNIT.get())).save(out, id("resistivity_logging_sonde"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcItems.CORE_BARREL.get())
                .pattern("I").pattern("I").pattern("D").define('I', Items.IRON_INGOT).define('D', Items.DIAMOND)
                .unlockedBy("has_drillers_console", has(PcBlocks.DRILLERS_CONSOLE.get())).save(out, id("core_barrel"));
        // плашечный превентор: корпус и два гидроцилиндра плашек; аккумулятор — баллоны (баки) с насосом и рычагами
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.BOP_RAM.get())
                .pattern("ISI").pattern("TBT").pattern("ISI").define('I', Items.IRON_BLOCK).define('S', sheet)
                .define('T', Items.PISTON).define('B', Items.IRON_BARS)
                .unlockedBy("has_bop_annular", has(PcBlocks.BOP_ANNULAR.get())).save(out, id("bop_ram"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.BOP_ACCUMULATOR.get())
                .pattern("LLL").pattern("TMT").pattern("SPS").define('L', Items.LEVER).define('T', AllBlocks.FLUID_TANK.get())
                .define('M', AllBlocks.MECHANICAL_PUMP.get()).define('S', sheet).define('P', AllBlocks.FLUID_PIPE.get())
                .unlockedBy("has_bop_annular", has(PcBlocks.BOP_ANNULAR.get())).save(out, id("bop_accumulator"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.CHOKE_MANIFOLD.get())
                .pattern("VPV").pattern("PGP").pattern("VPV").define('V', AllBlocks.FLUID_VALVE.get()).define('P', AllBlocks.FLUID_PIPE.get())
                .define('G', Items.CLOCK).unlockedBy("has_fluid_valve", has(AllBlocks.FLUID_VALVE.get())).save(out, id("choke_manifold"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.MUD_TANK.get(), 2)
                .pattern("S S").pattern("S S").pattern("SSS").define('S', sheet)
                .unlockedBy("has_iron_sheet", has(sheet)).save(out, id("mud_tank"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.MUD_MIXING_HOPPER.get())
                .pattern("S S").pattern(" H ").pattern("PPP").define('S', sheet).define('H', Items.HOPPER)
                .define('P', AllBlocks.FLUID_PIPE.get()).unlockedBy("has_hopper", has(Items.HOPPER)).save(out, id("mud_mixing_hopper"));
        ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, PcBlocks.DRILLERS_CONSOLE.get())
                .pattern("GCG").pattern("BTB").pattern("SAS").define('G', Items.GLASS_PANE).define('C', Items.CLOCK)
                .define('B', AllItems.BRASS_INGOT.get()).define('T', AllItems.ELECTRON_TUBE.get()).define('S', sheet)
                .define('A', AllBlocks.ANDESITE_CASING.get()).unlockedBy("has_electron_tube", has(AllItems.ELECTRON_TUBE.get()))
                .save(out, id("drillers_console"));
    }
}
