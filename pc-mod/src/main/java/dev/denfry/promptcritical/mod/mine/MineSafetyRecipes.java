package dev.denfry.promptcritical.mod.mine;

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
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Items;

/**
 * Рецепты проветривания и безопасности шахты I0 (08 §8.2.2–8.2.7): крепь, двери, заслоны, вентилятор, дегазация,
 * диспетчерская, канарейка, лампа, метанометр, самоспасатель, инертная пыль. Ванильные материалы и детали Create;
 * латунь (Create) — у ламп, клеток и приборов, как у настоящих.
 */
public final class MineSafetyRecipes extends RecipeProvider {
    public MineSafetyRecipes(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries);
    }

    public static DataProvider provider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        DataProvider inner = new MineSafetyRecipes(output, registries);
        return new DataProvider() {
            @Override
            public CompletableFuture<?> run(CachedOutput cache) {
                return inner.run(cache);
            }

            @Override
            public String getName() {
                return "Recipes: mine ventilation and safety";
            }
        };
    }

    private static String id(String name) {
        return PromptCritical.id("mine/" + name).toString();
    }

    @Override
    protected void buildRecipes(RecipeOutput out) {
        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, PcBlocks.MINE_SUPPORT.get(), 2)
                .pattern("LLL").pattern("L L").pattern("L L").define('L', ItemTags.LOGS)
                .unlockedBy("has_log", has(ItemTags.LOGS)).save(out, id("mine_support"));
        ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, PcBlocks.VENTILATION_DOOR.get())
                .pattern("PP").pattern("LP").pattern("PP").define('P', ItemTags.PLANKS).define('L', Items.LEATHER)
                .unlockedBy("has_planks", has(ItemTags.PLANKS)).save(out, id("ventilation_door"));
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, PcItems.STONE_DUST.get(), 4).requires(Items.CALCITE)
                .unlockedBy("has_calcite", has(Items.CALCITE)).save(out, id("stone_dust"));
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, PcItems.STONE_DUST.get(), 2).requires(Items.DIORITE)
                .unlockedBy("has_diorite", has(Items.DIORITE)).save(out, id("stone_dust_from_diorite"));
        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, PcBlocks.EXPLOSION_BARRIER.get(), 2)
                .pattern("PPP").pattern("DDD").pattern("PPP").define('P', ItemTags.PLANKS).define('D', PcItems.STONE_DUST.get())
                .unlockedBy("has_stone_dust", has(PcItems.STONE_DUST.get())).save(out, id("explosion_barrier"));
        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, PcItems.STONE_DUSTER.get())
                .pattern("S S").pattern("III").pattern("IBI").define('S', Items.STRING).define('I', Items.IRON_INGOT)
                .define('B', Items.BUCKET)
                .unlockedBy("has_stone_dust", has(PcItems.STONE_DUST.get())).save(out, id("stone_duster"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.MAIN_MINE_FAN.get())
                .pattern("IPI").pattern("SCS").pattern("IPI").define('I', Items.IRON_INGOT).define('P', AllItems.PROPELLER.get())
                .define('S', AllBlocks.SHAFT.get()).define('C', AllBlocks.COGWHEEL.get())
                .unlockedBy("has_propeller", has(AllItems.PROPELLER.get())).save(out, id("main_mine_fan"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.DEGASIFICATION_BOREHOLE.get())
                .pattern(" V ").pattern("IPI").pattern(" P ").define('V', AllBlocks.FLUID_VALVE.get())
                .define('P', AllBlocks.FLUID_PIPE.get()).define('I', Items.IRON_INGOT)
                .unlockedBy("has_pipe", has(AllBlocks.FLUID_PIPE.get())).save(out, id("degasification_borehole"));
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, PcBlocks.DEGASIFICATION_PUMP.get())
                .requires(AllBlocks.MECHANICAL_PUMP.get()).requires(Items.IRON_INGOT, 2).requires(AllBlocks.COGWHEEL.get())
                .unlockedBy("has_pump", has(AllBlocks.MECHANICAL_PUMP.get())).save(out, id("degasification_pump"));
        ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, PcBlocks.MINE_DISPATCH_CABINET.get())
                .pattern("IGI").pattern("RCR").pattern("III").define('I', Items.IRON_INGOT).define('G', Items.GLASS_PANE)
                .define('R', Items.REDSTONE).define('C', Items.COMPARATOR)
                .unlockedBy("has_comparator", has(Items.COMPARATOR)).save(out, id("mine_dispatch_cabinet"));
        ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, PcBlocks.CANARY_CAGE.get())
                .pattern("NNN").pattern("N N").pattern("NNN").define('N', AllItems.BRASS_NUGGET.get())
                .unlockedBy("has_brass", has(AllItems.BRASS_NUGGET.get())).save(out, id("canary_cage"));
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, PcItems.CANARY.get())
                .requires(Items.FEATHER, 2).requires(Items.YELLOW_DYE).requires(Items.WHEAT_SEEDS)
                .unlockedBy("has_cage", has(PcBlocks.CANARY_CAGE.get())).save(out, id("canary"));
        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, PcItems.FLAME_SAFETY_LAMP.get())
                .pattern(" B ").pattern("GTG").pattern(" B ").define('B', AllItems.BRASS_INGOT.get()).define('G', Items.IRON_BARS)
                .define('T', Items.TORCH)
                .unlockedBy("has_brass", has(AllItems.BRASS_INGOT.get())).save(out, id("flame_safety_lamp"));
        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, PcItems.METHANOMETER.get())
                .pattern(" G ").pattern("BRB").pattern("BCB").define('G', Items.GLASS_PANE).define('B', AllItems.BRASS_INGOT.get())
                .define('R', Items.REDSTONE).define('C', Items.COPPER_INGOT)
                .unlockedBy("has_brass", has(AllItems.BRASS_INGOT.get())).save(out, id("methanometer"));
        ShapelessRecipeBuilder.shapeless(RecipeCategory.TOOLS, PcItems.SELF_RESCUER.get())
                .requires(Items.IRON_INGOT, 2).requires(Items.LEATHER).requires(Items.CHARCOAL).requires(Items.COPPER_INGOT)
                .unlockedBy("has_leather", has(Items.LEATHER)).save(out, id("self_rescuer"));
    }
}
