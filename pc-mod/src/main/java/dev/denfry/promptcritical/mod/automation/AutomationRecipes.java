package dev.denfry.promptcritical.mod.automation;

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
import net.minecraft.world.level.ItemLike;

/**
 * Рецепты автоматики A1–A2 (03d §3d.3–3d.4): катушка реле — медная проволока на железном сердечнике; реле — катушка,
 * контакты, колпак из стекла на каменном (вместо бакелита) основании; блоки A2 — ламповый решающий усилитель (стекло,
 * факел-лампа, проволока) и своя «начинка» (конденсатор, диоды, реле). Сталь — железо до этапа 10.
 */
public final class AutomationRecipes extends RecipeProvider {
    public AutomationRecipes(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries);
    }

    public static DataProvider provider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        DataProvider inner = new AutomationRecipes(output, registries);
        return new DataProvider() {
            @Override
            public CompletableFuture<?> run(CachedOutput cache) {
                return inner.run(cache);
            }

            @Override
            public String getName() {
                return "Recipes: automation";
            }
        };
    }

    private static String id(String name) {
        return PromptCritical.id("automation/" + name).toString();
    }

    @Override
    protected void buildRecipes(RecipeOutput out) {
        ItemLike wire = PcItems.COPPER_WIRE.get(), coil = PcItems.RELAY_COIL.get(), relay = PcBlocks.RELAY.get();
        ItemLike summer = PcBlocks.ANALOG_SUMMER.get();
        var hasWire = has(wire);
        var hasCoil = has(coil);

        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, PcItems.WIRING_TOOL.get()).pattern("  I").pattern(" W ").pattern("S  ")
                .define('I', Items.IRON_INGOT).define('W', wire).define('S', Items.STICK)
                .unlockedBy("has_wire", hasWire).save(out, id("wiring_tool"));
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, coil, 2).requires(wire, 4).requires(Items.IRON_INGOT)
                .unlockedBy("has_wire", hasWire).save(out, id("relay_coil"));

        // A1
        ShapelessRecipeBuilder.shapeless(RecipeCategory.REDSTONE, relay).requires(coil).requires(wire, 2).requires(Items.GLASS_PANE)
                .requires(Items.STONE_SLAB).unlockedBy("has_coil", hasCoil).save(out, id("relay"));
        ShapelessRecipeBuilder.shapeless(RecipeCategory.REDSTONE, PcBlocks.RELAY_LATCHING.get()).requires(coil, 2).requires(wire, 2)
                .requires(Items.GLASS_PANE).requires(Items.STONE_SLAB).requires(Items.IRON_NUGGET)
                .unlockedBy("has_coil", hasCoil).save(out, id("relay_latching"));
        ShapelessRecipeBuilder.shapeless(RecipeCategory.REDSTONE, PcBlocks.TIME_RELAY.get()).requires(relay).requires(Items.CLOCK)
                .unlockedBy("has_relay", has(relay)).save(out, id("time_relay"));
        ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, PcBlocks.STEPPING_SWITCH.get()).pattern("WCW").pattern("BSB").pattern("WCW")
                .define('W', wire).define('C', coil).define('B', AllItems.BRASS_INGOT.get()).define('S', AllBlocks.SHAFT.get())
                .unlockedBy("has_coil", hasCoil).save(out, id("stepping_switch"));
        ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, PcBlocks.CAM_CONTROLLER.get()).pattern("WWW").pattern("BSB").pattern("III")
                .define('W', wire).define('B', AllItems.BRASS_INGOT.get()).define('S', AllBlocks.SHAFT.get()).define('I', PcItems.STEEL_PLATE.get())
                .unlockedBy("has_wire", hasWire).save(out, id("cam_controller"));
        ShapelessRecipeBuilder.shapeless(RecipeCategory.REDSTONE, PcBlocks.PROCESS_SWITCH.get()).requires(relay).requires(Items.COPPER_INGOT)
                .requires(AllBlocks.FLUID_PIPE.get()).unlockedBy("has_relay", has(relay)).save(out, id("process_switch"));
        ShapelessRecipeBuilder.shapeless(RecipeCategory.REDSTONE, PcBlocks.RELAY_POWER_SUPPLY.get()).requires(Items.IRON_INGOT, 2)
                .requires(wire, 4).requires(coil).requires(Items.QUARTZ).unlockedBy("has_coil", hasCoil).save(out, id("relay_power_supply"));
        ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, PcBlocks.RELAY_RACK.get()).pattern("III").pattern("IDI").pattern("III")
                .define('I', PcItems.STEEL_PLATE.get()).define('D', Items.IRON_DOOR)
                .unlockedBy("has_relay", has(relay)).save(out, id("relay_rack"));
        ShapelessRecipeBuilder.shapeless(RecipeCategory.REDSTONE, PcBlocks.REDSTONE_INTERFACE.get()).requires(relay).requires(Items.REDSTONE_TORCH)
                .unlockedBy("has_relay", has(relay)).save(out, id("redstone_interface"));

        // пульт 03c (проводной режим)
        ShapelessRecipeBuilder.shapeless(RecipeCategory.REDSTONE, PcBlocks.PUSHBUTTON.get()).requires(Items.STONE_BUTTON).requires(wire)
                .requires(Items.IRON_NUGGET).unlockedBy("has_wire", hasWire).save(out, id("pushbutton"));
        ShapelessRecipeBuilder.shapeless(RecipeCategory.REDSTONE, PcBlocks.SELECTOR_SWITCH.get()).requires(Items.LEVER).requires(wire, 2)
                .requires(Items.IRON_NUGGET).unlockedBy("has_wire", hasWire).save(out, id("selector_switch"));
        ShapelessRecipeBuilder.shapeless(RecipeCategory.REDSTONE, PcBlocks.KEY_SWITCH.get()).requires(Items.LEVER).requires(wire, 2)
                .requires(Items.GOLD_NUGGET).unlockedBy("has_wire", hasWire).save(out, id("key_switch"));
        ShapelessRecipeBuilder.shapeless(RecipeCategory.REDSTONE, PcBlocks.INDICATOR_LAMP.get()).requires(Items.GLOWSTONE_DUST)
                .requires(Items.GLASS_PANE).requires(wire).unlockedBy("has_wire", hasWire).save(out, id("indicator_lamp"));
        ShapelessRecipeBuilder.shapeless(RecipeCategory.REDSTONE, PcBlocks.GAS_DETECTOR.get()).requires(Items.IRON_INGOT)
                .requires(Items.GLASS_PANE).requires(wire, 2).requires(Items.REDSTONE).requires(Items.YELLOW_DYE)
                .unlockedBy("has_wire", hasWire).save(out, id("gas_detector"));

        // A2: ламповый решающий усилитель
        ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, summer).pattern("IGI").pattern("WTW").pattern("IRI")
                .define('I', Items.IRON_INGOT).define('G', Items.GLASS_PANE).define('W', wire).define('T', Items.REDSTONE_TORCH)
                .define('R', Items.REDSTONE).unlockedBy("has_wire", hasWire).save(out, id("analog_summer"));
        ShapelessRecipeBuilder.shapeless(RecipeCategory.REDSTONE, PcBlocks.ANALOG_INTEGRATOR.get()).requires(summer).requires(Items.COPPER_INGOT)
                .requires(Items.PAPER).unlockedBy("has_summer", has(summer)).save(out, id("analog_integrator"));
        ShapelessRecipeBuilder.shapeless(RecipeCategory.REDSTONE, PcBlocks.ANALOG_DIFFERENTIATOR.get()).requires(summer).requires(Items.COPPER_INGOT)
                .requires(Items.PAPER).requires(Items.REDSTONE).unlockedBy("has_summer", has(summer)).save(out, id("analog_differentiator"));
        ShapelessRecipeBuilder.shapeless(RecipeCategory.REDSTONE, PcBlocks.ANALOG_MULTIPLIER.get()).requires(summer).requires(Items.COMPARATOR)
                .unlockedBy("has_summer", has(summer)).save(out, id("analog_multiplier"));
        ShapelessRecipeBuilder.shapeless(RecipeCategory.REDSTONE, PcBlocks.ANALOG_FUNCTION.get()).requires(summer).requires(Items.QUARTZ, 2)
                .requires(Items.REDSTONE).unlockedBy("has_summer", has(summer)).save(out, id("analog_function"));
        ShapelessRecipeBuilder.shapeless(RecipeCategory.REDSTONE, PcBlocks.ANALOG_COMPARATOR.get()).requires(summer).requires(relay)
                .unlockedBy("has_summer", has(summer)).save(out, id("analog_comparator"));
        ShapelessRecipeBuilder.shapeless(RecipeCategory.REDSTONE, PcBlocks.MANUAL_STATION.get()).requires(Items.IRON_INGOT, 2)
                .requires(Items.GLASS_PANE, 2).requires(Items.LEVER).requires(wire).requires(Items.REDSTONE)
                .unlockedBy("has_wire", hasWire).save(out, id("manual_station"));
        ShapelessRecipeBuilder.shapeless(RecipeCategory.REDSTONE, PcBlocks.SIGNAL_CONVERTER.get()).requires(coil).requires(wire)
                .requires(Items.REDSTONE).requires(Items.IRON_NUGGET).unlockedBy("has_coil", hasCoil).save(out, id("signal_converter"));
    }
}
