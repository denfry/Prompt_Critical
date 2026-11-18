package dev.denfry.promptcritical.mod.chlor;

import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.panel.PanelLayout.LampColor;
import dev.denfry.promptcritical.mod.panel.PanelText;
import dev.denfry.promptcritical.mod.panel.PanelText.Tr;
import dev.denfry.promptcritical.mod.panel.PanelValues;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.chlor.PvcReactor;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * Щиты A0 хлорного производства: серия ванн (вольтметр ванны, амперметр серии, термометр, выход по току, водород в
 * хлоре с красной чертой 4 %), выпрямитель (напряжение ввода, мощность), рассольное отделение (жёсткость, стёкла
 * баков, мешки соли), сжижение (температура конденсации, давление, водород в абгазе), ЭДХ, печь пиролиза ЭДХ и
 * автоклав ПВХ. Каналы — целые.
 */
public final class ChlorPanels {
    // серия ванн
    public static final int C_VOLT_X100 = 0, C_KA = 1, C_SET_KA = 2, C_TEMP_C = 3, C_CE_PERMIL = 4, C_NAOH_PERMIL = 5, C_H2_PERMIL = 6,
            C_CL2_KGH = 7, C_KWH_T = 8, C_FOUL_PCT = 9, C_AGE_PCT = 10, C_BRINE = 11, C_FLAGS = 12, C_CHANNELS = 13;
    public static final int CF_RUN = 0, CF_TRIP = 1, CF_VENT = 2, CF_RAW = 3, CF_LOW_BRINE = 4;
    public static final int CB_RUN = 0, CB_DOWN = 1, CB_UP = 2, CB_REBUILD = 3;
    // выпрямитель
    public static final int R_SUPPLY_X100 = 0, R_KW = 1, R_DC_KW = 2, R_FLAGS = 3, R_CHANNELS = 4;
    public static final int RF_ON = 0, RF_NO_CELLS = 1, RF_LOW = 2;
    public static final int RB_BREAKER = 0;
    // рассол
    public static final int B_LS_X100 = 0, B_HARD = 1, B_REAGENT_PCT = 2, B_TANK = 3, B_RAW_TANK = 4, B_WATER = 5, B_FLAGS = 6, B_CHANNELS = 7;
    public static final int BF_RUN = 0, BF_RAW = 1;
    // сжижение
    public static final int L_COND_C = 0, L_P_X100 = 1, L_DEGREE_PERMIL = 2, L_TAIL_H2_PERMIL = 3, L_KGH = 4, L_TANK = 5, L_VENT_GH = 6,
            L_FLAGS = 7, L_CHANNELS = 8;
    public static final int LF_RUN = 0, LF_TRIP = 1, LF_VENT = 2, LF_H2 = 3;
    public static final int LB_RUN = 0, LB_T_DOWN = 1, LB_T_UP = 2, LB_P_DOWN = 3, LB_P_UP = 4;
    // ЭДХ
    public static final int E_OXY_C = 0, E_HCL_PERMIL = 1, E_SEL_PERMIL = 2, E_DIRECT_KGH = 3, E_OXY_KGH = 4, E_TANK = 5, E_FLAGS = 6,
            E_CHANNELS = 7;
    public static final int EF_ON = 0, EF_OXY = 1, EF_DIRECT = 2, EF_HOT = 3;
    public static final int EB_RUN = 0, EB_T_DOWN = 1, EB_T_UP = 2;
    // печь пиролиза ЭДХ
    public static final int K_COT_C = 0, K_CONV_PERMIL = 1, K_SEL_PERMIL = 2, K_COKE_X100 = 3, K_VCM_KGH = 4, K_FUEL_KW = 5, K_FLAGS = 6,
            K_CHANNELS = 7;
    public static final int KF_RUN = 0, KF_COKED = 1;
    public static final int KB_RUN = 0, KB_T_DOWN = 1, KB_T_UP = 2;
    // ПВХ
    public static final int P_TEMP_C = 0, P_P_X100 = 1, P_CONV_PERMIL = 2, P_K = 3, P_TIME_MIN = 4, P_HEAT_KW = 5, P_CHARGE_PERMIL = 6,
            P_PHASE = 7, P_FLAGS = 8, P_CHANNELS = 9;
    public static final int PF_AUTO = 0, PF_RUN = 1, PF_DROP = 2;
    public static final int PB_AUTO = 0, PB_T_DOWN = 1, PB_T_UP = 2;

