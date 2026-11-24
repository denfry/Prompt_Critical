package dev.denfry.promptcritical.mod.npp;

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
import net.minecraft.world.item.Items;

/** Рецепты АЭС T1 и T2a. */
public final class NppRecipes extends RecipeProvider {
    public NppRecipes(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries);
    }

    public static DataProvider provider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        DataProvider inner = new NppRecipes(output, registries);
        return new DataProvider() {
            @Override
            public CompletableFuture<?> run(CachedOutput cache) {
                return inner.run(cache);
            }

            @Override
            public String getName() {
                return "Recipes: nuclear power plant";
            }
        };
    }

    private static String id(String name) {
        return PromptCritical.id("npp/" + name).toString();
    }

    @Override
    protected void buildRecipes(RecipeOutput out) {
        var graphite = PcBlocks.MODERATOR_BLOCK_GRAPHITE.get();
        // реактор: графитовая кладка с каналами, приводы СУЗ, стальной кожух
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, dev.denfry.promptcritical.mod.site.SiteRegistry.AM1_REACTOR_DRAWING.get())
                .pattern("DCD").pattern("GRG").pattern("PGP").define('D', PcBlocks.CONTROL_ROD_DRIVE.get())
                .define('C', PcBlocks.CONTROL_ROD_CHANNEL.get()).define('G', graphite).define('R', PcBlocks.REACTOR_CONTROLLER.get())
                .define('P', PcItems.STEEL_PLATE.get())
                .unlockedBy("has_reactor_controller", has(PcBlocks.REACTOR_CONTROLLER.get())).save(out, id("am1_reactor_drawing"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.PRIMARY_PUMP.get())
                .pattern("PMP").pattern("FTF").pattern("PHP").define('P', PcItems.STEEL_PLATE.get()).define('M', AllItems.PRECISION_MECHANISM.get())
                .define('F', AllBlocks.FLYWHEEL.get()).define('T', AllBlocks.MECHANICAL_PUMP.get()).define('H', AllBlocks.SHAFT.get())
                .unlockedBy("has_am1_reactor_drawing", has(dev.denfry.promptcritical.mod.site.SiteRegistry.AM1_REACTOR_DRAWING.get())).save(out, id("primary_pump"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.STEAM_GENERATOR.get())
                .pattern("PTP").pattern("SXS").pattern("PTP").define('P', PcItems.STEEL_PLATE.get()).define('T', PcItems.SEAMLESS_PIPE.get())
                .define('S', AllItems.IRON_SHEET.get()).define('X', AllBlocks.FLUID_TANK.get())
                .unlockedBy("has_am1_reactor_drawing", has(dev.denfry.promptcritical.mod.site.SiteRegistry.AM1_REACTOR_DRAWING.get())).save(out, id("steam_generator"));
        // чертежи ГЦН-195 и ПГВ-1000 реального масштаба: проект по образцу насоса и парогенератора, которые уже есть
        net.minecraft.data.recipes.ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, dev.denfry.promptcritical.mod.site.SiteRegistry.VVER_COOLANT_PUMP_DRAWING.get())
                .requires(net.minecraft.world.item.Items.PAPER, 3).requires(PcBlocks.PRIMARY_PUMP.get())
                .unlockedBy("has_primary_pump", has(PcBlocks.PRIMARY_PUMP.get())).save(out, id("vver_coolant_pump_drawing"));
        net.minecraft.data.recipes.ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, dev.denfry.promptcritical.mod.site.SiteRegistry.VVER_STEAM_GENERATOR_DRAWING.get())
                .requires(net.minecraft.world.item.Items.PAPER, 3).requires(PcBlocks.STEAM_GENERATOR.get())
                .unlockedBy("has_steam_generator", has(PcBlocks.STEAM_GENERATOR.get())).save(out, id("vver_steam_generator_drawing"));
        // ВВЭР: блок приводов СУЗ над корпусом из листа, внутрикорпусная шахта из циркалоя, пульт
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, dev.denfry.promptcritical.mod.site.SiteRegistry.VVER_REACTOR_DRAWING.get())
                .pattern("DCD").pattern("PRP").pattern("PZP").define('D', PcBlocks.CONTROL_ROD_DRIVE.get())
                .define('C', PcBlocks.CONTROL_ROD_CHANNEL.get()).define('R', PcBlocks.REACTOR_CONTROLLER.get())
                .define('P', PcItems.STEEL_PLATE.get()).define('Z', PcItems.ZIRCALOY_INGOT.get())
                .unlockedBy("has_reactor_controller", has(PcBlocks.REACTOR_CONTROLLER.get())).save(out, id("vver_reactor_drawing"));
        // гермооболочка: железобетон со стальной облицовкой; люк — стальная дверь в обойме; ПАР — каталитические пластины
        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, PcBlocks.CONTAINMENT_WALL.get(), 8)
                .pattern("CCC").pattern("CPC").pattern("CCC").define('C', Items.GRAY_CONCRETE).define('P', PcItems.STEEL_PLATE.get())
                .unlockedBy("has_vver_reactor_drawing", has(dev.denfry.promptcritical.mod.site.SiteRegistry.VVER_REACTOR_DRAWING.get())).save(out, id("containment_wall"));
        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, PcBlocks.CONTAINMENT_HATCH.get())
                .pattern("PWP").pattern("PDP").pattern("PWP").define('P', PcItems.STEEL_PLATE.get())
                .define('W', PcBlocks.CONTAINMENT_WALL.get()).define('D', Items.IRON_DOOR)
                .unlockedBy("has_containment_wall", has(PcBlocks.CONTAINMENT_WALL.get())).save(out, id("containment_hatch"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.HYDROGEN_RECOMBINER.get())
                .pattern("PSP").pattern("SXS").pattern("PSP").define('P', PcItems.STEEL_PLATE.get())
                .define('S', AllItems.IRON_SHEET.get()).define('X', Items.IRON_BARS)
                .unlockedBy("has_containment_wall", has(PcBlocks.CONTAINMENT_WALL.get())).save(out, id("hydrogen_recombiner"));
        // САОЗ: многоступенчатый насос (механический насос Create с прецизионным механизмом) и бак под азотом с обратным клапаном
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.ECCS_PUMP.get())
                .pattern("PMP").pattern("TXT").pattern("PHP").define('P', PcItems.STEEL_PLATE.get()).define('M', AllItems.PRECISION_MECHANISM.get())
                .define('T', AllBlocks.MECHANICAL_PUMP.get()).define('X', PcItems.SEAMLESS_PIPE.get()).define('H', AllBlocks.SHAFT.get())
                .unlockedBy("has_vver_reactor_drawing", has(dev.denfry.promptcritical.mod.site.SiteRegistry.VVER_REACTOR_DRAWING.get())).save(out, id("eccs_pump"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.ECCS_ACCUMULATOR.get())
                .pattern("PVP").pattern("PXP").pattern("PTP").define('P', PcItems.STEEL_PLATE.get()).define('V', PcItems.SAFETY_VALVE.get())
                .define('X', AllBlocks.FLUID_TANK.get()).define('T', PcItems.SEAMLESS_PIPE.get())
                .unlockedBy("has_vver_reactor_drawing", has(dev.denfry.promptcritical.mod.site.SiteRegistry.VVER_REACTOR_DRAWING.get())).save(out, id("eccs_accumulator"));
        // ВВЭР-70 (В-2 Райнсберга): корпус меньше, ПГ вертикальный, ГЦН экранированный, КД газовый (баллоны азота вместо ТЭНов)
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, dev.denfry.promptcritical.mod.site.SiteRegistry.VVER70_REACTOR_REAL_DRAWING.get())
                .pattern("PDP").pattern("PRP").pattern("PZP").define('D', PcBlocks.CONTROL_ROD_DRIVE.get())
                .define('R', PcBlocks.REACTOR_CONTROLLER.get()).define('P', PcItems.STEEL_PLATE.get()).define('Z', PcItems.ZIRCALOY_INGOT.get())
                .unlockedBy("has_reactor_controller", has(PcBlocks.REACTOR_CONTROLLER.get())).save(out, id("vver70_reactor_real_drawing"));
        net.minecraft.data.recipes.ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, dev.denfry.promptcritical.mod.site.SiteRegistry.VVER70_STEAM_GENERATOR_REAL_DRAWING.get())
                .requires(net.minecraft.world.item.Items.PAPER, 2).requires(PcBlocks.STEAM_GENERATOR.get())
                .unlockedBy("has_vver70_reactor_real_drawing", has(dev.denfry.promptcritical.mod.site.SiteRegistry.VVER70_REACTOR_REAL_DRAWING.get()))
                .save(out, id("vver70_steam_generator_real_drawing"));
        net.minecraft.data.recipes.ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, dev.denfry.promptcritical.mod.site.SiteRegistry.VVER70_COOLANT_PUMP_REAL_DRAWING.get())
                .requires(net.minecraft.world.item.Items.PAPER, 2).requires(PcBlocks.PRIMARY_PUMP.get())
                .unlockedBy("has_vver70_reactor_real_drawing", has(dev.denfry.promptcritical.mod.site.SiteRegistry.VVER70_REACTOR_REAL_DRAWING.get()))
                .save(out, id("vver70_coolant_pump_real_drawing"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, dev.denfry.promptcritical.mod.site.SiteRegistry.VVER70_PRESSURIZER_REAL_DRAWING.get())
                .pattern("PVP").pattern("PXP").pattern("PGP").define('P', PcItems.STEEL_PLATE.get()).define('V', PcItems.SAFETY_VALVE.get())
                .define('X', AllBlocks.FLUID_TANK.get()).define('G', PcBlocks.INSTRUMENT_PRESSURE.get())
                .unlockedBy("has_vver70_reactor_real_drawing", has(dev.denfry.promptcritical.mod.site.SiteRegistry.VVER70_REACTOR_REAL_DRAWING.get()))
                .save(out, id("vver70_pressurizer_real_drawing"));
        // бассейн выдержки — по стеллажу старой серии, перегрузочная машина — балка и цепь
        net.minecraft.data.recipes.ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, dev.denfry.promptcritical.mod.site.SiteRegistry.VVER70_FUEL_POOL_REAL_DRAWING.get())
                .requires(net.minecraft.world.item.Items.PAPER, 2).requires(PcBlocks.FUEL_RACK.get())
                .unlockedBy("has_vver70_reactor_real_drawing", has(dev.denfry.promptcritical.mod.site.SiteRegistry.VVER70_REACTOR_REAL_DRAWING.get()))
                .save(out, id("vver70_fuel_pool_real_drawing"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, dev.denfry.promptcritical.mod.site.SiteRegistry.VVER70_REFUELLING_MACHINE_REAL_DRAWING.get())
                .pattern("PBP").pattern("PCP").pattern("PPP").define('P', net.minecraft.world.item.Items.PAPER).define('B', PcItems.STEEL_BEAM.get())
                .define('C', net.minecraft.world.item.Items.CHAIN)
                .unlockedBy("has_vver70_reactor_real_drawing", has(dev.denfry.promptcritical.mod.site.SiteRegistry.VVER70_REACTOR_REAL_DRAWING.get()))
                .save(out, id("vver70_refuelling_machine_real_drawing"));
        // CANDU NPD: каландр с каналами из циркалоя, машина и петля — от чертежа реактора
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, dev.denfry.promptcritical.mod.site.SiteRegistry.NPD_REACTOR_REAL_DRAWING.get())
                .pattern("PZP").pattern("ZRZ").pattern("PZP").define('Z', PcItems.ZIRCALOY_INGOT.get())
                .define('R', PcBlocks.REACTOR_CONTROLLER.get()).define('P', PcItems.STEEL_PLATE.get())
                .unlockedBy("has_reactor_controller", has(PcBlocks.REACTOR_CONTROLLER.get())).save(out, id("candu_npd_reactor_real_drawing"));
        net.minecraft.data.recipes.ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, dev.denfry.promptcritical.mod.site.SiteRegistry.NPD_FUELLING_MACHINE_REAL_DRAWING.get())
                .requires(net.minecraft.world.item.Items.PAPER, 2).requires(dev.denfry.promptcritical.mod.site.SiteRegistry.NPD_REACTOR_REAL_DRAWING.get())
                .requires(PcItems.STEEL_PLATE.get())
                .unlockedBy("has_candu_npd_reactor_real_drawing", has(dev.denfry.promptcritical.mod.site.SiteRegistry.NPD_REACTOR_REAL_DRAWING.get()))
                .save(out, id("candu_npd_fuelling_machine_real_drawing"));
        net.minecraft.data.recipes.ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, dev.denfry.promptcritical.mod.site.SiteRegistry.NPD_BOILER_REAL_DRAWING.get())
                .requires(net.minecraft.world.item.Items.PAPER, 2).requires(PcBlocks.STEAM_GENERATOR.get())
                .unlockedBy("has_candu_npd_reactor_real_drawing", has(dev.denfry.promptcritical.mod.site.SiteRegistry.NPD_REACTOR_REAL_DRAWING.get()))
                .save(out, id("candu_npd_boiler_real_drawing"));
        net.minecraft.data.recipes.ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, dev.denfry.promptcritical.mod.site.SiteRegistry.NPD_PUMP_REAL_DRAWING.get())
                .requires(net.minecraft.world.item.Items.PAPER, 2).requires(PcBlocks.PRIMARY_PUMP.get())
                .unlockedBy("has_candu_npd_reactor_real_drawing", has(dev.denfry.promptcritical.mod.site.SiteRegistry.NPD_REACTOR_REAL_DRAWING.get()))
                .save(out, id("candu_npd_pump_real_drawing"));
        // компенсатор давления: сосуд, ИПУ сверху, манометр, ТЭНы — медная обмотка
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, dev.denfry.promptcritical.mod.site.SiteRegistry.VVER_PRESSURIZER_DRAWING.get())
                .pattern("PVP").pattern("PXP").pattern("CGC").define('P', PcItems.STEEL_PLATE.get()).define('V', PcItems.SAFETY_VALVE.get())
                .define('X', AllBlocks.FLUID_TANK.get()).define('G', PcBlocks.INSTRUMENT_PRESSURE.get()).define('C', Items.COPPER_INGOT)
                .unlockedBy("has_vver_reactor_drawing", has(dev.denfry.promptcritical.mod.site.SiteRegistry.VVER_REACTOR_DRAWING.get())).save(out, id("vver_pressurizer_drawing"));
        // CANDU: каландр из листа с каналами из циркалоя, перегрузочная машина сверху
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, dev.denfry.promptcritical.mod.site.SiteRegistry.CANDU6_REACTOR_DRAWING.get())
                .pattern("PMP").pattern("ZRZ").pattern("PCP").define('M', PcBlocks.CONTROL_ROD_DRIVE.get())
                .define('C', PcBlocks.CONTROL_ROD_CHANNEL.get()).define('R', PcBlocks.REACTOR_CONTROLLER.get())
                .define('P', PcItems.STEEL_PLATE.get()).define('Z', PcItems.ZIRCALOY_INGOT.get())
                .unlockedBy("has_reactor_controller", has(PcBlocks.REACTOR_CONTROLLER.get())).save(out, id("candu6_reactor_drawing"));
        // кипящий реактор: корпус ВВЭР без КД, сверху сепараторы — барабан, снизу приводы стержней
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, dev.denfry.promptcritical.mod.site.SiteRegistry.BWR240_REACTOR_DRAWING.get())
                .pattern("PSP").pattern("PRP").pattern("DZD").define('S', PcBlocks.STEAM_DRUM.get())
                .define('R', PcBlocks.REACTOR_CONTROLLER.get()).define('D', PcBlocks.CONTROL_ROD_DRIVE.get())
                .define('P', PcItems.STEEL_PLATE.get()).define('Z', PcItems.ZIRCALOY_INGOT.get())
                .unlockedBy("has_vver_reactor_drawing", has(dev.denfry.promptcritical.mod.site.SiteRegistry.VVER_REACTOR_DRAWING.get())).save(out, id("bwr240_reactor_drawing"));
        // быстрый натриевый: бассейн натрия (баки) под пробкой с приводами стержней, открывается после кипящего
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, dev.denfry.promptcritical.mod.site.SiteRegistry.BN600_REACTOR_DRAWING.get())
                .pattern("DDD").pattern("PRP").pattern("PTP").define('D', PcBlocks.CONTROL_ROD_DRIVE.get())
                .define('R', PcBlocks.REACTOR_CONTROLLER.get()).define('T', AllBlocks.FLUID_TANK.get())
                .define('P', PcItems.STEEL_PLATE.get())
                .unlockedBy("has_bwr240_reactor_drawing", has(dev.denfry.promptcritical.mod.site.SiteRegistry.BWR240_REACTOR_DRAWING.get())).save(out, id("bn600_reactor_drawing"));
    }
}
