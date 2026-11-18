package dev.denfry.promptcritical.mod.uranium;

import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.panel.PanelLayout.LampColor;
import dev.denfry.promptcritical.mod.panel.PanelText;
import dev.denfry.promptcritical.mod.panel.PanelText.Tr;
import dev.denfry.promptcritical.mod.panel.PanelValues;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.uranium.UraniumLedger;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Щиты A0 передела урана. У всех один набор каналов: температура, четыре величины стадии, два стекла баков, учёт урана
 * (вход, продукт, потери, кг × 100) и флаги «включена», «работает», «внимание», «утечка/обход», «вспомогательный».
 */
public final class UraniumPanels {
    public static final int T_C = 0, A = 1, B = 2, C = 3, D = 4, TANK = 5, TANK2 = 6, U_IN = 7, U_OUT = 8, U_LOSS = 9, FLAGS = 10,
            CHANNELS = 11;
    public static final int F_ON = 0, F_RUN = 1, F_WARN = 2, F_LEAK = 3, F_AUX = 4;
    public static final int B_RUN = 0, B_T_DOWN = 1, B_T_UP = 2, B_AUX = 3;

    private static final Tr CELSIUS = PanelText.CELSIUS, PCT = PanelText.tr("unit.pct", "%", "%"),
            RUN = PanelText.tr("uranium.run", "Run", "Пуск/стоп"), ON = PanelText.tr("uranium.on", "On", "Вкл."),
            WORKING = PanelText.tr("uranium.working", "Working", "Работа"),
            TEMP = PanelText.tr("uranium.temp", "Temp.", "Темп."),
            T_DOWN = PanelText.tr("uranium.t_down", "−10 °C", "−10 °C"), T_UP = PanelText.tr("uranium.t_up", "+10 °C", "+10 °C"),
            LEDGER = PanelText.tr("uranium.ledger", "U in %s kg · to product %s kg · lost %s kg · recovery %s %%",
                    "U пришло %s кг · в продукт %s кг · потери %s кг · извлечение %s %%");
    // кислота
    private static final Tr BED = PanelText.tr("uranium.bed", "Last bed", "4-й слой"),
            CONV = PanelText.tr("uranium.conv", "SO₂→SO₃", "SO₂→SO₃"),
            COLD_CAT = PanelText.tr("uranium.cold_cat", "Cold cat.", "Масса гаснет"),
            PYRITE = PanelText.tr("uranium.pyrite", "Pyrite ore (sulphur by assay)", "Колчедан (сера по пробе)"),
            ACID_TANK = PanelText.tr("uranium.acid_tank", "H₂SO₄", "H₂SO₄"),
            ACID_STATUS = PanelText.tr("uranium.acid_status", "Conversion %s %% · acid %s kg/h · SO₂ to stack %s kg/h",
                    "Превращение %s %% · кислота %s кг/ч · SO₂ в трубу %s кг/ч");
    // HF
    private static final Tr HF_YIELD = PanelText.tr("uranium.hf_yield", "HF yield", "Выход HF"),
            FLUORSPAR = PanelText.tr("uranium.fluorspar", "Fluorite ore (CaF₂ by assay)", "Флюорит (CaF₂ по пробе)"),
            HF_TANK = PanelText.tr("uranium.hf_tank", "HF", "HF"),
            HF_LEAK = PanelText.tr("uranium.hf_leak", "HF vent", "Сброс HF"),
            OFF_RANGE = PanelText.tr("uranium.off_range", "Off range", "Вне режима"),
            HF_STATUS = PanelText.tr("uranium.hf_status", "HF yield %s %% · %s kg/h", "Выход HF %s %% · %s кг/ч");
    // выщелачивание
    private static final Tr EXTRACT = PanelText.tr("uranium.extract", "Extraction", "Извлечение"),
            NO_OX = PanelText.tr("uranium.no_ox", "No MnO₂", "Нет MnO₂"),
            PLS = PanelText.tr("uranium.pls", "Pregnant", "Продукт."),
            TAILS = PanelText.tr("uranium.tails", "Tailings", "Хвосты"),
            ORE = PanelText.tr("uranium.ore", "Ground uranium ore, 50 kg", "Молотая урановая руда, 50 кг"),
            PYROLUSITE = PanelText.tr("uranium.pyrolusite", "Pyrolusite (MnO₂ oxidant)", "Пиролюзит (окислитель MnO₂)"),
            LEACH_STATUS = PanelText.tr("uranium.leach_status", "Dissolving %s g U/h · oxidant %s %% of the norm",
                    "Растворяется %s г U/ч · окислитель %s %% нормы");
    // ионный обмен
    private static final Tr BED_A = PanelText.tr("uranium.bed_a", "Column 1", "Колонна 1"),
            BED_B = PanelText.tr("uranium.bed_b", "Column 2", "Колонна 2"),
            BYPASS = PanelText.tr("uranium.bypass", "Bypass", "Обход"),
            ELUTING = PanelText.tr("uranium.eluting", "Eluting", "Элюир."),
            ELUATE = PanelText.tr("uranium.eluate", "Eluate", "Элюат"),
            RESIN = PanelText.tr("uranium.resin", "Anion exchange resin, 100 l", "Анионит, 100 л"),
            IX_STATUS = PanelText.tr("uranium.ix_status", "Resin %s l · loading column %s", "Смола %s л · на сорбции колонна %s");
    // ДУА
    private static final Tr PRECIP = PanelText.tr("uranium.precip", "Precipit.", "Осаждение"),
            LOW_NH3 = PanelText.tr("uranium.low_nh3", "Low NH₃", "Мало NH₃"),
            NH3 = PanelText.tr("uranium.nh3", "NH₃", "NH₃"),
            CAKE = PanelText.tr("uranium.cake", "Yellowcake, 1 kg U; cryolite", "Жёлтый кек, 1 кг U; криолит"),
            HYDRATE = PanelText.tr("uranium.hydrate", "Aluminium hydroxide for cryolite (with HF and caustic)",
                    "Гидроксид алюминия на криолит (с HF и щёлочью)"),
            ADU_STATUS = PanelText.tr("uranium.adu_status", "Ammonia %s %% of the norm · in the filter press %s kg U",
                    "Аммиак %s %% нормы · на фильтр-прессе %s кг U");
    // конверсия
    private static final Tr WINDOW = PanelText.tr("uranium.window", "In window", "В режиме"),
            REAGENT = PanelText.tr("uranium.reagent", "Reagent", "Реагент"),
            FUEL = PanelText.tr("uranium.fuel", "Fuel", "Топливо"),
            FEED = PanelText.tr("uranium.feed", "Feed powder, 1 kg U", "Порошок на загрузку, 1 кг U"),
            PRODUCT = PanelText.tr("uranium.product", "Product, 1 kg U", "Продукт, 1 кг U"),
            CONV_STATUS = PanelText.tr("uranium.conv_status", "Throughput %s kg U/h · %s %% of capacity at this temperature",
                    "Переработка %s кг U/ч · %s %% мощности при этой температуре");
    // фторирование
    private static final Tr F2 = PanelText.tr("uranium.f2", "F₂", "F₂"),
            UF6_VENT = PanelText.tr("uranium.uf6_vent", "UF₆ vent", "Сдувка UF₆"),
            NO_CYL = PanelText.tr("uranium.no_cyl", "No cylinder", "Нет баллона"),
            SHIP = PanelText.tr("uranium.ship", "Ship", "Выдать"),
            SHIP_TIP = PanelText.tr("uranium.ship_tip", "Send the cylinder on as it is, partly filled",
                    "Выдать баллон как есть, недолитым"),
            CYL = PanelText.tr("uranium.cyl", "UF₄ and empty UF₆ cylinders (48Y, 5A)", "UF₄ и пустые баллоны UF₆ (48Y, 5A)"),
            FULL_CYL = PanelText.tr("uranium.full_cyl", "Filled UF₆ cylinder", "Наполненный баллон UF₆"),
            FL_STATUS = PanelText.tr("uranium.fl_status", "UF₆ %s kg/h · cold trap %s kg · cylinder %s kg",
                    "UF₆ %s кг/ч · ловушка %s кг · в баллоне %s кг");
    // фторные ванны
    private static final Tr CELL_V = PanelText.tr("uranium.cell_v", "Cell", "Ванна"),
            HF_BATH = PanelText.tr("uranium.hf_bath", "HF in melt", "HF в расплаве"),
            ANODE = PanelText.tr("uranium.anode", "Anode eff.", "Анодн. эфф."),
            HF_LOW = PanelText.tr("uranium.hf_low", "HF low", "Мало HF"),
            KA_DOWN = PanelText.tr("uranium.ka_down", "−0.5 kA", "−0,5 кА"), KA_UP = PanelText.tr("uranium.ka_up", "+0.5 kA", "+0,5 кА"),
            F2_STATUS = PanelText.tr("uranium.f2_status", "Current %s kA per cell · F₂ %s kg/h",
                    "Ток %s кА на ванну · F₂ %s кг/ч");