    private static final Tr CELSIUS = PanelText.CELSIUS, MPA = PanelText.MPA,
            VOLT = PanelText.tr("unit.v", "V", "В"), KA = PanelText.tr("unit.ka", "kA", "кА"), PCT = PanelText.tr("unit.pct", "%", "%"),
            MG_L = PanelText.tr("unit.mg_l", "mg/l", "мг/л"), PU = PanelText.tr("unit.pu", "p.u.", "о.е."),
            TEMP = PanelText.tr("chlor.temp", "Temp.", "Темп."), KW = PanelText.tr("unit.kw", "kW", "кВт"),
            RUN = PanelText.tr("chlor.run", "Run", "Пуск/стоп"),
            MINUS = PanelText.tr("chlor.minus", "−", "−"), PLUS = PanelText.tr("chlor.plus", "+", "+");
    // серия
    private static final Tr CELL_V = PanelText.tr("chlor.cell_v", "Cell", "Ванна"),
            SERIES_A = PanelText.tr("chlor.series_a", "Series", "Серия"),
            CELL_T = PanelText.tr("chlor.cell_t", "Anolyte", "Анолит"),
            H2_CL2 = PanelText.tr("chlor.h2_cl2", "H₂ in Cl₂", "H₂ в Cl₂"),
            ELECTROLYSIS = PanelText.tr("chlor.electrolysis", "Electrolysis", "Электролиз"),
            TRIP = PanelText.tr("chlor.trip", "Trip", "Защита"),
            VENT = PanelText.tr("chlor.vent", "Cl₂ leak", "Утечка Cl₂"),
            RAW = PanelText.tr("chlor.raw", "Raw brine", "Сырой рассол"),
            LOW_BRINE = PanelText.tr("chlor.low_brine", "Low anolyte", "Низк. анолит"),
            BRINE_LBL = PanelText.tr("chlor.brine", "Brine", "Рассол"),
            REBUILD = PanelText.tr("chlor.rebuild", "Diaphr.", "Диафр."),
            REBUILD_TIP = PanelText.tr("chlor.rebuild_tip", "Rebuild the asbestos diaphragms (series switched off)",
                    "Переосадить асбестовые диафрагмы (на отключённой серии)"),
            KA_DOWN = PanelText.tr("chlor.ka_down", "Series current −2.5 kA", "Ток серии −2,5 кА"),
            KA_UP = PanelText.tr("chlor.ka_up", "Series current +2.5 kA", "Ток серии +2,5 кА"),
            VOLT_TIP = PanelText.tr("chlor.volt_tip", "Cell voltage %s V: decomposition 2.19 V, overvoltages and the ohmic drop on top",
                    "Напряжение ванны %s В: разложение 2,19 В плюс перенапряжения и омика"),
            H2_TIP = PanelText.tr("chlor.h2_tip", "Hydrogen in chlorine %s %% · above 4 %% the mixture explodes",
                    "Водород в хлоре %s %% · выше 4 %% смесь взрывоопасна"),
            CELLS_STATUS = PanelText.tr("chlor.cells_status",
                    "Set %s kA · Cl₂ %s kg/h · current efficiency %s %% · NaOH %s %% · %s kWh/t DC · diaphragm +%s %%, worn %s %%",
                    "Уставка %s кА · Cl₂ %s кг/ч · выход по току %s %% · NaOH %s %% · %s кВт·ч/т пост. тока · диафрагма +%s %%, износ %s %%");
    // выпрямитель
    private static final Tr SUPPLY = PanelText.tr("chlor.supply", "6.3 kV", "6,3 кВ"),
            POWER = PanelText.tr("chlor.power", "Input", "Ввод"),
            BREAKER = PanelText.tr("chlor.breaker", "Breaker", "Выключатель"),
            ON = PanelText.tr("chlor.on", "On", "Вкл."),
            NO_CELLS = PanelText.tr("chlor.no_cells", "No cells", "Нет ванн"),
            LOW_V = PanelText.tr("chlor.low_v", "Low V", "Низк. U"),
            RECT_STATUS = PanelText.tr("chlor.rect_status", "DC to the cells %s kW · rectifier efficiency 96.5 %%",
                    "На ванны %s кВт постоянного тока · КПД выпрямителя 96,5 %%");
    // рассол
    private static final Tr HARDNESS = PanelText.tr("chlor.hardness", "Ca+Mg", "Ca+Mg"),
            PURE = PanelText.tr("chlor.pure", "Treated", "Очищ."),
            RAW_TANK = PanelText.tr("chlor.raw_tank", "Raw", "Сырой"),
            WATER = PanelText.tr("chlor.water", "Water", "Вода"),
            DISSOLVING = PanelText.tr("chlor.dissolving", "Dissolving", "Растворение"),
            SALT = PanelText.tr("chlor.salt", "Salt, 25 kg bag", "Соль, мешок 25 кг"),
            HARD_TIP = PanelText.tr("chlor.hard_tip", "Calcium and magnesium %s mg/l · cells need 5 or less",
                    "Кальций и магний %s мг/л · ваннам нужно не больше 5"),
            BRINE_STATUS = PanelText.tr("chlor.brine_status", "Brine %s l/s · reagents %s %% of the norm (cell liquor and flue gas)",
                    "Рассол %s л/с · реагенты %s %% нормы (щёлочь и дымовые газы)");
    // сжижение
    private static final Tr COND = PanelText.tr("chlor.cond", "Condenser", "Конденсатор"),
            PRESS = PanelText.tr("chlor.press", "Pressure", "Давление"),
            TAIL_H2 = PanelText.tr("chlor.tail_h2", "H₂ tail", "H₂ абгаз"),
            LIQUEFYING = PanelText.tr("chlor.liquefying", "Liquefying", "Сжижение"),
            H2_HIGH = PanelText.tr("chlor.h2_high", "H₂ high", "H₂ высок."),
            T_DOWN = PanelText.tr("chlor.t_down", "−5 °C", "−5 °C"), T_UP = PanelText.tr("chlor.t_up", "+5 °C", "+5 °C"),
            P_DOWN = PanelText.tr("chlor.p_down", "−0.1 MPa", "−0,1 МПа"), P_UP = PanelText.tr("chlor.p_up", "+0.1 MPa", "+0,1 МПа"),
            TAIL_TIP = PanelText.tr("chlor.tail_tip", "Hydrogen in tail gas %s %% · deeper liquefaction concentrates it, interlock at 4 %%",
                    "Водород в абгазе %s %% · чем глубже сжижение, тем его больше, защита на 4 %%"),
            LIQ_STATUS = PanelText.tr("chlor.liq_status", "Liquefied %s %% · %s kg/h · storage %s %% · vented %s g/h",
                    "Сжижено %s %% · %s кг/ч · хранилище %s %% · сброс %s г/ч");
    // ЭДХ
    private static final Tr OXY = PanelText.tr("chlor.oxy", "Oxychlor.", "Оксихлор."),
            DIRECT = PanelText.tr("chlor.direct", "Direct", "Прямое"),
            OXY_LAMP = PanelText.tr("chlor.oxy_lamp", "Oxy", "Окси"),
            HOT = PanelText.tr("chlor.hot", "Hot bed", "Перегрев"),
            EDC_TANK = PanelText.tr("chlor.edc_tank", "EDC", "ДХЭ"),
            EDC_STATUS = PanelText.tr("chlor.edc_status", "Direct %s kg/h · oxychlorination %s kg/h · HCl conversion %s %% · selectivity %s %%",
                    "Прямое %s кг/ч · оксихлорирование %s кг/ч · превращение HCl %s %% · селективность %s %%");
    // печь ЭДХ
    private static final Tr COT = PanelText.tr("chlor.cot", "Outlet", "Выход"),
            COKE = PanelText.tr("chlor.coke", "Coke", "Кокс"),
            CRACKING = PanelText.tr("chlor.cracking", "Cracking", "Крекинг"),
            COKED = PanelText.tr("chlor.coked", "Coked", "Закокс."),
            CRACK_STATUS = PanelText.tr("chlor.crack_status", "Conversion %s %% · VCM selectivity %s %% · VCM %s kg/h · fuel %s kW",
                    "Превращение %s %% · селективность по ВХ %s %% · ВХ %s кг/ч · топливо %s кВт");
    // ПВХ
    private static final Tr AUTO = PanelText.tr("chlor.auto", "Auto", "Автомат"),
            REACTING = PanelText.tr("chlor.reacting", "Reacting", "Полимер."),
            DROP = PanelText.tr("chlor.drop", "P drop", "Падение P"),
            CONV = PanelText.tr("chlor.conv", "Conversion", "Превращение"),
            PVC_T_DOWN = PanelText.tr("chlor.pvc_t_down", "Batch temperature −1 °C (higher K)", "Температура загрузки −1 °C (выше K)"),
            PVC_T_UP = PanelText.tr("chlor.pvc_t_up", "Batch temperature +1 °C (lower K)", "Температура загрузки +1 °C (ниже K)"),
            BAGS = PanelText.tr("chlor.bags", "PVC resin, 25 kg bags", "Смола ПВХ, мешки 25 кг"),
            PVC_STATUS = PanelText.tr("chlor.pvc_status", "%s · K %s · %s min · charge %s %% · reaction heat %s kW",
                    "%s · K %s · %s мин · загрузка %s %% · тепло реакции %s кВт");
    private static final Tr[] PHASES = {
            PanelText.tr("chlor.phase.idle", "Empty", "Пуст"),
            PanelText.tr("chlor.phase.charging", "Charging", "Загрузка"),
            PanelText.tr("chlor.phase.reacting", "Polymerising", "Полимеризация"),
            PanelText.tr("chlor.phase.done", "Discharging", "Выгрузка"),
    };

