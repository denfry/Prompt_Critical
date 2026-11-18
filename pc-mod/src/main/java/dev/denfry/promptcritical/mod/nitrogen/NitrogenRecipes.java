package dev.denfry.promptcritical.mod.nitrogen;

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

/** Рецепты азотного производства: установки и загрузки катализаторов. */
public final class NitrogenRecipes extends RecipeProvider {
    public NitrogenRecipes(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries);
    }

    public static DataProvider provider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        DataProvider inner = new NitrogenRecipes(output, registries);
        return new DataProvider() {
            @Override
            public CompletableFuture<?> run(CachedOutput cache) {
                return inner.run(cache);
            }

            @Override
            public String getName() {
                return "Recipes: nitrogen";
            }
        };
    }

    private static String id(String name) {
        return PromptCritical.id("nitrogen/" + name).toString();
    }

    @Override
    protected void buildRecipes(RecipeOutput out) {
        var sheet = AllItems.IRON_SHEET.get();
        var pipe = PcItems.SEAMLESS_PIPE.get();
        // печь риформинга: трубчатая печь в футеровке из огнеупора, реакционные трубы, шахта вторичного риформинга
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.STEAM_REFORMER.get())
                .pattern("PRP").pattern("PTP").pattern("RHR").define('P', pipe).define('R', PcItems.REFRACTORY_BRICK.get())
                .define('T', PcBlocks.TUBE_FURNACE.get()).define('H', AllBlocks.FLUID_TANK.get())
                .unlockedBy("has_tube_furnace", has(PcBlocks.TUBE_FURNACE.get())).save(out, id("steam_reformer"));
        // очистка: абсорбер и отпарная колонна МЭА, метанатор
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.CO2_ABSORBER.get())
                .pattern("SPS").pattern("STS").pattern("SVS").define('S', sheet).define('P', AllBlocks.FLUID_PIPE.get())
                .define('T', AllBlocks.FLUID_TANK.get()).define('V', AllBlocks.FLUID_VALVE.get())
                .unlockedBy("has_steam_reformer", has(PcBlocks.STEAM_REFORMER.get())).save(out, id("co2_absorber"));
        // колонна синтеза: толстостенный сосуд высокого давления, компрессор свежего газа, циркуляционное колесо
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.AMMONIA_CONVERTER.get())
                .pattern("IPI").pattern("CGC").pattern("IHI").define('I', Items.IRON_BLOCK).define('P', pipe)
                .define('C', AllBlocks.COGWHEEL.get()).define('G', PcBlocks.GAS_COMPRESSOR.get()).define('H', AllBlocks.SHAFT.get())
                .unlockedBy("has_co2_absorber", has(PcBlocks.CO2_ABSORBER.get())).save(out, id("ammonia_converter"));
        // агрегат азотной кислоты: воздушный компрессор (вентилятор), контактный аппарат, колонна
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.NITRIC_ACID_UNIT.get())
                .pattern("PFP").pattern("STS").pattern("CHC").define('P', pipe).define('F', AllBlocks.ENCASED_FAN.get())
                .define('S', sheet).define('T', AllBlocks.FLUID_TANK.get()).define('C', AllBlocks.COGWHEEL.get())
                .define('H', AllBlocks.SHAFT.get())
                .unlockedBy("has_seamless_pipe", has(pipe)).save(out, id("nitric_acid_unit"));
        // концентрирование: колонна и выпарной аппарат на огне
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.ACID_CONCENTRATOR.get())
                .pattern("SPS").pattern("STS").pattern("SFS").define('S', sheet).define('P', pipe)
                .define('T', AllBlocks.FLUID_TANK.get()).define('F', Items.FURNACE)
                .unlockedBy("has_nitric_acid_unit", has(PcBlocks.NITRIC_ACID_UNIT.get())).save(out, id("acid_concentrator"));
        // катализаторы: никель на глинозёме; Fe₂O₃–Cr₂O₃; Cu–ZnO; оксиды на носителе для очистки хвостовых газов
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, PcItems.CATALYST_STEAM_REFORMING.get())
                .requires(PcItems.RAW_PENTLANDITE.get(), 2).requires(Items.CLAY_BALL, 2)
                .unlockedBy("has_pentlandite", has(PcItems.RAW_PENTLANDITE.get())).save(out, id("catalyst_steam_reforming"));
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, PcItems.CATALYST_SHIFT_HT.get())
                .requires(PcItems.RAW_MAGNETITE.get(), 2).requires(PcItems.RAW_CHROMITE.get()).requires(Items.CLAY_BALL)
                .unlockedBy("has_chromite", has(PcItems.RAW_CHROMITE.get())).save(out, id("catalyst_shift_ht"));
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, PcItems.CATALYST_SHIFT_LT.get())
                .requires(Items.COPPER_INGOT, 2).requires(PcItems.RAW_SPHALERITE.get()).requires(Items.CLAY_BALL)
                .unlockedBy("has_sphalerite", has(PcItems.RAW_SPHALERITE.get())).save(out, id("catalyst_shift_lt"));
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, PcItems.CATALYST_DENOX.get())
                .requires(PcItems.RAW_MOLYBDENITE.get()).requires(Items.CLAY_BALL, 2)
                .unlockedBy("has_molybdenite", has(PcItems.RAW_MOLYBDENITE.get())).save(out, id("catalyst_denox"));
        // сетки: сплав платины с 7,5 % родия, вытянутый в проволоку 0,09 мм и сотканный
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcItems.PT_RH_GAUZE.get())
                .pattern("SSS").pattern("S S").pattern("SSS").define('S', PcItems.RAW_SPERRYLITE.get())
                .unlockedBy("has_sperrylite", has(PcItems.RAW_SPERRYLITE.get())).save(out, id("pt_rh_gauze"));
    }
}