    public static final PanelLayout ACID = base("acid_plant", true, BED, 800).dial(64, 24, false, A, 10, 100, Double.NaN, 1, PCT.get(),
            CONV.get(), null).glass(120, 24, 56, TANK, 0xFFE8E0C0, Double.NaN, Double.NaN, ACID_TANK.get(), null)
            .lamp(150, 52, FLAGS, F_WARN, LampColor.AMBER, COLD_CAT.get())
            .slot(150, 68, 0, AcidPlantBlockEntity::pyrite, PYRITE.get()).slot(168, 68, 1, AcidPlantBlockEntity::pyrite, PYRITE.get())
            .slot(186, 68, 2, AcidPlantBlockEntity::pyrite, PYRITE.get()).slot(204, 68, 3, AcidPlantBlockEntity::pyrite, PYRITE.get())
            .readout(8, 110, 216, v -> ACID_STATUS.get(v.num(A, 10, 1), v.raw(B), v.num(C, 10, 1)), null).build();

    public static final PanelLayout HF = base("hf_kiln", true, TEMP, 600).dial(64, 24, false, A, 10, 100, Double.NaN, 1, PCT.get(),
            HF_YIELD.get(), null).glass(120, 24, 56, TANK, 0xFFE0F0F0, Double.NaN, Double.NaN, HF_TANK.get(), null)
            .lamp(150, 24, FLAGS, F_LEAK, LampColor.RED, HF_LEAK.get()).lamp(150, 38, FLAGS, F_WARN, LampColor.AMBER, OFF_RANGE.get())
            .slots(150, 68, 0, 4, s -> s.is(PcBlocks.FLUORITE_ORE.get().asItem()), FLUORSPAR)
            .readout(8, 110, 216, v -> HF_STATUS.get(v.num(A, 10, 1), v.raw(B)), null).build();