    public static final PanelLayout CELLS = PanelLayout.builder("chlor_alkali_cell", 248, 140, C_CHANNELS)
            .dial(8, 24, false, C_VOLT_X100, 100, 5, Double.NaN, 2, VOLT.get(), CELL_V.get(), v -> VOLT_TIP.get(v.num(C_VOLT_X100, 100, 2)))
            .dial(64, 24, false, C_KA, 1, 45, 40, 0, KA.get(), SERIES_A.get(), null)
            .dial(120, 24, false, C_TEMP_C, 1, 110, 100, 0, CELSIUS.get(), CELL_T.get(), null)
            .bar(176, 24, 64, C_H2_PERMIL, 10, 6, 0xFFD04030, H2_CL2.get(), v -> H2_TIP.get(v.num(C_H2_PERMIL, 10, 1)))
            .bar(176, 40, 64, C_BRINE, 10, 100, 0xFFC8D8E0, BRINE_LBL.get(), null)
            .lamp(176, 56, C_FLAGS, CF_RUN, LampColor.GREEN, ELECTROLYSIS.get())
            .lamp(176, 68, C_FLAGS, CF_TRIP, LampColor.RED, TRIP.get())
            .lamp(212, 56, C_FLAGS, CF_VENT, LampColor.RED, VENT.get())
            .lamp(212, 68, C_FLAGS, CF_RAW, LampColor.AMBER, RAW.get())
            .lamp(176, 80, C_FLAGS, CF_LOW_BRINE, LampColor.AMBER, LOW_BRINE.get())
            .button(8, 92, 44, CB_RUN, RUN.get(), null)
            .button(56, 92, 20, CB_DOWN, MINUS.get(), KA_DOWN.get())
            .button(78, 92, 20, CB_UP, PLUS.get(), KA_UP.get())
            .button(104, 92, 44, CB_REBUILD, REBUILD.get(), REBUILD_TIP.get())
            .readout(8, 122, 232, ChlorPanels::cells, null)
            .noInventory().build();

