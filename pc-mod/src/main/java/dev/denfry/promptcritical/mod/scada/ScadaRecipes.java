package dev.denfry.promptcritical.mod.scada;

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

/** Рецепт шлюза телемеханики: шкаф из листа, электронные лампы, медный провод линии связи. */
public final class ScadaRecipes extends RecipeProvider {
    public ScadaRecipes(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries);
    }

    public static DataProvider provider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        DataProvider inner = new ScadaRecipes(output, registries);
        return new DataProvider() {
            @Override
            public CompletableFuture<?> run(CachedOutput cache) {
                return inner.run(cache);
            }

            @Override
            public String getName() {
                return "Recipes: telemetry";
            }
        };
    }

    @Override
    protected void buildRecipes(RecipeOutput out) {
        var sheet = AllItems.IRON_SHEET.get();
        ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, PcBlocks.RTU_STATION.get())
                // A4: КП телемеханики — микросхемы на плате (11 §11.5.4)
                .pattern("STS").pattern("WRW").pattern("SBS").define('S', sheet).define('T', PcItems.INTEGRATED_CIRCUIT.get())
                .define('W', PcItems.COPPER_WIRE.get()).define('R', PcItems.MEMORY_CHIP.get()).define('B', PcItems.PRINTED_CIRCUIT_BOARD.get())
                .unlockedBy("has_integrated_circuit", has(PcItems.INTEGRATED_CIRCUIT.get()))
                .save(out, PromptCritical.id("scada/rtu_station").toString());
        // историан: память (архив) и микропроцессор (сжатие) в стойке
        ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, PcBlocks.HISTORIAN_SERVER.get())
                .pattern("SMS").pattern("MPM").pattern("SBS").define('S', sheet).define('M', PcItems.MEMORY_CHIP.get())
                .define('P', PcItems.MICROPROCESSOR.get()).define('B', PcItems.PRINTED_CIRCUIT_BOARD.get())
                .unlockedBy("has_memory_chip", has(PcItems.MEMORY_CHIP.get()))
                .save(out, PromptCritical.id("scada/historian_server").toString());
        // A5: сервер завода — микропроцессор и память на плате, связь с КП по медной линии
        ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, PcBlocks.MES_SERVER.get())
                .pattern("SPS").pattern("MBM").pattern("SWS").define('S', sheet).define('P', PcItems.MICROPROCESSOR.get())
                .define('M', PcItems.MEMORY_CHIP.get()).define('B', PcItems.PRINTED_CIRCUIT_BOARD.get()).define('W', PcItems.COPPER_WIRE.get())
                .unlockedBy("has_microprocessor", has(PcItems.MICROPROCESSOR.get()))
                .save(out, PromptCritical.id("scada/mes_server").toString());
        // цифровой двойник и тренажёр (03d §3d.9.1, 11 §11.3.2): мониторы — стекло, расчёт — микропроцессор и память
        var glass = net.minecraft.world.item.Items.GLASS_PANE;
        ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, PcBlocks.SIMULATION_WORKSTATION.get())
                .pattern("GPG").pattern("MBM").pattern("S S").define('G', glass).define('P', PcItems.MICROPROCESSOR.get())
                .define('M', PcItems.MEMORY_CHIP.get()).define('B', PcItems.PRINTED_CIRCUIT_BOARD.get()).define('S', sheet)
                .unlockedBy("has_microprocessor", has(PcItems.MICROPROCESSOR.get()))
                .save(out, PromptCritical.id("scada/simulation_workstation").toString());
        ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, PcBlocks.TRAINING_SIMULATOR.get())
                .pattern("SPS").pattern("PMP").pattern("SBS").define('S', sheet).define('P', PcItems.MICROPROCESSOR.get())
                .define('M', PcItems.MEMORY_CHIP.get()).define('B', PcItems.PRINTED_CIRCUIT_BOARD.get())
                .unlockedBy("has_microprocessor", has(PcItems.MICROPROCESSOR.get()))
                .save(out, PromptCritical.id("scada/training_simulator").toString());
        ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, PcBlocks.SIMULATOR_CONSOLE.get())
                .pattern("GGG").pattern("WBW").pattern("SSS").define('G', glass).define('W', PcItems.COPPER_WIRE.get())
                .define('B', PcItems.PRINTED_CIRCUIT_BOARD.get()).define('S', sheet)
                .unlockedBy("has_printed_circuit_board", has(PcItems.PRINTED_CIRCUIT_BOARD.get()))
                .save(out, PromptCritical.id("scada/simulator_console").toString());
        ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, PcBlocks.INSTRUCTOR_STATION.get())
                .pattern("GPG").pattern("WBW").pattern("S S").define('G', glass).define('P', PcItems.MICROPROCESSOR.get())
                .define('W', PcItems.COPPER_WIRE.get()).define('B', PcItems.PRINTED_CIRCUIT_BOARD.get()).define('S', sheet)
                .unlockedBy("has_microprocessor", has(PcItems.MICROPROCESSOR.get()))
                .save(out, PromptCritical.id("scada/instructor_station").toString());
    }
}
