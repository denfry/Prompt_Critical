package dev.denfry.promptcritical.mod.nitrogen;

import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.panel.PanelLayout.LampColor;
import dev.denfry.promptcritical.mod.panel.PanelText;
import dev.denfry.promptcritical.mod.panel.PanelText.Tr;
import dev.denfry.promptcritical.mod.panel.PanelValues;
import dev.denfry.promptcritical.sim.nitrogen.AmmoniaSynthesis;
import dev.denfry.promptcritical.sim.nitrogen.NitricAcidPlant;
import dev.denfry.promptcritical.sim.nitrogen.Purification;
import dev.denfry.promptcritical.sim.nitrogen.SynthesisLoop;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * Щиты A0 азотного производства: трубчатая печь с вторичным риформингом и конверсией CO, очистка (МЭА и метанатор),
 * цикл синтеза аммиака, агрегат азотной кислоты и концентрирование. Каналы — целые.
 */
public final class NitrogenPanels {
    // печь риформинга
    public static final int R_OUT_C = 0, R_SEC_C = 1, R_CH4_P = 2, R_CH4_S = 3, R_CO_HTS = 4, R_CO_LTS = 5, R_SC = 6, R_RATIO = 7,
            R_AIR = 8, R_FEED = 9, R_FUEL_KW = 10, R_NI = 11, R_FLAGS = 12, R_CHANNELS = 13;
    public static final int RF_RUN = 0, RF_NO_ABS = 1, RF_NO_NI = 2, RF_NO_HTS = 3, RF_NO_LTS = 4, RF_CARBON = 5, RF_NO_FEED = 6;
    public static final int RB_RUN = 0, RB_SC_DOWN = 1, RB_SC_UP = 2, RB_T_DOWN = 3, RB_T_UP = 4, RB_RATIO_DOWN = 5, RB_RATIO_UP = 6,
            RB_RATE_DOWN = 7, RB_RATE_UP = 8;
    // очистка
    public static final int A_CO2_PPM = 0, A_OXY_PPM10 = 1, A_METH_C = 2, A_RATIO = 3, A_REGEN_KW = 4, A_HEAT_KW = 5, A_CIRC = 6,
            A_TANK = 7, A_FLOW = 8, A_FLAGS = 9, A_CHANNELS = 10;
    public static final int AF_ON_SPEC = 0, AF_VENT = 1, AF_TRIP = 2, AF_IDLE = 3;
    public static final int AB_CIRC_DOWN = 0, AB_CIRC_UP = 1, AB_RESET = 2;
    // цикл синтеза
    public static final int S_P_BAR = 0, S_BED_C = 1, S_IN_NH3 = 2, S_OUT_NH3 = 3, S_INERTS = 4, S_NH3_KGH = 5, S_MAKEUP = 6,
            S_PURGE = 7, S_CIRC = 8, S_SEP_C = 9, S_SHAFT_KW = 10, S_CHILL_KW = 11, S_ACT = 12, S_SYN_TANK = 13, S_NH3_TANK = 14,
            S_FLAGS = 15, S_CHANNELS = 16;
    public static final int SF_RUN = 0, SF_HOT = 1, SF_PMAX = 2, SF_NO_GAS = 3;
    public static final int SB_RUN = 0, SB_PURGE_DOWN = 1, SB_PURGE_UP = 2, SB_SEP_DOWN = 3, SB_SEP_UP = 4, SB_BED_DOWN = 5, SB_BED_UP = 6,
            SB_CIRC_DOWN = 7, SB_CIRC_UP = 8;
    // азотная кислота
    public static final int N_GAUZE_C = 0, N_NOX_PPM = 1, N_NH3 = 2, N_LEL = 3, N_SEL = 4, N_STRENGTH = 5, N_HNO3_KGH = 6,
            N_PT_G = 7, N_SHAFT_KW = 8, N_PRESSURE = 9, N_ACID_TANK = 10, N_ABSORB = 11, N_FLAGS = 12, N_CHANNELS = 13;
    public static final int NF_RUN = 0, NF_FLASH = 1, NF_NO_GAUZE = 2, NF_DENOX = 3, NF_FOX = 4;
    public static final int NB_RUN = 0, NB_PRESSURE = 1, NB_NH3_DOWN = 2, NB_NH3_UP = 3, NB_STR_DOWN = 4, NB_STR_UP = 5;
    // концентрирование
    public static final int C_TOP = 0, C_SALT = 1, C_REFLUX = 2, C_FEED_KGH = 3, C_PRODUCT_KGH = 4, C_HEAT_KW = 5, C_WEAK_TANK = 6,
            C_PRODUCT_TANK = 7, C_FLAGS = 8, C_CHANNELS = 9;
    public static final int CF_RUN = 0, CF_OFF_SPEC = 1, CF_NO_FUEL = 2;
    public static final int CB_RUN = 0, CB_SALT_DOWN = 1, CB_SALT_UP = 2, CB_REFLUX_DOWN = 3, CB_REFLUX_UP = 4;

