package dev.denfry.promptcritical.mod.refinery;

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

/** Рецепты подготовки и углубления переработки НПЗ и ГПЗ (09 §9.7.3, §9.9–9.10). */
public final class ConversionRecipes extends RecipeProvider {
    public ConversionRecipes(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries);
    }

    public static DataProvider provider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        DataProvider inner = new ConversionRecipes(output, registries);
        return new DataProvider() {
            @Override
            public CompletableFuture<?> run(CachedOutput cache) {
                return inner.run(cache);
            }

            @Override
            public String getName() {
                return "Recipes: refinery conversion";
            }
        };
    }

    private static String id(String name) {
        return PromptCritical.id("refinery/" + name).toString();
    }

    @Override
    protected void buildRecipes(RecipeOutput out) {
        var plate = PcItems.STEEL_PLATE.get();
        var pipe = PcItems.SEAMLESS_PIPE.get();
        // горизонтальный сосуд с электродами на изоляторах, трансформатор на крыше, смесительный клапан
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.ELECTROSTATIC_DESALTER.get())
                .pattern("IWI").pattern("PTP").pattern("SVS").define('I', PcItems.INSULATOR_PIN.get()).define('W', PcItems.COPPER_WIRE.get())
                .define('P', pipe).define('T', AllBlocks.FLUID_TANK.get()).define('S', plate).define('V', AllBlocks.FLUID_VALVE.get())
                .unlockedBy("has_crude_column", has(PcBlocks.CRUDE_COLUMN.get())).save(out, id("electrostatic_desalter"));
        // куб колонны большого диаметра с насадкой, печь, конденсаторы вакуумной системы
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.VACUUM_COLUMN.get())
                .pattern("SCS").pattern("STS").pattern("SFS").define('S', plate).define('C', PcBlocks.COLUMN_TRAY_SECTION.get())
                .define('T', AllBlocks.FLUID_TANK.get()).define('F', PcBlocks.TUBE_FURNACE.get())
                .unlockedBy("has_crude_column", has(PcBlocks.CRUDE_COLUMN.get())).save(out, id("vacuum_column"));
        // сопло Лаваля и диффузор: труба, клапан пара
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.STEAM_EJECTOR.get(), 2)
                .pattern("PVP").define('P', pipe).define('V', AllBlocks.FLUID_VALVE.get())
                .unlockedBy("has_crude_column", has(PcBlocks.CRUDE_COLUMN.get())).save(out, id("steam_ejector"));
        // футерованные райзер, реактор-сепаратор с циклонами и регенератор, шиберные задвижки
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.FCC_UNIT.get())
                .pattern("BTB").pattern("VPV").pattern("BFB").define('B', net.minecraft.world.item.Items.BRICKS).define('T', AllBlocks.FLUID_TANK.get())
                .define('V', AllBlocks.FLUID_VALVE.get()).define('P', pipe).define('F', PcBlocks.TUBE_FURNACE.get())
                .unlockedBy("has_vacuum_column", has(PcBlocks.VACUUM_COLUMN.get())).save(out, id("fcc_unit"));
        // абсорбер и десорбер с ребойлером; заправляется раствором МДЭА из аммиака и метанола
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.AMINE_UNIT.get())
                .pattern("CPC").pattern("TAT").pattern("SFS").define('C', PcBlocks.COLUMN_TRAY_SECTION.get()).define('P', pipe)
                .define('T', AllBlocks.FLUID_TANK.get()).define('A', net.minecraft.world.item.Items.GLASS_BOTTLE).define('S', plate)
                .define('F', PcBlocks.TUBE_FURNACE.get())
                .unlockedBy("has_gas_plant", has(PcBlocks.LOW_TEMPERATURE_SEPARATOR.get())).save(out, id("amine_unit"));
        // резервуар смешения, четыре регулирующих клапана с расходомерами
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.GASOLINE_BLENDER.get())
                .pattern("VPV").pattern("PTP").pattern("VPV").define('V', AllBlocks.FLUID_VALVE.get()).define('P', pipe)
                .define('T', AllBlocks.FLUID_TANK.get())
                .unlockedBy("has_crude_column", has(PcBlocks.CRUDE_COLUMN.get())).save(out, id("gasoline_blender"));
        // моторная установка CFR (одноцилиндровый двигатель), аппарат разгонки, весы
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.QUALITY_LAB.get())
                .pattern("GGG").pattern("BEB").pattern("SSS").define('G', net.minecraft.world.item.Items.GLASS_PANE)
                .define('B', net.minecraft.world.item.Items.GLASS_BOTTLE).define('E', AllBlocks.ENCASED_FAN.get()).define('S', plate)
                .unlockedBy("has_crude_column", has(PcBlocks.CRUDE_COLUMN.get())).save(out, id("quality_lab"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.QUALITY_ANALYZER.get())
                .pattern("SGS").pattern("WBW").pattern("SPS").define('S', plate).define('G', net.minecraft.world.item.Items.GLASS_PANE)
                .define('W', PcItems.COPPER_WIRE.get()).define('B', net.minecraft.world.item.Items.GLASS_BOTTLE).define('P', pipe)
                .unlockedBy("has_quality_lab", has(PcBlocks.QUALITY_LAB.get())).save(out, id("quality_analyzer"));
        net.minecraft.data.recipes.ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, PcItems.SAMPLE_BOTTLE.get(), 4)
                .requires(net.minecraft.world.item.Items.GLASS_BOTTLE, 4).requires(net.minecraft.world.item.Items.PAPER)
                .unlockedBy("has_quality_lab", has(PcBlocks.QUALITY_LAB.get())).save(out, id("sample_bottle"));
    }
}
