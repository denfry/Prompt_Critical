package dev.denfry.promptcritical.mod.registry;

import dev.denfry.promptcritical.mod.PromptCritical;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Жидкости и газы M2 (07 §7.22, 08 §8.20): существуют в трубах и баках (Create, гидросети); вёдер нет — газ из
 * ведра в мир не выливается; блок есть только у шахтной воды (затопление). Плотность газов отрицательная (всплывают), как принято в NeoForge.
 */
public final class PcFluids {
    public static final DeferredRegister<FluidType> TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, PromptCritical.MOD_ID);
    public static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, PromptCritical.MOD_ID);

    private static final List<Entry> ALL = new ArrayList<>();

    public static final Entry FLUE_GAS = gas("flue_gas", 450, -1);
    public static final Entry MINE_METHANE = gas("mine_methane", 293, -1);
    public static final Entry RAW_WATER = liquid("raw_water", 1000, 1000, 288);
    /** Шахтная вода: единственная с блоком — ею затапливаются выработки (08 §8.2.9); в источник не превращается. */
    public static final Entry MINE_WATER = liquid("mine_water", 1005, 1050, 293);
    // коксохим и домна (08 §8.4–8.5, этап 10 M3): газы в трубах Create — 1 мБ = 1 нм³
    public static final Entry COKE_OVEN_GAS = gas("coke_oven_gas", 303, -1);
    public static final Entry BLAST_FURNACE_GAS = gas("blast_furnace_gas", 313, -1);
    public static final Entry COAL_TAR = liquid("coal_tar", 1170, 6000, 333);
    public static final Entry CRUDE_BENZOL = liquid("crude_benzol", 880, 700, 293);
    // сталь и ВРУ (08 §8.6, §8.11, этап 10б M3): газы — 1 мБ = 1 нм³, жидкости — 1 мБ = 1 л
    public static final Entry CONVERTER_GAS = gas("converter_gas", 303, -1);
    public static final Entry OXYGEN = gas("oxygen", 293, -1);
    public static final Entry NITROGEN = gas("nitrogen", 293, -1);
    public static final Entry ARGON = gas("argon", 293, -1);
    public static final Entry LIQUID_OXYGEN = liquid("liquid_oxygen", 1141, 200, 90);
    public static final Entry LIQUID_NITROGEN = liquid("liquid_nitrogen", 807, 160, 77);
    // водоподготовка (08 §8.12, этап 11 M3): сорт воды — номинальное качество WaterGrade
    public static final Entry CLARIFIED_WATER = liquid("clarified_water", 1000, 1000, 288);
    public static final Entry FILTERED_WATER = liquid("filtered_water", 1000, 1000, 288);
    public static final Entry SOFTENED_WATER = liquid("softened_water", 1000, 1000, 288);
    /** Питательная вода котла I1 после деаэратора: 104 °C, O₂ ≤ 15 мкг/кг (этап 11б M3). */
    public static final Entry BOILER_FEEDWATER = liquid("boiler_feedwater", 955, 300, 377);

    // нефть и газ I2: продукция скважины и прямой перегонки; жидкости — 1 мБ = 1 л, газ — 1 мБ = 1 нм³
    public static final Entry CRUDE_OIL = liquid("crude_oil", 850, 8000, 293);
    public static final Entry PRODUCED_WATER = liquid("produced_water", 1030, 1000, 300);
    public static final Entry ASSOCIATED_GAS = gas("associated_gas", 300, -1);
    public static final Entry GASOLINE = liquid("gasoline", 735, 600, 293);
    public static final Entry KEROSENE = liquid("kerosene", 790, 1500, 293);
    public static final Entry DIESEL_FUEL = liquid("diesel_fuel", 840, 3500, 293);
    /** Мазут — остаток атмосферной перегонки, горячий: хранят и качают подогретым. */
    public static final Entry FUEL_OIL = liquid("fuel_oil", 950, 60000, 343);

    // газопереработка I2: товарный газ — 1 мБ = 1 нм³; сжиженные углеводороды и метанол — 1 мБ = 1 л жидкости
    public static final Entry SALES_GAS = gas("sales_gas", 293, -1);
    public static final Entry METHANOL = liquid("methanol", 791, 600, 293);
    public static final Entry NGL = liquid("ngl", 540, 300, 278);
    public static final Entry PROPANE = liquid("propane", 507, 120, 288);
    public static final Entry BUTANE = liquid("butane", 575, 180, 288);
    public static final Entry NATURAL_GASOLINE = liquid("natural_gasoline", 660, 350, 293);

    static {
        MINE_WATER.props.block(PcBlocks.MINE_WATER);
    }

    /** Тип, источник и текущая форма одной жидкости. */
    public static final class Entry {
        public final String name;
        public final DeferredHolder<FluidType, FluidType> type;
        public final DeferredHolder<Fluid, BaseFlowingFluid.Source> source;
        public final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> flowing;
        private final BaseFlowingFluid.Properties props;

        private Entry(String name, Supplier<FluidType> typeFactory) {
            this.name = name;
            this.type = TYPES.register(name, typeFactory);
            BaseFlowingFluid.Properties[] props = new BaseFlowingFluid.Properties[1];
            this.source = FLUIDS.register(name, () -> new BaseFlowingFluid.Source(props[0]));
            this.flowing = FLUIDS.register("flowing_" + name, () -> new BaseFlowingFluid.Flowing(props[0]));
            props[0] = new BaseFlowingFluid.Properties(type, source, flowing);
            this.props = props[0];
        }
    }

    private PcFluids() {
    }

    private static Entry gas(String name, int temperatureK, int density) {
        Entry e = new Entry(name, () -> new FluidType(FluidType.Properties.create()
                .density(density).viscosity(20).temperature(temperatureK).canSwim(false).canDrown(false)
                .canPushEntity(false).canExtinguish(false).supportsBoating(false)));
        ALL.add(e);
        return e;
    }

    private static Entry liquid(String name, int density, int viscosity, int temperatureK) {
        Entry e = new Entry(name, () -> new FluidType(FluidType.Properties.create()
                .density(density).viscosity(viscosity).temperature(temperatureK).canExtinguish(true)));
        ALL.add(e);
        return e;
    }

    public static List<Entry> all() {
        return Collections.unmodifiableList(ALL);
    }
}