    private static final Tr CELSIUS = PanelText.CELSIUS, BAR = PanelText.tr("unit.bar", "bar", "бар"),
            PPM = PanelText.tr("unit.ppm", "ppm", "ppm"), PERCENT = PanelText.PERCENT;
    private static final Tr RUN = PanelText.tr("nitrogen.run", "Run", "Пуск/стоп"),
            WORKING = PanelText.tr("nitrogen.working", "Working", "Работа");

    // --- печь ---
    private static final Tr OUTLET = PanelText.tr("reformer.outlet", "Tubes out", "Выход труб"),
            SECONDARY = PanelText.tr("reformer.secondary", "Secondary", "Вторичный"),
            NICKEL = PanelText.tr("reformer.nickel", "Ni", "Ni"),
            CARBON = PanelText.tr("reformer.carbon", "Carbon", "Углерод"),
            NO_ABS = PanelText.tr("reformer.no_abs", "No absorber", "Нет очистки"),
            NO_NI = PanelText.tr("reformer.no_ni", "No Ni", "Нет Ni"),
            NO_HTS = PanelText.tr("reformer.no_hts", "No HTS", "Нет СТК"),
            NO_LTS = PanelText.tr("reformer.no_lts", "No LTS", "Нет НТК"),
            NO_FEED = PanelText.tr("reformer.no_feed", "No gas", "Нет газа"),
            OUTLET_TIP = PanelText.tr("reformer.outlet_tip", "Tube outlet %s °C · methane %s %% dry · steam to carbon %s",
                    "Выход труб %s °C · метан %s %% (сухой) · пар/газ %s"),
            SECONDARY_TIP = PanelText.tr("reformer.secondary_tip", "Secondary reformer %s °C · methane slip %s %% · air %s nm³/h",
                    "Вторичный риформинг %s °C · проскок метана %s %% · воздух %s нм³/ч"),
            NI_TIP = PanelText.tr("reformer.ni_tip", "Nickel catalyst activity %s %%: below steam to carbon 2.5 carbon grows on it",
                    "Активность никеля %s %%: при пар/газ ниже 2,5 на нём растёт углерод"),
            SC_LABEL = PanelText.tr("reformer.sc_label", "S/C", "Пар"),
            T_LABEL = PanelText.tr("reformer.t_label", "Tubes", "Трубы"),
            RATIO_LABEL = PanelText.tr("reformer.ratio_label", "H/N", "H/N"),
            RATE_LABEL = PanelText.tr("reformer.rate_label", "Gas", "Газ"),
            SC_DOWN = PanelText.tr("reformer.sc_down", "Steam to carbon −0.1", "Пар/газ −0,1"),
            SC_UP = PanelText.tr("reformer.sc_up", "Steam to carbon +0.1", "Пар/газ +0,1"),
            T_DOWN = PanelText.tr("reformer.t_down", "Tube outlet −5 °C", "Выход труб −5 °C"),
            T_UP = PanelText.tr("reformer.t_up", "Tube outlet +5 °C", "Выход труб +5 °C"),
            RATIO_DOWN = PanelText.tr("reformer.ratio_down", "(H₂ + CO)/N₂ −0.02: more process air", "(H₂ + CO)/N₂ −0,02: больше воздуха"),
            RATIO_UP = PanelText.tr("reformer.ratio_up", "(H₂ + CO)/N₂ +0.02: less process air", "(H₂ + CO)/N₂ +0,02: меньше воздуха"),
            RATE_DOWN = PanelText.tr("reformer.rate_down", "Gas rate −10 %", "Расход газа −10 %"),
            RATE_UP = PanelText.tr("reformer.rate_up", "Gas rate +10 %", "Расход газа +10 %"),
            REFORMER_STATUS = PanelText.tr("reformer.status", "Gas %s nm³/h · fuel %s kW · CO after HTS %s %%, after LTS %s %% · (H₂+CO)/N₂ %s",
                    "Газ %s нм³/ч · топливо %s кВт · CO после СТК %s %%, после НТК %s %% · (H₂+CO)/N₂ %s");

