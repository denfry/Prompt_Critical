package dev.denfry.promptcritical.mod.environment;

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
import net.minecraft.data.recipes.SimpleCookingRecipeBuilder;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

/** Рецепты радиационной защиты: дозиметр-радиометр и свинцовые блоки из галенита. */
public final class RadiationRecipes extends RecipeProvider {
    public RadiationRecipes(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries);
    }

    public static DataProvider provider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        DataProvider inner = new RadiationRecipes(output, registries);
        return new DataProvider() {
            @Override
            public CompletableFuture<?> run(CachedOutput cache) {
                return inner.run(cache);
            }

            @Override
            public String getName() {
                return "Recipes: radiation protection";
            }
        };
    }

    private static String id(String name) {
        return PromptCritical.id("radiation/" + name).toString();
    }

    @Override
    protected void buildRecipes(RecipeOutput out) {
        // счётчик Гейгера — стеклянная трубка с газом, высокое напряжение — электронная лампа, корпус — латунь
        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, PcItems.DOSIMETER.get())
                .pattern("BGB").pattern("BEB").pattern("BRB").define('B', AllItems.BRASS_SHEET.get()).define('G', Items.GLASS)
                .define('E', AllItems.ELECTRON_TUBE.get()).define('R', Items.REDSTONE)
                .unlockedBy("has_electron_tube", has(AllItems.ELECTRON_TUBE.get())).save(out, id("dosimeter_radiometer"));
        // стеллаж бассейна: нержавеющая сталь с бором — железо и пластины, бура заменена латунью-поглотителем условно
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.FUEL_RACK.get())
                .pattern("S S").pattern("SBS").pattern("S S").define('S', AllItems.IRON_SHEET.get()).define('B', Items.IRON_BARS)
                .unlockedBy("has_iron_sheet", has(AllItems.IRON_SHEET.get())).save(out, id("fuel_rack"));
        // гелиевая установка: криоблок (теплообменники, детандер) и адсорберы с углём
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.HELIUM_RECOVERY_UNIT.get())
                .pattern("SPS").pattern("TLT").pattern("SCS").define('S', AllItems.IRON_SHEET.get()).define('P', PcItems.SEAMLESS_PIPE.get())
                .define('T', com.simibubi.create.AllBlocks.FLUID_TANK.get()).define('L', PcBlocks.LOW_TEMPERATURE_SEPARATOR.get())
                .define('C', Items.CHARCOAL).unlockedBy("has_low_temperature_separator", has(PcBlocks.LOW_TEMPERATURE_SEPARATOR.get()))
                .save(out, id("helium_recovery_unit"));
        // дезактивация: радиометр — плоский зонд (сцинтиллятор за стеклом) и счётная электроника
        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, PcItems.CONTAMINATION_METER.get())
                .pattern("GGG").pattern("BEB").pattern("BRB").define('G', Items.GLASS_PANE).define('B', AllItems.BRASS_SHEET.get())
                .define('E', AllItems.ELECTRON_TUBE.get()).define('R', Items.REDSTONE)
                .unlockedBy("has_dosimeter", has(PcItems.DOSIMETER.get())).save(out, id("contamination_meter"));
        // латекс покрытия — полимер (слизь) в банке
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcItems.STRIPPABLE_COATING.get(), 4)
                .pattern(" S ").pattern("IBI").pattern(" I ").define('S', Items.SLIME_BALL).define('I', AllItems.IRON_SHEET.get())
                .define('B', Items.GLASS_BOTTLE).unlockedBy("has_slime_ball", has(Items.SLIME_BALL)).save(out, id("strippable_coating"));
        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, PcItems.DECON_SPRAYER.get())
                .pattern("P  ").pattern("TT ").pattern("II ").define('P', com.simibubi.create.AllBlocks.FLUID_PIPE.get())
                .define('T', com.simibubi.create.AllBlocks.FLUID_TANK.get()).define('I', AllItems.IRON_SHEET.get())
                .unlockedBy("has_fluid_tank", has(com.simibubi.create.AllBlocks.FLUID_TANK.get())).save(out, id("decon_sprayer"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.ULTRASONIC_BATH.get())
                .pattern("I I").pattern("ITI").pattern("EIE").define('I', AllItems.IRON_SHEET.get())
                .define('T', com.simibubi.create.AllBlocks.FLUID_TANK.get()).define('E', AllItems.ELECTRON_TUBE.get())
                .unlockedBy("has_electron_tube", has(AllItems.ELECTRON_TUBE.get())).save(out, id("ultrasonic_bath"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.DECON_SHOWER.get())
                .pattern("PPP").pattern(" P ").pattern("I I").define('P', com.simibubi.create.AllBlocks.FLUID_PIPE.get())
                .define('I', AllItems.IRON_SHEET.get())
                .unlockedBy("has_fluid_pipe", has(com.simibubi.create.AllBlocks.FLUID_PIPE.get())).save(out, id("decon_shower"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.HAND_FOOT_MONITOR.get())
                .pattern("MBM").pattern("BEB").pattern("III").define('M', PcItems.CONTAMINATION_METER.get())
                .define('B', AllItems.BRASS_SHEET.get()).define('E', AllItems.ELECTRON_TUBE.get()).define('I', AllItems.IRON_SHEET.get())
                .unlockedBy("has_contamination_meter", has(PcItems.CONTAMINATION_METER.get())).save(out, id("hand_foot_monitor"));
        // колонна с пластиковым сцинтиллятором: в рецепте — дозиметр как счётный тракт
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.PORTAL_MONITOR.get())
                .pattern("IDI").pattern("BEB").pattern("III").define('D', PcItems.DOSIMETER.get())
                .define('B', AllItems.BRASS_SHEET.get()).define('E', AllItems.ELECTRON_TUBE.get()).define('I', AllItems.IRON_SHEET.get())
                .unlockedBy("has_dosimeter", has(PcItems.DOSIMETER.get())).save(out, id("portal_monitor"));
        // свинец выплавляют из галенита (обжиг PbS и восстановление — свёрнуты в доменную плавку)
        SimpleCookingRecipeBuilder.blasting(Ingredient.of(PcBlocks.GALENA_ORE.get(), PcBlocks.DEEPSLATE_GALENA_ORE.get()), RecipeCategory.BUILDING_BLOCKS,
                        PcBlocks.LEAD_BLOCK.get(), 0.3f, 200)
                .unlockedBy("has_galena", has(PcBlocks.GALENA_ORE.get())).save(out, id("lead_block_from_galena"));
    }
}
