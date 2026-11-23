package dev.denfry.promptcritical.mod.mine;

import com.simibubi.create.AllBlocks;
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

/**
 * Рецепты подъёма, водоотлива и механизированной лавы I0 (08 §8.2.3, 8.2.8–8.2.9): железо, цепи и детали Create.
 * Канат до канатовьющей машины (M3) вьют из железных цепей — 6×19 ОС ⌀ 32 мм.
 */
public final class HoistRecipes extends RecipeProvider {
    public HoistRecipes(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries);
    }

    /** Провайдер под своим именем: {@code RecipeProvider#getName} финальный, а имена провайдеров уникальны. */
    public static DataProvider provider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        DataProvider inner = new HoistRecipes(output, registries);
        return new DataProvider() {
            @Override
            public CompletableFuture<?> run(CachedOutput cache) {
                return inner.run(cache);
            }

            @Override
            public String getName() {
                return "Recipes: mine hoist and drainage";
            }
        };
    }

    private static String id(String name) {
        return PromptCritical.id("mine/" + name).toString();
    }

    @Override
    protected void buildRecipes(RecipeOutput out) {
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcItems.WIRE_ROPE.get(), 2)
                .pattern("CCC").pattern("CIC").pattern("CCC").define('C', Items.CHAIN).define('I', Items.IRON_INGOT)
                .unlockedBy("has_chain", has(Items.CHAIN)).save(out, id("wire_rope"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.MINE_HOIST.get())
                .pattern("III").pattern("SDS").pattern("KCK").define('I', Items.IRON_INGOT).define('S', AllBlocks.SHAFT.get())
                .define('D', AllBlocks.LARGE_COGWHEEL.get()).define('K', Items.IRON_BLOCK).define('C', AllBlocks.COGWHEEL.get())
                .unlockedBy("has_large_cogwheel", has(AllBlocks.LARGE_COGWHEEL.get())).save(out, id("mine_hoist"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.HEADFRAME.get())
                .pattern("B B").pattern(" B ").pattern("B B").define('B', Items.IRON_BARS)
                .unlockedBy("has_iron_bars", has(Items.IRON_BARS)).save(out, id("headframe"));
        // чертёж стального копра: копёр строится на стройплощадке по массе прототипа (SitePlans)
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, dev.denfry.promptcritical.mod.site.SiteRegistry.HEADFRAME_STEEL_DRAWING.get())
                .pattern("PPP").pattern("BWB").pattern("B B").define('P', Items.PAPER).define('B', Items.IRON_BARS)
                .define('W', AllBlocks.LARGE_COGWHEEL.get())
                .unlockedBy("has_iron_bars", has(Items.IRON_BARS)).save(out, id("headframe_steel_drawing"));
        // чертёж паровой подъёмной машины: машина строится на стройплощадке по массе прототипа (SitePlans)
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, dev.denfry.promptcritical.mod.site.SiteRegistry.WINDING_ENGINE_DRAWING.get())
                .pattern("PPP").pattern("XWX").pattern("SCS").define('P', Items.PAPER).define('X', Items.PISTON)
                .define('W', AllBlocks.LARGE_COGWHEEL.get()).define('S', AllBlocks.SHAFT.get()).define('C', Items.CHAIN)
                .unlockedBy("has_piston", has(Items.PISTON)).save(out, id("winding_engine_drawing"));
        // чертёж трёхплунжерного насоса: насос строится на стройплощадке по массе прототипа (SitePlans)
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, dev.denfry.promptcritical.mod.site.SiteRegistry.MINE_DRAINAGE_PUMP_GEARED_DRAWING.get())
                .pattern("PPP").pattern("XCX").pattern("BBB").define('P', Items.PAPER).define('X', Items.PISTON)
                .define('C', AllBlocks.COGWHEEL.get()).define('B', Items.BUCKET)
                .unlockedBy("has_piston", has(Items.PISTON)).save(out, id("mine_drainage_pump_geared_drawing"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.MINE_CAGE.get())
                .pattern("BBB").pattern("BDB").pattern("III").define('B', Items.IRON_BARS).define('D', Items.IRON_DOOR)
                .define('I', Items.IRON_INGOT)
                .unlockedBy("has_iron_door", has(Items.IRON_DOOR)).save(out, id("mine_cage"));
        // стволовой сигнальный аппарат: звонок, лампа «клеть здесь», кнопка вызова в чугунном корпусе
        ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, PcBlocks.SHAFT_SIGNAL.get())
                .pattern(" B ").pattern("RIL").pattern(" I ").define('B', Items.BELL).define('R', Items.REDSTONE)
                .define('I', Items.IRON_INGOT).define('L', Items.REDSTONE_LAMP)
                .unlockedBy("has_mine_cage", has(PcBlocks.MINE_CAGE.get())).save(out, id("shaft_signal"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.MINE_DRAINAGE_PUMP.get())
                .pattern("IPI").pattern("SCS").pattern("III").define('I', Items.IRON_INGOT).define('P', AllBlocks.MECHANICAL_PUMP.get())
                .define('S', AllBlocks.SHAFT.get()).define('C', AllBlocks.COGWHEEL.get())
                .unlockedBy("has_mechanical_pump", has(AllBlocks.MECHANICAL_PUMP.get())).save(out, id("mine_drainage_pump"));
        // механизированная лава (08 §8.2.3): шнеки — буры Create, гидростойки — поршни
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.LONGWALL_SHEARER.get())
                .pattern("DGD").pattern("III").define('D', AllBlocks.MECHANICAL_DRILL.get()).define('G', AllBlocks.GEARBOX.get())
                .define('I', Items.IRON_BLOCK)
                .unlockedBy("has_mechanical_drill", has(AllBlocks.MECHANICAL_DRILL.get())).save(out, id("longwall_shearer"));
        // чертёж комбайна реального масштаба: строится на стройплощадке по массе прототипа (SitePlans)
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, dev.denfry.promptcritical.mod.site.SiteRegistry.LONGWALL_SHEARER_REAL_DRAWING.get())
                .pattern("PPP").pattern("DGD").pattern("III").define('P', Items.PAPER).define('D', AllBlocks.MECHANICAL_DRILL.get())
                .define('G', AllBlocks.GEARBOX.get()).define('I', Items.IRON_BLOCK)
                .unlockedBy("has_mechanical_drill", has(AllBlocks.MECHANICAL_DRILL.get())).save(out, id("longwall_shearer_real_drawing"));
        // чертёж щитовой секции крепи реального масштаба (SitePlans)
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, dev.denfry.promptcritical.mod.site.SiteRegistry.POWERED_ROOF_SUPPORT_REAL_DRAWING.get())
                .pattern("PPP").pattern("III").pattern("T T").define('P', Items.PAPER).define('I', Items.IRON_INGOT).define('T', Items.PISTON)
                .unlockedBy("has_piston", has(Items.PISTON)).save(out, id("powered_roof_support_real_drawing"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.POWERED_ROOF_SUPPORT.get())
                .pattern("III").pattern("P P").pattern("III").define('I', Items.IRON_INGOT).define('P', Items.PISTON)
                .unlockedBy("has_piston", has(Items.PISTON)).save(out, id("powered_roof_support"));
    }
}