    public static final PanelLayout REFORMER = PanelLayout.builder("steam_reformer", 248, 150, R_CHANNELS)
            .dial(8, 24, false, R_OUT_C, 1, 1000, 900, 0, CELSIUS.get(), OUTLET.get(),
                    v -> OUTLET_TIP.get(v.raw(R_OUT_C), v.num(R_CH4_P, 100, 1), v.num(R_SC, 100, 2)))
            .dial(64, 24, false, R_SEC_C, 1, 1200, Double.NaN, 0, CELSIUS.get(), SECONDARY.get(),
                    v -> SECONDARY_TIP.get(v.raw(R_SEC_C), v.num(R_CH4_S, 100, 2), v.raw(R_AIR)))
            .bar(120, 30, 60, R_NI, 10, 100, 0xFF4F7A4A, NICKEL.get(), v -> NI_TIP.get(v.num(R_NI, 10, 0)))
            .lamp(188, 22, R_FLAGS, RF_RUN, LampColor.GREEN, WORKING.get())
            .lamp(188, 36, R_FLAGS, RF_CARBON, LampColor.AMBER, CARBON.get())
            .lamp(188, 50, R_FLAGS, RF_NO_ABS, LampColor.RED, NO_ABS.get())
            .lamp(188, 64, R_FLAGS, RF_NO_NI, LampColor.RED, NO_NI.get())
            .lamp(120, 50, R_FLAGS, RF_NO_HTS, LampColor.AMBER, NO_HTS.get())
            .lamp(120, 64, R_FLAGS, RF_NO_LTS, LampColor.AMBER, NO_LTS.get())
            .lamp(188, 78, R_FLAGS, RF_NO_FEED, LampColor.AMBER, NO_FEED.get())
            .button(8, 96, 44, RB_RUN, RUN.get(), null)
            .caption(56, 86, SC_LABEL.get())
            .button(56, 96, 20, RB_SC_DOWN, Component.literal("−"), SC_DOWN.get())
            .button(78, 96, 20, RB_SC_UP, Component.literal("+"), SC_UP.get())
            .caption(104, 86, T_LABEL.get())
            .button(104, 96, 20, RB_T_DOWN, Component.literal("−"), T_DOWN.get())
            .button(126, 96, 20, RB_T_UP, Component.literal("+"), T_UP.get())
            .caption(152, 86, RATIO_LABEL.get())
            .button(152, 96, 20, RB_RATIO_DOWN, Component.literal("−"), RATIO_DOWN.get())
            .button(174, 96, 20, RB_RATIO_UP, Component.literal("+"), RATIO_UP.get())
            .caption(200, 86, RATE_LABEL.get())
            .button(200, 96, 20, RB_RATE_DOWN, Component.literal("−"), RATE_DOWN.get())
            .button(222, 96, 20, RB_RATE_UP, Component.literal("+"), RATE_UP.get())
            .readout(8, 126, 232, NitrogenPanels::reformer, null)
            .noInventory().build();

