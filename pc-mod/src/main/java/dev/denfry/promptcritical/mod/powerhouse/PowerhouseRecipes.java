package dev.denfry.promptcritical.mod.powerhouse;

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

/**
 * Рецепты котла I1 и деаэратора (07 §7.9.5, 08 §8.12): экраны — из бесшовной трубы с огнеупором, барабан и щит — из
 * стального листа, вентиляторы — на валу Create. Угольная пыль — помолом угля жерновами Create (статический рецепт).
 */
public final class PowerhouseRecipes extends RecipeProvider {
    public PowerhouseRecipes(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries);
    }

    public static DataProvider provider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        DataProvider inner = new PowerhouseRecipes(output, registries);
        return new DataProvider() {
            @Override
            public CompletableFuture<?> run(CachedOutput cache) {
                return inner.run(cache);
            }

            @Override
            public String getName() {
                return "Recipes: water-tube boiler and deaerator";
            }
        };
    }

    private static String id(String name) {
        return PromptCritical.id("powerhouse/" + name).toString();
    }

    @Override
    protected void buildRecipes(RecipeOutput out) {
        var plate = PcItems.STEEL_PLATE.get();
        var seamless = PcItems.SEAMLESS_PIPE.get();
        var welded = PcItems.WELDED_PIPE.get();
        var brick = PcItems.REFRACTORY_BRICK.get();
        var shaft = AllBlocks.SHAFT.get();
        var pipe = AllBlocks.FLUID_PIPE.get();
        var hasSeamless = has(seamless);
        var wall = PcBlocks.BOILER_WALL.get();

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, wall, 6)
                .pattern("PRP").pattern("PRP").pattern("PRP").define('P', seamless).define('R', brick)
                .unlockedBy("has_seamless_pipe", hasSeamless).save(out, id("boiler_wall"));
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, PcBlocks.BOILER_BURNER.get())
                .requires(wall).requires(AllBlocks.FLUID_VALVE.get()).requires(Items.FLINT_AND_STEEL).requires(plate)
                .unlockedBy("has_boiler_wall", has(wall)).save(out, id("boiler_burner"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.STEAM_DRUM.get())
                .pattern("PPP").pattern("PTP").pattern("PPP").define('P', plate).define('T', AllBlocks.FLUID_TANK.get())
                .unlockedBy("has_seamless_pipe", hasSeamless).save(out, id("steam_drum"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.BOILER_SUPERHEATER.get())
                .pattern("SSS").pattern(" P ").define('S', seamless).define('P', plate)
                .unlockedBy("has_seamless_pipe", hasSeamless).save(out, id("boiler_superheater"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.BOILER_ECONOMIZER.get())
                .pattern("SFS").pattern(" P ").define('S', seamless).define('F', pipe).define('P', plate)
                .unlockedBy("has_seamless_pipe", hasSeamless).save(out, id("boiler_economizer"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.AIR_PREHEATER.get())
                .pattern("WWW").pattern("WPW").define('W', welded).define('P', plate)
                .unlockedBy("has_welded_pipe", has(welded)).save(out, id("air_preheater"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.FORCED_DRAFT_FAN.get())
                .pattern("PFP").pattern("S P").define('P', plate).define('F', AllItems.PROPELLER.get()).define('S', shaft)
                .unlockedBy("has_steel_plate", has(plate)).save(out, id("forced_draft_fan"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.INDUCED_DRAFT_FAN.get())
                .pattern("PFP").pattern("SRP").define('P', plate).define('F', AllItems.PROPELLER.get()).define('S', shaft)
                .define('R', brick).unlockedBy("has_steel_plate", has(plate)).save(out, id("induced_draft_fan"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.WATER_TUBE_BOILER.get())
                .pattern("PKP").pattern("RCR").define('P', plate).define('K', Items.COMPARATOR).define('R', Items.REDSTONE)
                .define('C', AllBlocks.BRASS_CASING.get()).unlockedBy("has_boiler_wall", has(wall)).save(out, id("water_tube_boiler"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.DEAERATOR.get())
                .pattern("PTP").pattern("FVF").define('P', plate).define('T', AllBlocks.FLUID_TANK.get()).define('F', pipe)
                .define('V', AllBlocks.FLUID_VALVE.get()).unlockedBy("has_steel_plate", has(plate)).save(out, id("deaerator"));
        // турбина: корпус из листа, ротор — лопатки (пропеллеры Create) на валу, клапан на подводе пара
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.STEAM_TURBINE.get())
                .pattern("PVP").pattern("FSF").pattern("PGP").define('P', plate).define('V', AllBlocks.FLUID_VALVE.get())
                .define('F', AllItems.PROPELLER.get()).define('S', shaft).define('G', AllBlocks.COGWHEEL.get())
                .unlockedBy("has_seamless_pipe", hasSeamless).save(out, id("steam_turbine"));
        // гидроагрегат: спиральная камера из листа, рабочее колесо — лопатки на валу; затвор — щит в направляющих
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.HYDRO_TURBINE.get())
                .pattern("PSP").pattern("FWF").pattern("PCP").define('P', plate).define('S', shaft)
                .define('F', AllItems.PROPELLER.get()).define('W', AllBlocks.WATER_WHEEL.get()).define('C', AllBlocks.COGWHEEL.get())
                .unlockedBy("has_steel_plate", has(plate)).save(out, id("hydro_turbine"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.DAM_GATE.get())
                .pattern("PCP").pattern("P P").define('P', plate).define('C', AllBlocks.COGWHEEL.get())
                .unlockedBy("has_steel_plate", has(plate)).save(out, id("dam_gate"));
        // ГТУ: компрессор и турбина — пропеллеры на валу в кожухе, камера сгорания — горелка; редуктор — шестерни
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.GAS_TURBINE.get())
                .pattern("PFP").pattern("BSB").pattern("PFP").define('P', plate).define('F', AllItems.PROPELLER.get())
                .define('B', PcBlocks.BOILER_BURNER.get()).define('S', shaft)
                .unlockedBy("has_boiler_wall", has(wall)).save(out, id("gas_turbine"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.TURBINE_GEARBOX.get())
                .pattern("PCP").pattern("SLS").pattern("PCP").define('P', plate).define('C', AllBlocks.COGWHEEL.get())
                .define('L', AllBlocks.LARGE_COGWHEEL.get()).define('S', shaft)
                .unlockedBy("has_steel_plate", has(plate)).save(out, id("turbine_gearbox"));
        // ВЭУ: колесо — пропеллеры на подшипнике ветряка Create, мультипликатор — большая шестерня, генератор —
        // асинхронная машина (тот же двигатель, вращаемый выше синхронной скорости), башня — лист на железных прутьях
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.WIND_TURBINE.get())
                .pattern("FWF").pattern("LMP").pattern("IPI").define('F', AllItems.PROPELLER.get())
                .define('W', AllBlocks.WINDMILL_BEARING.get()).define('L', AllBlocks.LARGE_COGWHEEL.get())
                .define('M', PcBlocks.ELECTRIC_MOTOR.get()).define('P', plate).define('I', net.minecraft.world.item.Items.IRON_BARS)
                .unlockedBy("has_electric_motor", has(PcBlocks.ELECTRIC_MOTOR.get())).save(out, id("wind_turbine"));
        // капсульный агрегат: обтекаемая капсула из листа, рабочее колесо — лопатки на валу, водовод — трубы
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.TIDAL_UNIT.get())
                .pattern("PSP").pattern("FHF").pattern("PCP").define('P', plate).define('S', shaft)
                .define('F', AllItems.PROPELLER.get()).define('H', PcBlocks.HYDRO_TURBINE.get()).define('C', AllBlocks.COGWHEEL.get())
                .unlockedBy("has_hydro_turbine", has(PcBlocks.HYDRO_TURBINE.get())).save(out, id("tidal_unit"));
        // мареограф: футшток (рейка) с поплавком в колодце и самописец
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.TIDE_GAUGE.get())
                .pattern(" C ").pattern("SGS").pattern(" B ").define('C', net.minecraft.world.item.Items.CLOCK)
                .define('S', net.minecraft.world.item.Items.STICK).define('G', net.minecraft.world.item.Items.GLASS_PANE)
                .define('B', net.minecraft.world.item.Items.BUCKET)
                .unlockedBy("has_clock", has(net.minecraft.world.item.Items.CLOCK)).save(out, id("tide_gauge"));
        // обратимый агрегат: насос-турбина (гидротурбина и механический насос Create) на синхронной машине
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.PUMPED_STORAGE_UNIT.get())
                .pattern("PMP").pattern("HUH").pattern("PCP").define('P', plate).define('M', PcBlocks.ELECTRIC_MOTOR.get())
                .define('H', PcBlocks.HYDRO_TURBINE.get()).define('U', AllBlocks.MECHANICAL_PUMP.get())
                .define('C', AllBlocks.COGWHEEL.get())
                .unlockedBy("has_hydro_turbine", has(PcBlocks.HYDRO_TURBINE.get())).save(out, id("pumped_storage_unit"));
        // электродный котёл: корпус из листа, электроды — медь в изоляторах, вода — котёл
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.ELECTRODE_BOILER.get())
                .pattern("PCP").pattern("CTC").pattern("PCP").define('P', plate)
                .define('C', net.minecraft.world.item.Items.COPPER_INGOT).define('T', AllBlocks.FLUID_TANK.get())
                .unlockedBy("has_steel_plate", has(plate)).save(out, id("electrode_boiler"));
        // конденсатор: корпус из листа, трубный пучок — трубы
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.CONDENSER.get())
                .pattern("PPP").pattern("FFF").pattern("PPP").define('P', plate).define('F', pipe)
                .unlockedBy("has_steel_plate", has(plate)).save(out, id("condenser"));
        // секция градирни: обшивка и ороситель из досок, вентилятор — пропеллер Create
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.COOLING_TOWER_CELL.get())
                .pattern("PFP").pattern("WWW").pattern("PBP").define('P', plate).define('F', AllItems.PROPELLER.get())
                .define('W', net.minecraft.tags.ItemTags.PLANKS).define('B', brick)
                .unlockedBy("has_steel_plate", has(plate)).save(out, id("cooling_tower_cell"));
    }
}
