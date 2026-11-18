package dev.denfry.promptcritical.mod.chem;

import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.panel.PanelLayout.LampColor;
import dev.denfry.promptcritical.mod.panel.PanelText;
import dev.denfry.promptcritical.mod.panel.PanelText.Tr;
import dev.denfry.promptcritical.mod.panel.PanelValues;
import dev.denfry.promptcritical.sim.petrochem.LdpeReactor;
import dev.denfry.promptcritical.sim.petrochem.PyrolysisFeed;
import dev.denfry.promptcritical.sim.petrochem.PyrolysisFurnace;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * Щиты A0 пиролиза: печь (COT, температура стенки змеевика с красной чертой 1100 °C, кокс, расход сырья, лампы
 * «крекинг», «выжиг», «авария TMT», «нет газоразделения», «факел»; ключи пуска, выжига, сырья, COT, пара, расхода),
 * газоразделение (стёкла продуктовых баков, чистота этилена и пропилена) и ПЭВД (давление, пик зоны с пределом
 * разложения, кислород, превращение, плотность; ключи пуска, кислорода, давления). Каналы — целые.
 */
public final class ChemPanels {
    // печь
    public static final int COT_C = 0, TMT_C = 1, COKE_X100 = 2, FEED_KGH = 3, CONV_PERMIL = 4, STEAM_X100 = 5, TAU_MS = 6,
            HP_STEAM_KGH = 7, FUEL_KW = 8, FEED = 9, ETHYLENE_PERMIL = 10, PROPYLENE_PERMIL = 11, MODE = 12, FLAGS = 13,
            CHANNELS = 14;
    public static final int F_RUN = 0, F_TRIP = 1, F_DECOKE = 2, F_NO_SEP = 3, F_FLARE = 4;
    public static final int B_RUN = 0, B_DECOKE = 1, B_FEED = 2, B_COT_DOWN = 3, B_COT_UP = 4, B_STEAM_DOWN = 5, B_STEAM_UP = 6,
            B_RATE_DOWN = 7, B_RATE_UP = 8;
    // газоразделение
    public static final int S_TANK0 = 0, S_C2H4_X100 = 5, S_C3H6_X100 = 6, S_CAUSTIC_G = 7, S_FLAGS = 8, S_CHANNELS = 9;
    public static final int SF_FURNACE = 0;
    // ПЭВД
    public static final int L_PRESSURE = 0, L_PEAK_C = 1, L_O2_PPM = 2, L_CONV_PERMIL = 3, L_DENSITY = 4, L_KGH = 5, L_SHAFT_KW = 6,
            L_LIMIT_C = 7, L_TANK = 8, L_TOTAL_KG = 9, L_FLAGS = 10, L_CHANNELS = 11;
    public static final int LF_RUN = 0, LF_DECOMPOSED = 1, LF_HOT = 2;
    public static final int LB_RUN = 0, LB_O2_DOWN = 1, LB_O2_UP = 2, LB_P_DOWN = 3, LB_P_UP = 4;