    // --- очистка ---
    private static final Tr CO2 = PanelText.tr("absorber.co2", "CO₂ out", "CO₂ после"),
            METHANATOR = PanelText.tr("absorber.methanator", "Methanator", "Метанатор"),
            SYNGAS = PanelText.tr("absorber.syngas", "Syngas", "АВС"),
            ON_SPEC = PanelText.tr("absorber.on_spec", "On spec", "В норме"),
            VENT = PanelText.tr("absorber.vent", "Vent", "Сброс"),
            TRIP = PanelText.tr("absorber.trip", "Trip", "Блокировка"),
            IDLE = PanelText.tr("absorber.idle", "No gas", "Нет газа"),
            CIRC_LABEL = PanelText.tr("absorber.circ_label", "MEA", "МЭА"),
            CIRC_DOWN = PanelText.tr("absorber.circ_down", "Solvent circulation −10 %", "Циркуляция раствора −10 %"),
            CIRC_UP = PanelText.tr("absorber.circ_up", "Solvent circulation +10 %", "Циркуляция раствора +10 %"),
            RESET = PanelText.tr("absorber.reset", "Reset", "Сброс"),
            RESET_TIP = PanelText.tr("absorber.reset_tip", "Reset the methanator trip", "Сбросить блокировку метанатора"),
            CO2_TIP = PanelText.tr("absorber.co2_tip", "CO₂ after the MEA absorber %s ppm · CO + CO₂ after the methanator %s ppm",
                    "CO₂ после абсорбера МЭА %s ppm · CO + CO₂ после метанатора %s ppm"),
            METH_TIP = PanelText.tr("absorber.meth_tip", "Methanator outlet %s °C · trip at %s °C", "Выход метанатора %s °C · блокировка при %s °C"),
            ABS_STATUS = PanelText.tr("absorber.status", "Syngas %s nm³/h · H₂:N₂ %s · MEA %s %% · regeneration %s of %s kW",
                    "АВС %s нм³/ч · H₂:N₂ %s · МЭА %s %% · регенерация %s из %s кВт"),
            ABS_IDLE = PanelText.tr("absorber.status_idle", "Waiting for shifted gas: put the column against a steam reformer",
                    "Ждёт конвертированный газ: поставьте колонну вплотную к печи риформинга");

    public static final PanelLayout ABSORBER = PanelLayout.builder("co2_absorber", 232, 124, A_CHANNELS)
            .dial(8, 24, false, A_CO2_PPM, 1, 5000, 1000, 0, PPM.get(), CO2.get(),
                    v -> CO2_TIP.get(v.raw(A_CO2_PPM), v.num(A_OXY_PPM10, 10, 1)))
            .dial(64, 24, false, A_METH_C, 1, 500, Purification.TRIP_K - 273.15, 0, CELSIUS.get(), METHANATOR.get(),
                    v -> METH_TIP.get(v.raw(A_METH_C), Math.round(Purification.TRIP_K - 273.15)))
            .glass(124, 24, 60, A_TANK, 0xFFDCDCD0, Double.NaN, Double.NaN, SYNGAS.get(), null)
            .lamp(160, 22, A_FLAGS, AF_ON_SPEC, LampColor.GREEN, ON_SPEC.get())
            .lamp(160, 36, A_FLAGS, AF_VENT, LampColor.AMBER, VENT.get())
            .lamp(160, 50, A_FLAGS, AF_TRIP, LampColor.RED, TRIP.get())
            .lamp(160, 64, A_FLAGS, AF_IDLE, LampColor.AMBER, IDLE.get())
            .caption(8, 78, CIRC_LABEL.get())
            .button(8, 88, 20, AB_CIRC_DOWN, Component.literal("−"), CIRC_DOWN.get())
            .button(30, 88, 20, AB_CIRC_UP, Component.literal("+"), CIRC_UP.get())
            .button(56, 88, 52, AB_RESET, RESET.get(), RESET_TIP.get())
            .readout(8, 108, 216, NitrogenPanels::absorber, null)
            .noInventory().build();

