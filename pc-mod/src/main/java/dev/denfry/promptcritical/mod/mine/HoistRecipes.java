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
 * Рецепты подъёма и водоотлива I0 (08 §8.2.8–8.2.9): железо, цепи и детали Create. Канат до канатовьющей машины
 * (M3) вьют из железных цепей — 6×19 ОС ⌀ 32 мм.
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
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.MINE_CAGE.get())
                .pattern("BBB").pattern("BDB").pattern("III").define('B', Items.IRON_BARS).define('D', Items.IRON_DOOR)
                .define('I', Items.IRON_INGOT)
                .unlockedBy("has_iron_door", has(Items.IRON_DOOR)).save(out, id("mine_cage"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.MINE_DRAINAGE_PUMP.get())
                .pattern("IPI").pattern("SCS").pattern("III").define('I', Items.IRON_INGOT).define('P', AllBlocks.MECHANICAL_PUMP.get())
                .define('S', AllBlocks.SHAFT.get()).define('C', AllBlocks.COGWHEEL.get())
                .unlockedBy("has_mechanical_pump", has(AllBlocks.MECHANICAL_PUMP.get())).save(out, id("mine_drainage_pump"));
    }
}