    private static final Tr CELSIUS = PanelText.CELSIUS, MM = PanelText.tr("unit.mm", "mm", "мм"),
            MPA = PanelText.MPA, PPM = PanelText.tr("unit.ppm", "ppm", "ppm"),
            COT = PanelText.tr("pyro.cot", "COT", "Выход"),
            TMT = PanelText.tr("pyro.tmt", "Tube wall", "Стенка"),
            COKE = PanelText.tr("pyro.coke", "Coke", "Кокс"),
            CRACKING = PanelText.tr("pyro.cracking", "Cracking", "Крекинг"),
            DECOKING = PanelText.tr("pyro.decoking", "Decoking", "Выжиг"),
            TRIP = PanelText.tr("pyro.trip", "TMT trip", "Авария TMT"),
            NO_SEP = PanelText.tr("pyro.no_sep", "No sep.", "Нет ГРУ"),
            FLARE = PanelText.tr("pyro.flare", "Flare", "Факел"),
            RUN = PanelText.tr("pyro.run", "Run", "Пуск/стоп"),
            DECOKE = PanelText.tr("pyro.decoke", "Decoke", "Выжиг"),
            FEED_BTN = PanelText.tr("pyro.feed_btn", "Feed", "Сырьё"),
            COT_TIP = PanelText.tr("pyro.cot_tip", "Coil outlet %s °C · residence %s ms · conversion %s %%",
                    "Выход змеевика %s °C · время пребывания %s мс · превращение %s %%"),
            TMT_TIP = PanelText.tr("pyro.tmt_tip", "Tube metal %s °C · HP alloy limit 1100 °C: the feed is cut above it",
                    "Стенка змеевика %s °C · предел сплава HP 1100 °C: выше сырьё отсекается"),
            COKE_TIP = PanelText.tr("pyro.coke_tip", "Coke %s mm · each mm adds about 16 K to the tube wall",
                    "Кокс %s мм · каждый миллиметр — около 16 К к стенке"),
            FEED_TIP = PanelText.tr("pyro.feed_tip", "Next feed (with the furnace stopped)", "Следующее сырьё (на остановленной печи)"),
            STATUS = PanelText.tr("pyro.status", "%s · %s kg/h · steam %s kg/kg · ethylene %s %%, propylene %s %% · HP steam %s kg/h",
                    "%s · %s кг/ч · пар %s кг/кг · этилен %s %%, пропилен %s %% · пар ВД %s кг/ч");
    private static final Tr[] FEEDS = {
            PanelText.tr("pyro.feed.ethane", "ethane (recycle)", "этан (рецикл)"),
            PanelText.tr("pyro.feed.propane", "propane", "пропан"),
            PanelText.tr("pyro.feed.butane", "butane", "бутан"),
            PanelText.tr("pyro.feed.natural_gasoline", "natural gasoline", "газовый бензин"),
            PanelText.tr("pyro.feed.naphtha", "naphtha", "прямогонный бензин"),
    };
    private static final Tr ETHYLENE = PanelText.tr("pyro.ethylene", "Ethylene", "Этилен"),
            PROPYLENE = PanelText.tr("pyro.propylene", "Propylene", "Пропилен"),
            C4 = PanelText.tr("pyro.c4", "C4", "C4"),
            PYGAS = PanelText.tr("pyro.pygas", "Pygas", "Пиробенз."),
            OIL = PanelText.tr("pyro.oil", "Tar", "Смола"),
            FURNACE_LAMP = PanelText.tr("pyro.furnace", "Furnace", "Печь"),
            SEP_STATUS = PanelText.tr("pyro.sep_status", "Ethylene %s %% · propylene %s %% · caustic used %s kg",
                    "Этилен %s %% · пропилен %s %% · щёлочи израсходовано %s кг"),
            SEP_IDLE = PanelText.tr("pyro.sep_idle", "Waiting for cracked gas: put the unit against a pyrolysis furnace",
                    "Ждёт пирогаз: поставьте установку вплотную к печи пиролиза");
    private static final Tr PRESSURE = PanelText.tr("ldpe.pressure", "Pressure", "Давление"),
            PEAK = PanelText.tr("ldpe.peak", "Peak", "Пик зоны"),
            OXYGEN = PanelText.tr("ldpe.oxygen", "Oxygen", "Кислород"),
            RUNNING = PanelText.tr("ldpe.running", "Running", "Работа"),
            DECOMPOSED = PanelText.tr("ldpe.decomposed", "Decomp.", "Разлож."),
            HOT = PanelText.tr("ldpe.hot", "Hot", "Перегрев"),
            O2_DOWN = PanelText.tr("ldpe.o2_down", "O₂ −5", "O₂ −5"),
            O2_UP = PanelText.tr("ldpe.o2_up", "O₂ +5", "O₂ +5"),
            P_DOWN = PanelText.tr("ldpe.p_down", "−10 MPa", "−10 МПа"),
            P_UP = PanelText.tr("ldpe.p_up", "+10 MPa", "+10 МПа"),
            PEAK_TIP = PanelText.tr("ldpe.peak_tip", "Zone peak %s °C · ethylene decomposes above %s °C at this pressure",
                    "Пик зоны %s °C · выше %s °C при этом давлении этилен разлагается"),
            LDPE_STATUS = PanelText.tr("ldpe.status", "Conversion %s %% · %s kg/h · density %s g/cm³ · shaft %s kW · made %s kg",
                    "Превращение %s %% · %s кг/ч · плотность %s г/см³ · вал %s кВт · выработано %s кг");