    // --- цикл синтеза ---
    private static final Tr LOOP_P = PanelText.tr("loop.pressure", "Loop", "Цикл"),
            BED = PanelText.tr("loop.bed", "Bed", "Слой"),
            NH3_OUT = PanelText.tr("loop.nh3_out", "NH₃ out", "NH₃ выход"),
            INERTS = PanelText.tr("loop.inerts", "Inerts", "Инерты"),
            AMMONIA = PanelText.tr("loop.ammonia", "NH₃", "NH₃"),
            HOT = PanelText.tr("loop.hot", "Hot bed", "Перегрев"),
            PMAX = PanelText.tr("loop.pmax", "P max", "P макс"),
            NO_GAS = PanelText.tr("loop.no_gas", "No gas", "Нет АВС"),
            PURGE_LABEL = PanelText.tr("loop.purge_label", "Purge", "Продувка"),
            SEP_LABEL = PanelText.tr("loop.sep_label", "Sep.", "Сепар."),
            BED_LABEL = PanelText.tr("loop.bed_label", "Bed in", "Вход"),
            CIRC = PanelText.tr("loop.circ_label", "Circ.", "Цирк."),
            PURGE_DOWN = PanelText.tr("loop.purge_down", "Purge −40 nm³/h", "Продувка −40 нм³/ч"),
            PURGE_UP = PanelText.tr("loop.purge_up", "Purge +40 nm³/h", "Продувка +40 нм³/ч"),
            SEP_DOWN = PanelText.tr("loop.sep_down", "Separator −5 °C (ammonia chiller)", "Сепаратор −5 °C (аммиачный холодильник)"),
            SEP_UP = PanelText.tr("loop.sep_up", "Separator +5 °C", "Сепаратор +5 °C"),
            BED_DOWN = PanelText.tr("loop.bed_down", "Bed inlet −5 °C", "Вход в слои −5 °C"),
            BED_UP = PanelText.tr("loop.bed_up", "Bed inlet +5 °C", "Вход в слои +5 °C"),
            CIRC_DOWN_L = PanelText.tr("loop.circ_down", "Circulation −10 %", "Циркуляция −10 %"),
            CIRC_UP_L = PanelText.tr("loop.circ_up", "Circulation +10 %", "Циркуляция +10 %"),
            P_TIP = PanelText.tr("loop.p_tip", "Loop %s bar · makeup %s nm³/h · purge %s nm³/h · circulation %s %%",
                    "Цикл %s бар · свежий газ %s нм³/ч · продувка %s нм³/ч · циркуляция %s %%"),
            BED_TIP = PanelText.tr("loop.bed_tip", "Hottest bed outlet %s °C · iron catalyst recrystallises above %s °C · activity %s %%",
                    "Самый горячий слой %s °C · выше %s °C железо рекристаллизуется · активность %s %%"),
            NH3_TIP = PanelText.tr("loop.nh3_tip", "NH₃ in %s %% → out %s %%", "NH₃ на входе %s %% → на выходе %s %%"),
            LOOP_STATUS = PanelText.tr("loop.status", "Ammonia %s kg/h · separator %s °C · shaft %s kW (chiller %s kW)",
                    "Аммиак %s кг/ч · сепаратор %s °C · вал %s кВт (холод %s кВт)");

    public static final PanelLayout LOOP = PanelLayout.builder("ammonia_converter", 248, 150, S_CHANNELS)
            .dial(8, 24, false, S_P_BAR, 1, 350, SynthesisLoop.MAX_BAR, 0, BAR.get(), LOOP_P.get(),
                    v -> P_TIP.get(v.raw(S_P_BAR), v.raw(S_MAKEUP), v.raw(S_PURGE), v.raw(S_CIRC)))
            .dial(64, 24, false, S_BED_C, 1, 600, AmmoniaSynthesis.CATALYST_LIMIT_K - 273.15, 0, CELSIUS.get(), BED.get(),
                    v -> BED_TIP.get(v.raw(S_BED_C), Math.round(AmmoniaSynthesis.CATALYST_LIMIT_K - 273.15), v.num(S_ACT, 10, 0)))
            .bar(120, 26, 56, S_OUT_NH3, 100, 25, 0xFFC8DCE6, NH3_OUT.get(),
                    v -> NH3_TIP.get(v.num(S_IN_NH3, 100, 1), v.num(S_OUT_NH3, 100, 1)))
            .bar(120, 44, 56, S_INERTS, 100, 40, 0xFF8A8A7A, INERTS.get(), null)
            .glass(184, 24, 50, S_SYN_TANK, 0xFFDCDCD0, Double.NaN, Double.NaN, SYNGAS.get(), null)
            .glass(212, 24, 50, S_NH3_TANK, 0xFFE2E8F0, Double.NaN, Double.NaN, AMMONIA.get(), null)
            .lamp(120, 62, S_FLAGS, SF_RUN, LampColor.GREEN, WORKING.get())
            .lamp(120, 76, S_FLAGS, SF_HOT, LampColor.RED, HOT.get())
            .lamp(184, 80, S_FLAGS, SF_PMAX, LampColor.AMBER, PMAX.get())
            .lamp(184, 66, S_FLAGS, SF_NO_GAS, LampColor.AMBER, NO_GAS.get())
            .button(8, 96, 44, SB_RUN, RUN.get(), null)
            .caption(56, 86, PURGE_LABEL.get())
            .button(56, 96, 20, SB_PURGE_DOWN, Component.literal("−"), PURGE_DOWN.get())
            .button(78, 96, 20, SB_PURGE_UP, Component.literal("+"), PURGE_UP.get())
            .caption(104, 86, SEP_LABEL.get())
            .button(104, 96, 20, SB_SEP_DOWN, Component.literal("−"), SEP_DOWN.get())
            .button(126, 96, 20, SB_SEP_UP, Component.literal("+"), SEP_UP.get())
            .caption(152, 86, BED_LABEL.get())
            .button(152, 96, 20, SB_BED_DOWN, Component.literal("−"), BED_DOWN.get())
            .button(174, 96, 20, SB_BED_UP, Component.literal("+"), BED_UP.get())
            .caption(200, 86, CIRC.get())
            .button(200, 96, 20, SB_CIRC_DOWN, Component.literal("−"), CIRC_DOWN_L.get())
            .button(222, 96, 20, SB_CIRC_UP, Component.literal("+"), CIRC_UP_L.get())
            .readout(8, 126, 232, NitrogenPanels::loop, null)
            .noInventory().build();