    public static final PanelLayout RECTIFIER = PanelLayout.builder("rectifier_unit", 176, 100, R_CHANNELS)
            .dial(8, 24, false, R_SUPPLY_X100, 100, 1.2, Double.NaN, 2, PU.get(), SUPPLY.get(), null)
            .dial(64, 24, false, R_KW, 1, 3500, Double.NaN, 0, KW.get(), POWER.get(), null)
            .lamp(124, 24, R_FLAGS, RF_ON, LampColor.GREEN, ON.get())
            .lamp(124, 38, R_FLAGS, RF_NO_CELLS, LampColor.AMBER, NO_CELLS.get())
            .lamp(124, 52, R_FLAGS, RF_LOW, LampColor.RED, LOW_V.get())
            .button(8, 72, 60, RB_BREAKER, BREAKER.get(), null)
            .readout(8, 88, 160, v -> RECT_STATUS.get(v.raw(R_DC_KW)), null)
            .noInventory().build();

    public static final PanelLayout BRINE = PanelLayout.builder("brine_treater", 200, 110, B_CHANNELS)
            .dial(8, 24, false, B_HARD, 1, 400, 5, 0, MG_L.get(), HARDNESS.get(), v -> HARD_TIP.get(v.raw(B_HARD)))
            .glass(64, 24, 56, B_TANK, 0xFFD8E8F0, Double.NaN, Double.NaN, PURE.get(), null)
            .glass(92, 24, 56, B_RAW_TANK, 0xFFC0B8A0, Double.NaN, Double.NaN, RAW_TANK.get(), null)
            .glass(120, 24, 56, B_WATER, 0xFF4060C0, Double.NaN, Double.NaN, WATER.get(), null)
            .lamp(150, 24, B_FLAGS, BF_RUN, LampColor.GREEN, DISSOLVING.get())
            .lamp(150, 38, B_FLAGS, BF_RAW, LampColor.AMBER, RAW.get())
            .slot(150, 56, 0, s -> s.is(PcItems.SALT.get()), SALT.get())
            .slot(168, 56, 1, s -> s.is(PcItems.SALT.get()), SALT.get())
            .slot(150, 74, 2, s -> s.is(PcItems.SALT.get()), SALT.get())
            .slot(168, 74, 3, s -> s.is(PcItems.SALT.get()), SALT.get())
            .readout(8, 96, 184, v -> BRINE_STATUS.get(v.num(B_LS_X100, 100, 2), v.raw(B_REAGENT_PCT)), null)
            .build();

