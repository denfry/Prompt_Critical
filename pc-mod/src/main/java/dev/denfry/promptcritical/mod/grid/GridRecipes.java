package dev.denfry.promptcritical.mod.grid;

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
import net.minecraft.data.recipes.SimpleCookingRecipeBuilder;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;

/**
 * Рецепты электросети I1 (07 §7.22): медь — проволока, обмотки и шины; изоляция — стекло и шерсть (резины и
 * фарфора в I1 нет); сталь заменена железом до прокатного стана (этап 10). Модули реле — общая база (проволока,
 * железо, редстоун, стекло) и свой механизм на каждую функцию.
 */
public final class GridRecipes extends RecipeProvider {
    public GridRecipes(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries);
    }

    public static DataProvider provider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        DataProvider inner = new GridRecipes(output, registries);
        return new DataProvider() {
            @Override
            public CompletableFuture<?> run(CachedOutput cache) {
                return inner.run(cache);
            }

            @Override
            public String getName() {
                return "Recipes: power grid";
            }
        };
    }

    private static String id(String name) {
        return PromptCritical.id("grid/" + name).toString();
    }

    @Override
    protected void buildRecipes(RecipeOutput out) {
        ItemLike wire = PcItems.COPPER_WIRE.get();
        ItemLike pin = PcItems.INSULATOR_PIN.get();
        var hasWire = has(wire);

        // материалы и расходники
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, wire, 3).requires(Items.COPPER_INGOT)
                .unlockedBy("has_copper", has(Items.COPPER_INGOT)).save(out, id("copper_wire"));
        // медь из халькопирита: упрощённо плавка концентрата в штейн и черновую медь сведена к печи
        SimpleCookingRecipeBuilder.smelting(Ingredient.of(PcItems.RAW_CHALCOPYRITE.get()), RecipeCategory.MISC, Items.COPPER_INGOT, 0.7f, 200)
                .unlockedBy("has_chalcopyrite", has(PcItems.RAW_CHALCOPYRITE.get())).save(out, id("copper_ingot_from_chalcopyrite"));
        SimpleCookingRecipeBuilder.blasting(Ingredient.of(PcItems.RAW_CHALCOPYRITE.get()), RecipeCategory.MISC, Items.COPPER_INGOT, 0.7f, 100)
                .unlockedBy("has_chalcopyrite", has(PcItems.RAW_CHALCOPYRITE.get()))
                .save(out, id("copper_ingot_from_chalcopyrite_blasting"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, pin, 2).pattern("G").pattern("N")
                .define('G', Items.GLASS).define('N', Items.IRON_NUGGET)
                .unlockedBy("has_wire", hasWire).save(out, id("insulator_pin"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcItems.WIRE_COIL_LV.get()).pattern("CCC").pattern("CBC").pattern("CCC")
                .define('C', wire).define('B', Items.BLACK_WOOL)
                .unlockedBy("has_wire", hasWire).save(out, id("wire_coil_lv"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcItems.OVERHEAD_CONDUCTOR.get()).pattern("CCC").pattern("CSC").pattern("CCC")
                .define('C', wire).define('S', Items.STICK)
                .unlockedBy("has_wire", hasWire).save(out, id("overhead_conductor"));
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, PcItems.LV_FUSE.get(), 2)
                .requires(Items.BRICK).requires(wire).requires(Items.SAND).requires(Items.IRON_NUGGET)
                .unlockedBy("has_wire", hasWire).save(out, id("lv_fuse"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcItems.GENERATOR_COUPLING.get()).pattern(" N ").pattern("ISI").pattern(" N ")
                .define('N', Items.IRON_NUGGET).define('I', PcItems.STEEL_ROD.get()).define('S', AllBlocks.SHAFT.get())
                .unlockedBy("has_shaft", has(AllBlocks.SHAFT.get())).save(out, id("generator_coupling"));
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, PcItems.INTERLOCK_MODULE.get())
                .requires(Items.IRON_INGOT).requires(Items.TRIPWIRE_HOOK).requires(Items.REDSTONE)
                .unlockedBy("has_wire", hasWire).save(out, id("interlock_module"));

        // проводники
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.POWER_CABLE.get(), 6).pattern("BBB").pattern("CCC").pattern("BBB")
                .define('B', Items.BLACK_WOOL).define('C', wire)
                .unlockedBy("has_wire", hasWire).save(out, id("power_cable"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.BUSBAR.get(), 6).pattern("CCC").pattern(" P ")
                .define('C', Items.COPPER_INGOT).define('P', pin)
                .unlockedBy("has_pin", has(pin)).save(out, id("busbar"));
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, PcBlocks.WIRE_CONNECTOR_LV.get(), 2)
                .requires(pin).requires(wire).requires(Items.IRON_NUGGET)
                .unlockedBy("has_pin", has(pin)).save(out, id("wire_connector_lv"));
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, PcBlocks.WIRE_CONNECTOR_MV.get())
                .requires(pin, 2).requires(PcItems.STEEL_ROD.get())
                .unlockedBy("has_pin", has(pin)).save(out, id("wire_connector_mv"));
        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, PcBlocks.WOODEN_POLE.get(), 3).pattern("L").pattern("L").pattern("L")
                .define('L', ItemTags.LOGS)
                .unlockedBy("has_pin", has(pin)).save(out, id("wooden_pole"));
        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, PcBlocks.CONCRETE_POLE.get(), 3).pattern("S").pattern("I").pattern("S")
                .define('S', Items.SMOOTH_STONE).define('I', PcItems.STEEL_ROD.get())
                .unlockedBy("has_pin", has(pin)).save(out, id("concrete_pole"));

        // машины
        // вал ротора и вал двигателя — стальной пруток со шпоночным пазом под полумуфту (фрезерный станок)
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.SHAFT_GENERATOR.get()).pattern("IWI").pattern("WSW").pattern("IBI")
                .define('I', PcItems.STEEL_PLATE.get()).define('W', wire).define('S', PcItems.KEYED_SHAFT.get()).define('B', PcItems.STEEL_BEAM.get())
                .unlockedBy("has_wire", hasWire).save(out, id("shaft_generator"));
        // пульт диспетчера: щит с приборами, мнемосхема (карта) и телемеханика (компаратор)
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.GRID_DISPATCH_CENTER.get()).pattern("PMP").pattern("WCW").pattern("PPP")
                .define('P', PcItems.STEEL_PLATE.get()).define('M', net.minecraft.world.item.Items.MAP).define('W', wire)
                .define('C', net.minecraft.world.item.Items.COMPARATOR).unlockedBy("has_wire", hasWire).save(out, id("grid_dispatch_center"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.GENERATOR_EXCITER.get()).pattern("BWB").pattern("WSW").pattern("BRB")
                .define('B', AllItems.BRASS_INGOT.get()).define('W', wire).define('S', AllBlocks.SHAFT.get()).define('R', Items.REDSTONE)
                .unlockedBy("has_generator", has(PcBlocks.SHAFT_GENERATOR.get())).save(out, id("generator_exciter"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.ELECTRIC_MOTOR.get()).pattern("IWI").pattern("WSW").pattern("ICI")
                .define('I', PcItems.STEEL_PLATE.get()).define('W', wire).define('S', PcItems.KEYED_SHAFT.get())
                .define('C', AllBlocks.ANDESITE_CASING.get())
                .unlockedBy("has_wire", hasWire).save(out, id("electric_motor"));
        // чертежи генератора и двигателя реального масштаба: строятся на стройплощадке по массе прототипа (SitePlans)
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, dev.denfry.promptcritical.mod.site.SiteRegistry.SHAFT_GENERATOR_REAL_DRAWING.get())
                .pattern("PPP").pattern("WSW").pattern(" B ").define('P', Items.PAPER).define('W', wire)
                .define('S', PcItems.KEYED_SHAFT.get()).define('B', PcItems.STEEL_BEAM.get())
                .unlockedBy("has_wire", hasWire).save(out, id("shaft_generator_real_drawing"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, dev.denfry.promptcritical.mod.site.SiteRegistry.ELECTRIC_MOTOR_MV_REAL_DRAWING.get())
                .pattern("PPP").pattern("WSW").pattern(" C ").define('P', Items.PAPER).define('W', wire)
                .define('S', PcItems.KEYED_SHAFT.get()).define('C', AllBlocks.ANDESITE_CASING.get())
                .unlockedBy("has_wire", hasWire).save(out, id("electric_motor_mv_real_drawing"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.DISTRIBUTION_TRANSFORMER.get()).pattern("PPP").pattern("WBW").pattern("IWI")
                .define('P', pin).define('W', wire).define('B', PcItems.STEEL_PLATE.get()).define('I', PcItems.STEEL_BEAM.get())
                .unlockedBy("has_pin", has(pin)).save(out, id("distribution_transformer"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.GRID_CONSUMER.get()).pattern("BLB").pattern("BWB").pattern("BDB")
                .define('B', Items.BRICKS).define('L', Items.LANTERN).define('W', wire).define('D', ItemTags.WOODEN_DOORS)
                .unlockedBy("has_wire", hasWire).save(out, id("grid_consumer"));
        // интерфейс сети и FE: инвертор на тиристорах, щит управления и трансформатор у больших вариантов
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.GRID_FE_INTERFACE_SMALL.get()).pattern("PWP").pattern("CRC").pattern("PWP")
                .define('P', PcItems.STEEL_PLATE.get()).define('W', wire).define('C', Items.COMPARATOR).define('R', Items.REDSTONE_BLOCK)
                .unlockedBy("has_wire", hasWire).save(out, id("grid_fe_interface_small"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.GRID_FE_INTERFACE.get()).pattern("SWS").pattern("WTW").pattern("SWS")
                .define('S', PcBlocks.GRID_FE_INTERFACE_SMALL.get()).define('W', wire).define('T', PcBlocks.DISTRIBUTION_TRANSFORMER.get())
                .unlockedBy("has_fe_interface", has(PcBlocks.GRID_FE_INTERFACE_SMALL.get())).save(out, id("grid_fe_interface"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.GRID_FE_INTERFACE_LARGE.get()).pattern("SWS").pattern("WTW").pattern("SWS")
                .define('S', PcBlocks.GRID_FE_INTERFACE.get()).define('W', wire).define('T', PcBlocks.DISTRIBUTION_TRANSFORMER.get())
                .unlockedBy("has_fe_interface", has(PcBlocks.GRID_FE_INTERFACE.get())).save(out, id("grid_fe_interface_large"));
        // конденсаторы: обкладки — фольга из листа, диэлектрик — бумага в масле (банки на стеллаже)
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.CAPACITOR_BANK.get()).pattern("SPS").pattern("SPS").pattern("WBW")
                .define('S', AllItems.IRON_SHEET.get()).define('P', Items.PAPER).define('W', wire).define('B', PcItems.STEEL_PLATE.get())
                .unlockedBy("has_wire", hasWire).save(out, id("capacitor_bank"));

        // коммутация и щиты
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.DISCONNECTOR.get()).pattern("WLW").pattern("P P")
                .define('W', wire).define('L', Items.LEVER).define('P', pin)
                .unlockedBy("has_pin", has(pin)).save(out, id("disconnector"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.SWITCHGEAR_CELL.get()).pattern("SLS").pattern("PWP").pattern("SBS")
                .define('S', AllItems.IRON_SHEET.get()).define('L', Items.LEVER).define('P', pin).define('W', wire)
                .define('B', PcItems.STEEL_BEAM.get())
                .unlockedBy("has_pin", has(pin)).save(out, id("switchgear_cell"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.LV_SWITCHBOARD.get()).pattern("SLS").pattern("WRW").pattern("SSS")
                .define('S', AllItems.IRON_SHEET.get()).define('L', Items.LEVER).define('W', wire).define('R', Items.REDSTONE)
                .unlockedBy("has_wire", hasWire).save(out, id("lv_switchboard"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.LV_DISTRIBUTION_BOX.get()).pattern("SSS").pattern("FWF").pattern("SSS")
                .define('S', AllItems.IRON_SHEET.get()).define('F', PcItems.LV_FUSE.get()).define('W', wire)
                .unlockedBy("has_fuse", has(PcItems.LV_FUSE.get())).save(out, id("lv_distribution_box"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.LIGHTING_BOARD.get()).pattern("SLS").pattern("FWF").pattern("SGS")
                .define('S', AllItems.IRON_SHEET.get()).define('L', Items.LEVER).define('F', PcItems.LV_FUSE.get()).define('W', wire)
                .define('G', Items.GLOWSTONE_DUST)
                .unlockedBy("has_fuse", has(PcItems.LV_FUSE.get())).save(out, id("lighting_board"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.POWER_SOCKET.get(), 2).pattern(" Q ").pattern("CWC").pattern(" Q ")
                .define('Q', Items.QUARTZ).define('C', Items.COPPER_INGOT).define('W', wire)
                .unlockedBy("has_fuse", has(PcItems.LV_FUSE.get())).save(out, id("power_socket"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.POWER_SOCKET_3P.get()).pattern("SRS").pattern("CWC").pattern("SFS")
                .define('S', AllItems.IRON_SHEET.get()).define('R', Items.RED_DYE).define('C', Items.COPPER_INGOT).define('W', wire)
                .define('F', PcItems.LV_FUSE.get())
                .unlockedBy("has_fuse", has(PcItems.LV_FUSE.get())).save(out, id("power_socket_3p"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.POWER_BOARD.get()).pattern("SLS").pattern("FWF").pattern("SRS")
                .define('S', AllItems.IRON_SHEET.get()).define('L', Items.LEVER).define('F', PcItems.LV_FUSE.get()).define('W', wire)
                .define('R', Items.REDSTONE)
                .unlockedBy("has_fuse", has(PcItems.LV_FUSE.get())).save(out, id("power_board"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.UPS_CABINET.get()).pattern("SDS").pattern("BWB").pattern("SBS")
                .define('S', AllItems.IRON_SHEET.get()).define('D', Items.COMPARATOR).define('B', PcBlocks.POWER_SOCKET.get()).define('W', wire)
                .unlockedBy("has_power_board", has(PcBlocks.POWER_BOARD.get())).save(out, id("ups_cabinet"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcItems.SOLAR_CELL.get(), 4).pattern(" C ").pattern("CWC").pattern(" G ")
                .define('C', Items.COPPER_INGOT).define('W', PcItems.SILICON_WAFER.get()).define('G', Items.GLOWSTONE_DUST)
                .unlockedBy("has_wafer", has(PcItems.SILICON_WAFER.get())).save(out, id("solar_cell"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.SOLAR_PANEL.get(), 2).pattern("GGG").pattern("CEC").pattern("AAA")
                .define('G', Items.GLASS_PANE).define('C', PcItems.SOLAR_CELL.get()).define('E', PcItems.POLYETHYLENE.get())
                .define('A', PcItems.ALUMINIUM_INGOT.get())
                .unlockedBy("has_cell", has(PcItems.SOLAR_CELL.get())).save(out, id("solar_panel"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.SOLAR_PANEL_TRACKER.get(), 2).pattern("PP").pattern("SS")
                .define('P', PcBlocks.SOLAR_PANEL.get()).define('S', AllBlocks.SHAFT.get())
                .unlockedBy("has_panel", has(PcBlocks.SOLAR_PANEL.get())).save(out, id("solar_panel_tracker"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.SOLAR_TRACKER_DRIVE.get()).pattern("SGS").pattern("HCH").pattern("SSS")
                .define('S', AllItems.IRON_SHEET.get()).define('G', AllBlocks.GEARBOX.get()).define('H', AllBlocks.SHAFT.get())
                .define('C', AllBlocks.COGWHEEL.get())
                .unlockedBy("has_panel", has(PcBlocks.SOLAR_PANEL.get())).save(out, id("solar_tracker_drive"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.SOLAR_INVERTER.get()).pattern("SDS").pattern("WTW").pattern("SCS")
                .define('S', AllItems.IRON_SHEET.get()).define('D', Items.COMPARATOR).define('W', wire).define('C', PcItems.SOLAR_CELL.get())
                .define('T', PcBlocks.POWER_SOCKET_3P.get())
                .unlockedBy("has_cell", has(PcItems.SOLAR_CELL.get())).save(out, id("solar_inverter"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.GRID_BATTERY_RACK.get()).pattern("SLS").pattern("LAL").pattern("SLS")
                .define('S', AllItems.IRON_SHEET.get()).define('L', PcBlocks.LEAD_BLOCK.get()).define('A', PcItems.SULFUR.get())
                .unlockedBy("has_lead", has(PcBlocks.LEAD_BLOCK.get())).save(out, id("grid_battery_rack"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.BATTERY_INVERTER.get()).pattern("SDS").pattern("WTW").pattern("SRS")
                .define('S', AllItems.IRON_SHEET.get()).define('D', Items.COMPARATOR).define('W', wire)
                .define('T', PcBlocks.DISTRIBUTION_TRANSFORMER.get()).define('R', PcBlocks.GRID_BATTERY_RACK.get())
                .unlockedBy("has_rack", has(PcBlocks.GRID_BATTERY_RACK.get())).save(out, id("battery_inverter"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.RELAY_CABINET.get()).pattern("SGS").pattern("SCS").pattern("SWS")
                .define('S', AllItems.IRON_SHEET.get()).define('G', Items.GLASS_PANE).define('C', Items.COMPARATOR).define('W', wire)
                .unlockedBy("has_wire", hasWire).save(out, id("relay_cabinet"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.INSTRUMENT_SYNCHROSCOPE.get()).pattern(" G ").pattern("WKW").pattern(" W ")
                .define('G', Items.GLASS_PANE).define('W', wire).define('K', Items.CLOCK)
                .unlockedBy("has_generator", has(PcBlocks.SHAFT_GENERATOR.get())).save(out, id("instrument_synchroscope"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.ENERGY_METER.get()).pattern(" G ").pattern("WCW").pattern(" I ")
                .define('G', Items.GLASS_PANE).define('W', wire).define('C', Items.COMPARATOR).define('I', Items.IRON_INGOT)
                .unlockedBy("has_wire", hasWire).save(out, id("energy_meter"));

        // молниезащита (07 §7.15.1): медный стержень с хомутом; разрядник — фарфор, искровой промежуток и резистор
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.GROUND_ELECTRODE.get(), 2).pattern("C").pattern("C").pattern("I")
                .define('C', Items.COPPER_INGOT).define('I', Items.IRON_INGOT)
                .unlockedBy("has_pole", has(PcBlocks.WOODEN_POLE.get())).save(out, id("ground_electrode"));
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, PcBlocks.SURGE_ARRESTER.get())
                .requires(PcItems.INSULATOR_PIN.get(), 2).requires(Items.IRON_INGOT).requires(PcItems.COPPER_WIRE.get()).requires(Items.REDSTONE)
                .unlockedBy("has_transformer", has(PcBlocks.DISTRIBUTION_TRANSFORMER.get())).save(out, id("surge_arrester"));

        // пусковые модули двигателя (07 §7.6.3): звезда–треугольник — три контактора и реле времени; плавный пуск —
        // тиристоры на кремнии, плата управления и шунтирующий контактор; частотный привод — силовые ключи и контроллер
        ShapelessRecipeBuilder.shapeless(RecipeCategory.REDSTONE, PcItems.STAR_DELTA_STARTER.get())
                .requires(PcItems.RELAY_COIL.get(), 3).requires(PcItems.COPPER_WIRE.get(), 2).requires(Items.IRON_INGOT).requires(Items.REPEATER)
                .unlockedBy("has_relay_coil", has(PcItems.RELAY_COIL.get())).save(out, id("star_delta_starter"));
        ShapelessRecipeBuilder.shapeless(RecipeCategory.REDSTONE, PcItems.SOFT_STARTER.get())
                .requires(PcItems.SILICON_WAFER.get(), 2).requires(PcItems.PRINTED_CIRCUIT_BOARD.get()).requires(PcItems.RELAY_COIL.get())
                .requires(PcItems.COPPER_WIRE.get(), 2).requires(Items.IRON_INGOT)
                .unlockedBy("has_pcb", has(PcItems.PRINTED_CIRCUIT_BOARD.get())).save(out, id("soft_starter"));
        ShapelessRecipeBuilder.shapeless(RecipeCategory.REDSTONE, PcItems.VARIABLE_FREQUENCY_DRIVE.get())
                .requires(PcItems.TRANSISTOR.get(), 3).requires(PcItems.INTEGRATED_CIRCUIT.get()).requires(PcItems.PRINTED_CIRCUIT_BOARD.get())
                .requires(PcItems.COPPER_WIRE.get(), 2).requires(Items.IRON_INGOT, 2)
                .unlockedBy("has_ic", has(PcItems.INTEGRATED_CIRCUIT.get())).save(out, id("variable_frequency_drive"));

        // модули реле: база и механизм функции
        relay(out, PcItems.RELAY_OVERCURRENT.get(), "relay_overcurrent", Items.REDSTONE_TORCH);
        relay(out, PcItems.RELAY_SYNC_CHECK.get(), "relay_sync_check", Items.CLOCK);
        relay(out, PcItems.RELAY_REVERSE_POWER.get(), "relay_reverse_power", Items.REPEATER);
        relay(out, PcItems.RELAY_FREQUENCY.get(), "relay_frequency", Items.AMETHYST_SHARD);
        relay(out, PcItems.RELAY_VOLTAGE.get(), "relay_voltage", Items.COMPARATOR);
        relay(out, PcItems.RELAY_THERMAL.get(), "relay_thermal", Items.COPPER_INGOT);
        relay(out, PcItems.RELAY_UFLS.get(), "relay_ufls", Items.AMETHYST_SHARD, Items.REPEATER);
        relay(out, PcItems.RELAY_AUTORECLOSE.get(), "relay_autoreclose", Items.CLOCK, Items.REPEATER);
        relay(out, PcItems.RELAY_BREAKER_FAILURE.get(), "relay_breaker_failure", Items.REPEATER, Items.REDSTONE_TORCH);
    }

    private void relay(RecipeOutput out, ItemLike result, String name, ItemLike... mechanism) {
        ShapelessRecipeBuilder b = ShapelessRecipeBuilder.shapeless(RecipeCategory.REDSTONE, result)
                .requires(PcItems.COPPER_WIRE.get()).requires(Items.IRON_INGOT).requires(Items.REDSTONE).requires(Items.GLASS_PANE);
        for (ItemLike m : mechanism) b.requires(m);
        b.unlockedBy("has_cabinet", has(PcBlocks.RELAY_CABINET.get())).save(out, id(name));
    }
}