    // --- азотная кислота ---
    private static final Tr GAUZE = PanelText.tr("nitric.gauze", "Gauze", "Сетки"),
            NOX = PanelText.tr("nitric.nox", "Tail NOx", "NOx хвост"),
            ACID = PanelText.tr("nitric.acid", "HNO₃", "HNO₃"),
            FLASH = PanelText.tr("nitric.flash", "Flash", "Вспышка"),
            NO_GAUZE = PanelText.tr("nitric.no_gauze", "No gauze", "Нет сеток"),
            DENOX = PanelText.tr("nitric.denox", "DeNOx", "Очистка"),
            FOX = PanelText.tr("nitric.fox", "Fox tail", "Лисий хвост"),
            PRESSURE_BTN = PanelText.tr("nitric.pressure_btn", "Pressure", "Давление"),
            PRESSURE_TIP = PanelText.tr("nitric.pressure_tip", "Next scheme: atmospheric, 0.42 or 0.73 MPa (with the unit stopped)",
                    "Следующая схема: атмосферная, 0,42 или 0,73 МПа (на остановленном агрегате)"),
            NH3_LABEL = PanelText.tr("nitric.nh3_label", "NH₃", "NH₃"),
            STR_LABEL = PanelText.tr("nitric.str_label", "Acid", "Кисл."),
            NH3_DOWN = PanelText.tr("nitric.nh3_down", "Ammonia in air −0.25 %", "Аммиак в воздухе −0,25 %"),
            NH3_UP = PanelText.tr("nitric.nh3_up", "Ammonia in air +0.25 % (mind the lower explosive limit)",
                    "Аммиак в воздухе +0,25 % (помните о нижнем пределе взрываемости)"),
            STR_DOWN = PanelText.tr("nitric.str_down", "Acid strength −1 %: more process water", "Крепость кислоты −1 %: больше воды"),
            STR_UP = PanelText.tr("nitric.str_up", "Acid strength +1 %: less process water", "Крепость кислоты +1 %: меньше воды"),
            GAUZE_TIP = PanelText.tr("nitric.gauze_tip", "Gauze %s °C · NO yield %s %% · platinum left %s g",
                    "Сетки %s °C · выход NO %s %% · платины осталось %s г"),
            NOX_TIP = PanelText.tr("nitric.nox_tip", "Tail gas NOx %s ppm · absorption %s %%", "NOx в хвостовых газах %s ppm · поглощение %s %%"),
            NITRIC_STATUS = PanelText.tr("nitric.status", "%s · NH₃ %s %% of air (limit %s %%) · acid %s %% · %s kg/h HNO₃ · shaft %s kW",
                    "%s · NH₃ %s %% в воздухе (предел %s %%) · кислота %s %% · %s кг/ч HNO₃ · вал %s кВт");
    private static final Tr[] SCHEMES = {
            PanelText.tr("nitric.scheme.0", "atmospheric", "атмосферная"),
            PanelText.tr("nitric.scheme.1", "0.42 MPa", "0,42 МПа"),
            PanelText.tr("nitric.scheme.2", "0.73 MPa (UKL-7)", "0,73 МПа (УКЛ-7)"),
    };

