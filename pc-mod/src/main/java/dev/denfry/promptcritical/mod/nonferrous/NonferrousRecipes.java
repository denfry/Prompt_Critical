package dev.denfry.promptcritical.mod.nonferrous;

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
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * Рецепты цветной металлургии (08 §8.8). Вехи (06: M3 — эра I1, M8 — I2) держат ингредиенты: стальной лист и
 * бесшовная труба проката I1, механизмы Create, огнеупор.
 */
public final class NonferrousRecipes extends RecipeProvider {
    public NonferrousRecipes(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries);
    }

    public static DataProvider provider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        DataProvider inner = new NonferrousRecipes(output, registries);
        return new DataProvider() {
            @Override
            public CompletableFuture<?> run(CachedOutput cache) {
                return inner.run(cache);
            }

            @Override
            public String getName() {
                return "Recipes: nonferrous metallurgy";
            }
        };
    }

    private static String id(String name) {
        return PromptCritical.id("nonferrous/" + name).toString();
    }

    @Override
    protected void buildRecipes(RecipeOutput out) {
        var plate = PcItems.STEEL_PLATE.get();
        var pipe = PcItems.SEAMLESS_PIPE.get();
        var tank = AllBlocks.FLUID_TANK.get();
        var brick = PcItems.REFRACTORY_BRICK.get();
        // Байер: автоклав — толстостенный сосуд на насосе, декомпозёр — бак с мешалкой, печь — футерованная труба на валу
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.BAYER_DIGESTER.get())
                .pattern("PTP").pattern("SFS").pattern("PMP").define('P', plate).define('T', tank).define('S', pipe)
                .define('F', Items.FURNACE).define('M', AllBlocks.MECHANICAL_PUMP.get())
                .unlockedBy("has_raw_bauxite", has(PcItems.RAW_BAUXITE.get())).save(out, id("bayer_digester"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.ALUMINA_PRECIPITATOR.get())
                .pattern("PXP").pattern("PTP").pattern("PTP").define('P', plate).define('T', tank)
                .define('X', AllBlocks.MECHANICAL_MIXER.get())
                .unlockedBy("has_bayer_digester", has(PcBlocks.BAYER_DIGESTER.get())).save(out, id("alumina_precipitator"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.ALUMINA_CALCINER.get())
                .pattern("BPB").pattern("SFS").pattern("BPB").define('B', brick).define('P', plate)
                .define('S', AllBlocks.SHAFT.get()).define('F', Items.BLAST_FURNACE)
                .unlockedBy("has_aluminium_hydroxide", has(PcItems.ALUMINIUM_HYDROXIDE.get())).save(out, id("alumina_calciner"));
        // Холл–Эру: стальной кожух на подовых блоках, анодная рама над ним (08 §8.8.2)
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.REDUCTION_POT.get())
                .pattern("PAP").pattern("BCB").pattern("PCP").define('P', plate).define('A', PcItems.CARBON_ANODE.get())
                .define('B', brick).define('C', PcItems.CATHODE_BLOCK.get())
                .unlockedBy("has_alumina", has(PcItems.ALUMINA.get())).save(out, id("reduction_pot"));
        // обожжённый анод: нефтяной или пековый кокс на пеке (85 : 15 по массе, §8.8.2), мешки по 100 кг
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, PcItems.CARBON_ANODE.get(), 7)
                .requires(Ingredient.of(PcItems.PETROLEUM_COKE.get(), PcItems.PITCH_COKE.get()), 6).requires(PcItems.COAL_TAR_PITCH.get())
                .unlockedBy("has_coal_tar_pitch", has(PcItems.COAL_TAR_PITCH.get())).save(out, id("carbon_anode"));
        // подовый блок: кокс с блюмсом-токоподводом [BALANCE]
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, PcItems.CATHODE_BLOCK.get(), 2)
                .requires(PcItems.COKE.get(), 3).requires(PcItems.STEEL_INGOT.get())
                .unlockedBy("has_coke", has(PcItems.COKE.get())).save(out, id("cathode_block"));
        // медь и никель (08 §8.8.3–8.8.4): флотомашина — бак с импеллером, печь — кирпичный свод, конвертер — бочка на
        // валу, ванны — футерованные свинцом баки с медной ошиновкой
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.FLOTATION_CELL.get())
                .pattern("PXP").pattern("PTP").define('P', plate).define('T', tank).define('X', AllBlocks.MECHANICAL_MIXER.get())
                .unlockedBy("has_raw_chalcopyrite", has(PcItems.RAW_CHALCOPYRITE.get())).save(out, id("flotation_cell"));
        // чертёж машины реального масштаба: строится на стройплощадке по массе прототипа (SitePlans)
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, dev.denfry.promptcritical.mod.site.SiteRegistry.FLOTATION_CELL_REAL_DRAWING.get())
                .pattern("PPP").pattern("SBS").define('P', Items.PAPER).define('S', PcItems.STEEL_PLATE.get())
                .define('B', PcBlocks.FLOTATION_CELL.get()).unlockedBy("has_flotation_cell", has(PcBlocks.FLOTATION_CELL.get()))
                .save(out, id("flotation_cell_real_drawing"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.MATTE_SMELTER.get())
                .pattern("BBB").pattern("BFB").pattern("PSP").define('B', brick).define('F', Items.BLAST_FURNACE).define('P', plate)
                .define('S', pipe)
                .unlockedBy("has_flotation_concentrate", has(PcItems.FLOTATION_CONCENTRATE.get())).save(out, id("matte_smelter"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.PEIRCE_SMITH_CONVERTER.get())
                .pattern("PBP").pattern("SBS").pattern("PCP").define('P', plate).define('B', brick).define('S', AllBlocks.SHAFT.get())
                .define('C', AllBlocks.ENCASED_FAN.get())
                .unlockedBy("has_sulfide_matte", has(PcItems.SULFIDE_MATTE.get())).save(out, id("peirce_smith_converter"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.ELECTROREFINING_CELL.get())
                .pattern("CCC").pattern("LTL").define('C', Items.COPPER_INGOT).define('L', PcBlocks.LEAD_BLOCK.get()).define('T', tank)
                .unlockedBy("has_rectifier_unit", has(PcBlocks.RECTIFIER_UNIT.get())).save(out, id("electrorefining_cell"));
        // катодная медь (100 кг) — в ванильные слитки меди для крафта Create и ванилы [BALANCE]
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, Items.COPPER_INGOT, 9).requires(PcItems.COPPER_CATHODE.get())
                .unlockedBy("has_copper_cathode", has(PcItems.COPPER_CATHODE.get())).save(out, id("copper_ingot_from_cathode"));
        // аффинаж (M8): фарфоровые сосуды под вытяжкой; работать он начнёт с азотной кислотой и HCl химии I3
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.PGM_REFINERY.get())
                .pattern("GFG").pattern("TPT").pattern("SSS").define('G', Items.GLASS).define('F', AllBlocks.ENCASED_FAN.get())
                .define('T', tank).define('P', Items.FLOWER_POT).define('S', plate)
                .unlockedBy("has_anode_slime", has(PcItems.ANODE_SLIME.get())).save(out, id("pgm_refinery"));
        // олово (08 §8.8.5): бронза и припой (11); оловянная бронза ≈ 10 % Sn [BALANCE]
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, PcItems.BRONZE_INGOT.get(), 4).requires(Items.COPPER_INGOT, 3)
                .requires(PcItems.TIN_INGOT.get())
                .unlockedBy("has_tin_ingot", has(PcItems.TIN_INGOT.get())).save(out, id("bronze_ingot_from_tin"));
    }
}