    public static final PanelLayout LIQUEFIER = PanelLayout.builder("chlorine_liquefier", 232, 124, L_CHANNELS)
            .dial(8, 24, false, L_COND_C, 1, 20, Double.NaN, 0, CELSIUS.get(), COND.get(), null)
            .dial(64, 24, false, L_P_X100, 100, 1.4, 1.2, 1, MPA.get(), PRESS.get(), null)
            .bar(120, 24, 60, L_TAIL_H2_PERMIL, 10, 6, 0xFFD04030, TAIL_H2.get(), v -> TAIL_TIP.get(v.num(L_TAIL_H2_PERMIL, 10, 1)))
            .lamp(188, 22, L_FLAGS, LF_RUN, LampColor.GREEN, LIQUEFYING.get())
            .lamp(188, 36, L_FLAGS, LF_H2, LampColor.AMBER, H2_HIGH.get())
            .lamp(188, 50, L_FLAGS, LF_TRIP, LampColor.RED, TRIP.get())
            .lamp(188, 64, L_FLAGS, LF_VENT, LampColor.RED, VENT.get())
            .button(8, 84, 44, LB_RUN, RUN.get(), null)
            .button(56, 84, 40, LB_T_DOWN, T_DOWN.get(), null)
            .button(100, 84, 40, LB_T_UP, T_UP.get(), null)
            .button(144, 84, 40, LB_P_DOWN, P_DOWN.get(), null)
            .button(188, 84, 40, LB_P_UP, P_UP.get(), null)
            .readout(8, 108, 216, v -> LIQ_STATUS.get(v.num(L_DEGREE_PERMIL, 10, 1), v.raw(L_KGH), v.num(L_TANK, 10, 0), v.raw(L_VENT_GH)), null)
            .noInventory().build();

    public static final PanelLayout EDC = PanelLayout.builder("edc_unit", 216, 110, E_CHANNELS)
            .dial(8, 24, false, E_OXY_C, 1, 300, 260, 0, CELSIUS.get(), OXY.get(), null)
            .glass(64, 24, 56, E_TANK, 0xFFE0D8A0, Double.NaN, Double.NaN, EDC_TANK.get(), null)
            .lamp(96, 22, E_FLAGS, EF_ON, LampColor.GREEN, ON.get())
            .lamp(96, 36, E_FLAGS, EF_DIRECT, LampColor.GREEN, DIRECT.get())
            .lamp(96, 50, E_FLAGS, EF_OXY, LampColor.GREEN, OXY_LAMP.get())
            .lamp(96, 64, E_FLAGS, EF_HOT, LampColor.AMBER, HOT.get())
            .button(8, 80, 44, EB_RUN, RUN.get(), null)
            .button(56, 80, 40, EB_T_DOWN, T_DOWN.get(), null)
            .button(100, 80, 40, EB_T_UP, T_UP.get(), null)
            .readout(8, 96, 200, v -> EDC_STATUS.get(v.raw(E_DIRECT_KGH), v.raw(E_OXY_KGH), v.num(E_HCL_PERMIL, 10, 1), v.num(E_SEL_PERMIL, 10, 1)),
                    null)
            .noInventory().build();

