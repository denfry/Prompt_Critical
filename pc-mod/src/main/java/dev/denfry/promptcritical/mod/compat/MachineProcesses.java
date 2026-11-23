package dev.denfry.promptcritical.mod.compat;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.foundry.FoundryTags;
import dev.denfry.promptcritical.mod.ironworks.IronworksTags;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcItems;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/**
 * Справочный каталог процессов машин для просмотрщиков рецептов (JEI, EMI через плагин JEI). Машины мода считают
 * выход симуляцией (анализ угля, химия плавки, режим со щита), поэтому каталог — не рецепты, а «что входит и что
 * выходит» с пояснением; количество 0 — «по режиму». Без зависимостей от API просмотрщиков: плагин только рисует.
 */
public final class MachineProcesses {
    /** Цех — категория просмотрщика. Первая машина — значок категории. */
    public enum Plant {
        COAL_PREP, POWER, WATER, FOUNDRY, COKE, ELECTRODES, IRON, STEEL, ROLLING, AIR, MINE;

        public String id() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }

        public String titleKey() {
            return "jei." + PromptCritical.MOD_ID + ".plant." + id();
        }
    }

    /** Вход или выход процесса: предметы (список вариантов) или жидкости; {@code amount} 0 — по режиму. */
    public sealed interface Part permits Items_, Fluids_ {
        int amount();
    }

    public record Items_(Supplier<List<ItemStack>> stacks, int amount) implements Part {
        public List<ItemStack> resolve() {
            int n = Math.max(1, amount);
            return stacks.get().stream().map(s -> s.copyWithCount(n)).toList();
        }
    }

    public record Fluids_(Supplier<List<Fluid>> fluids, int amount) implements Part {
    }

    /**
     * Процесс: {@code id} — ключ пояснения {@code jei.promptcritical.process.<id>}; {@code machines} — блоки, на
     * которых он идёт (катализаторы категории).
     */
    public record Process(String id, Plant plant, List<Supplier<? extends ItemLike>> machines, List<Part> inputs,
            List<Part> outputs) {
        public String noteKey() {
            return "jei." + PromptCritical.MOD_ID + ".process." + id;
        }

        public List<ItemStack> machineStacks() {
            List<ItemStack> out = new ArrayList<>();
            for (Supplier<? extends ItemLike> m : machines) {
                Item item = m.get().asItem();
                if (item != Items.AIR) out.add(new ItemStack(item));
            }
            return out;
        }
    }

    private static List<Process> all;

    private MachineProcesses() {
    }

    /** Все процессы; строится лениво — после регистрации блоков и предметов. */
    public static synchronized List<Process> all() {
        if (all == null) all = Collections.unmodifiableList(build());
        return all;
    }

    /** Машины цеха без повторов, в порядке процессов. */
    public static List<ItemStack> machines(Plant plant) {
        List<ItemStack> out = new ArrayList<>();
        for (Process p : all()) {
            if (p.plant() != plant) continue;
            for (ItemStack s : p.machineStacks()) {
                if (out.stream().noneMatch(o -> o.is(s.getItem()))) out.add(s);
            }
        }
        return out;
    }

    // --- конструкторы частей ---

    @SafeVarargs
    private static Part item(int amount, Supplier<? extends ItemLike>... items) {
        return new Items_(() -> Arrays.stream(items).map(i -> new ItemStack(i.get())).toList(), amount);
    }

    private static Part item(Supplier<? extends ItemLike> item) {
        return item(1, item);
    }

    private static Part tag(TagKey<Item> tag, int amount) {
        return new Items_(() -> BuiltInRegistries.ITEM.getTag(tag)
                .map(set -> set.stream().map(h -> new ItemStack(h.value())).toList()).orElse(List.of()), amount);
    }

    /** Жидкость мода по id ({@code promptcritical:<id>}). */
    private static Part fluid(int amount, String... ids) {
        return new Fluids_(() -> Arrays.stream(ids)
                .map(id -> BuiltInRegistries.FLUID.get(PromptCritical.id(id)))
                .filter(f -> f != Fluids.EMPTY).toList(), amount);
    }

    private static Part water(int amount) {
        return new Fluids_(() -> List.of(Fluids.WATER), amount);
    }

    private static Part fluidTag(TagKey<Fluid> tag, int amount) {
        return new Fluids_(() -> BuiltInRegistries.FLUID.getTag(tag)
                .map(set -> set.stream().map(h -> h.value()).filter(f -> f.isSource(f.defaultFluidState())).toList())
                .orElse(List.of()), amount);
    }

    /** Горючие газы завода ({@code ProcessGas}): горелки печей и котла I1. */
    private static Part fuelGas() {
        return fluid(0, "coke_oven_gas", "blast_furnace_gas", "mine_methane", "converter_gas");
    }

    private static final TagKey<Fluid> SULFURIC_ACID = IronworksTags.SULFURIC_ACID;
    private static final TagKey<Fluid> WASH_OIL = IronworksTags.WASH_OIL;

    @SafeVarargs
    private static List<Supplier<? extends ItemLike>> on(Supplier<? extends ItemLike>... machines) {
        return List.of(machines);
    }

    private static List<Part> of(Part... parts) {
        return List.of(parts);
    }

    private static List<Process> build() {
        List<Process> p = new ArrayList<>();
        // углеобогащение и котельная I0 (08 §8.3, 07 §7.9.1)
        p.add(new Process("coal_jig", Plant.COAL_PREP, on(PcBlocks.COAL_JIG),
                of(item(0, PcItems.RAW_COAL), water(0)),
                of(item(0, PcItems.WASHED_COAL), item(0, PcItems.COAL_MIDDLINGS), item(0, PcItems.COAL_REFUSE))));
        p.add(new Process("fire_tube_boiler", Plant.POWER, on(PcBlocks.FIRE_TUBE_BOILER, PcBlocks.GRATE_STOKER, PcBlocks.LANCASHIRE_BOILER, PcBlocks.LANCASHIRE_STOKER),
                of(item(1, PcItems.RAW_COAL, PcItems.WASHED_COAL, PcItems.COAL_MIDDLINGS, () -> Items.COAL,
                        () -> Items.CHARCOAL, () -> Items.OAK_LOG), water(0)),
                of(item(0, PcItems.BOILER_SLAG))));
        p.add(new Process("water_tube_boiler", Plant.POWER,
                on(PcBlocks.WATER_TUBE_BOILER, PcBlocks.BOILER_BURNER, PcBlocks.STEAM_DRUM),
                of(item(0, PcItems.COAL_DUST), fuelGas(), fluid(0, "boiler_feedwater")), of()));
        p.add(new Process("deaerator", Plant.POWER, on(PcBlocks.DEAERATOR),
                of(fluid(0, "softened_water")), of(fluid(0, "boiler_feedwater"))));
        // водоподготовка (08 §8.12)
        p.add(new Process("water_clarifier", Plant.WATER, on(PcBlocks.WATER_CLARIFIER),
                of(water(0), item(0, PcItems.BURNT_LIME)), of(fluid(0, "clarified_water"))));
        p.add(new Process("sand_filter", Plant.WATER, on(PcBlocks.SAND_FILTER),
                of(fluid(0, "clarified_water")), of(fluid(0, "filtered_water"))));
        p.add(new Process("ion_exchange_column", Plant.WATER, on(PcBlocks.ION_EXCHANGE_COLUMN),
                of(fluid(0, "filtered_water"), item(0, PcItems.SALT)), of(fluid(0, "softened_water"))));
        // литейка (08 §8.9, 08 §8.10, 13 §13.6.7)
        p.add(new Process("molding_station", Plant.FOUNDRY, on(PcBlocks.MOLDING_STATION),
                of(item(1, PcItems.FOUNDRY_SAND), item(PcItems.CASTING_PATTERN), water(5)),
                of(item(PcBlocks.SAND_MOLD))));
        p.add(new Process("cupola_furnace", Plant.FOUNDRY, on(PcBlocks.CUPOLA_FURNACE),
                of(tag(FoundryTags.CUPOLA_IRON, 1), tag(FoundryTags.CUPOLA_COKE, 1), item(PcBlocks.SAND_MOLD)),
                of(item(PcItems.CASTING))));
        p.add(new Process("crucible_furnace", Plant.FOUNDRY, on(PcBlocks.CRUCIBLE_FURNACE, PcBlocks.CRUCIBLE_FURNACE_REAL),
                of(new Items_(() -> concat(tagStacks(FoundryTags.COPPER_INGOTS), tagStacks(FoundryTags.BRONZE_INGOTS),
                        tagStacks(FoundryTags.ALUMINUM_INGOTS)), 1), tag(FoundryTags.FURNACE_COAL, 0),
                        item(PcBlocks.SAND_MOLD)),
                of(item(PcItems.CASTING))));
        p.add(new Process("shakeout_grate", Plant.FOUNDRY, on(PcBlocks.SHAKEOUT_GRATE),
                of(item(PcBlocks.SAND_MOLD)), of(item(PcItems.CASTING), item(0, PcItems.FOUNDRY_SAND))));
        p.add(new Process("machine_tool", Plant.FOUNDRY, on(PcBlocks.MACHINE_LATHE, PcBlocks.DRILL_PRESS),
                of(item(PcItems.CASTING), item(PcItems.CUTTING_TOOL)), of(item(PcItems.CASTING))));
        // станочный парк: детали машин завода (Ra и квалитет — от режима)
        p.add(new Process("milling_machine", Plant.FOUNDRY, on(PcBlocks.MILLING_MACHINE),
                of(item(PcItems.CASTING), item(PcItems.MILLING_CUTTER)), of(item(PcItems.SPUR_GEAR))));
        p.add(new Process("milling_keyway", Plant.FOUNDRY, on(PcBlocks.MILLING_MACHINE),
                of(item(PcItems.STEEL_ROD), item(PcItems.MILLING_CUTTER)), of(item(PcItems.KEYED_SHAFT))));
        p.add(new Process("shaping_slideway", Plant.FOUNDRY, on(PcBlocks.SHAPING_MACHINE),
                of(item(PcItems.CASTING), item(PcItems.CUTTING_TOOL)), of(item(PcItems.MACHINE_SLIDEWAY), item(PcItems.PLANED_PLATE))));
        p.add(new Process("shaping_plate", Plant.FOUNDRY, on(PcBlocks.SHAPING_MACHINE),
                of(item(PcItems.STEEL_PLATE), item(PcItems.CUTTING_TOOL)), of(item(PcItems.PLANED_PLATE))));
        p.add(new Process("surface_grinding", Plant.FOUNDRY, on(PcBlocks.SURFACE_GRINDER),
                of(item(PcItems.PLANED_PLATE), item(PcItems.GRINDING_WHEEL)), of(item(PcItems.GROUND_PLATE))));
        p.add(new Process("casting_impeller", Plant.FOUNDRY, on(() -> Items.CRAFTING_TABLE),
                of(item(PcItems.CASTING)), of(item(PcItems.PUMP_IMPELLER))));
        p.add(new Process("casting_bearing", Plant.FOUNDRY, on(() -> Items.CRAFTING_TABLE),
                of(item(PcItems.CASTING)), of(item(2, PcItems.BRONZE_BEARING_SHELL))));
        // коксохим (08 §8.4)
        p.add(new Process("coke_oven", Plant.COKE,
                on(PcBlocks.COKE_OVEN, PcBlocks.COKE_BATTERY_WALL, PcBlocks.COKE_PUSHER_RAM, PcBlocks.GAS_COLLECTOR_MAIN,
                        PcBlocks.GAS_EXHAUSTER),
                of(tag(IronworksTags.COKING_COAL, 0), tag(IronworksTags.FIREBOX_FUEL, 0)),
                of(item(0, PcItems.COKE), item(0, PcItems.COKE_BREEZE), fluid(0, "coke_oven_gas"))));
        p.add(new Process("pitch_coke", Plant.COKE, on(PcBlocks.COKE_OVEN),
                of(item(0, PcItems.COAL_TAR_PITCH)), of(item(0, PcItems.PITCH_COKE), item(0, PcItems.COKE_BREEZE))));
        p.add(new Process("quench_tower", Plant.COKE, on(PcBlocks.QUENCH_TOWER),
                of(item(PcItems.COKE), water(0)), of(item(PcItems.COKE))));
        p.add(new Process("byproduct_plant", Plant.COKE, on(PcBlocks.BYPRODUCT_PLANT),
                of(fluid(0, "coke_oven_gas"), fluidTag(SULFURIC_ACID, 0), fluidTag(WASH_OIL, 0)),
                of(fluid(0, "coal_tar"), item(0, PcItems.AMMONIUM_SULFATE), fluid(0, "crude_benzol"))));
        // электроды (08 §8.4.5)
        p.add(new Process("electrode_extruder", Plant.ELECTRODES, on(PcBlocks.ELECTRODE_EXTRUDER),
                of(item(3, PcItems.PITCH_COKE), item(0, PcItems.COAL_TAR_PITCH)), of(item(PcItems.GREEN_ELECTRODE))));
        p.add(new Process("baking_furnace", Plant.ELECTRODES, on(PcBlocks.BAKING_FURNACE),
                of(item(PcItems.GREEN_ELECTRODE), fuelGas()), of(item(PcItems.GREEN_ELECTRODE))));
        // аглофабрика и доменный цех (08 §8.5)
        p.add(new Process("magnetic_separator", Plant.IRON, on(PcBlocks.MAGNETIC_SEPARATOR),
                of(item(4, PcItems.RAW_MAGNETITE)), of(item(0, PcItems.IRON_ORE_CONCENTRATE), item(0, PcItems.STONE_DUST))));
        p.add(new Process("sinter_strand", Plant.IRON, on(PcBlocks.SINTER_STRAND),
                of(tag(IronworksTags.SINTER_ORE, 10), item(0, PcItems.COKE_BREEZE), tag(IronworksTags.FLUX, 0), fuelGas()),
                of(item(0, PcItems.SINTER))));
        p.add(new Process("blast_furnace", Plant.IRON,
                on(PcBlocks.BLAST_FURNACE_CONTROLLER, PcBlocks.BLAST_FURNACE_TOP, PcBlocks.BLAST_FURNACE_SHELL,
                        PcBlocks.BLAST_FURNACE_TUYERE, PcBlocks.BLAST_FURNACE_TAPHOLE, PcBlocks.HOT_BLAST_STOVE,
                        PcBlocks.BLAST_BLOWER, PcBlocks.DUST_CATCHER, PcBlocks.CAST_HOUSE_MACHINE, PcBlocks.IRON_RUNNER,
                        PcBlocks.LADLE),
                of(tag(IronworksTags.FURNACE_ORE, 0), item(0, PcItems.COKE), tag(IronworksTags.FLUX, 0),
                        item(2, PcItems.TAPHOLE_CLAY)),
                of(item(0, PcItems.PIG_IRON), fluid(0, "blast_furnace_gas"))));
        p.add(new Process("slag_granulator", Plant.IRON, on(PcBlocks.SLAG_GRANULATOR),
                of(water(0)), of(item(0, PcItems.GRANULATED_SLAG))));
        // сталеплавильный цех (08 §8.6, 08 §8.7.1)
        p.add(new Process("bof_vessel", Plant.STEEL, on(PcBlocks.BOF_VESSEL, PcBlocks.OXYGEN_LANCE),
                of(item(0, PcItems.PIG_IRON), item(0, PcItems.STEEL_SCRAP), item(0, PcItems.BURNT_LIME),
                        fluid(0, "oxygen")),
                of(item(PcBlocks.LADLE), fluid(0, "converter_gas"))));
        p.add(new Process("ladle_station", Plant.STEEL, on(PcBlocks.LADLE_STATION),
                of(item(PcBlocks.LADLE), item(0, PcItems.FERROALLOY), fluid(0, "argon")), of(item(PcBlocks.LADLE))));
        p.add(new Process("ingot_mold", Plant.STEEL, on(PcBlocks.INGOT_MOLD),
                of(item(PcBlocks.LADLE), item(1, PcItems.REFRACTORY_BRICK)),
                of(item(0, PcItems.STEEL_INGOT), item(0, PcItems.STEEL_SCRAP))));
        // прокат и трубы (08 §8.7)
        p.add(new Process("reheating_furnace", Plant.ROLLING, on(PcBlocks.REHEATING_FURNACE),
                of(item(1, PcItems.STEEL_INGOT, PcItems.STEEL_BILLET), fuelGas()),
                of(item(1, PcItems.STEEL_INGOT, PcItems.STEEL_BILLET))));
        p.add(new Process("rolling_mill_stand", Plant.ROLLING, on(PcBlocks.ROLLING_MILL_STAND, PcBlocks.ROLLER_TABLE),
                of(item(1, PcItems.STEEL_INGOT, PcItems.STEEL_BILLET)),
                of(item(0, PcItems.STEEL_BILLET, PcItems.STEEL_PLATE, PcItems.STEEL_ROD, PcItems.STEEL_BEAM))));
        p.add(new Process("piercing_mill", Plant.ROLLING, on(PcBlocks.PIERCING_MILL),
                of(item(1, PcItems.STEEL_BILLET, PcItems.STEEL_INGOT)), of(item(0, PcItems.SEAMLESS_PIPE))));
        p.add(new Process("pipe_welder", Plant.ROLLING, on(PcBlocks.PIPE_WELDER),
                of(item(PcItems.STEEL_PLATE)), of(item(0, PcItems.WELDED_PIPE))));
        p.add(new Process("heat_treatment", Plant.ROLLING, on(PcBlocks.HEAT_TREATMENT_FURNACE, PcBlocks.QUENCH_TANK),
                of(item(1, PcItems.STEEL_PLATE, PcItems.STEEL_ROD, PcItems.STEEL_BEAM, PcItems.SEAMLESS_PIPE,
                        PcItems.WELDED_PIPE), fuelGas(), water(0)),
                of(item(1, PcItems.STEEL_PLATE, PcItems.STEEL_ROD, PcItems.STEEL_BEAM, PcItems.SEAMLESS_PIPE,
                        PcItems.WELDED_PIPE))));
        p.add(new Process("hydrotest_bench", Plant.ROLLING, on(PcBlocks.HYDROTEST_BENCH),
                of(item(1, PcItems.SEAMLESS_PIPE, PcItems.WELDED_PIPE), water(20)),
                of(item(1, PcItems.SEAMLESS_PIPE, PcItems.WELDED_PIPE), item(0, PcItems.STEEL_SCRAP))));
        // кислородная станция (08 §8.11)
        p.add(new Process("air_separator", Plant.AIR,
                on(PcBlocks.AIR_SEPARATOR, PcBlocks.ASU_MAIN_COMPRESSOR, PcBlocks.ASU_MOLSIEVE, PcBlocks.ASU_EXPANDER,
                        PcBlocks.CRYOGENIC_TANK),
                of(water(0)),
                of(fluid(0, "oxygen"), fluid(0, "nitrogen"), fluid(0, "argon"), fluid(0, "liquid_oxygen"),
                        fluid(0, "liquid_nitrogen"))));
        // шахта (08 §8.2)
        p.add(new Process("mine_drainage", Plant.MINE, on(PcBlocks.MINE_DRAINAGE_PUMP),
                of(), of(fluid(0, "mine_water"))));
        p.add(new Process("degasification", Plant.MINE, on(PcBlocks.DEGASIFICATION_PUMP, PcBlocks.DEGASIFICATION_BOREHOLE),
                of(), of(fluid(0, "mine_methane"))));
        p.add(new Process("recording_gauge", Plant.POWER, on(PcBlocks.RECORDING_GAUGE),
                of(item(PcItems.CHART_PAPER)), of(item(PcItems.RECORDED_CHART))));
        return p;
    }

    private static List<ItemStack> tagStacks(TagKey<Item> tag) {
        return BuiltInRegistries.ITEM.getTag(tag).map(set -> set.stream().map(h -> new ItemStack(h.value())).toList())
                .orElse(List.of());
    }

    @SafeVarargs
    private static List<ItemStack> concat(List<ItemStack>... lists) {
        List<ItemStack> out = new ArrayList<>();
        for (List<ItemStack> l : lists) out.addAll(l);
        return out;
    }
}