    public static final PanelLayout NITRIC = PanelLayout.builder("nitric_acid_unit", 248, 140, N_CHANNELS)
            .dial(8, 24, false, N_GAUZE_C, 1, 1000, 920, 0, CELSIUS.get(), GAUZE.get(),
                    v -> GAUZE_TIP.get(v.raw(N_GAUZE_C), v.num(N_SEL, 10, 1), v.raw(N_PT_G)))
            .dial(64, 24, false, N_NOX_PPM, 1, 5000, 2000, 0, PPM.get(), NOX.get(),
                    v -> NOX_TIP.get(v.raw(N_NOX_PPM), v.num(N_ABSORB, 10, 1)))
            .glass(124, 24, 60, N_ACID_TANK, 0xFFE8DC8C, Double.NaN, Double.NaN, ACID.get(), null)
            .lamp(160, 22, N_FLAGS, NF_RUN, LampColor.GREEN, WORKING.get())
            .lamp(160, 36, N_FLAGS, NF_FLASH, LampColor.RED, FLASH.get())
            .lamp(160, 50, N_FLAGS, NF_NO_GAUZE, LampColor.RED, NO_GAUZE.get())
            .lamp(160, 64, N_FLAGS, NF_DENOX, LampColor.GREEN, DENOX.get())
            .lamp(204, 22, N_FLAGS, NF_FOX, LampColor.AMBER, FOX.get())
            .button(8, 88, 44, NB_RUN, RUN.get(), null)
            .button(56, 88, 52, NB_PRESSURE, PRESSURE_BTN.get(), PRESSURE_TIP.get())
            .caption(116, 78, NH3_LABEL.get())
            .button(116, 88, 20, NB_NH3_DOWN, Component.literal("−"), NH3_DOWN.get())
            .button(138, 88, 20, NB_NH3_UP, Component.literal("+"), NH3_UP.get())
            .caption(166, 78, STR_LABEL.get())
            .button(166, 88, 20, NB_STR_DOWN, Component.literal("−"), STR_DOWN.get())
            .button(188, 88, 20, NB_STR_UP, Component.literal("+"), STR_UP.get())
            .readout(8, 116, 232, NitrogenPanels::nitric, null)
            .noInventory().build();

    // --- концентрирование ---
    private static final Tr TOP = PanelText.tr("conc.top", "Top", "Верх"),
            WEAK = PanelText.tr("conc.weak", "56 %", "56 %"),
            STRONG = PanelText.tr("conc.strong", "98 %", "98 %"),
            OFF_SPEC = PanelText.tr("conc.off_spec", "Off spec", "Не в норме"),
            NO_FUEL = PanelText.tr("conc.no_fuel", "No fuel", "Нет топлива"),
            SALT_LABEL = PanelText.tr("conc.salt_label", "Melt", "Плав"),
            REFLUX_LABEL = PanelText.tr("conc.reflux_label", "Reflux", "Флегма"),
            SALT_DOWN = PanelText.tr("conc.salt_down", "Magnesium nitrate melt −0.5 kg/kg", "Плав нитрата магния −0,5 кг/кг"),
            SALT_UP = PanelText.tr("conc.salt_up", "Magnesium nitrate melt +0.5 kg/kg", "Плав нитрата магния +0,5 кг/кг"),
            REFLUX_DOWN = PanelText.tr("conc.reflux_down", "Reflux ratio −0.1", "Флегмовое число −0,1"),
            REFLUX_UP = PanelText.tr("conc.reflux_up", "Reflux ratio +0.1", "Флегмовое число +0,1"),
            TOP_TIP = PanelText.tr("conc.top_tip", "Top product %s %% HNO₃ · saleable from 97 %%", "Верхний продукт %s %% HNO₃ · товарная — от 97 %%"),
            CONC_STATUS = PanelText.tr("conc.status", "Melt %s kg/kg · reflux %s · feed %s kg/h · product %s kg/h · heat %s kW",
                    "Плав %s кг/кг · флегма %s · питание %s кг/ч · продукт %s кг/ч · тепло %s кВт");

