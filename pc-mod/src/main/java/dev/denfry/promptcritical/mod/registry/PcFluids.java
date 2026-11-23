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
 * ведра в мир не выливается; блок есть только у шахтной воды (затопление) и реакторной тяжёлой воды — её, как и
 * обычную воду, носят ведром. Плотность газов отрицательная (всплывают), как принято в NeoForge.
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

    /** Пенообразователь (13 §13.5.3): дозатор подмешивает 3 или 6 % в воду пожарной сети. */
    public static final Entry FOAM_CONCENTRATE = liquid("foam_concentrate", 1050, 3000, 293);
    // нефть и газ I2: продукция скважины и прямой перегонки; жидкости — 1 мБ = 1 л, газ — 1 мБ = 1 нм³
    public static final Entry CRUDE_OIL = liquid("crude_oil", 850, 8000, 293);
    public static final Entry PRODUCED_WATER = liquid("produced_water", 1030, 1000, 300);
    /** Вода для ППД 09 §9.4.6: подготовленная (взвесь до 5 мг/л). */
    public static final Entry INJECTION_WATER = liquid("injection_water", 1010, 1000, 300);
    public static final Entry ASSOCIATED_GAS = gas("associated_gas", 300, -1);
    public static final Entry GASOLINE = liquid("gasoline", 735, 600, 293);
    public static final Entry KEROSENE = liquid("kerosene", 790, 1500, 293);
    public static final Entry DIESEL_FUEL = liquid("diesel_fuel", 840, 3500, 293);
    /** Мазут — остаток атмосферной перегонки, горячий: хранят и качают подогретым. */
    public static final Entry FUEL_OIL = liquid("fuel_oil", 950, 60000, 343);
    // углубление переработки (09 §9.9.2, §9.9.5): жидкости — 1 мБ = 1 л
    /** Вакуумный газойль 350–550 °C, горячий: сырьё каткрекинга. */
    public static final Entry VACUUM_GAS_OIL = liquid("vacuum_gas_oil", 910, 20000, 353);
    /** Гудрон &gt; 550 °C: сырьё коксования, хранят горячим. */
    public static final Entry VACUUM_RESIDUE = liquid("vacuum_residue", 990, 200000, 423);
    /** Бензин каткрекинга: олефины 20–25 %, ОЧИ 91–93. */
    public static final Entry FCC_GASOLINE = liquid("fcc_gasoline", 740, 600, 293);
    /** Лёгкий газойль каткрекинга — компонент дизельного топлива после гидроочистки. */
    public static final Entry LIGHT_CYCLE_OIL = liquid("light_cycle_oil", 940, 4000, 293);
    /** Тяжёлый газойль каткрекинга (шлам-масло): высокоароматичный, сырьё игольчатого кокса. */
    public static final Entry DECANT_OIL = liquid("decant_oil", 1050, 30000, 343);
    /** Сжиженный углеводородный газ: пропан и бутан-бутилены, под давлением насыщения. */
    public static final Entry LPG = liquid("lpg", 540, 150, 288);
    /** Риформат: ароматика 60–70 %, компонент бензина (раньше выходил из риформинга как прямогонный бензин). */
    public static final Entry REFORMATE = liquid("reformate", 815, 550, 293);
    /** Товарный бензин станции смешения (09 §9.9.14); ID {@code gasoline} занят прямогонным с I2. */
    public static final Entry MOTOR_GASOLINE = liquid("motor_gasoline", 745, 550, 293);

    // газопереработка I2: товарный газ — 1 мБ = 1 нм³; сжиженные углеводороды и метанол — 1 мБ = 1 л жидкости
    public static final Entry SALES_GAS = gas("sales_gas", 293, -1);
    public static final Entry METHANOL = liquid("methanol", 791, 600, 293);
    public static final Entry NGL = liquid("ngl", 540, 300, 278);
    public static final Entry PROPANE = liquid("propane", 507, 120, 288);
    public static final Entry BUTANE = liquid("butane", 575, 180, 288);
    public static final Entry NATURAL_GASOLINE = liquid("natural_gasoline", 660, 350, 293);
    /** Собранная с воды нефть: водонефтяная эмульсия скиммера, до 70 % воды (14 §14.5.4) — на обезвоживание. */
    public static final Entry RECOVERED_OIL = liquid("recovered_oil", 950, 20000, 288);

    // химия I3, пиролиз: этилен — 1 мБ = 1 нм³; сжиженный пропилен, C4-фракция, пиробензин и смола — 1 мБ = 1 л
    public static final Entry ETHYLENE = gas("ethylene", 293, -1);
    public static final Entry PROPYLENE = liquid("propylene", 514, 110, 288);
    /** C4-фракция пиролиза: бутадиен-1,3 (40–50 %), бутены и бутаны. */
    public static final Entry C4_FRACTION = liquid("c4_fraction", 620, 180, 288);
    public static final Entry PYROLYSIS_GASOLINE = liquid("pyrolysis_gasoline", 830, 500, 293);
    public static final Entry PYROLYSIS_FUEL_OIL = liquid("pyrolysis_fuel_oil", 1080, 20000, 343);
    // химия I3, азот: азотоводородная смесь (H₂:N₂ = 3) — 1 мБ = 1 нм³; жидкий аммиак, слабая (56 %) и
    // концентрированная (98 %) азотная кислота — 1 мБ = 1 л
    public static final Entry SYNGAS = gas("syngas", 313, -1);
    public static final Entry AMMONIA = liquid("ammonia", 682, 250, 240);
    public static final Entry NITRIC_ACID = liquid("nitric_acid", 1345, 2000, 293);
    public static final Entry CONCENTRATED_NITRIC_ACID = liquid("concentrated_nitric_acid", 1500, 900, 293);

    // хлор-щелочное производство и ПВХ, I3: газы — 1 мБ = 1 нм³, жидкости — 1 мБ = 1 л
    /** Очищенный рассол: 310 г/л NaCl, Ca + Mg ≤ 5 мг/л. */
    public static final Entry BRINE = liquid("brine", 1200, 1300, 333);
    /** Сырой рассол: жёсткость ≈ 300 мг/л. */
    public static final Entry RAW_BRINE = liquid("raw_brine", 1200, 1300, 333);
    public static final Entry CHLORINE = gas("chlorine", 293, 3);
    /** Жидкий хлор под собственным давлением, 1,47 кг/л при 20 °C. */
    public static final Entry LIQUID_CHLORINE = liquid("liquid_chlorine", 1470, 350, 293);
    public static final Entry HYDROGEN = gas("hydrogen", 293, -1);
    /** Гелий для заправки твэлов, 1 мБ = 1 нм³. */
    public static final Entry HELIUM = gas("helium", 293, -1);
    /** Электролитическая щёлочь диафрагменных ванн: 11 % NaOH, 15 % NaCl. */
    public static final Entry SODIUM_HYDROXIDE_SOLUTION = liquid("sodium_hydroxide_solution", 1240, 1800, 343);
    public static final Entry HYDROGEN_CHLORIDE = gas("hydrogen_chloride", 293, 2);
    /** Соляная кислота 31–36 % HCl с абсорбера печи синтеза (10 §10.3.4), ≈ 1,16 кг/л. */
    public static final Entry HYDROCHLORIC_ACID = liquid("hydrochloric_acid", 1160, 1700, 293);
    /** Трихлорсилан SiHCl₃, 1,342 кг/л, кипит при 31,8 °C: сырьё реактора Сименса (11 §11.5.2). */
    public static final Entry TRICHLOROSILANE = liquid("trichlorosilane", 1342, 330, 293);

    // передел урана I4: жидкости — 1 мБ = 1 л, газы — 1 мБ = 1 нм³
    /** Серная кислота 98 %, 1,84 кг/л. */
    public static final Entry SULFURIC_ACID = liquid("sulfuric_acid", 1840, 2400, 293);
    /** Безводный фтористый водород, 0,99 кг/л (кипит при 19,5 °C). */
    public static final Entry HYDROGEN_FLUORIDE = liquid("hydrogen_fluoride", 990, 250, 283);
    public static final Entry FLUORINE = gas("fluorine", 293, 2);
    /** Продуктивный раствор выщелачивания: 1 г/л U в сернокислом растворе. */
    public static final Entry PREGNANT_LEACH_SOLUTION = liquid("pregnant_leach_solution", 1030, 1100, 313);
    /** Маточник сорбции: обеднённый раствор, идёт обратно на выщелачивание. */
    public static final Entry RAFFINATE = liquid("raffinate", 1030, 1100, 303);
    /** Хвостовая пульпа: 50 % твёрдого. */
    public static final Entry URANIUM_TAILINGS = liquid("uranium_tailings", 1450, 8000, 303);
    /** Элюат: 10 г/л U в подкисленном растворе NaCl. */
    public static final Entry URANIUM_ELUATE = liquid("uranium_eluate", 1060, 1100, 303);
    /**
     * Жидкие радиоактивные отходы АЭС: трапные воды, регенерационные растворы фильтров СВО, сбросы дезактивации —
     * солесодержание порядка граммов на литр, Cs-137, Cs-134 и Co-60 около 10⁻⁶ Ки/л (3,7·10⁴ Бк/л, состав — в
     * {@link dev.denfry.promptcritical.mod.waste.LrwPool}). Идут на выпарку.
     */
    public static final Entry LIQUID_RADIOACTIVE_WASTE = liquid("liquid_radioactive_waste", 1005, 1000, 303);
    public static final Entry ETHYLENE_DICHLORIDE = liquid("ethylene_dichloride", 1253, 840, 293);
    public static final Entry VINYL_CHLORIDE = liquid("vinyl_chloride", 911, 200, 288);

    // дезактивация: растворы — 1 мБ = 1 л
    /** Лимонная + щавелевая кислота по 2–3 % (комплексоны): растворяют оксиды железа с радионуклидами. */
    public static final Entry DECON_SOLUTION = liquid("decon_solution", 1020, 1050, 293);
    /** Щелочной перманганат (KMnO₄ ≈ 1 %, NaOH ≈ 3 %): окислительная стадия, переводит хром оксидной плёнки в хромат. */
    public static final Entry DECON_PERMANGANATE = liquid("decon_permanganate", 1040, 1050, 293);
    /** Отработанный дезактивирующий раствор и смыв душа — жидкие радиоактивные отходы. */
    public static final Entry CONTAMINATED_DECON_SOLUTION = liquid("contaminated_decon_solution", 1010, 1050, 293);
    // тяжёлая вода T3: сорта по доле D ({@code HeavyWaterGrade}), 1 мБ = 1 л; вязкость D₂O при 20 °C — 1,25 мПа·с
    /** Реакторная D₂O, 99,75 ат. % D, 1,105 кг/л: единственная с блоком и ведром. */
    public static final Entry HEAVY_WATER = liquid("heavy_water", 1105, 1250, 293);
    /** Разбавленная D₂O, 90 ат. % D: слив реактора и сбор утечек — сырьё доводочной колонны. */
    public static final Entry DOWNGRADED_HEAVY_WATER = liquid("downgraded_heavy_water", 1094, 1230, 293);
    /** Продукт сероводородного завода, 15 ат. % D. */
    public static final Entry DEUTERIUM_ENRICHED_WATER = liquid("deuterium_enriched_water", 1014, 1040, 293);
    // цветная металлургия (08 §8.8.1): оборотный раствор 200 г/л NaOH, 150 г/л Al₂O₃ после автоклава; шлам — пульпа 40 %
    public static final Entry SODIUM_ALUMINATE_LIQUOR = liquid("sodium_aluminate_liquor", 1300, 3000, 368);
    public static final Entry RED_MUD = liquid("red_mud", 1350, 30000, 333);

    static {
        MINE_WATER.props.block(PcBlocks.MINE_WATER);
        // лениво: PcItems может быть ещё в инициализации, когда сюда приходит PcFluids
        HEAVY_WATER.props.block(() -> PcBlocks.HEAVY_WATER.get()).bucket(() -> PcItems.HEAVY_WATER_BUCKET.get());
        // разлив нефти на суше (09 §9.11.9): вязкая, растекается медленно и недалеко, горит
        CRUDE_OIL.props.block(() -> PcBlocks.CRUDE_OIL.get()).tickRate(30).slopeFindDistance(2).levelDecreasePerBlock(2);
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
