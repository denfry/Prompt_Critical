package dev.denfry.promptcritical.mod.safety;

import com.simibubi.create.AllItems;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.ops.FilterType;
import java.util.Locale;
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

/** Рецепты промышленной безопасности 13: СИЗ §13.7 (каска, противогаз и фильтры, аппарат, костюм, привязь, фал). */
public final class SafetyRecipes extends RecipeProvider {
    public SafetyRecipes(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries);
    }

    public static DataProvider provider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        DataProvider inner = new SafetyRecipes(output, registries);
        return new DataProvider() {
            @Override
            public CompletableFuture<?> run(CachedOutput cache) {
                return inner.run(cache);
            }

            @Override
            public String getName() {
                return "Recipes: industrial safety";
            }
        };
    }

    static String id(String name) {
        return PromptCritical.id("safety/" + name).toString();
    }

    @Override
    protected void buildRecipes(RecipeOutput out) {
        var sheet = AllItems.IRON_SHEET.get();
        var hasSheet = has(sheet);
        ShapedRecipeBuilder.shaped(RecipeCategory.COMBAT, PcItems.HARD_HAT.get())
                .pattern("SYS").pattern("S S").define('S', sheet).define('Y', Items.YELLOW_DYE)
                .unlockedBy("has_sheet", hasSheet).save(out, id("hard_hat"));
        ShapedRecipeBuilder.shaped(RecipeCategory.COMBAT, PcItems.GAS_MASK.get())
                .pattern("LGL").pattern("LNL").define('L', Items.LEATHER).define('G', Items.GLASS_PANE).define('N', Items.IRON_NUGGET)
                .unlockedBy("has_leather", has(Items.LEATHER)).save(out, id("gas_mask"));
        // сорбент — активированный уголь; группа задаётся пропиткой (EN 14387): B — соли меди, E — щёлочь (зола костей),
        // K — кислота (сера), CO — гопкалит (оксиды марганца и меди: редстоун как заместитель), AX — двойной слой угля,
        // P — волокнистый материал
        filter(out, FilterType.A, Items.CHARCOAL);
        filter(out, FilterType.B, Items.COPPER_INGOT);
        filter(out, FilterType.E, Items.BONE_MEAL);
        filter(out, FilterType.K, PcItems.SULFUR.get());
        filter(out, FilterType.CO, Items.REDSTONE);
        filter(out, FilterType.AX, Items.COAL);
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, GasMaskFilterItem.of(FilterType.P))
                .requires(Items.IRON_NUGGET).requires(Items.PAPER).requires(Items.WHITE_WOOL)
                .unlockedBy("has_mask", has(PcItems.GAS_MASK.get())).save(out, id("gas_mask_filter_p"));
        ShapedRecipeBuilder.shaped(RecipeCategory.COMBAT, PcItems.SCBA.get())
                .pattern("LGL").pattern("SCS").pattern(" B ").define('L', Items.LEATHER).define('G', Items.GLASS_PANE)
                .define('S', sheet).define('C', PcItems.SCBA_CYLINDER.get()).define('B', AllItems.BRASS_INGOT.get())
                .unlockedBy("has_cylinder", has(PcItems.SCBA_CYLINDER.get())).save(out, id("scba"));
        // баллон заправляют компрессором: здесь — стальной баллон, наполненный при изготовлении
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcItems.SCBA_CYLINDER.get())
                .pattern(" B ").pattern("SXS").pattern("SSS").define('B', AllItems.BRASS_INGOT.get()).define('S', sheet)
                .define('X', AllItems.PROPELLER.get())
                .unlockedBy("has_sheet", hasSheet).save(out, id("scba_cylinder"));
        ShapedRecipeBuilder.shaped(RecipeCategory.COMBAT, PcItems.FIRE_SUIT.get())
                .pattern("WGW").pattern("WWW").pattern("W W").define('W', Items.WHITE_WOOL).define('G', Items.GOLD_NUGGET)
                .unlockedBy("has_wool", has(Items.WHITE_WOOL)).save(out, id("fire_suit"));
        ShapedRecipeBuilder.shaped(RecipeCategory.COMBAT, PcItems.SAFETY_HARNESS.get())
                .pattern("L L").pattern("LNL").pattern("L L").define('L', Items.LEATHER).define('N', Items.IRON_INGOT)
                .unlockedBy("has_leather", has(Items.LEATHER)).save(out, id("safety_harness"));
        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, PcItems.LIFELINE.get())
                .pattern("SSS").pattern("S N").define('S', Items.STRING).define('N', Items.IRON_INGOT)
                .unlockedBy("has_harness", has(PcItems.SAFETY_HARNESS.get())).save(out, id("lifeline"));
        confined(out, sheet);
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, dev.denfry.promptcritical.mod.registry.PcBlocks.WATER_INTAKE.get())
                .pattern("CBC").pattern("BPB").pattern("CBC").define('C', Items.SMOOTH_STONE).define('B', Items.IRON_BARS)
                .define('P', com.simibubi.create.AllBlocks.MECHANICAL_PUMP.get())
                .unlockedBy("has_iron_bars", has(Items.IRON_BARS)).save(out, id("water_intake"));
        // маркировка §13.8
        ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, dev.denfry.promptcritical.mod.registry.PcBlocks.HAZARD_SIGN.get(), 4)
                .pattern("YSY").pattern(" S ").define('Y', Items.YELLOW_DYE).define('S', sheet)
                .unlockedBy("has_sheet", has(sheet)).save(out, id("hazard_sign"));
        ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, dev.denfry.promptcritical.mod.registry.PcBlocks.HAZARD_DIAMOND.get(), 2)
                .pattern("RB").pattern("YW").define('R', Items.RED_DYE).define('B', Items.BLUE_DYE).define('Y', Items.YELLOW_DYE).define('W', sheet)
                .unlockedBy("has_sheet", has(sheet)).save(out, id("hazard_diamond"));
        ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, dev.denfry.promptcritical.mod.registry.PcBlocks.UN_PLACARD.get(), 2)
                .pattern("OSO").define('O', Items.ORANGE_DYE).define('S', sheet)
                .unlockedBy("has_sheet", has(sheet)).save(out, id("un_placard"));
        // вытяжка зала 03a §3a.9.3: йодный фильтр — уголь с TEDA (здесь — древесный уголь), труба
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, dev.denfry.promptcritical.mod.registry.PcBlocks.CHARCOAL_FILTER.get())
                .pattern("SSS").pattern("CCC").pattern("SSS").define('S', sheet).define('C', Items.CHARCOAL)
                .unlockedBy("has_charcoal", has(Items.CHARCOAL)).save(out, id("charcoal_filter"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, dev.denfry.promptcritical.mod.registry.PcBlocks.VENT_STACK.get(), 4)
                .pattern("S S").pattern("SFS").pattern("S S").define('S', sheet).define('F', com.simibubi.create.AllBlocks.ENCASED_FAN.get())
                .unlockedBy("has_sheet", has(sheet)).save(out, id("vent_stack"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, dev.denfry.promptcritical.mod.registry.PcBlocks.TURBINE_TURNING_GEAR.get())
                .pattern("SCS").pattern("GMG").pattern("SSS").define('S', sheet).define('C', com.simibubi.create.AllBlocks.COGWHEEL.get())
                .define('G', com.simibubi.create.AllBlocks.LARGE_COGWHEEL.get()).define('M', com.simibubi.create.AllBlocks.GEARBOX.get())
                .unlockedBy("has_turbine", has(dev.denfry.promptcritical.mod.registry.PcBlocks.STEAM_TURBINE.get())).save(out, id("turbine_turning_gear"));
        // факельная система §13.4.3
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, dev.denfry.promptcritical.mod.registry.PcBlocks.FLARE_IGNITION_PANEL.get())
                .pattern("SLS").pattern("RFR").pattern("SCS").define('S', sheet).define('L', Items.GLASS_PANE).define('R', Items.REDSTONE)
                .define('F', Items.FLINT_AND_STEEL).define('C', Items.COPPER_INGOT)
                .unlockedBy("has_flare_tip", has(dev.denfry.promptcritical.mod.registry.PcBlocks.FLARE_TIP.get())).save(out, id("flare_ignition_panel"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, dev.denfry.promptcritical.mod.registry.PcBlocks.FLARE_KNOCKOUT_DRUM.get())
                .pattern("SSS").pattern("T T").pattern("SSS").define('S', sheet).define('T', com.simibubi.create.AllBlocks.FLUID_TANK.get())
                .unlockedBy("has_flare_tip", has(dev.denfry.promptcritical.mod.registry.PcBlocks.FLARE_TIP.get())).save(out, id("flare_knockout_drum"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, dev.denfry.promptcritical.mod.registry.PcBlocks.FLARE_SEAL_DRUM.get())
                .pattern("SPS").pattern("STS").pattern("SPS").define('S', sheet).define('P', com.simibubi.create.AllBlocks.FLUID_PIPE.get())
                .define('T', com.simibubi.create.AllBlocks.FLUID_TANK.get())
                .unlockedBy("has_flare_tip", has(dev.denfry.promptcritical.mod.registry.PcBlocks.FLARE_TIP.get())).save(out, id("flare_seal_drum"));
        // обходы §13.3.1
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, dev.denfry.promptcritical.mod.registry.PcBlocks.PATROL_CHECKPOINT.get(), 4)
                .pattern(" N ").pattern("NRN").pattern(" N ").define('N', Items.IRON_NUGGET).define('R', Items.REDSTONE)
                .unlockedBy("has_redstone", has(Items.REDSTONE)).save(out, id("patrol_checkpoint"));
        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, PcItems.PATROL_READER.get())
                .pattern("GC").pattern("RC").define('G', Items.GLASS_PANE).define('C', Items.COPPER_INGOT).define('R', Items.REDSTONE)
                .unlockedBy("has_copper", has(Items.COPPER_INGOT)).save(out, id("patrol_reader"));
    }

    /** Замкнутые пространства §13.2.6. */
    private void confined(RecipeOutput out, net.minecraft.world.item.Item sheet) {
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, dev.denfry.promptcritical.mod.registry.PcBlocks.MANWAY.get())
                .pattern("SNS").pattern("NTN").pattern("SNS").define('S', sheet).define('N', Items.IRON_NUGGET).define('T', Items.IRON_TRAPDOOR)
                .unlockedBy("has_sheet", has(sheet)).save(out, id("manway"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, dev.denfry.promptcritical.mod.registry.PcBlocks.RESCUE_TRIPOD.get())
                .pattern(" W ").pattern("I I").pattern("I I").define('W', AllItems.PRECISION_MECHANISM.get()).define('I', Items.IRON_INGOT)
                .unlockedBy("has_lifeline", has(PcItems.LIFELINE.get())).save(out, id("rescue_tripod"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, dev.denfry.promptcritical.mod.registry.PcBlocks.PORTABLE_VENTILATOR.get())
                .pattern("SPS").pattern("SMS").pattern("LLL").define('S', sheet).define('P', AllItems.PROPELLER.get())
                .define('M', com.simibubi.create.AllBlocks.ENCASED_FAN.get()).define('L', Items.LEATHER)
                .unlockedBy("has_sheet", has(sheet)).save(out, id("portable_ventilator"));
    }

    private void filter(RecipeOutput out, FilterType type, ItemLike impregnation) {
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, GasMaskFilterItem.of(type))
                .requires(Items.IRON_NUGGET).requires(Items.CHARCOAL).requires(impregnation)
                .unlockedBy("has_mask", has(PcItems.GAS_MASK.get())).save(out, id("gas_mask_filter_" + type.name().toLowerCase(Locale.ROOT)));
    }
}