    public static final PanelLayout LEACH = base("leach_tank", true, TEMP, 100).dial(64, 24, false, A, 10, 100, Double.NaN, 1, PCT.get(),
            EXTRACT.get(), null).glass(120, 24, 56, TANK, 0xFFD8C840, Double.NaN, Double.NaN, PLS.get(), null)
            .glass(140, 24, 56, TANK2, 0xFF8A7A60, Double.NaN, Double.NaN, TAILS.get(), null)
            .lamp(162, 24, FLAGS, F_WARN, LampColor.AMBER, NO_OX.get())
            .slots(150, 68, 0, 4, s -> s.is(PcItems.GROUND_URANIUM_ORE.get()), ORE)
            .slots(150, 86, 4, 2, s -> s.is(PcItems.RAW_PYROLUSITE.get()), PYROLUSITE)
            .readout(8, 110, 216, v -> LEACH_STATUS.get(v.raw(B), v.raw(C)), null).build();

    public static final PanelLayout IX = base("uranium_ion_exchange", false, null, 0)
            .bar(8, 30, 100, A, 10, 100, 0xFFD8C840, BED_A.get(), null).bar(8, 44, 100, B, 10, 100, 0xFFD8C840, BED_B.get(), null)
            .glass(120, 24, 56, TANK, 0xFFE8D040, Double.NaN, Double.NaN, ELUATE.get(), null)
            .glass(140, 24, 56, TANK2, 0xFFD8C840, Double.NaN, Double.NaN, PLS.get(), null)
            .lamp(162, 24, FLAGS, F_AUX, LampColor.GREEN, ELUTING.get()).lamp(162, 38, FLAGS, F_LEAK, LampColor.RED, BYPASS.get())
            .slots(150, 68, 0, 4, s -> s.is(PcItems.ION_EXCHANGE_RESIN_SBA.get()), RESIN)
            .readout(8, 110, 216, v -> IX_STATUS.get(v.raw(C), v.raw(D) + 1), null).build();

