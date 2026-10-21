package dev.denfry.promptcritical.mod.ironworks;

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
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * Рецепты коксохима и домны (08 §8.4–8.5): огнеупоры (динас, лёточная масса), запасная фурма, агрегаты из деталей
 * Create. Пек из смолы — смешивание Create с нагревом (статический рецепт {@code ironworks/coal_tar_pitch}).
 */
public final class IronworksRecipes extends RecipeProvider {
    public IronworksRecipes(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries);
    }

    public static DataProvider provider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        DataProvider inner = new IronworksRecipes(output, registries);
        return new DataProvider() {
            @Override
            public CompletableFuture<?> run(CachedOutput cache) {
                return inner.run(cache);
            }

            @Override
            public String getName() {
                return "Recipes: coke plant and blast furnace";
            }
        };
    }

    private static String id(String name) {
        return PromptCritical.id("ironworks/" + name).toString();
    }

    @Override
    protected void buildRecipes(RecipeOutput out) {
        var sheet = AllItems.IRON_SHEET.get();
        var casing = AllBlocks.ANDESITE_CASING.get();
        var shaft = AllBlocks.SHAFT.get();
        var pipe = AllBlocks.FLUID_PIPE.get();
        var brick = PcItems.REFRACTORY_BRICK.get();
        var silica = PcItems.SILICA_BRICK.get();

        // огнеупоры и расходники
        SimpleCookingRecipeBuilder.smelting(Ingredient.of(PcBlocks.VEIN_QUARTZ.get(), Items.QUARTZ), RecipeCategory.MISC, silica, 0.1f, 200)
                .unlockedBy("has_vein_quartz", has(PcBlocks.VEIN_QUARTZ.get())).save(out, id("silica_brick"));
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, PcItems.TAPHOLE_CLAY.get(), 4)
                .requires(Items.CLAY_BALL, 2).requires(PcItems.COKE_BREEZE.get()).requires(PcItems.COAL_TAR_PITCH.get())
                .unlockedBy("has_pitch", has(PcItems.COAL_TAR_PITCH.get())).save(out, id("taphole_clay"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcItems.TUYERE_SPARE.get())
                .pattern(" C ").pattern("CBC").pattern(" C ").define('C', Items.COPPER_INGOT).define('B', Items.WATER_BUCKET)
                .unlockedBy("has_copper", has(Items.COPPER_INGOT)).save(out, id("tuyere_spare"));

        // коксовая батарея
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.COKE_OVEN.get(), 4)
                .pattern("SSS").pattern("SDS").pattern("SSS").define('S', silica).define('D', Items.IRON_DOOR)
                .unlockedBy("has_silica_brick", has(silica)).save(out, id("coke_oven"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.COKE_BATTERY_WALL.get(), 8)
                .pattern("SSS").pattern("SRS").pattern("SSS").define('S', silica).define('R', brick)
                .unlockedBy("has_silica_brick", has(silica)).save(out, id("coke_battery_wall"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.GAS_COLLECTOR_MAIN.get(), 4)
                .pattern("I I").pattern("PPP").define('I', sheet).define('P', pipe)
                .unlockedBy("has_pipe", has(pipe)).save(out, id("gas_collector_main"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.COKE_PUSHER_RAM.get())
                .pattern("III").pattern(" C ").pattern(" P ").define('I', Items.IRON_INGOT).define('C', casing).define('P', Items.PISTON)
                .unlockedBy("has_casing", has(casing)).save(out, id("coke_pusher_ram"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.GAS_EXHAUSTER.get())
                .pattern("IFI").pattern("SCS").define('I', sheet).define('F', AllItems.PROPELLER.get()).define('S', shaft).define('C', casing)
                .unlockedBy("has_casing", has(casing)).save(out, id("gas_exhauster"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.BYPRODUCT_PLANT.get())
                .pattern("ITI").pattern("PCP").define('I', sheet).define('T', AllBlocks.FLUID_TANK.get()).define('P', pipe).define('C', casing)
                .unlockedBy("has_casing", has(casing)).save(out, id("byproduct_plant"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.QUENCH_TOWER.get())
                .pattern("BBB").pattern("B B").pattern("BWB").define('B', Items.BRICKS).define('W', Items.WATER_BUCKET)
                .unlockedBy("has_coke", has(PcItems.COKE.get())).save(out, id("quench_tower"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.ELECTRODE_EXTRUDER.get())
                .pattern("IPI").pattern("SCS").define('I', sheet).define('P', Items.PISTON).define('S', shaft).define('C', casing)
                .unlockedBy("has_pitch", has(PcItems.COAL_TAR_PITCH.get())).save(out, id("electrode_extruder"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.BAKING_FURNACE.get())
                .pattern("RRR").pattern("RFR").pattern("RRR").define('R', brick).define('F', Items.FURNACE)
                .unlockedBy("has_green_electrode", has(PcItems.GREEN_ELECTRODE.get())).save(out, id("baking_furnace"));

        // подготовка руды
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.MAGNETIC_SEPARATOR.get())
                .pattern("IRI").pattern("SCS").define('I', Items.IRON_INGOT).define('R', Items.REDSTONE_BLOCK).define('S', shaft)
                .define('C', casing).unlockedBy("has_magnetite", has(PcItems.RAW_MAGNETITE.get())).save(out, id("magnetic_separator"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.SINTER_STRAND.get())
                .pattern("BBB").pattern("RFR").pattern("SCS").define('B', Items.IRON_BARS).define('R', brick).define('F', Items.FURNACE)
                .define('S', shaft).define('C', casing)
                .unlockedBy("has_concentrate", has(PcItems.IRON_ORE_CONCENTRATE.get())).save(out, id("sinter_strand"));

        // домна
        var shell = PcBlocks.BLAST_FURNACE_SHELL.get();
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, shell, 8)
                .pattern("III").pattern("IRI").pattern("III").define('I', sheet).define('R', brick)
                .unlockedBy("has_refractory_brick", has(brick)).save(out, id("blast_furnace_shell"));
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, PcBlocks.BLAST_FURNACE_TUYERE.get())
                .requires(shell).requires(PcItems.TUYERE_SPARE.get())
                .unlockedBy("has_shell", has(shell)).save(out, id("blast_furnace_tuyere"));
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, PcBlocks.BLAST_FURNACE_TAPHOLE.get())
                .requires(shell).requires(PcItems.TAPHOLE_CLAY.get(), 2).requires(brick)
                .unlockedBy("has_shell", has(shell)).save(out, id("blast_furnace_taphole"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.BLAST_FURNACE_CONTROLLER.get())
                .pattern("IKI").pattern("RCR").define('I', sheet).define('K', Items.COMPARATOR).define('R', Items.REDSTONE).define('C', casing)
                .unlockedBy("has_shell", has(shell)).save(out, id("blast_furnace_controller"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.BLAST_FURNACE_TOP.get())
                .pattern("I I").pattern("IHI").pattern("III").define('I', sheet).define('H', Items.HOPPER)
                .unlockedBy("has_shell", has(shell)).save(out, id("blast_furnace_top"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.HOT_BLAST_STOVE.get())
                .pattern("SRS").pattern("RFR").pattern("SRS").define('S', shell).define('R', brick).define('F', Items.BLAST_FURNACE)
                .unlockedBy("has_shell", has(shell)).save(out, id("hot_blast_stove"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.BLAST_BLOWER.get())
                .pattern("IPI").pattern("PCP").pattern("ISI").define('I', sheet).define('P', AllItems.PROPELLER.get()).define('C', casing)
                .define('S', shaft).unlockedBy("has_shell", has(shell)).save(out, id("blast_blower"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.DUST_CATCHER.get())
                .pattern("I I").pattern("ICI").pattern("III").define('I', sheet).define('C', Items.CAULDRON)
                .unlockedBy("has_shell", has(shell)).save(out, id("dust_catcher"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.CAST_HOUSE_MACHINE.get())
                .pattern(" D ").pattern("ICI").define('D', AllBlocks.MECHANICAL_DRILL.get()).define('I', sheet).define('C', casing)
                .unlockedBy("has_shell", has(shell)).save(out, id("cast_house_machine"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.IRON_RUNNER.get(), 4)
                .pattern("R R").pattern("RRR").define('R', brick)
                .unlockedBy("has_refractory_brick", has(brick)).save(out, id("iron_runner"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.LADLE.get())
                .pattern("I I").pattern("IRI").pattern("III").define('I', sheet).define('R', brick)
                .unlockedBy("has_refractory_brick", has(brick)).save(out, id("ladle"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.SLAG_GRANULATOR.get())
                .pattern("IPI").pattern("ICI").define('I', sheet).define('P', pipe).define('C', casing)
                .unlockedBy("has_shell", has(shell)).save(out, id("slag_granulator"));
    }
}