    private static final Tr COT_DOWN = PanelText.tr("pyro.cot_down", "Coil outlet −5 °C", "Выход змеевика −5 °C"),
            COT_UP = PanelText.tr("pyro.cot_up", "Coil outlet +5 °C", "Выход змеевика +5 °C"),
            STEAM_DOWN = PanelText.tr("pyro.steam_down", "Dilution steam −0.05 kg/kg", "Пар разбавления −0,05 кг/кг"),
            STEAM_UP = PanelText.tr("pyro.steam_up", "Dilution steam +0.05 kg/kg", "Пар разбавления +0,05 кг/кг"),
            RATE_DOWN = PanelText.tr("pyro.rate_down", "Feed rate −10 %", "Расход сырья −10 %"),
            RATE_UP = PanelText.tr("pyro.rate_up", "Feed rate +10 %", "Расход сырья +10 %"),
            T_LABEL = PanelText.tr("pyro.t_label", "COT", "Темп."),
            S_LABEL = PanelText.tr("pyro.s_label", "Steam", "Пар"),
            Q_LABEL = PanelText.tr("pyro.q_label", "Feed", "Расход");

    public static final PanelLayout FURNACE = PanelLayout.builder("pyrolysis_furnace", 248, 140, CHANNELS)
            .dial(8, 24, false, COT_C, 1, 1000, Double.NaN, 0, CELSIUS.get(), COT.get(),
                    v -> COT_TIP.get(v.raw(COT_C), v.raw(TAU_MS), v.num(CONV_PERMIL, 10, 1)))
            .dial(64, 24, false, TMT_C, 1, 1200, PyrolysisFurnace.TMT_LIMIT_K - 273.15, 0, CELSIUS.get(), TMT.get(),
                    v -> TMT_TIP.get(v.raw(TMT_C)))
            .bar(120, 30, 60, COKE_X100, 100, 12, 0xFF3A3A3A, COKE.get(), v -> COKE_TIP.get(v.num(COKE_X100, 100, 2)))
            .lamp(188, 22, FLAGS, F_RUN, LampColor.GREEN, CRACKING.get())
            .lamp(188, 36, FLAGS, F_DECOKE, LampColor.AMBER, DECOKING.get())
            .lamp(188, 50, FLAGS, F_TRIP, LampColor.RED, TRIP.get())
            .lamp(188, 64, FLAGS, F_NO_SEP, LampColor.RED, NO_SEP.get())
            .lamp(120, 64, FLAGS, F_FLARE, LampColor.AMBER, FLARE.get())
            .button(8, 88, 44, B_RUN, RUN.get(), null)
            .button(56, 88, 44, B_DECOKE, DECOKE.get(), null)
            .button(104, 88, 44, B_FEED, FEED_BTN.get(), FEED_TIP.get())
            .caption(152, 78, T_LABEL.get())
            .button(152, 88, 20, B_COT_DOWN, Component.literal("−"), COT_DOWN.get())
            .button(174, 88, 20, B_COT_UP, Component.literal("+"), COT_UP.get())
            .caption(152, 100, S_LABEL.get())
            .button(152, 108, 20, B_STEAM_DOWN, Component.literal("−"), STEAM_DOWN.get())
            .button(174, 108, 20, B_STEAM_UP, Component.literal("+"), STEAM_UP.get())
            .caption(200, 78, Q_LABEL.get())
            .button(200, 88, 20, B_RATE_DOWN, Component.literal("−"), RATE_DOWN.get())
            .button(222, 88, 20, B_RATE_UP, Component.literal("+"), RATE_UP.get())
            .readout(8, 126, 232, ChemPanels::furnace, null)
            .noInventory().build();

