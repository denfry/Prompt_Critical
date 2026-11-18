package dev.denfry.promptcritical.mod.chlor;

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

/** Рецепты хлорного производства и ПВХ. */
public final class ChlorRecipes extends RecipeProvider {
    public ChlorRecipes(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries);
    }

    public static DataProvider provider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        DataProvider inner = new ChlorRecipes(output, registries);
        return new DataProvider() {
            @Override
            public CompletableFuture<?> run(CachedOutput cache) {
                return inner.run(cache);
            }

            @Override
            public String getName() {
                return "Recipes: chlor-alkali and PVC";
            }
        };
    }

    private static String id(String name) {
        return PromptCritical.id("chlor/" + name).toString();
    }

    @Override
    protected void buildRecipes(RecipeOutput out) {
        var sheet = AllItems.IRON_SHEET.get();
        var copper = AllItems.COPPER_SHEET.get();
        var pipe = PcItems.SEAMLESS_PIPE.get();
        var tank = AllBlocks.FLUID_TANK.get();
        // рассольное отделение: растворитель, мешалки осаждения, осветлитель
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.BRINE_TREATER.get())
                .pattern("STS").pattern("PMP").pattern("SCS").define('S', sheet).define('T', tank).define('P', pipe)
                .define('M', AllBlocks.MECHANICAL_MIXER.get()).define('C', Items.CAULDRON)
                .unlockedBy("has_salt", has(PcItems.SALT.get())).save(out, id("brine_treater"));
        // ванна: титановый анод заменяет медная шина с покрытием, стальной катод, асбестовая диафрагма — глина
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.CHLOR_ALKALI_CELL.get())
                .pattern("CPC").pattern("SAS").pattern("CTC").define('C', copper).define('P', pipe).define('S', sheet)
                .define('A', Items.CLAY).define('T', tank).unlockedBy("has_copper_sheet", has(copper)).save(out, id("chlor_alkali_cell"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.RECTIFIER_UNIT.get())
                .pattern("CEC").pattern("BIB").pattern("SES").define('C', copper).define('E', AllItems.ELECTRON_TUBE.get())
                .define('B', Items.COPPER_BLOCK).define('I', Items.IRON_BLOCK).define('S', sheet)
                .unlockedBy("has_electron_tube", has(AllItems.ELECTRON_TUBE.get())).save(out, id("rectifier_unit"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.CHLORINE_LIQUEFIER.get())
                .pattern("SPS").pattern("TIT").pattern("SKS").define('S', sheet).define('P', pipe).define('T', tank)
                .define('I', Items.PACKED_ICE).define('K', AllBlocks.MECHANICAL_PUMP.get())
                .unlockedBy("has_chlor_alkali_cell", has(PcBlocks.CHLOR_ALKALI_CELL.get())).save(out, id("chlorine_liquefier"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.EDC_UNIT.get())
                .pattern("SPS").pattern("TVT").pattern("SCS").define('S', sheet).define('P', pipe).define('T', tank)
                .define('V', AllBlocks.FLUID_VALVE.get()).define('C', copper)
                .unlockedBy("has_chlor_alkali_cell", has(PcBlocks.CHLOR_ALKALI_CELL.get())).save(out, id("edc_unit"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.EDC_CRACKER.get())
                .pattern("BPB").pattern("PFP").pattern("BPB").define('B', Items.BRICKS).define('P', pipe).define('F', Items.BLAST_FURNACE)
                .unlockedBy("has_edc_unit", has(PcBlocks.EDC_UNIT.get())).save(out, id("edc_cracker"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.PVC_REACTOR.get())
                .pattern("SMS").pattern("STS").pattern("SPS").define('S', sheet).define('M', AllBlocks.MECHANICAL_MIXER.get())
                .define('T', tank).define('P', pipe).unlockedBy("has_edc_cracker", has(PcBlocks.EDC_CRACKER.get())).save(out, id("pvc_reactor"));
    }
}