    public static final PanelLayout CRACKER = PanelLayout.builder("edc_cracker", 216, 110, K_CHANNELS)
            .dial(8, 24, false, K_COT_C, 1, 600, Double.NaN, 0, CELSIUS.get(), COT.get(), null)
            .bar(64, 30, 60, K_COKE_X100, 100, 6, 0xFF3A3A3A, COKE.get(), null)
            .lamp(132, 22, K_FLAGS, KF_RUN, LampColor.GREEN, CRACKING.get())
            .lamp(132, 36, K_FLAGS, KF_COKED, LampColor.RED, COKED.get())
            .button(8, 80, 44, KB_RUN, RUN.get(), null)
            .button(56, 80, 40, KB_T_DOWN, T_DOWN.get(), null)
            .button(100, 80, 40, KB_T_UP, T_UP.get(), null)
            .readout(8, 96, 200, v -> CRACK_STATUS.get(v.num(K_CONV_PERMIL, 10, 1), v.num(K_SEL_PERMIL, 10, 1), v.raw(K_VCM_KGH), v.raw(K_FUEL_KW)),
                    null)
            .noInventory().build();

    public static final PanelLayout PVC = PanelLayout.builder("pvc_reactor", 216, 120, P_CHANNELS)
            .dial(8, 24, false, P_TEMP_C, 1, 80, 70, 0, CELSIUS.get(), TEMP.get(), null)
            .dial(64, 24, false, P_P_X100, 100, 1.6, 1.4, 2, MPA.get(), PRESS.get(), null)
            .bar(120, 24, 60, P_CONV_PERMIL, 10, 100, 0xFFE8E8E8, CONV.get(), null)
            .lamp(120, 40, P_FLAGS, PF_AUTO, LampColor.GREEN, AUTO.get())
            .lamp(120, 54, P_FLAGS, PF_RUN, LampColor.GREEN, REACTING.get())
            .lamp(120, 68, P_FLAGS, PF_DROP, LampColor.AMBER, DROP.get())
            .output(188, 24, 0, BAGS.get())
            .output(188, 42, 1, BAGS.get())
            .output(188, 60, 2, BAGS.get())
            .output(188, 78, 3, BAGS.get())
            .button(8, 84, 44, PB_AUTO, AUTO.get(), null)
            .button(56, 84, 20, PB_T_DOWN, MINUS.get(), PVC_T_DOWN.get())
            .button(78, 84, 20, PB_T_UP, PLUS.get(), PVC_T_UP.get())
            .readout(8, 106, 200, ChlorPanels::pvc, null)
            .build();

    private ChlorPanels() {
    }

    public static List<PanelLayout> all() {
        return List.of(CELLS, RECTIFIER, BRINE, LIQUEFIER, EDC, CRACKER, PVC);
    }

    private static Component cells(PanelValues v) {
        Component c = CELLS_STATUS.get(v.raw(C_SET_KA), v.raw(C_CL2_KGH), v.num(C_CE_PERMIL, 10, 1), v.num(C_NAOH_PERMIL, 10, 1),
                v.raw(C_KWH_T), v.raw(C_FOUL_PCT), v.raw(C_AGE_PCT));
        return v.bit(C_FLAGS, CF_TRIP) || v.bit(C_FLAGS, CF_VENT) ? c.copy().withStyle(ChatFormatting.GOLD) : c;
    }

    private static Component pvc(PanelValues v) {
        Tr phase = PHASES[Math.floorMod(v.raw(P_PHASE), PHASES.length)];
        return PVC_STATUS.get(phase.get(), v.raw(P_K), v.raw(P_TIME_MIN), v.num(P_CHARGE_PERMIL, 10, 0), v.raw(P_HEAT_KW));
    }

    static {
        assert PvcReactor.Phase.values().length == PHASES.length;
    }
}
