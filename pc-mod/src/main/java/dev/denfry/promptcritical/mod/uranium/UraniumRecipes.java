package dev.denfry.promptcritical.mod.uranium;

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
import net.minecraft.world.level.ItemLike;

/** Рецепты передела урана: аппараты гидрометаллургии и конверсии. */
public final class UraniumRecipes extends RecipeProvider {
    public UraniumRecipes(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries);
    }

    public static DataProvider provider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        DataProvider inner = new UraniumRecipes(output, registries);
        return new DataProvider() {
            @Override
            public CompletableFuture<?> run(CachedOutput cache) {
                return inner.run(cache);
            }

            @Override
            public String getName() {
                return "Recipes: uranium front end";
            }
        };
    }

    private static String id(String name) {
        return PromptCritical.id("uranium/" + name).toString();
    }

    private static void vessel(RecipeOutput out, ItemLike result, ItemLike core, ItemLike side, String unlockName, ItemLike unlock, String name) {
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, result)
                .pattern("SPS").pattern("XCX").pattern("SPS").define('S', AllItems.IRON_SHEET.get()).define('P', PcItems.SEAMLESS_PIPE.get())
                .define('X', side).define('C', core).unlockedBy(unlockName, has(unlock)).save(out, id(name));
    }

    @Override
    protected void buildRecipes(RecipeOutput out) {
        var tank = AllBlocks.FLUID_TANK.get();
        vessel(out, PcBlocks.ACID_PLANT.get(), Items.BLAST_FURNACE, tank, "has_pyrite", PcBlocks.PYRITE_ORE.get(), "acid_plant");
        vessel(out, PcBlocks.HF_KILN.get(), Items.BLAST_FURNACE, Items.BRICKS, "has_fluorite", PcBlocks.FLUORITE_ORE.get(), "hf_kiln");
        vessel(out, PcBlocks.LEACH_TANK.get(), AllBlocks.MECHANICAL_MIXER.get(), tank, "has_ground_uranium_ore",
                PcItems.GROUND_URANIUM_ORE.get(), "leach_tank");
        vessel(out, PcBlocks.URANIUM_ION_EXCHANGE.get(), tank, AllBlocks.FLUID_VALVE.get(), "has_ion_exchange_resin",
                PcItems.ION_EXCHANGE_RESIN_SBA.get(), "uranium_ion_exchange");
        vessel(out, PcBlocks.PRECIPITATION_TANK.get(), AllBlocks.MECHANICAL_MIXER.get(), Items.PAPER, "has_leach_tank",
                PcBlocks.LEACH_TANK.get(), "precipitation_tank");
        vessel(out, PcBlocks.ROTARY_CALCINER.get(), Items.BLAST_FURNACE, AllBlocks.SHAFT.get(), "has_yellowcake", PcItems.YELLOWCAKE.get(),
                "rotary_calciner");
        vessel(out, PcBlocks.REDUCTION_FURNACE.get(), Items.BLAST_FURNACE, AllItems.COPPER_SHEET.get(), "has_uo3_powder",
                PcItems.UO3_POWDER.get(), "reduction_furnace");
        vessel(out, PcBlocks.HYDROFLUORINATION_REACTOR.get(), Items.BLAST_FURNACE, AllItems.COPPER_SHEET.get(), "has_uo2_powder",
                PcItems.UO2_POWDER.get(), "hydrofluorination_reactor");
        // фтор съедает всё, кроме меди, никеля и монеля: внутри — медь
        vessel(out, PcBlocks.FLUORINATION_REACTOR.get(), Items.COPPER_BLOCK, AllItems.COPPER_SHEET.get(), "has_uf4_powder",
                PcItems.UF4_POWDER.get(), "fluorination_reactor");
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.FLUORINE_CELL.get())
                .pattern("CEC").pattern("SGS").pattern("CTC").define('C', AllItems.COPPER_SHEET.get()).define('E', AllItems.ELECTRON_TUBE.get())
                .define('S', AllItems.IRON_SHEET.get()).define('G', Items.COAL_BLOCK).define('T', tank)
                .unlockedBy("has_rectifier_unit", has(PcBlocks.RECTIFIER_UNIT.get())).save(out, id("fluorine_cell"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcItems.UF6_CYLINDER_5A.get())
                .pattern(" C ").pattern("S S").pattern("SSS").define('C', AllBlocks.FLUID_VALVE.get()).define('S', AllItems.COPPER_SHEET.get())
                .unlockedBy("has_uf4_powder", has(PcItems.UF4_POWDER.get())).save(out, id("uf6_cylinder_5a"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcItems.UF6_CYLINDER_48Y.get())
                .pattern("SVS").pattern("BTB").pattern("SBS").define('S', AllItems.IRON_SHEET.get()).define('V', AllBlocks.FLUID_VALVE.get())
                .define('B', Items.IRON_BLOCK).define('T', tank)
                .unlockedBy("has_uf4_powder", has(PcItems.UF4_POWDER.get())).save(out, id("uf6_cylinder_48y"));
    }
}
