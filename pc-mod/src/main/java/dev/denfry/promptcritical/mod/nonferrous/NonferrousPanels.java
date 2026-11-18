package dev.denfry.promptcritical.mod.nonferrous;

import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.panel.PanelLayout.LampColor;
import dev.denfry.promptcritical.mod.panel.PanelText;
import dev.denfry.promptcritical.mod.panel.PanelText.Tr;
import dev.denfry.promptcritical.mod.registry.PcItems;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;

/**
 * Щиты цветной металлургии (08 §8.8). Общий набор каналов: температура, пять величин стадии, три стекла баков и флаги
 * «включено», «работа», «вне режима», «вспомогательный», «авария»; кнопки — пуск, уставка −/+ и две служебные.
 */
public final class NonferrousPanels {
    public static final int T_C = 0, A = 1, B = 2, C = 3, D = 4, E = 5, TANK = 6, TANK2 = 7, TANK3 = 8, FLAGS = 9, CHANNELS = 10;
    public static final int F_ON = 0, F_RUN = 1, F_WARN = 2, F_AUX = 3, F_ALARM = 4;
    public static final int B_RUN = 0, B_DOWN = 1, B_UP = 2, B_AUX = 3, B_AUX2 = 4;

    private static final Tr CELSIUS = PanelText.CELSIUS, PCT = PanelText.PERCENT,
            RUN = PanelText.tr("nf.run", "Run", "Пуск/стоп"), ON = PanelText.tr("nf.on", "On", "Вкл."),
            WORKING = PanelText.tr("nf.working", "Working", "Работа"), TEMP = PanelText.tr("nf.temp", "Temp.", "Темп."),
            OFF_RANGE = PanelText.tr("nf.off_range", "Off range", "Вне режима");
    // Байер
    private static final Tr EXTRACT = PanelText.tr("nf.extract", "Al₂O₃ out", "Извлеч."),
            LIQUOR = PanelText.tr("nf.liquor", "Liquor", "Раствор"), MUD = PanelText.tr("nf.mud", "Red mud", "Шлам"),
            CAUSTIC = PanelText.tr("nf.caustic", "NaOH", "NaOH"), NO_FUEL = PanelText.tr("nf.no_fuel", "No fuel", "Нет газа"),
            BAUXITE = PanelText.tr("nf.bauxite", "Bauxite (Al₂O₃ by assay)", "Боксит (Al₂O₃ по пробе)"),
            DIGEST_STATUS = PanelText.tr("nf.digest_status", "Al₂O₃ %s kg/h · %s t bauxite and %s kg NaOH per t of alumina",
                    "Al₂O₃ %s кг/ч · %s т боксита и %s кг NaOH на т глинозёма"),
            DIGEST_TIP = PanelText.tr("nf.digest_tip", "Gibbsite dissolves from 140 °C, boehmite only from 240 °C",
                    "Гиббсит растворяется от 140 °C, бёмит — только от 240 °C"),
            YIELD = PanelText.tr("nf.yield", "Yield", "Выход"), SPENT = PanelText.tr("nf.spent", "Spent", "Отраб."),
            HYDRATE = PanelText.tr("nf.hydrate", "Aluminium hydroxide", "Гидроксид алюминия"),
            PRECIP_STATUS = PanelText.tr("nf.precip_status", "Hydrate %s kg/h · on the filter %s kg · keep 50–70 °C",
                    "Гидроксид %s кг/ч · на фильтре %s кг · держать 50–70 °C"),
            CONVERSION = PanelText.tr("nf.conversion", "Calcined", "Прокалка"), FUEL = PanelText.tr("nf.fuel", "Fuel", "Газ"),
            ALUMINA = PanelText.tr("nf.alumina", "Alumina", "Глинозём"),
            CALC_STATUS = PanelText.tr("nf.calc_status", "Alumina %s kg/h · %s GJ/t · keep 1000–1100 °C",
                    "Глинозём %s кг/ч · %s ГДж/т · держать 1000–1100 °C");

