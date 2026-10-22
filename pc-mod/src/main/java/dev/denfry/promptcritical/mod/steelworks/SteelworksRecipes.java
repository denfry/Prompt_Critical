package dev.denfry.promptcritical.mod.steelworks;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.ironworks.IronworksTags;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcItems;
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
import net.minecraft.data.recipes.SimpleCookingRecipeBuilder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * Рецепты сталеплавильного, прокатного и трубного цехов и ВРУ (08 §8.6–8.8, §8.11): известь, ферросплавы M3 (Mn, Si),
 * балка из листов, трубы Create из бесшовных, агрегаты 10б. Агрегаты прокатки и ВРУ — на прокате и трубах первой
 * плавки; конвертер и ВРУ (кислород первой плавки) — без стали.
 */
public final class SteelworksRecipes extends RecipeProvider {
    public SteelworksRecipes(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries);
    }

    public static DataProvider provider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        DataProvider inner = new SteelworksRecipes(output, registries);
        return new DataProvider() {
            @Override
            public CompletableFuture<?> run(CachedOutput cache) {
                return inner.run(cache);
            }

            @Override
            public String getName() {
                return "Recipes: steel shop, rolling, pipes and air separation";
            }
        };
    }

    private static String id(String name) {
        return PromptCritical.id("steelworks/" + name).toString();
    }

    private static ItemStack alloy(Ferroalloy a, int count) {
        ItemStack s = new ItemStack(PcItems.FERROALLOY.get(), count);
        s.set(SteelworksComponents.FERROALLOY.get(), a.id);
        return s;
    }

    @Override
    protected void buildRecipes(RecipeOutput out) {
        var sheet = AllItems.IRON_SHEET.get();
        var casing = AllBlocks.ANDESITE_CASING.get();
        var shaft = AllBlocks.SHAFT.get();
        var pipe = AllBlocks.FLUID_PIPE.get();
        var brick = PcItems.REFRACTORY_BRICK.get();
        var plate = PcItems.STEEL_PLATE.get();
        var rod = PcItems.STEEL_ROD.get();
        var beam = PcItems.STEEL_BEAM.get();
        var seamless = PcItems.SEAMLESS_PIPE.get();
        var hasPlate = has(plate);

        // шихта и добавки
        SimpleCookingRecipeBuilder.blasting(Ingredient.of(IronworksTags.FLUX), RecipeCategory.MISC, PcItems.BURNT_LIME.get(), 0.1f, 100)
                .unlockedBy("has_calcite", has(Items.CALCITE)).save(out, id("burnt_lime"));
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, alloy(Ferroalloy.FERROMANGANESE, 2))
                .requires(PcItems.RAW_PYROLUSITE.get(), 2).requires(PcItems.PIG_IRON.get()).requires(PcItems.COKE.get())
                .unlockedBy("has_pyrolusite", has(PcItems.RAW_PYROLUSITE.get())).save(out, id("ferromanganese"));
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, alloy(Ferroalloy.FERROSILICON, 2))
                .requires(Items.QUARTZ, 2).requires(PcItems.PIG_IRON.get()).requires(PcItems.COKE.get())
                .unlockedBy("has_pig_iron", has(PcItems.PIG_IRON.get())).save(out, id("ferrosilicon"));

        // изделия из проката и труб
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, beam, 7).pattern("PPP").pattern(" P ").pattern("PPP").define('P', plate)
                .unlockedBy("has_plate", hasPlate).save(out, id("steel_beam"));
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, pipe, 4).requires(seamless)
                .unlockedBy("has_seamless_pipe", has(seamless)).save(out, id("fluid_pipe_from_seamless"));
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, AllBlocks.FLUID_TANK.get()).requires(plate).requires(Items.GLASS)
                .unlockedBy("has_plate", hasPlate).save(out, id("fluid_tank_from_plate"));

        // конвертер и ковшевая доводка
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.BOF_VESSEL.get())
                .pattern("R R").pattern("RBR").pattern("ISI").define('R', brick).define('B', Items.IRON_BLOCK).define('I', sheet)
                .define('S', shaft).unlockedBy("has_pig_iron", has(PcItems.PIG_IRON.get())).save(out, id("bof_vessel"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.OXYGEN_LANCE.get())
                .pattern(" P ").pattern(" P ").pattern("CTC").define('P', pipe).define('C', Items.COPPER_INGOT).define('T', AllItems.BRASS_INGOT.get())
                .unlockedBy("has_bof", has(PcBlocks.BOF_VESSEL.get())).save(out, id("oxygen_lance"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.LADLE_STATION.get())
                .pattern("IHI").pattern("PCP").define('I', sheet).define('H', Items.HOPPER).define('P', pipe).define('C', casing)
                .unlockedBy("has_bof", has(PcBlocks.BOF_VESSEL.get())).save(out, id("ladle_station"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.INGOT_MOLD.get(), 2)
                .pattern("I I").pattern("I I").pattern("IRI").define('I', Items.IRON_INGOT).define('R', brick)
                .unlockedBy("has_bof", has(PcBlocks.BOF_VESSEL.get())).save(out, id("ingot_mold"));

        // печи
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.REHEATING_FURNACE.get())
                .pattern("RRR").pattern("RFR").pattern("PRP").define('R', brick).define('F', Items.BLAST_FURNACE).define('P', pipe)
                .unlockedBy("has_steel_ingot", has(PcItems.STEEL_INGOT.get())).save(out, id("reheating_furnace"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.HEAT_TREATMENT_FURNACE.get())
                .pattern("RRR").pattern("RFR").pattern("SCS").define('R', brick).define('F', Items.BLAST_FURNACE).define('S', plate)
                .define('C', Items.COMPARATOR).unlockedBy("has_plate", hasPlate).save(out, id("heat_treatment_furnace"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.QUENCH_TANK.get())
                .pattern("S S").pattern("SWS").pattern("SSS").define('S', plate).define('W', Items.WATER_BUCKET)
                .unlockedBy("has_plate", hasPlate).save(out, id("quench_tank"));

        // прокат и трубы: первая клеть и рольганг — из железа, остальное — из стали
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.ROLLER_TABLE.get(), 4)
                .pattern("SSS").pattern("ICI").define('S', shaft).define('I', Items.IRON_INGOT).define('C', casing)
                .unlockedBy("has_steel_ingot", has(PcItems.STEEL_INGOT.get())).save(out, id("roller_table"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.ROLLING_MILL_STAND.get())
                .pattern("BIB").pattern("SGS").pattern("BCB").define('B', Items.IRON_BLOCK).define('I', Items.IRON_INGOT).define('S', shaft)
                .define('G', AllBlocks.COGWHEEL.get()).define('C', casing)
                .unlockedBy("has_steel_ingot", has(PcItems.STEEL_INGOT.get())).save(out, id("rolling_mill_stand"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.PIERCING_MILL.get())
                .pattern("BRB").pattern("SGS").pattern("BCB").define('B', beam).define('R', rod).define('S', shaft)
                .define('G', AllBlocks.LARGE_COGWHEEL.get()).define('C', casing)
                .unlockedBy("has_rod", has(rod)).save(out, id("piercing_mill"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.PIPE_WELDER.get())
                .pattern("PWP").pattern("SCS").define('P', plate).define('W', PcItems.COPPER_WIRE.get()).define('S', shaft).define('C', casing)
                .unlockedBy("has_plate", hasPlate).save(out, id("pipe_welder"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.HYDROTEST_BENCH.get())
                .pattern("GPG").pattern("BMB").define('G', AllBlocks.FLUID_VALVE.get()).define('P', AllBlocks.MECHANICAL_PUMP.get())
                .define('B', beam).define('M', Items.CLOCK)
                .unlockedBy("has_seamless_pipe", has(seamless)).save(out, id("hydrotest_bench"));

        // ВРУ даёт кислород первой плавке — без стали; криобак — на бесшовных трубах
        var copper = Items.COPPER_INGOT;
        var hasBof = has(PcBlocks.BOF_VESSEL.get());
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.AIR_SEPARATOR.get())
                .pattern("IPI").pattern("CKC").pattern("IPI").define('I', sheet).define('P', pipe).define('C', copper).define('K', Items.COMPARATOR)
                .unlockedBy("has_bof", hasBof).save(out, id("air_separator"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.ASU_MAIN_COMPRESSOR.get())
                .pattern("IFI").pattern("SCS").pattern("IPI").define('I', sheet).define('F', AllItems.PROPELLER.get()).define('S', shaft)
                .define('C', casing).define('P', pipe)
                .unlockedBy("has_bof", hasBof).save(out, id("asu_main_compressor"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.ASU_MOLSIEVE.get())
                .pattern("IPI").pattern("IZI").pattern("IPI").define('I', sheet).define('P', pipe).define('Z', Items.CLAY_BALL)
                .unlockedBy("has_bof", hasBof).save(out, id("asu_molsieve"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.ASU_EXPANDER.get())
                .pattern("CFC").pattern("ISI").define('C', copper).define('F', AllItems.PROPELLER.get()).define('I', sheet).define('S', shaft)
                .unlockedBy("has_bof", hasBof).save(out, id("asu_expander"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.CRYOGENIC_TANK.get())
                .pattern("PWP").pattern("TKT").pattern("PWP").define('P', plate).define('W', Items.WHITE_WOOL).define('T', seamless)
                .define('K', AllBlocks.FLUID_TANK.get())
                .unlockedBy("has_seamless_pipe", has(seamless)).save(out, id("cryogenic_tank"));
    }
}
