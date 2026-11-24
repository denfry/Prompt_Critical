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
        // малотоксичные насадки (07 §7.8.4): завихритель из листа, сопла вторичного воздуха из трубы, огнеупорная амбразура
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, PcItems.LOW_NOX_BURNER_KIT.get())
                .requires(plate).requires(seamless).requires(brick)
                .unlockedBy("has_boiler_burner", has(PcBlocks.BOILER_BURNER.get())).save(out, id("low_nox_burner_kit"));
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
        // чертёж машины реального масштаба: строится на стройплощадке по массе прототипа (SitePlans)
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, dev.denfry.promptcritical.mod.site.SiteRegistry.FORCED_DRAFT_FAN_REAL_DRAWING.get())
                .pattern("PPP").pattern("SBS").define('P', Items.PAPER).define('S', PcItems.STEEL_PLATE.get())
                .define('B', PcBlocks.FORCED_DRAFT_FAN.get()).unlockedBy("has_forced_draft_fan", has(PcBlocks.FORCED_DRAFT_FAN.get()))
                .save(out, id("forced_draft_fan_real_drawing"));
        // чертёж машины реального масштаба: строится на стройплощадке по массе прототипа (SitePlans)
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, dev.denfry.promptcritical.mod.site.SiteRegistry.INDUCED_DRAFT_FAN_REAL_DRAWING.get())
                .pattern("PPP").pattern("SBS").define('P', Items.PAPER).define('S', PcItems.STEEL_PLATE.get())
                .define('B', PcBlocks.INDUCED_DRAFT_FAN.get()).unlockedBy("has_induced_draft_fan", has(PcBlocks.INDUCED_DRAFT_FAN.get()))
                .save(out, id("induced_draft_fan_real_drawing"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.WATER_TUBE_BOILER.get())
                .pattern("PKP").pattern("RCR").define('P', plate).define('K', Items.COMPARATOR).define('R', Items.REDSTONE)
                .define('C', AllBlocks.BRASS_CASING.get()).unlockedBy("has_boiler_wall", has(wall)).save(out, id("water_tube_boiler"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.DEAERATOR.get())
                .pattern("PTP").pattern("FVF").define('P', plate).define('T', AllBlocks.FLUID_TANK.get()).define('F', pipe)
                .define('V', AllBlocks.FLUID_VALVE.get()).unlockedBy("has_steel_plate", has(plate)).save(out, id("deaerator"));
        // чертёж ДА-25 реального масштаба: деаэратор строится на стройплощадке по массе прототипа (SitePlans)
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, dev.denfry.promptcritical.mod.site.SiteRegistry.DEAERATOR_REAL_DRAWING.get())
                .pattern("PPP").pattern("FTV").define('P', Items.PAPER).define('T', AllBlocks.FLUID_TANK.get()).define('F', pipe)
                .define('V', AllBlocks.FLUID_VALVE.get()).unlockedBy("has_steel_plate", has(plate)).save(out, id("deaerator_real_drawing"));
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
        // ДГ: дизель (корпус из листа, поршни, коленвал), генератор с возбудителем, баллоны пускового воздуха — баки
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.DIESEL_GENERATOR.get())
                .pattern("PTP").pattern("ISG").pattern("PEP").define('P', plate).define('T', AllBlocks.FLUID_TANK.get())
                .define('I', AllBlocks.MECHANICAL_PISTON.get()).define('S', shaft).define('G', PcBlocks.SHAFT_GENERATOR.get())
                .define('E', PcBlocks.GENERATOR_EXCITER.get())
                .unlockedBy("has_shaft_generator", has(PcBlocks.SHAFT_GENERATOR.get())).save(out, id("diesel_generator"));
        // подогреватель рубашки — ТЭН (медная спираль) с термостатом (компаратор)
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcItems.JACKET_HEATER.get())
                .pattern("PCP").pattern("CRC").pattern("PCP").define('P', plate)
                .define('C', net.minecraft.world.item.Items.COPPER_INGOT).define('R', net.minecraft.world.item.Items.COMPARATOR)
                .unlockedBy("has_steel_plate", has(plate)).save(out, id("jacket_heater"));
        // секвенсор нагрузки — программный барабан (часы) и реле (повторители) в корпусе
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcItems.LOAD_SEQUENCER.get())
                .pattern("PRP").pattern("RKR").pattern("PRP").define('P', plate)
                .define('R', net.minecraft.world.item.Items.REPEATER).define('K', net.minecraft.world.item.Items.CLOCK)
                .unlockedBy("has_steel_plate", has(plate)).save(out, id("load_sequencer"));
        // котёл-утилизатор: барабан, пароперегреватель, испаритель (экраны) и экономайзер в газоходе из листа
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.HRSG.get())
                .pattern("PDP").pattern("WSW").pattern("PEP").define('P', plate).define('D', PcBlocks.STEAM_DRUM.get())
                .define('W', wall).define('S', PcBlocks.BOILER_SUPERHEATER.get()).define('E', PcBlocks.BOILER_ECONOMIZER.get())
                .unlockedBy("has_gas_turbine", has(PcBlocks.GAS_TURBINE.get())).save(out, id("hrsg"));
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
        // чертёж машины реального масштаба: строится на стройплощадке по массе прототипа (SitePlans)
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, dev.denfry.promptcritical.mod.site.SiteRegistry.ELECTRODE_BOILER_REAL_DRAWING.get())
                .pattern("PPP").pattern("SBS").define('P', Items.PAPER).define('S', PcItems.STEEL_PLATE.get())
                .define('B', PcBlocks.ELECTRODE_BOILER.get()).unlockedBy("has_electrode_boiler", has(PcBlocks.ELECTRODE_BOILER.get()))
                .save(out, id("electrode_boiler_real_drawing"));
        // водородная станция: чертежи — по знакомым машинам (электролиз хлора, двигатель, инвертор)
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, dev.denfry.promptcritical.mod.site.SiteRegistry.H2_ELECTROLYSER_REAL_DRAWING.get())
                .pattern("PPP").pattern("WCW").define('P', Items.PAPER).define('W', PcItems.COPPER_WIRE.get())
                .define('C', PcBlocks.CHLOR_ALKALI_CELL.get()).unlockedBy("has_chlor_alkali_cell", has(PcBlocks.CHLOR_ALKALI_CELL.get()))
                .save(out, id("h2_electrolyser_real_drawing"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, dev.denfry.promptcritical.mod.site.SiteRegistry.H2_COMPRESSOR_REAL_DRAWING.get())
                .pattern("PPP").pattern("SMU").define('P', Items.PAPER).define('S', PcItems.STEEL_PLATE.get())
                .define('M', PcBlocks.ELECTRIC_MOTOR.get()).define('U', AllBlocks.MECHANICAL_PUMP.get())
                .unlockedBy("has_electric_motor", has(PcBlocks.ELECTRIC_MOTOR.get())).save(out, id("h2_compressor_real_drawing"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, dev.denfry.promptcritical.mod.site.SiteRegistry.H2_TUBE_BANK_REAL_DRAWING.get())
                .pattern("PPP").pattern("TTT").define('P', Items.PAPER).define('T', PcItems.SEAMLESS_PIPE.get())
                .unlockedBy("has_seamless_pipe", has(PcItems.SEAMLESS_PIPE.get())).save(out, id("h2_tube_bank_real_drawing"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, dev.denfry.promptcritical.mod.site.SiteRegistry.H2_GASHOLDER_REAL_DRAWING.get())
                .pattern("PPP").pattern("STS").define('P', Items.PAPER).define('S', PcItems.STEEL_PLATE.get())
                .define('T', AllBlocks.FLUID_TANK.get()).unlockedBy("has_steel_plate", has(PcItems.STEEL_PLATE.get()))
                .save(out, id("h2_gasholder_real_drawing"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, dev.denfry.promptcritical.mod.site.SiteRegistry.H2_FUEL_CELL_REAL_DRAWING.get())
                .pattern("PPP").pattern("WIW").define('P', Items.PAPER).define('W', PcItems.COPPER_WIRE.get())
                .define('I', PcBlocks.BATTERY_INVERTER.get()).unlockedBy("has_battery_inverter", has(PcBlocks.BATTERY_INVERTER.get()))
                .save(out, id("h2_fuel_cell_real_drawing"));
        // конденсатор: корпус из листа, трубный пучок — трубы
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.CONDENSER.get())
                .pattern("PPP").pattern("FFF").pattern("PPP").define('P', plate).define('F', pipe)
                .unlockedBy("has_steel_plate", has(plate)).save(out, id("condenser"));
        // секция градирни: обшивка и ороситель из досок, вентилятор — пропеллер Create
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.COOLING_TOWER_CELL.get())
                .pattern("PFP").pattern("WWW").pattern("PBP").define('P', plate).define('F', AllItems.PROPELLER.get())
                .define('W', net.minecraft.tags.ItemTags.PLANKS).define('B', brick)
                .unlockedBy("has_steel_plate", has(plate)).save(out, id("cooling_tower_cell"));
        // регистр теплицы (17 §17.3.6): оребрённые медные трубы на стальной раме с патрубками
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.GREENHOUSE_COIL.get())
                .pattern("PCP").pattern("CCC").pattern("PCP").define('P', pipe).define('C', Items.COPPER_INGOT)
                .unlockedBy("has_condenser", has(PcBlocks.CONDENSER.get())).save(out, id("greenhouse_coil"));
        // парник: рама из досок, остекление
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.COLD_FRAME.get())
                .pattern("GGG").pattern("W W").define('G', net.minecraft.world.level.block.Blocks.GLASS_PANE)
                .define('W', net.minecraft.tags.ItemTags.PLANKS)
                .unlockedBy("has_glass_pane", has(net.minecraft.world.level.block.Blocks.GLASS_PANE)).save(out, id("cold_frame"));
        // садок: рама из досок, сеть из нити, зарыбление — ведро трески
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.FISH_CAGE.get())
                .pattern("WSW").pattern("SBS").pattern("WSW").define('W', net.minecraft.tags.ItemTags.PLANKS).define('S', Items.STRING)
                .define('B', Items.COD_BUCKET).unlockedBy("has_cod_bucket", has(Items.COD_BUCKET)).save(out, id("fish_cage"));
        // сетевой подогреватель ТЭЦ: корпус из листа, трубный пучок, как у конденсатора, и медь трубок
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.DISTRICT_HEAT_EXCHANGER.get())
                .pattern("PCP").pattern("FFF").pattern("PCP").define('P', plate).define('F', pipe).define('C', Items.COPPER_INGOT)
                .unlockedBy("has_condenser", has(PcBlocks.CONDENSER.get())).save(out, id("district_heat_exchanger"));
    }
}
