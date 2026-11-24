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
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
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
        radprot(out);
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
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcItems.STRIPPABLE_COATING.get())
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
        gear(out);
        // свинец выплавляют из галенита (обжиг PbS и восстановление — свёрнуты в доменную плавку)
        SimpleCookingRecipeBuilder.blasting(Ingredient.of(PcBlocks.GALENA_ORE.get(), PcBlocks.DEEPSLATE_GALENA_ORE.get()), RecipeCategory.BUILDING_BLOCKS,
                        PcBlocks.LEAD_BLOCK.get(), 0.3f, 200)
                .unlockedBy("has_galena", has(PcBlocks.GALENA_ORE.get())).save(out, id("lead_block_from_galena"));
    }

    /** Индивидуальная дозиметрия, радиационные СИЗ и санпропускник (04b §4b.7, §4b.8.3, §4b.12). */
    private void gear(RecipeOutput out) {
        var brass = AllItems.BRASS_SHEET.get();
        var iron = AllItems.IRON_SHEET.get();
        var tube = AllItems.ELECTRON_TUBE.get();
        var pipe = com.simibubi.create.AllBlocks.FLUID_PIPE.get();
        // бытовой счётчик: та же трубка, без второго (камерного) тракта и коррекции — дешевле ДКС
        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, PcItems.GEIGER_COUNTER.get())
                .pattern(" G ").pattern("BEB").pattern(" R ").define('G', Items.GLASS).define('B', brass).define('E', tube)
                .define('R', Items.REDSTONE).unlockedBy("has_electron_tube", has(tube)).save(out, id("geiger_counter"));
        // ЭПД: кремниевый диод — пластина, счёт и память — микросхема
        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, PcItems.PERSONAL_DOSIMETER_EPD.get())
                .pattern("BWB").pattern("BCB").pattern(" R ").define('B', brass).define('W', PcItems.SILICON_WAFER.get())
                .define('C', PcItems.INTEGRATED_CIRCUIT.get()).define('R', Items.REDSTONE)
                .unlockedBy("has_integrated_circuit", has(PcItems.INTEGRATED_CIRCUIT.get())).save(out, id("personal_dosimeter_epd"));
        // ТЛД: кристалл-термолюминофор в кассете
        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, PcItems.TLD_BADGE.get())
                .pattern("Q").pattern("I").define('Q', Items.QUARTZ).define('I', iron)
                .unlockedBy("has_dosimeter", has(PcItems.DOSIMETER.get())).save(out, id("tld_badge"));
        // считыватель: нагрев кассеты и фотоумножитель — лампа за стеклом
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.TLD_READER.get())
                .pattern("GEG").pattern("ITI").pattern("III").define('G', Items.GLASS_PANE).define('E', tube).define('I', iron)
                .define('T', PcItems.TLD_BADGE.get()).unlockedBy("has_tld_badge", has(PcItems.TLD_BADGE.get())).save(out, id("tld_reader"));
        // йод добывали из золы морских водорослей; таблетки в блистере
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, PcItems.POTASSIUM_IODIDE.get(), 4)
                .requires(Items.DRIED_KELP, 3).requires(Items.PAPER).unlockedBy("has_dried_kelp", has(Items.DRIED_KELP))
                .save(out, id("potassium_iodide"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcItems.IODINE_CARTRIDGE.get(), 2)
                .pattern("ICI").pattern("CCC").pattern("ICI").define('I', iron).define('C', Items.CHARCOAL)
                .unlockedBy("has_charcoal", has(Items.CHARCOAL)).save(out, id("iodine_cartridge"));
        ShapedRecipeBuilder.shaped(RecipeCategory.COMBAT, PcItems.RESPIRATOR.get())
                .pattern("LSL").pattern(" W ").define('L', Items.LEATHER).define('S', Items.STRING).define('W', Items.WHITE_WOOL)
                .unlockedBy("has_leather", has(Items.LEATHER)).save(out, id("respirator"));
        ShapedRecipeBuilder.shaped(RecipeCategory.COMBAT, PcItems.FULL_FACE_RESPIRATOR.get())
                .pattern("LGL").pattern("IWI").define('L', Items.LEATHER).define('G', Items.GLASS_PANE).define('I', iron)
                .define('W', Items.WHITE_WOOL).unlockedBy("has_respirator", has(PcItems.RESPIRATOR.get())).save(out, id("full_face_respirator"));
        // PAPR — полнолицевая маска на капюшоне с блоком вентилятора (тот же, что у химического респиратора)
        ShapedRecipeBuilder.shaped(RecipeCategory.COMBAT, PcItems.PAPR.get())
                .pattern(" F ").pattern("CRC").define('F', PcItems.FULL_FACE_RESPIRATOR.get()).define('C', PcItems.IODINE_CARTRIDGE.get())
                .define('R', PcItems.POWERED_RESPIRATOR.get()).unlockedBy("has_full_face_respirator", has(PcItems.FULL_FACE_RESPIRATOR.get()))
                .save(out, id("papr"));
        // пневмокостюм — пластикат (ПВХ), как ЛГ-1
        var pvc = PcItems.PVC.get();
        ShapedRecipeBuilder.shaped(RecipeCategory.COMBAT, PcItems.PNEUMOSUIT_HELMET.get())
                .pattern("PPP").pattern("PGP").define('P', pvc).define('G', Items.GLASS_PANE)
                .unlockedBy("has_pvc", has(pvc)).save(out, id("pneumosuit_helmet"));
        ShapedRecipeBuilder.shaped(RecipeCategory.COMBAT, PcItems.PNEUMOSUIT_JACKET.get())
                .pattern("P P").pattern("PTP").pattern("PPP").define('P', pvc).define('T', pipe)
                .unlockedBy("has_pvc", has(pvc)).save(out, id("pneumosuit_jacket"));
        ShapedRecipeBuilder.shaped(RecipeCategory.COMBAT, PcItems.PNEUMOSUIT_TROUSERS.get())
                .pattern("PPP").pattern("P P").pattern("P P").define('P', pvc)
                .unlockedBy("has_pvc", has(pvc)).save(out, id("pneumosuit_trousers"));
        ShapedRecipeBuilder.shaped(RecipeCategory.COMBAT, PcItems.PNEUMOSUIT_BOOTS.get())
                .pattern("P P").pattern("P P").define('P', pvc)
                .unlockedBy("has_pvc", has(pvc)).save(out, id("pneumosuit_boots"));
        ShapedRecipeBuilder.shaped(RecipeCategory.COMBAT, PcItems.PROTECTIVE_OVERALLS.get())
                .pattern("W W").pattern("WWW").pattern("WWW").define('W', Items.WHITE_WOOL)
                .unlockedBy("has_white_wool", has(Items.WHITE_WOOL)).save(out, id("protective_overalls"));
        ShapedRecipeBuilder.shaped(RecipeCategory.COMBAT, PcItems.BOOT_COVERS.get())
                .pattern("P P").pattern("P P").define('P', PcItems.POLYETHYLENE.get())
                .unlockedBy("has_polyethylene", has(PcItems.POLYETHYLENE.get())).save(out, id("boot_covers"));
        ShapedRecipeBuilder.shaped(RecipeCategory.COMBAT, PcItems.LEAD_APRON.get())
                .pattern("L L").pattern("LBL").pattern("LLL").define('L', Items.LEATHER).define('B', PcBlocks.LEAD_BLOCK.get())
                .unlockedBy("has_lead_block", has(PcBlocks.LEAD_BLOCK.get())).save(out, id("lead_apron"));
        // санпропускник и зона
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.INSTRUMENT_DOSE_RATE.get())
                .pattern("IDI").pattern("IEI").define('I', iron).define('D', PcItems.DOSIMETER.get()).define('E', tube)
                .unlockedBy("has_dosimeter", has(PcItems.DOSIMETER.get())).save(out, id("instrument_dose_rate"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.ZONE_CONTROLLER.get())
                .pattern("BEB").pattern("IRI").define('B', brass).define('E', tube).define('I', iron).define('R', Items.REDSTONE)
                .unlockedBy("has_dosimeter", has(PcItems.DOSIMETER.get())).save(out, id("zone_controller"));
        ShapelessRecipeBuilder.shapeless(RecipeCategory.REDSTONE, PcBlocks.ZONE_ACCESS_DOOR.get())
                .requires(Items.IRON_DOOR).requires(brass).requires(Items.REDSTONE)
                .unlockedBy("has_dosimeter", has(PcItems.DOSIMETER.get())).save(out, id("zone_access_door"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.CLEAN_LOCKER.get())
                .pattern("III").pattern("ICI").pattern("III").define('I', iron).define('C', Items.CHEST)
                .unlockedBy("has_iron_sheet", has(iron)).save(out, id("clean_locker"));
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, PcBlocks.DIRTY_LOCKER.get())
                .requires(PcBlocks.CLEAN_LOCKER.get()).requires(Items.YELLOW_DYE)
                .unlockedBy("has_clean_locker", has(PcBlocks.CLEAN_LOCKER.get())).save(out, id("dirty_locker"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PcBlocks.AIR_SUPPLY_PORT.get())
                .pattern("P").pattern("I").pattern("P").define('P', pipe).define('I', iron)
                .unlockedBy("has_fluid_pipe", has(pipe)).save(out, id("air_supply_port"));
    }

    /**
     * Радзащита реального масштаба: плитки защиты крафтятся (это материал, а не машина), узлы строятся на стройплощадке
     * по чертежам (SitePlans), чертёж — бумага вокруг старого прибора или материала узла. Бора в наборе предметов пока нет:
     * его заменяет глина — бура (Na₂B₄O₇) добывается из глинистых эвапоритов [BALANCE: заменитель до своего предмета].
     */
    private static void radprot(RecipeOutput out) {
        // магнетитовый бетон: заполнитель — магнетит вместо гравия, 3,6 г/см³ против 2,3
        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, PcBlocks.SHIELD_HEAVY_CONCRETE_REAL.get(), 4)
                .pattern("CMC").pattern("MCM").pattern("CMC").define('C', Items.GRAY_CONCRETE).define('M', PcItems.RAW_MAGNETITE.get())
                .unlockedBy("has_raw_magnetite", has(PcItems.RAW_MAGNETITE.get())).save(out, id("shield_heavy_concrete_real"));
        // шевронные кирпичи перекладкой свинцового блока, и обратно
        ShapelessRecipeBuilder.shapeless(RecipeCategory.BUILDING_BLOCKS, PcBlocks.SHIELD_LEAD_BRICK_REAL.get())
                .requires(PcBlocks.LEAD_BLOCK.get()).unlockedBy("has_lead_block", has(PcBlocks.LEAD_BLOCK.get())).save(out, id("shield_lead_brick_real"));
        ShapelessRecipeBuilder.shapeless(RecipeCategory.BUILDING_BLOCKS, PcBlocks.LEAD_BLOCK.get())
                .requires(PcBlocks.SHIELD_LEAD_BRICK_REAL.get()).unlockedBy("has_shield_lead_brick", has(PcBlocks.SHIELD_LEAD_BRICK_REAL.get()))
                .save(out, id("lead_block_from_bricks"));
        // бак: сварная нержавейка, вода, борная кислота
        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, PcBlocks.SHIELD_BORATED_WATER_TANK_REAL.get())
                .pattern("PPP").pattern("PWP").pattern("PBP").define('P', PcItems.STEEL_PLATE.get()).define('W', Items.WATER_BUCKET)
                .define('B', Items.CLAY_BALL).unlockedBy("has_steel_plate", has(PcItems.STEEL_PLATE.get())).save(out, id("shield_borated_water_tank_real"));
        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, PcBlocks.SHIELD_BORATED_PE_REAL.get(), 4)
                .pattern("PPP").pattern("PBP").pattern("PPP").define('P', PcItems.POLYETHYLENE.get()).define('B', Items.CLAY_BALL)
                .unlockedBy("has_polyethylene", has(PcItems.POLYETHYLENE.get())).save(out, id("shield_borated_pe_real"));
        drawing(out, dev.denfry.promptcritical.mod.site.SiteRegistry.PORTAL_MONITOR_REAL_DRAWING.get(), PcBlocks.PORTAL_MONITOR.get(), "portal_monitor_real");
        drawing(out, dev.denfry.promptcritical.mod.site.SiteRegistry.HAND_FOOT_MONITOR_REAL_DRAWING.get(), PcBlocks.HAND_FOOT_MONITOR.get(),
                "hand_foot_monitor_real");
        drawing(out, dev.denfry.promptcritical.mod.site.SiteRegistry.HOT_CELL_REAL_DRAWING.get(), PcBlocks.SHIELD_HEAVY_CONCRETE_REAL.get(), "hot_cell_real");
        drawing(out, dev.denfry.promptcritical.mod.site.SiteRegistry.SPENT_FUEL_CASK_REAL_DRAWING.get(), PcBlocks.FUEL_RACK.get(), "spent_fuel_cask_real");
        drawing(out, dev.denfry.promptcritical.mod.site.SiteRegistry.CASK_TRANSPORT_CART_REAL_DRAWING.get(), Items.RAIL, "cask_transport_cart_real");
        drawing(out, dev.denfry.promptcritical.mod.site.SiteRegistry.DRY_STORAGE_MODULE_REAL_DRAWING.get(), Items.GRAY_CONCRETE, "dry_storage_module_real");
    }

    private static void drawing(RecipeOutput out, net.minecraft.world.item.Item drawing, net.minecraft.world.level.ItemLike core, String plan) {
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, drawing).pattern("PPP").pattern("PMP").pattern("PPP").define('P', Items.PAPER)
                .define('M', core).unlockedBy("has_core", has(core)).save(out, id(plan + "_drawing"));
    }
}
