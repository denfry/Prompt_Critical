package dev.denfry.promptcritical.mod.ops;

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
import net.minecraft.data.recipes.SpecialRecipeBuilder;
import net.minecraft.world.item.Items;

/**
 * Рецепты эксплуатации и водоподготовки (13 §13.6.5–13.6.7, 08 §8.12): инструменты среза, расходники, щит
 * обслуживания, опрессовочный насос, самописец, блоки водоподготовки; вкладыш и колесо — из отливки
 * ({@link CastingPartRecipe}). Технический жир и соль — смешиванием Create с нагревом (статические рецепты).
 */
public final class OpsRecipes extends RecipeProvider {
    public OpsRecipes(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries);
    }

    public static DataProvider provider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        DataProvider inner = new OpsRecipes(output, registries);
        return new DataProvider() {
            @Override
            public CompletableFuture<?> run(CachedOutput cache) {
                return inner.run(cache);
            }

            @Override
            public String getName() {
                return "Recipes: operations and water treatment";
            }
        };
    }

    private static String id(String name) {
        return PromptCritical.id("ops/" + name).toString();
    }

    @Override
    protected void buildRecipes(RecipeOutput out) {
        var sheet = AllItems.IRON_SHEET.get();
        var brass = AllItems.BRASS_INGOT.get();
        var pipe = AllBlocks.FLUID_PIPE.get();
        var hasSheet = has(sheet);

        // LOTO (13 §13.1.2): замок, бирка, накладка, указатель напряжения
        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, PcItems.LOCKOUT_PADLOCK.get(), 2)
                .pattern(" I ").pattern("IRI").pattern("III").define('I', Items.IRON_INGOT).define('R', Items.RED_DYE)
                .unlockedBy("has_iron", has(Items.IRON_INGOT)).save(out, id("lockout_padlock"));
        ShapelessRecipeBuilder.shapeless(RecipeCategory.TOOLS, PcItems.LOCKOUT_TAG.get(), 4)
                .requires(Items.PAPER).requires(Items.RED_DYE).requires(Items.STRING)
                .unlockedBy("has_paper", has(Items.PAPER)).save(out, id("lockout_tag"));
        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, PcItems.LOCKOUT_HASP.get())
                .pattern("I I").pattern("III").define('I', Items.IRON_INGOT)
                .unlockedBy("has_iron", has(Items.IRON_INGOT)).save(out, id("lockout_hasp"));
        ShapelessRecipeBuilder.shapeless(RecipeCategory.TOOLS, PcItems.VOLTAGE_TESTER.get())
                .requires(Items.REDSTONE_TORCH).requires(Items.COPPER_INGOT).requires(Items.STICK).requires(Items.YELLOW_DYE)
                .unlockedBy("has_copper", has(Items.COPPER_INGOT)).save(out, id("voltage_tester"));

        // наряды-допуски 13 §13.2
        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, PcItems.PORTABLE_GAS_DETECTOR.get())
                .pattern("YGY").pattern("YRY").pattern("YCY").define('Y', Items.YELLOW_DYE).define('G', Items.GLASS_PANE)
                .define('R', Items.REDSTONE).define('C', Items.COPPER_INGOT)
                .unlockedBy("has_copper", has(Items.COPPER_INGOT)).save(out, id("portable_gas_detector"));
        ShapelessRecipeBuilder.shapeless(RecipeCategory.TOOLS, PcItems.CALIBRATION_GAS.get(), 4)
                .requires(Items.IRON_INGOT).requires(Items.GLASS_BOTTLE).requires(Items.CHARCOAL)
                .unlockedBy("has_iron", has(Items.IRON_INGOT)).save(out, id("calibration_gas"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.PERMIT_BOARD.get())
                .pattern("PPP").pattern("WWW").define('P', Items.PAPER).define('W', net.minecraft.tags.ItemTags.PLANKS)
                .unlockedBy("has_paper", has(Items.PAPER)).save(out, id("permit_board"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.LINE_BLIND.get())
                .pattern("I I").pattern("PRP").pattern("I I").define('I', Items.IRON_INGOT).define('P', pipe).define('R', Items.RED_DYE)
                .unlockedBy("has_iron", has(Items.IRON_INGOT)).save(out, id("line_blind"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.LOCKOUT_BOX.get())
                .pattern("III").pattern("IGI").pattern("IRI").define('I', Items.IRON_INGOT).define('G', Items.GLASS_PANE)
                .define('R', Items.RED_DYE).unlockedBy("has_iron", has(Items.IRON_INGOT)).save(out, id("lockout_box"));
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, PcBlocks.MECHANICAL_DISCONNECT.get())
                .requires(AllBlocks.CLUTCH.get()).requires(Items.LEVER).requires(Items.IRON_INGOT)
                .unlockedBy("has_clutch", has(AllBlocks.CLUTCH.get())).save(out, id("mechanical_disconnect"));

        // пожаротушение 13 §13.5
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.SMOKE_DETECTOR.get()).pattern("III").pattern("GRG")
                .define('I', Items.IRON_INGOT).define('G', Items.GLASS_PANE).define('R', Items.REDSTONE)
                .unlockedBy("has_redstone", has(Items.REDSTONE)).save(out, id("smoke_detector"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.HEAT_DETECTOR.get()).pattern("III").pattern("CRC")
                .define('I', Items.IRON_INGOT).define('C', Items.COPPER_INGOT).define('R', Items.REDSTONE)
                .unlockedBy("has_redstone", has(Items.REDSTONE)).save(out, id("heat_detector"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.FLAME_DETECTOR.get()).pattern("IGI").pattern("IRI")
                .define('I', Items.IRON_INGOT).define('G', Items.TINTED_GLASS).define('R', Items.REDSTONE)
                .unlockedBy("has_redstone", has(Items.REDSTONE)).save(out, id("flame_detector"));
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, PcBlocks.MANUAL_CALL_POINT.get())
                .requires(Items.STONE_BUTTON).requires(Items.RED_DYE).requires(Items.GLASS_PANE).requires(Items.IRON_INGOT)
                .unlockedBy("has_redstone", has(Items.REDSTONE)).save(out, id("manual_call_point"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.FIRE_ALARM_PANEL.get()).pattern("IRI").pattern("RBR").pattern("IRI")
                .define('I', Items.IRON_INGOT).define('R', Items.REDSTONE).define('B', Items.BELL)
                .unlockedBy("has_redstone", has(Items.REDSTONE)).save(out, id("fire_alarm_panel"));
        // ГГС 13 §13.3.4: пульт — микрофон и усилитель на плате, громкоговоритель — рупор с катушкой из медного провода
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.INTERCOM_STATION.get()).pattern("IWI").pattern("RBR").pattern("IRI")
                .define('I', Items.IRON_INGOT).define('R', Items.REDSTONE).define('B', Items.NOTE_BLOCK)
                .define('W', dev.denfry.promptcritical.mod.registry.PcItems.COPPER_WIRE.get())
                .unlockedBy("has_redstone", has(Items.REDSTONE)).save(out, id("intercom_station"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.PLANT_LOUDSPEAKER.get(), 2).pattern(" II").pattern("WNI").pattern(" II")
                .define('I', Items.IRON_INGOT).define('N', Items.NOTE_BLOCK)
                .define('W', dev.denfry.promptcritical.mod.registry.PcItems.COPPER_WIRE.get())
                .unlockedBy("has_redstone", has(Items.REDSTONE)).save(out, id("plant_loudspeaker"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.SPRINKLER_HEAD.get(), 2).pattern(" P ").pattern("CGC")
                .define('P', pipe).define('C', Items.COPPER_INGOT).define('G', Items.GLASS_PANE)
                .unlockedBy("has_pipe", has(pipe)).save(out, id("sprinkler_head"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.DELUGE_NOZZLE.get(), 2).pattern(" P ").pattern("C C")
                .define('P', pipe).define('C', Items.COPPER_INGOT)
                .unlockedBy("has_pipe", has(pipe)).save(out, id("deluge_nozzle"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.GAS_SUPPRESSION_CYLINDER.get()).pattern(" V ").pattern("ITI").pattern("ITI")
                .define('V', AllBlocks.FLUID_VALVE.get()).define('I', Items.IRON_INGOT).define('T', AllBlocks.FLUID_TANK.get())
                .unlockedBy("has_iron", has(Items.IRON_INGOT)).save(out, id("gas_suppression_cylinder"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.FIRE_MONITOR.get()).pattern("PPI").pattern(" V ").pattern("III")
                .define('P', pipe).define('I', Items.IRON_INGOT).define('V', AllBlocks.FLUID_VALVE.get())
                .unlockedBy("has_pipe", has(pipe)).save(out, id("fire_monitor"));
        // противопожарный водопровод 13 §13.5.3: насос — крыльчатка на валу двигателя, гидрант и камера — арматура и труба
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.FIRE_WATER_PUMP.get()).pattern("IRI").pattern("PMP").pattern("III")
                .define('I', Items.IRON_INGOT).define('R', Items.RED_DYE).define('P', pipe).define('M', AllBlocks.MECHANICAL_PUMP.get())
                .unlockedBy("has_pipe", has(pipe)).save(out, id("fire_water_pump"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.FIRE_HYDRANT.get()).pattern(" R ").pattern("VPV").pattern(" I ")
                .define('I', Items.IRON_INGOT).define('R', Items.RED_DYE).define('P', pipe).define('V', AllBlocks.FLUID_VALVE.get())
                .unlockedBy("has_pipe", has(pipe)).save(out, id("fire_hydrant"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.FOAM_PROPORTIONER.get()).pattern("IRI").pattern("ITI").pattern("PVP")
                .define('I', Items.IRON_INGOT).define('R', Items.RED_DYE).define('T', AllBlocks.FLUID_TANK.get()).define('P', pipe)
                .define('V', AllBlocks.FLUID_VALVE.get()).unlockedBy("has_pipe", has(pipe)).save(out, id("foam_proportioner"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.FOAM_CHAMBER.get()).pattern("IRI").pattern("P I").pattern("III")
                .define('I', Items.IRON_INGOT).define('R', Items.RED_DYE).define('P', pipe)
                .unlockedBy("has_pipe", has(pipe)).save(out, id("foam_chamber"));
        ShapelessRecipeBuilder.shapeless(RecipeCategory.TOOLS, PcItems.FIRE_EXTINGUISHER.get())
                .requires(Items.IRON_INGOT).requires(Items.IRON_INGOT).requires(Items.RED_DYE).requires(Items.BONE_MEAL)
                .unlockedBy("has_iron", has(Items.IRON_INGOT)).save(out, id("fire_extinguisher"));
        ShapelessRecipeBuilder.shapeless(RecipeCategory.TOOLS, PcItems.FIRE_EXTINGUISHER_WATER.get())
                .requires(Items.IRON_INGOT).requires(Items.IRON_INGOT).requires(Items.RED_DYE).requires(Items.WATER_BUCKET)
                .unlockedBy("has_iron", has(Items.IRON_INGOT)).save(out, id("fire_extinguisher_water"));
        ShapelessRecipeBuilder.shapeless(RecipeCategory.TOOLS, PcItems.FIRE_EXTINGUISHER_FOAM.get())
                .requires(Items.IRON_INGOT).requires(Items.IRON_INGOT).requires(Items.RED_DYE).requires(Items.WATER_BUCKET).requires(Items.SLIME_BALL)
                .unlockedBy("has_iron", has(Items.IRON_INGOT)).save(out, id("fire_extinguisher_foam"));
        ShapelessRecipeBuilder.shapeless(RecipeCategory.TOOLS, PcItems.FIRE_EXTINGUISHER_CO2.get())
                .requires(Items.IRON_INGOT).requires(Items.IRON_INGOT).requires(Items.IRON_INGOT).requires(Items.BLACK_DYE).requires(Items.CHARCOAL)
                .unlockedBy("has_iron", has(Items.IRON_INGOT)).save(out, id("fire_extinguisher_co2"));
        ShapelessRecipeBuilder.shapeless(RecipeCategory.TOOLS, PcItems.FIRE_EXTINGUISHER_METAL.get())
                .requires(Items.IRON_INGOT).requires(Items.IRON_INGOT).requires(Items.YELLOW_DYE).requires(Items.SAND).requires(Items.SAND)
                .unlockedBy("has_iron", has(Items.IRON_INGOT)).save(out, id("fire_extinguisher_metal"));

        // инструменты
        ShapelessRecipeBuilder.shapeless(RecipeCategory.TOOLS, PcItems.MECHANIC_STETHOSCOPE.get())
                .requires(Items.COPPER_INGOT).requires(Items.IRON_INGOT).requires(Items.STRING)
                .unlockedBy("has_copper", has(Items.COPPER_INGOT)).save(out, id("mechanic_stethoscope"));
        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, PcItems.TORQUE_WRENCH.get())
                .pattern(" I").pattern("IK").pattern("I ").define('I', Items.IRON_INGOT).define('K', Items.COMPARATOR)
                .unlockedBy("has_iron", has(Items.IRON_INGOT)).save(out, id("torque_wrench"));
        ShapelessRecipeBuilder.shapeless(RecipeCategory.TOOLS, PcItems.TEST_PRESSURE_GAUGE.get())
                .requires(Items.CLOCK).requires(Items.COPPER_INGOT).requires(Items.GLASS_PANE)
                .unlockedBy("has_clock", has(Items.CLOCK)).save(out, id("test_pressure_gauge"));
        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, PcItems.CAULKING_HAMMER.get())
                .pattern("II").pattern(" S").pattern(" S").define('I', Items.IRON_INGOT).define('S', Items.STICK)
                .unlockedBy("has_iron", has(Items.IRON_INGOT)).save(out, id("caulking_hammer"));
        ShapelessRecipeBuilder.shapeless(RecipeCategory.TOOLS, PcItems.OIL_CAN.get())
                .requires(sheet).requires(Items.COPPER_INGOT).requires(Items.BUCKET)
                .unlockedBy("has_sheet", hasSheet).save(out, id("oil_can"));
        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, PcItems.BOILER_SCRAPER.get())
                .pattern("P").pattern("S").pattern("S").define('P', sheet).define('S', Items.STICK)
                .unlockedBy("has_sheet", hasSheet).save(out, id("boiler_scraper"));

        // запчасти и расходники
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcItems.SAFETY_VALVE.get())
                .pattern(" B ").pattern("PVP").define('B', brass).define('P', pipe).define('V', AllBlocks.FLUID_VALVE.get())
                .unlockedBy("has_brass", has(brass)).save(out, id("safety_valve"));
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, PcItems.GLAND_PACKING.get(), 2)
                .requires(Items.STRING, 2).requires(PcItems.TALLOW.get())
                .unlockedBy("has_tallow", has(PcItems.TALLOW.get())).save(out, id("gland_packing"));
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, PcItems.CHART_PAPER.get(), 3)
                .requires(Items.PAPER, 3).requires(Items.BLACK_DYE)
                .unlockedBy("has_paper", has(Items.PAPER)).save(out, id("chart_paper"));
        SpecialRecipeBuilder.special(CastingPartRecipe::new).save(out, id("casting_part"));

        // щит, насос, самописец
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.MAINTENANCE_PANEL.get())
                .pattern("IGI").pattern("BBB").pattern("III").define('I', Items.IRON_INGOT).define('G', Items.GLASS_PANE)
                .define('B', brass).unlockedBy("has_brass", has(brass)).save(out, id("maintenance_panel"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.HYDROTEST_PUMP.get())
                .pattern("L  ").pattern("IPI").define('L', Items.LEVER).define('I', Items.IRON_INGOT).define('P', pipe)
                .unlockedBy("has_pipe", has(pipe)).save(out, id("hydrotest_pump"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.RECORDING_GAUGE.get())
                .pattern("IGI").pattern("CKC").pattern("III").define('I', Items.IRON_INGOT).define('G', Items.GLASS_PANE)
                .define('C', Items.COPPER_INGOT).define('K', Items.CLOCK)
                .unlockedBy("has_clock", has(Items.CLOCK)).save(out, id("recording_gauge"));

        // водоподготовка
        var tank = AllBlocks.FLUID_TANK.get();
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.WATER_CLARIFIER.get())
                .pattern("IPI").pattern("ITI").pattern("III").define('I', sheet).define('P', pipe).define('T', tank)
                .unlockedBy("has_sheet", hasSheet).save(out, id("water_clarifier"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.SAND_FILTER.get())
                .pattern("IPI").pattern("ISI").pattern("IPI").define('I', sheet).define('P', pipe).define('S', Items.SAND)
                .unlockedBy("has_sheet", hasSheet).save(out, id("sand_filter"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.ION_EXCHANGE_COLUMN.get())
                .pattern("IPI").pattern("ICI").pattern("IPI").define('I', sheet).define('P', pipe).define('C', Items.COAL)
                .unlockedBy("has_sheet", hasSheet).save(out, id("ion_exchange_column"));
        // чертёж Na-катионитового фильтра реального масштаба: строится на стройплощадке по массе прототипа (SitePlans)
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, dev.denfry.promptcritical.mod.site.SiteRegistry.ION_EXCHANGE_COLUMN_REAL_DRAWING.get())
                .pattern("PPP").pattern("ICI").define('P', Items.PAPER).define('I', sheet).define('C', Items.COAL)
                .unlockedBy("has_sheet", hasSheet).save(out, id("ion_exchange_column_real_drawing"));
    }
}