    // электролиз алюминия
    private static final Tr BATH = PanelText.tr("nf.bath", "Bath", "Расплав"), AL2O3 = PanelText.tr("nf.al2o3", "Al₂O₃", "Al₂O₃"),
            ANODES = PanelText.tr("nf.anodes", "Anodes", "Аноды"), METAL = PanelText.tr("nf.metal", "Metal", "Металл"),
            LOW_ALUMINA = PanelText.tr("nf.low_alumina", "Low Al₂O₃", "Мало Al₂O₃"),
            THERMAL = PanelText.tr("nf.thermal", "Not in run", "Не в работе"),
            POT_ALARM = PanelText.tr("nf.pot_alarm", "Anode eff./frozen", "АЭ/замёрзла"),
            RELINE = PanelText.tr("nf.reline", "Reline", "Капремонт"),
            RELINE_TIP = PanelText.tr("nf.reline_tip", "Frozen or failed pot: a new cathode from 4 cathode blocks and 8 refractory bricks in the load",
                    "Замёрзшая или пробитая ванна: новая подина из 4 подовых блоков и 8 огнеупоров из загрузки"),
            KA_TIP = PanelText.tr("nf.ka_tip", "Series current ±10 kA (the rectifier the pots hang on)", "Ток серии ±10 кА (на выпрямителе серии)"),
            POT_LOAD = PanelText.tr("nf.pot_load", "Alumina, anodes, cryolite; cathode blocks and bricks for a reline",
                    "Глинозём, аноды, криолит; подовые блоки и кирпич на капремонт"),
            INGOT = PanelText.tr("nf.ingot", "Aluminium", "Алюминий"),
            POT_STATUS = PanelText.tr("nf.pot_status", "%s · %s V at %s kA (set %s) · Al %s kg/h",
                    "%s · %s В при %s кА (уставка %s) · Al %s кг/ч");
    private static final Tr[] POT_STATES = {PanelText.tr("nf.pot.cold", "Cold", "Холодная"), PanelText.tr("nf.pot.startup", "Start-up", "Пуск"),
            PanelText.tr("nf.pot.normal", "Normal", "Норма"), PanelText.tr("nf.pot.anode_effect", "ANODE EFFECT", "АНОДНЫЙ ЭФФЕКТ"),
            PanelText.tr("nf.pot.freezing", "Freezing", "Остывает"), PanelText.tr("nf.pot.frozen", "FROZEN", "ЗАМОРОЖЕНА"),
            PanelText.tr("nf.pot.cathode_failure", "CATHODE FAILURE", "ПРОБОЙ ПОДИНЫ")};

    public static final PanelLayout POT = pot();

    public static final PanelLayout DIGESTER = base("bayer_digester", TEMP, 300)
            .dial(64, 24, false, A, 10, 100, Double.NaN, 0, PCT.get(), EXTRACT.get(), v -> DIGEST_TIP.get())
            .glass(120, 24, 56, TANK, 0xFF9A7A50, Double.NaN, Double.NaN, LIQUOR.get(), null)
            .glass(138, 24, 56, TANK2, 0xFFB04A2A, Double.NaN, Double.NaN, MUD.get(), null)
            .glass(156, 24, 56, TANK3, 0xFFE0E0E8, Double.NaN, Double.NaN, CAUSTIC.get(), null)
            .lamp(200, 50, FLAGS, F_WARN, LampColor.AMBER, OFF_RANGE.get()).lamp(200, 64, FLAGS, F_AUX, LampColor.RED, NO_FUEL.get())
            .slot(120, 88, 0, s -> s.is(PcItems.RAW_BAUXITE.get()), BAUXITE.get()).slot(138, 88, 1, s -> s.is(PcItems.RAW_BAUXITE.get()), BAUXITE.get())
            .slot(156, 88, 2, s -> s.is(PcItems.RAW_BAUXITE.get()), BAUXITE.get()).slot(174, 88, 3, s -> s.is(PcItems.RAW_BAUXITE.get()), BAUXITE.get())
            .readout(8, 112, 216, v -> DIGEST_STATUS.get(v.raw(B), v.num(C, 100, 2), v.raw(D)), null).build();

    public static final PanelLayout PRECIPITATOR = base("alumina_precipitator", TEMP, 120)
            .dial(64, 24, false, A, 10, 100, Double.NaN, 0, PCT.get(), YIELD.get(), null)
            .glass(120, 24, 56, TANK, 0xFF9A7A50, Double.NaN, Double.NaN, LIQUOR.get(), null)
            .glass(138, 24, 56, TANK2, 0xFFE0E0E8, Double.NaN, Double.NaN, SPENT.get(), null)
            .lamp(200, 50, FLAGS, F_WARN, LampColor.AMBER, OFF_RANGE.get())
            .output(120, 88, 0, HYDRATE.get()).output(138, 88, 1, HYDRATE.get()).output(156, 88, 2, HYDRATE.get())
            .output(174, 88, 3, HYDRATE.get())
            .readout(8, 112, 216, v -> PRECIP_STATUS.get(v.raw(B), v.raw(C)), null).build();