    public static final PanelLayout SEPARATION = PanelLayout.builder("cracked_gas_unit", 176, 108, S_CHANNELS)
            .glass(8, 24, 60, S_TANK0, 0xFFD8E4F0, Double.NaN, Double.NaN, ETHYLENE.get(), null)
            .glass(40, 24, 60, S_TANK0 + 1, 0xFFE8D8A0, Double.NaN, Double.NaN, PROPYLENE.get(), null)
            .glass(72, 24, 60, S_TANK0 + 2, 0xFFC8B070, Double.NaN, Double.NaN, C4.get(), null)
            .glass(104, 24, 60, S_TANK0 + 3, 0xFFD8B040, Double.NaN, Double.NaN, PYGAS.get(), null)
            .glass(136, 24, 60, S_TANK0 + 4, 0xFF2A2018, Double.NaN, Double.NaN, OIL.get(), null)
            .lamp(8, 88, S_FLAGS, SF_FURNACE, LampColor.GREEN, FURNACE_LAMP.get())
            .readout(8, 100, 160, ChemPanels::separation, null)
            .noInventory().build();

    public static final PanelLayout LDPE = PanelLayout.builder("ldpe_reactor", 232, 124, L_CHANNELS)
            .dial(8, 24, false, L_PRESSURE, 1, 320, 300, 0, MPA.get(), PRESSURE.get(), null)
            .dial(64, 24, false, L_PEAK_C, 1, 400, LdpeReactor.decompositionK(LdpeReactor.REF_PRESSURE_MPA) - 273.15, 0,
                    CELSIUS.get(), PEAK.get(), v -> PEAK_TIP.get(v.raw(L_PEAK_C), v.raw(L_LIMIT_C)))
            .dial(120, 24, false, L_O2_PPM, 1, 150, Double.NaN, 0, PPM.get(), OXYGEN.get(), null)
            .lamp(178, 22, L_FLAGS, LF_RUN, LampColor.GREEN, RUNNING.get())
            .lamp(178, 36, L_FLAGS, LF_HOT, LampColor.AMBER, HOT.get())
            .lamp(178, 50, L_FLAGS, LF_DECOMPOSED, LampColor.RED, DECOMPOSED.get())
            .button(8, 84, 44, LB_RUN, RUN.get(), null)
            .button(56, 84, 40, LB_O2_DOWN, O2_DOWN.get(), null)
            .button(100, 84, 40, LB_O2_UP, O2_UP.get(), null)
            .button(144, 84, 40, LB_P_DOWN, P_DOWN.get(), null)
            .button(188, 84, 40, LB_P_UP, P_UP.get(), null)
            .readout(8, 108, 216, ChemPanels::ldpe, null)
            .noInventory().build();

    private ChemPanels() {
    }

    public static List<PanelLayout> all() {
        return List.of(FURNACE, SEPARATION, LDPE);
    }

    private static Component furnace(PanelValues v) {
        Tr feed = FEEDS[Math.floorMod(v.raw(FEED), PyrolysisFeed.ALL.length)];
        Component c = STATUS.get(feed.get(), v.raw(FEED_KGH), v.num(STEAM_X100, 100, 2), v.num(ETHYLENE_PERMIL, 10, 1),
                v.num(PROPYLENE_PERMIL, 10, 1), v.raw(HP_STEAM_KGH));
        return v.bit(FLAGS, F_TRIP) || v.bit(FLAGS, F_NO_SEP) ? c.copy().withStyle(ChatFormatting.GOLD) : c;
    }

    private static Component separation(PanelValues v) {
        if (v.raw(S_C2H4_X100) == 0) return SEP_IDLE.get().withStyle(ChatFormatting.GOLD);
        return SEP_STATUS.get(v.num(S_C2H4_X100, 100, 2), v.num(S_C3H6_X100, 100, 2), v.num(S_CAUSTIC_G, 1000, 2));
    }

    private static Component ldpe(PanelValues v) {
        return LDPE_STATUS.get(v.num(L_CONV_PERMIL, 10, 1), v.raw(L_KGH), v.num(L_DENSITY, 1000, 3), v.raw(L_SHAFT_KW), v.raw(L_TOTAL_KG));
    }
}