    public static final PanelLayout ADU = base("precipitation_tank", false, null, 0).dial(8, 24, false, A, 10, 100, Double.NaN, 1,
            PCT.get(), PRECIP.get(), null).glass(120, 24, 56, TANK, 0xFFE8D040, Double.NaN, Double.NaN, ELUATE.get(), null)
            .glass(140, 24, 56, TANK2, 0xFFE0E8F0, Double.NaN, Double.NaN, NH3.get(), null)
            .lamp(162, 24, FLAGS, F_WARN, LampColor.AMBER, LOW_NH3.get())
            .slots(150, 86, 0, 2, s -> s.is(PcItems.ALUMINIUM_HYDROXIDE.get()), HYDRATE)
            .outputs(150, 68, 2, 4, CAKE)
            .readout(8, 110, 216, v -> ADU_STATUS.get(v.raw(B), v.num(C, 100, 2)), null).build();

    public static final PanelLayout CALCINER = conversion("rotary_calciner", 800);
    public static final PanelLayout REDUCTION = conversion("reduction_furnace", 1100);
    public static final PanelLayout HYDROFLUORINATION = conversion("hydrofluorination_reactor", 1000);

    public static final PanelLayout FLUORINATION = base("fluorination_reactor", true, TEMP, 900).dial(64, 24, false, A, 10, 100,
            Double.NaN, 0, PCT.get(), WINDOW.get(), null).glass(120, 24, 56, TANK, 0xFFE8F0B0, Double.NaN, Double.NaN, F2.get(), null)
            .lamp(150, 24, FLAGS, F_LEAK, LampColor.RED, UF6_VENT.get()).lamp(150, 38, FLAGS, F_WARN, LampColor.AMBER, NO_CYL.get())
            .button(104, 92, 40, B_AUX, SHIP.get(), SHIP_TIP.get())
            .slots(150, 68, 0, 4, FluorinationBlockEntity::loadable, CYL).outputs(150, 86, 4, 2, FULL_CYL)
            .readout(8, 110, 216, v -> FL_STATUS.get(v.raw(B), v.num(C, 10, 1), v.num(D, 10, 1)), null).build();

    public static final PanelLayout F2_CELLS = PanelLayout.builder("fluorine_cell", 232, 124, CHANNELS)
            .dial(8, 24, false, A, 10, 40, 12, 1, PanelText.tr("unit.v", "V", "В").get(), CELL_V.get(), null)
            .dial(64, 24, false, B, 10, 50, Double.NaN, 1, PCT.get(), HF_BATH.get(), null)
            .glass(120, 24, 56, TANK, 0xFFE8F0B0, Double.NaN, Double.NaN, F2.get(), null)
            .glass(140, 24, 56, TANK2, 0xFFE0F0F0, Double.NaN, Double.NaN, HF_TANK.get(), null)
            .lamp(162, 22, FLAGS, F_RUN, LampColor.GREEN, WORKING.get()).lamp(162, 36, FLAGS, F_WARN, LampColor.AMBER, HF_LOW.get())
            .lamp(162, 50, FLAGS, F_LEAK, LampColor.RED, ANODE.get())
            .button(8, 92, 44, B_RUN, RUN.get(), null).button(56, 92, 40, B_T_DOWN, KA_DOWN.get(), null)
            .button(100, 92, 40, B_T_UP, KA_UP.get(), null)
            .readout(8, 110, 216, v -> F2_STATUS.get(v.num(D, 10, 1), v.num(C, 10, 1)), null).noInventory().build();

    private UraniumPanels() {
    }

    /** Общее: термометр (если есть), лампы «вкл.»/«работа», пуск и уставка температуры, учёт урана. */
    private static Builder base(String id, boolean temperature, Tr tempLabel, double tempMax) {
        Builder b = new Builder(PanelLayout.builder(id, 232, 140, CHANNELS));
        if (temperature) {
            b.b.dial(8, 24, false, T_C, 1, tempMax, Double.NaN, 0, CELSIUS.get(), tempLabel.get(), null);
            b.b.button(56, 92, 20, B_T_DOWN, Component.literal("−"), T_DOWN.get()).button(78, 92, 20, B_T_UP, Component.literal("+"), T_UP.get());
        }
        b.b.lamp(200, 22, FLAGS, F_ON, LampColor.GREEN, ON.get()).lamp(200, 36, FLAGS, F_RUN, LampColor.GREEN, WORKING.get())
                .button(8, 92, 44, B_RUN, RUN.get(), null)
                .readout(8, 124, 216, UraniumPanels::ledger, null);
        return b;
    }