    public static final PanelLayout CALCINER = base("alumina_calciner", TEMP, 1200)
            .dial(64, 24, false, A, 10, 100, Double.NaN, 0, PCT.get(), CONVERSION.get(), null)
            .glass(120, 24, 56, TANK, 0xFFB0B8C0, Double.NaN, Double.NaN, FUEL.get(), null)
            .lamp(200, 50, FLAGS, F_WARN, LampColor.AMBER, OFF_RANGE.get())
            .slot(120, 70, 0, s -> s.is(PcItems.ALUMINIUM_HYDROXIDE.get()), HYDRATE.get())
            .slot(138, 70, 1, s -> s.is(PcItems.ALUMINIUM_HYDROXIDE.get()), HYDRATE.get())
            .slot(156, 70, 2, s -> s.is(PcItems.ALUMINIUM_HYDROXIDE.get()), HYDRATE.get())
            .slot(174, 70, 3, s -> s.is(PcItems.ALUMINIUM_HYDROXIDE.get()), HYDRATE.get())
            .output(120, 88, 4, ALUMINA.get()).output(138, 88, 5, ALUMINA.get()).output(156, 88, 6, ALUMINA.get())
            .output(174, 88, 7, ALUMINA.get())
            .readout(8, 112, 216, v -> CALC_STATUS.get(v.raw(B), v.num(C, 10, 1)), null).build();

    private NonferrousPanels() {
    }

    public static List<PanelLayout> all() {
        return new ArrayList<>(List.of(DIGESTER, PRECIPITATOR, CALCINER, POT));
    }

    private static PanelLayout pot() {
        PanelLayout.Builder b = PanelLayout.builder("reduction_pot", 232, 156, CHANNELS)
                .dial(8, 24, false, T_C, 1, 1100, 1000, 0, CELSIUS.get(), BATH.get(), null)
                .dial(64, 24, false, A, 10, 6, Double.NaN, 1, PCT.get(), AL2O3.get(), null)
                .glass(120, 24, 56, TANK, 0xFF303030, Double.NaN, Double.NaN, ANODES.get(), null)
                .glass(138, 24, 56, TANK2, 0xFFC8CCD0, Double.NaN, Double.NaN, METAL.get(), null)
                .lamp(160, 22, FLAGS, F_ON, LampColor.GREEN, ON.get()).lamp(160, 36, FLAGS, F_RUN, LampColor.GREEN, WORKING.get())
                .lamp(160, 50, FLAGS, F_WARN, LampColor.AMBER, LOW_ALUMINA.get()).lamp(160, 64, FLAGS, F_AUX, LampColor.AMBER, THERMAL.get())
                .lamp(160, 78, FLAGS, F_ALARM, LampColor.RED, POT_ALARM.get())
                .button(8, 98, 44, B_RUN, RUN.get(), null)
                .button(56, 98, 20, B_DOWN, Component.literal("−"), KA_TIP.get()).button(78, 98, 20, B_UP, Component.literal("+"), KA_TIP.get())
                .button(8, 116, 64, B_AUX, RELINE.get(), RELINE_TIP.get());
        for (int i = 0; i < 6; i++) b.slot(120 + 18 * (i % 3), 98 + 18 * (i / 3), i, ReductionPotBlockEntity::loadable, POT_LOAD.get());
        for (int i = 0; i < 4; i++) b.output(178 + 18 * (i % 2), 98 + 18 * (i / 2), 6 + i, INGOT.get());
        return b.readout(8, 140, 216, v -> POT_STATUS.get(POT_STATES[Math.clamp(v.raw(E), 0, POT_STATES.length - 1)].get(),
                v.num(B, 100, 2), v.raw(C), v.raw(TANK3), v.num(D, 10, 1)), null).build();
    }

    /** Общее: термометр с кнопками уставки, лампы «вкл.»/«работа», пуск. */
    static PanelLayout.Builder base(String id, Tr tempLabel, double tempMax) {
        PanelLayout.Builder b = PanelLayout.builder(id, 232, 132, CHANNELS);
        if (tempLabel != null) {
            b.dial(8, 24, false, T_C, 1, tempMax, Double.NaN, 0, CELSIUS.get(), tempLabel.get(), null);
            b.button(56, 92, 20, B_DOWN, Component.literal("−"), null).button(78, 92, 20, B_UP, Component.literal("+"), null);
        }
        return b.lamp(200, 22, FLAGS, F_ON, LampColor.GREEN, ON.get()).lamp(200, 36, FLAGS, F_RUN, LampColor.GREEN, WORKING.get())
                .button(8, 92, 44, B_RUN, RUN.get(), null);
    }
}