    public static final PanelLayout CONCENTRATOR = PanelLayout.builder("acid_concentrator", 232, 124, C_CHANNELS)
            .dial(8, 24, false, C_TOP, 100, 100, Double.NaN, 1, PERCENT.get(), TOP.get(), v -> TOP_TIP.get(v.num(C_TOP, 100, 2)))
            .glass(68, 24, 60, C_WEAK_TANK, 0xFFE8DC8C, Double.NaN, Double.NaN, WEAK.get(), null)
            .glass(100, 24, 60, C_PRODUCT_TANK, 0xFFE8B060, Double.NaN, Double.NaN, STRONG.get(), null)
            .lamp(140, 22, C_FLAGS, CF_RUN, LampColor.GREEN, WORKING.get())
            .lamp(140, 36, C_FLAGS, CF_OFF_SPEC, LampColor.AMBER, OFF_SPEC.get())
            .lamp(140, 50, C_FLAGS, CF_NO_FUEL, LampColor.RED, NO_FUEL.get())
            .button(8, 88, 44, CB_RUN, RUN.get(), null)
            .caption(60, 78, SALT_LABEL.get())
            .button(60, 88, 20, CB_SALT_DOWN, Component.literal("−"), SALT_DOWN.get())
            .button(82, 88, 20, CB_SALT_UP, Component.literal("+"), SALT_UP.get())
            .caption(110, 78, REFLUX_LABEL.get())
            .button(110, 88, 20, CB_REFLUX_DOWN, Component.literal("−"), REFLUX_DOWN.get())
            .button(132, 88, 20, CB_REFLUX_UP, Component.literal("+"), REFLUX_UP.get())
            .readout(8, 108, 216, NitrogenPanels::concentrator, null)
            .noInventory().build();

    private NitrogenPanels() {
    }

    public static List<PanelLayout> all() {
        return List.of(REFORMER, ABSORBER, LOOP, NITRIC, CONCENTRATOR);
    }

    private static Component reformer(PanelValues v) {
        Component c = REFORMER_STATUS.get(v.raw(R_FEED), v.raw(R_FUEL_KW), v.num(R_CO_HTS, 100, 2), v.num(R_CO_LTS, 100, 2),
                v.num(R_RATIO, 100, 2));
        return v.bit(R_FLAGS, RF_NO_ABS) || v.bit(R_FLAGS, RF_NO_NI) || v.bit(R_FLAGS, RF_CARBON)
                ? c.copy().withStyle(ChatFormatting.GOLD) : c;
    }

    private static Component absorber(PanelValues v) {
        if (v.bit(A_FLAGS, AF_IDLE)) return ABS_IDLE.get().withStyle(ChatFormatting.GOLD);
        Component c = ABS_STATUS.get(v.raw(A_FLOW), v.num(A_RATIO, 100, 2), v.raw(A_CIRC), v.raw(A_REGEN_KW), v.raw(A_HEAT_KW));
        return v.bit(A_FLAGS, AF_ON_SPEC) ? c : c.copy().withStyle(ChatFormatting.GOLD);
    }

    private static Component loop(PanelValues v) {
        return LOOP_STATUS.get(v.raw(S_NH3_KGH), v.raw(S_SEP_C), v.raw(S_SHAFT_KW), v.raw(S_CHILL_KW));
    }

    private static Component nitric(PanelValues v) {
        Tr scheme = SCHEMES[Math.floorMod(v.raw(N_PRESSURE), NitricAcidPlant.PRESSURES_MPA.length)];
        Component c = NITRIC_STATUS.get(scheme.get(), v.num(N_NH3, 100, 2), v.num(N_LEL, 100, 1), v.num(N_STRENGTH, 10, 1),
                v.raw(N_HNO3_KGH), v.raw(N_SHAFT_KW));
        return v.bit(N_FLAGS, NF_FLASH) || v.bit(N_FLAGS, NF_FOX) ? c.copy().withStyle(ChatFormatting.GOLD) : c;
    }

    private static Component concentrator(PanelValues v) {
        return CONC_STATUS.get(v.num(C_SALT, 10, 1), v.num(C_REFLUX, 10, 1), v.raw(C_FEED_KGH), v.raw(C_PRODUCT_KGH), v.raw(C_HEAT_KW));
    }
}