    private static PanelLayout conversion(String id, double tempMax) {
        return base(id, true, TEMP, tempMax).dial(64, 24, false, A, 10, 100, Double.NaN, 0, PCT.get(), WINDOW.get(), null)
                .glass(120, 24, 56, TANK, 0xFFE0E8F0, Double.NaN, Double.NaN, REAGENT.get(), null)
                .glass(140, 24, 56, TANK2, 0xFFB0B8C0, Double.NaN, Double.NaN, FUEL.get(), null)
                .lamp(162, 24, FLAGS, F_WARN, LampColor.AMBER, OFF_RANGE.get())
                .slots(150, 68, 0, 4, s -> true, FEED).outputs(150, 86, 4, 4, PRODUCT)
                .readout(8, 110, 216, v -> CONV_STATUS.get(v.raw(B), v.num(A, 10, 0)), null).build();
    }

    /** Учёт урана стадии в каналы панели. */
    static void ledger(int[] v, UraniumLedger l) {
        v[U_IN] = (int) Math.round(l.inKg() * 100);
        v[U_OUT] = (int) Math.round(l.outKg() * 100);
        v[U_LOSS] = (int) Math.round(l.lossKg() * 100);
    }

    private static Component ledger(PanelValues v) {
        double done = v.raw(U_OUT) + v.raw(U_LOSS);
        return LEDGER.get(v.num(U_IN, 100, 2), v.num(U_OUT, 100, 2), v.num(U_LOSS, 100, 2),
                done > 0 ? String.format(java.util.Locale.ROOT, "%.1f", 100.0 * v.raw(U_OUT) / done) : "—");
    }

    public static List<PanelLayout> all() {
        return List.of(ACID, HF, LEACH, IX, ADU, CALCINER, REDUCTION, HYDROFLUORINATION, FLUORINATION, F2_CELLS);
    }

    /** Обёртка построителя с рядами слотов. */
    private static final class Builder {
        final PanelLayout.Builder b;

        Builder(PanelLayout.Builder b) {
            this.b = b;
        }

        Builder dial(int x, int y, boolean large, int ch, double scale, double max, double red, int dec, Component unit, Component label,
                Function<PanelValues, Component> tip) {
            b.dial(x, y, large, ch, scale, max, red, dec, unit, label, tip == null ? null : tip::apply);
            return this;
        }

        Builder glass(int x, int y, int h, int ch, int color, double low, double normal, Component label, Object tip) {
            b.glass(x, y, h, ch, color, low, normal, label, null);
            return this;
        }

        Builder bar(int x, int y, int w, int ch, double scale, double max, int color, Component label, Object tip) {
            b.bar(x, y, w, ch, scale, max, color, label, null);
            return this;
        }

        Builder lamp(int x, int y, int ch, int bit, LampColor c, Component label) {
            b.lamp(x, y, ch, bit, c, label);
            return this;
        }

        Builder button(int x, int y, int w, int id, Component label, Component tip) {
            b.button(x, y, w, id, label, tip);
            return this;
        }

        Builder slot(int x, int y, int index, Predicate<ItemStack> accepts, Component tip) {
            b.slot(x, y, index, accepts, tip);
            return this;
        }

        Builder slots(int x, int y, int first, int n, Predicate<ItemStack> accepts, Tr tip) {
            for (int i = 0; i < n; i++) b.slot(x + 18 * i, y, first + i, accepts, tip.get());
            return this;
        }

        Builder outputs(int x, int y, int first, int n, Tr tip) {
            for (int i = 0; i < n; i++) b.output(x + 18 * i, y, first + i, tip.get());
            return this;
        }

        Builder readout(int x, int y, int w, Function<PanelValues, Component> text, Object tip) {
            b.readout(x, y, w, text, null);
            return this;
        }

        PanelLayout build() {
            return b.build();
        }
    }
}
