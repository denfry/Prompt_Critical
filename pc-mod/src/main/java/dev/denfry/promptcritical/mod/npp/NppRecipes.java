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
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.POWER_REACTOR.get())
                .pattern("DCD").pattern("GRG").pattern("PGP").define('D', PcBlocks.CONTROL_ROD_DRIVE.get())
                .define('C', PcBlocks.CONTROL_ROD_CHANNEL.get()).define('G', graphite).define('R', PcBlocks.REACTOR_CONTROLLER.get())
                .define('P', PcItems.STEEL_PLATE.get())
                .unlockedBy("has_reactor_controller", has(PcBlocks.REACTOR_CONTROLLER.get())).save(out, id("power_reactor"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.PRIMARY_PUMP.get())
                .pattern("PMP").pattern("FTF").pattern("PHP").define('P', PcItems.STEEL_PLATE.get()).define('M', AllItems.PRECISION_MECHANISM.get())
                .define('F', AllBlocks.FLYWHEEL.get()).define('T', AllBlocks.MECHANICAL_PUMP.get()).define('H', AllBlocks.SHAFT.get())
                .unlockedBy("has_power_reactor", has(PcBlocks.POWER_REACTOR.get())).save(out, id("primary_pump"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.STEAM_GENERATOR.get())
                .pattern("PTP").pattern("SXS").pattern("PTP").define('P', PcItems.STEEL_PLATE.get()).define('T', PcItems.SEAMLESS_PIPE.get())
                .define('S', AllItems.IRON_SHEET.get()).define('X', AllBlocks.FLUID_TANK.get())
                .unlockedBy("has_power_reactor", has(PcBlocks.POWER_REACTOR.get())).save(out, id("steam_generator"));
        // ВВЭР: блок приводов СУЗ над корпусом из листа, внутрикорпусная шахта из циркалоя, пульт
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.PWR_REACTOR.get())
                .pattern("DCD").pattern("PRP").pattern("PZP").define('D', PcBlocks.CONTROL_ROD_DRIVE.get())
                .define('C', PcBlocks.CONTROL_ROD_CHANNEL.get()).define('R', PcBlocks.REACTOR_CONTROLLER.get())
                .define('P', PcItems.STEEL_PLATE.get()).define('Z', PcItems.ZIRCALOY_INGOT.get())
                .unlockedBy("has_reactor_controller", has(PcBlocks.REACTOR_CONTROLLER.get())).save(out, id("pwr_reactor"));
        // компенсатор давления: сосуд, ИПУ сверху, манометр, ТЭНы — медная обмотка
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.PRESSURIZER.get())
                .pattern("PVP").pattern("PXP").pattern("CGC").define('P', PcItems.STEEL_PLATE.get()).define('V', PcItems.SAFETY_VALVE.get())
                .define('X', AllBlocks.FLUID_TANK.get()).define('G', PcBlocks.INSTRUMENT_PRESSURE.get()).define('C', Items.COPPER_INGOT)
                .unlockedBy("has_pwr_reactor", has(PcBlocks.PWR_REACTOR.get())).save(out, id("pressurizer"));
        // кипящий реактор: корпус ВВЭР без КД, сверху сепараторы — барабан, снизу приводы стержней
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.BWR_REACTOR.get())
                .pattern("PSP").pattern("PRP").pattern("DZD").define('S', PcBlocks.STEAM_DRUM.get())
                .define('R', PcBlocks.REACTOR_CONTROLLER.get()).define('D', PcBlocks.CONTROL_ROD_DRIVE.get())
                .define('P', PcItems.STEEL_PLATE.get()).define('Z', PcItems.ZIRCALOY_INGOT.get())
                .unlockedBy("has_pwr_reactor", has(PcBlocks.PWR_REACTOR.get())).save(out, id("bwr_reactor"));
    }
}
