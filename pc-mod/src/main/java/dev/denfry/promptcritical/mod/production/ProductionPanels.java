package dev.denfry.promptcritical.mod.production;

import static dev.denfry.promptcritical.mod.panel.PanelText.MPA;
import static dev.denfry.promptcritical.mod.panel.PanelText.PERCENT;

import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.panel.PanelLayout.LampColor;
import dev.denfry.promptcritical.mod.panel.PanelText;
import dev.denfry.promptcritical.mod.panel.PanelText.Tr;
import dev.denfry.promptcritical.sim.production.BeamPump;
import java.util.List;

/** Местные щиты добычи и НПЗ: устье скважины с динамограммой, сепаратор, атмосферная колонна с печью. */
public final class ProductionPanels {
    // устье
    public static final int W_THP_X100 = 0, W_BHP_X100 = 1, W_PR_X100 = 2, W_OIL_M3D10 = 3, W_GOR = 4, W_WC_PERMIL = 5, W_CHOKE = 6,
            W_MODE = 7, W_FLAGS = 8, W_FILL_PERMIL = 9, W_PEAK_KN = 10, W_SPM10 = 11, W_CARD = 12,
            W_CB_KN10 = W_CARD + 2 * BeamPump.CARD_POINTS, W_TORQUE_KNM10 = W_CB_KN10 + 1, W_GOODMAN_PCT = W_CB_KN10 + 2,
            W_LEVEL_M = W_CB_KN10 + 3, W_ECHO_MS = W_CB_KN10 + 4, W_LEAK_PCT = W_CB_KN10 + 5, W_GAS_PCT = W_CB_KN10 + 6,
            W_GL_INJ_X100 = W_CB_KN10 + 7, W_GL_DEPTH_M = W_CB_KN10 + 8, W_GL_CSG_X100 = W_CB_KN10 + 9, W_GL_VALVES = W_CB_KN10 + 10,
            W_INJ_SET_X10 = W_CB_KN10 + 11, W_INJ_M3D10 = W_CB_KN10 + 12, W_VRR_X100 = W_CB_KN10 + 13, W_SKIN_X10 = W_CB_KN10 + 14,
            W_WATER_PERMIL = W_CB_KN10 + 15, W_CHANNELS = W_CB_KN10 + 16;
    public static final int WF_OPEN = 0, WF_PUMPING = 1, WF_TREE = 2, WF_SEPARATOR = 3, WF_ROD_PART = 4, WF_GEARBOX = 5,
            WF_GAS_LIFT = 6, WF_GL_STUCK = 7, WF_INJECTOR = 8, WF_FRACTURING = 9;
    public static final int WB_CHOKE_DOWN = 0, WB_CHOKE_UP = 1, WB_VALVE = 2, WB_CB_DOWN = 3, WB_CB_UP = 4, WB_ECHO = 5,
            WB_GL_DOWN = 6, WB_GL_UP = 7, WB_INJECT = 8, WB_INJ_DOWN = 9, WB_INJ_UP = 10;
    // сепаратор
    public static final int S_OIL_PERMIL = 0, S_WATER_PERMIL = 1, S_GAS_PERMIL = 2, S_BSW_X100 = 3, S_RET_S = 4, S_FLARE_M3H = 5,
            S_FLAGS = 6, S_CHANNELS = 7;
    public static final int SF_FLARING = 0, SF_CARRYOVER = 1, SF_FULL = 2;
    // колонна
    public static final int C_T_OUT = 0, C_FEED_M3H10 = 1, C_FUEL_M3H = 2, C_REFLUX_X10 = 3, C_TRAYS = 4, C_Y_GASOLINE = 5,
            C_Y_KEROSENE = 6, C_Y_DIESEL = 7, C_Y_RESIDUE = 8, C_OVERLAP_K = 9, C_FLAGS = 10, C_TANK0 = 11, C_SALT_X10 = 15,
            C_CORROSION_PERMIL = 16, C_CHANNELS = 17;
    public static final int CF_LIT = 0, CF_READY = 1, CF_LEAK = 2;
    public static final int CB_T_DOWN = 0, CB_T_UP = 1, CB_R_DOWN = 2, CB_R_UP = 3;

    private static final Tr M3D = PanelText.tr("unit.m3d", "m³/d", "м³/сут"), M3H = PanelText.tr("unit.m3h_prod", "m³/h", "м³/ч"),
            CELSIUS = PanelText.tr("unit.celsius_prod", "°C", "°C"), MIN = PanelText.tr("unit.min", "min", "мин"),
            THP = PanelText.tr("prod.thp", "Buffer", "Буфер"),
            THP_TIP = PanelText.tr("prod.thp_tip", "Tubing head pressure before the choke %s MPa", "Давление на буфере перед штуцером %s МПа"),
            BHP = PanelText.tr("prod.bhp", "Bottomhole", "Забой"),
            PR = PanelText.tr("prod.pr", "Reservoir", "Пласт"),
            PR_TIP = PanelText.tr("prod.pr_tip", "Average reservoir pressure %s MPa: falls as the field is produced",
                    "Среднее пластовое давление %s МПа: падает с отбором"),
            OIL = PanelText.tr("prod.oil", "Oil", "Нефть"),
            CARD = PanelText.tr("prod.card", "Dynamometer card", "Динамограмма"),
            CARD_TIP = PanelText.tr("prod.card_tip", "Polished rod load against position: peak %s kN, pump fillage %s %%, %s strokes/min",
                    "Нагрузка на полированный шток по ходу: максимум %s кН, заполнение насоса %s %%, %s кач/мин"),
            OPEN = PanelText.tr("prod.open", "Open", "Открыта"),
            PUMPING = PanelText.tr("prod.pumping", "Pump", "Насос"),
            TREE = PanelText.tr("prod.tree", "Tree", "Арматура"),
            SEP = PanelText.tr("prod.sep", "Separator", "Сепаратор"),
            CHOKE_DOWN = PanelText.tr("prod.choke_down", "Choke −", "Штуцер −"),
            CHOKE_UP = PanelText.tr("prod.choke_up", "Choke +", "Штуцер +"),
            VALVE = PanelText.tr("prod.valve", "Master", "Задвижка"),
            VALVE_TIP = PanelText.tr("prod.valve_tip", "Open or close the master valve", "Открыть или закрыть центральную задвижку"),
            ROD_PART = PanelText.tr("prod.rod_part", "Rods parted", "Обрыв штанг"),
            GEARBOX = PanelText.tr("prod.gearbox", "Gearbox", "Редуктор"),
            CB_DOWN = PanelText.tr("prod.cb_down", "Weights −", "Противовес −"),
            CB_UP = PanelText.tr("prod.cb_up", "Weights +", "Противовес +"),
            CB_TIP = PanelText.tr("prod.cb_tip", "Move the crank counterweights by 2 kN at the polished rod; balanced is half the fluid load plus the rods in fluid",
                    "Сдвинуть противовесы кривошипа на 2 кН по штоку; уравновешено — половина веса жидкости плюс штанги в жидкости"),
            ECHO = PanelText.tr("prod.echo", "Echo", "Эхолот"),
            ECHO_TIP = PanelText.tr("prod.echo_tip", "Fire an acoustic shot down the annulus: the echo from the liquid level gives z = c·t/2",
                    "Выстрел в затрубье: эхо от уровня жидкости даёт z = c·t/2"),
            PUMP = PanelText.tr("prod.pump", "Counterweight %s kN · gearbox %s of 25.8 kN·m · rods %s %% Goodman · valve leak %s %% · gas %s %%",
                    "Противовес %s кН · редуктор %s из 25,8 кН·м · штанги %s %% Гудмана · утечка клапана %s %% · газ %s %%"),
            GL_DOWN = PanelText.tr("prod.gl_down", "Gas −", "Газ −"),
            GL_UP = PanelText.tr("prod.gl_up", "Gas +", "Газ +"),
            GL_TIP = PanelText.tr("prod.gl_tip", "Gas lift injection into the annulus in steps of 0.05 nm³/s, taken from the separator gas",
                    "Закачка газлифтного газа в затрубье шагом 0,05 нм³/с из газа сепаратора"),
            GL_LAMP = PanelText.tr("prod.gl_lamp", "Gas lift", "Газлифт"),
            GL = PanelText.tr("prod.gl", "Gas lift %s nm³/s · %s valves · injecting at %s m · casing %s MPa",
                    "Газлифт %s нм³/с · клапанов %s · ввод газа на %s м · затрубье %s МПа"),
            GL_STUCK = PanelText.tr("prod.gl_stuck", "Top valve open", "Верхний клапан открыт"),
            INJECT = PanelText.tr("prod.inject", "Inject", "ППД"),
            INJECT_TIP = PanelText.tr("prod.inject_tip", "Turn the well into a water injector (water from Create pipes into the wellhead) or back",
                    "Перевести скважину под закачку воды (вода трубами Create в устье) или обратно в добывающие"),
            INJ_DOWN = PanelText.tr("prod.inj_down", "P inj −", "Pзак −"),
            INJ_UP = PanelText.tr("prod.inj_up", "P inj +", "Pзак +"),
            INJ_TIP = PanelText.tr("prod.inj_tip", "Injection pressure on the wellhead in steps of 0.5 MPa; above the fracture pressure the rock opens",
                    "Давление закачки на устье шагом 0,5 МПа; выше давления гидроразрыва пласт раскрывается"),
            INJECTOR = PanelText.tr("prod.injector", "Injector", "Нагнет."),
            FRACTURING = PanelText.tr("prod.fracturing", "Fracturing", "ГРП"),
            INJ = PanelText.tr("prod.inj", "Injection %s MPa on the head · %s m³/d · VRR %s · skin %s · water tank %s %%",
                    "Закачка %s МПа на устье · %s м³/сут · компенсация %s · скин %s · бак воды %s %%"),
            LEVEL = PanelText.tr("prod.level", "Echo %s s: fluid level %s m", "Эхо %s с: уровень жидкости %s м"),
            WELL = PanelText.tr("prod.well", "Choke %s/64″ · water cut %s %% · GOR %s m³/m³",
                    "Штуцер %s/64″ · обводнённость %s %% · газовый фактор %s м³/м³"),
            OIL_LEVEL = PanelText.tr("prod.oil_level", "Oil", "Нефть"),
            WATER_LEVEL = PanelText.tr("prod.water_level", "Water", "Вода"),
            GAS_LEVEL = PanelText.tr("prod.gas_level", "Gas", "Газ"),
            BSW = PanelText.tr("prod.bsw", "BS&W", "Вода в нефти"),
            BSW_TIP = PanelText.tr("prod.bsw_tip", "Water left in the oil %s %%: drops smaller than the Stokes cut size stay in it",
                    "Вода в нефти %s %%: капли мельче стоксовского граничного размера не успевают осесть"),
            RETENTION = PanelText.tr("prod.retention", "Retention", "Отстой"),
            FLARING = PanelText.tr("prod.flaring", "Flare", "Факел"),
            CARRYOVER = PanelText.tr("prod.carryover", "Carryover", "Унос"),
            FULL = PanelText.tr("prod.full", "Full", "Полон"),
            SEPARATOR = PanelText.tr("prod.separator", "Oil to the sides, gas from the top, water from the bottom · flare %s nm³/h",
                    "Нефть — с боков, газ — сверху, вода — снизу · на факел %s нм³/ч"),
            OUTLET = PanelText.tr("prod.outlet", "Furnace out", "Выход печи"),
            FEED = PanelText.tr("prod.feed", "Feed", "Сырьё"),
            FUEL = PanelText.tr("prod.fuel", "Fuel gas", "Топливо"),
            LIT = PanelText.tr("prod.lit", "Burners", "Горелки"),
            READY = PanelText.tr("prod.ready", "Column", "Колонна"),
            T_DOWN = PanelText.tr("prod.t_down", "T −", "T −"),
            T_UP = PanelText.tr("prod.t_up", "T +", "T +"),
            R_DOWN = PanelText.tr("prod.r_down", "Reflux −", "Флегма −"),
            R_UP = PanelText.tr("prod.r_up", "Reflux +", "Флегма +"),
            YIELDS = PanelText.tr("prod.yields", "Gasoline %s %% · kerosene %s %% · diesel %s %% · mazut %s %%",
                    "Бензин %s %% · керосин %s %% · дизель %s %% · мазут %s %%"),
            SHARPNESS = PanelText.tr("prod.sharpness", "%s theoretical trays · reflux %s · kerosene/diesel overlap %s K",
                    "Теоретических тарелок %s · флегмовое число %s · наложение керосин/дизель %s К"),
            LEAK = PanelText.tr("prod.overhead_leak", "Overhead cooler leaks: salt", "Течь конденсатора: соли"),
            SALT = PanelText.tr("prod.salt", "Salt in crude %s mg/l · overhead tube wear %s %%",
                    "Соли в нефти %s мг/л · износ пучка конденсатора %s %%"),
            GASOLINE = PanelText.tr("prod.gasoline", "Gasoline", "Бензин"),
            KEROSENE = PanelText.tr("prod.kerosene", "Kerosene", "Керосин"),
            DIESEL = PanelText.tr("prod.diesel", "Diesel", "Дизель"),
            MAZUT = PanelText.tr("prod.mazut", "Mazut", "Мазут");

    public static final PanelLayout WELLHEAD = PanelLayout.builder("wellhead", 352, 240, W_CHANNELS)
            .dial(8, 20, false, W_THP_X100, 100, 20, Double.NaN, 1, MPA.get(), THP.get(), v -> THP_TIP.get(v.num(W_THP_X100, 100, 2)))
            .dial(64, 20, false, W_BHP_X100, 100, 30, Double.NaN, 1, MPA.get(), BHP.get(), null)
            .dial(120, 20, false, W_PR_X100, 100, 30, Double.NaN, 1, MPA.get(), PR.get(), v -> PR_TIP.get(v.num(W_PR_X100, 100, 2)))
            .dial(176, 20, false, W_OIL_M3D10, 10, 100, Double.NaN, 0, M3D.get(), OIL.get(), null)
            .plot(8, 84, 180, 70, W_CARD, BeamPump.CARD_POINTS, 2000, 1500, W_FLAGS, WF_PUMPING, CARD.get(),
                    v -> CARD_TIP.get(v.raw(W_PEAK_KN), v.num(W_FILL_PERMIL, 10, 0), v.num(W_SPM10, 10, 1)))
            .lamp(236, 24, W_FLAGS, WF_OPEN, LampColor.GREEN, OPEN.get())
            .lamp(236, 40, W_FLAGS, WF_PUMPING, LampColor.GREEN, PUMPING.get())
            .lamp(236, 56, W_FLAGS, WF_TREE, LampColor.AMBER, TREE.get())
            .lamp(236, 72, W_FLAGS, WF_SEPARATOR, LampColor.AMBER, SEP.get())
            .lamp(8, 68, W_FLAGS, WF_ROD_PART, LampColor.RED, ROD_PART.get())
            .lamp(100, 68, W_FLAGS, WF_GEARBOX, LampColor.RED, GEARBOX.get())
            .button(200, 100, 46, WB_CHOKE_DOWN, CHOKE_DOWN.get(), null)
            .button(250, 100, 46, WB_CHOKE_UP, CHOKE_UP.get(), null)
            .button(200, 118, 46, WB_VALVE, VALVE.get(), VALVE_TIP.get())
            .button(250, 118, 46, WB_ECHO, ECHO.get(), ECHO_TIP.get())
            .button(200, 136, 46, WB_CB_DOWN, CB_DOWN.get(), CB_TIP.get())
            .button(250, 136, 46, WB_CB_UP, CB_UP.get(), CB_TIP.get())
            .button(200, 154, 46, WB_GL_DOWN, GL_DOWN.get(), GL_TIP.get())
            .button(250, 154, 46, WB_GL_UP, GL_UP.get(), GL_TIP.get())
            .button(302, 100, 46, WB_INJECT, INJECT.get(), INJECT_TIP.get())
            .button(302, 118, 46, WB_INJ_DOWN, INJ_DOWN.get(), INJ_TIP.get())
            .button(302, 136, 46, WB_INJ_UP, INJ_UP.get(), INJ_TIP.get())
            .lamp(296, 24, W_FLAGS, WF_INJECTOR, LampColor.AMBER, INJECTOR.get())
            .lamp(296, 40, W_FLAGS, WF_FRACTURING, LampColor.RED, FRACTURING.get())
            .lamp(236, 88, W_FLAGS, WF_GAS_LIFT, LampColor.GREEN, GL_LAMP.get())

            .readout(8, 172, 336, v -> WELL.get(v.raw(W_CHOKE), v.num(W_WC_PERMIL, 10, 0), v.raw(W_GOR)), null)
            .readout(8, 185, 336, v -> PUMP.get(v.num(W_CB_KN10, 10, 1), v.num(W_TORQUE_KNM10, 10, 1), v.raw(W_GOODMAN_PCT),
                    v.raw(W_LEAK_PCT), v.raw(W_GAS_PCT)), null)
            .readout(8, 198, 336, v -> LEVEL.get(PanelText.num(v.raw(W_ECHO_MS) / 1000.0, 2), v.raw(W_LEVEL_M)), null)
            .readout(8, 211, 336, v -> GL.get(PanelText.num(v.raw(W_GL_INJ_X100) / 100.0, 2), v.raw(W_GL_VALVES), v.raw(W_GL_DEPTH_M),
                    v.num(W_GL_CSG_X100, 100, 2)), null)
            .readout(8, 224, 336, v -> INJ.get(v.num(W_INJ_SET_X10, 10, 1), v.num(W_INJ_M3D10, 10, 1), v.num(W_VRR_X100, 100, 2),
                    v.num(W_SKIN_X10, 10, 1), v.num(W_WATER_PERMIL, 10, 0)), null)
            .noInventory().build();

    public static final PanelLayout SEPARATOR_PANEL = PanelLayout.builder("three_phase_separator", 240, 120, S_CHANNELS)
            .glass(8, 22, 64, S_OIL_PERMIL, 0xFF3A2A18, Double.NaN, Double.NaN, OIL_LEVEL.get(), null)
            .glass(36, 22, 64, S_WATER_PERMIL, 0xFF4A6A8A, Double.NaN, Double.NaN, WATER_LEVEL.get(), null)
            .glass(64, 22, 64, S_GAS_PERMIL, 0xFFB0B0A0, Double.NaN, Double.NaN, GAS_LEVEL.get(), null)
            .dial(96, 22, false, S_BSW_X100, 100, 10, 2, 2, PERCENT.get(), BSW.get(), v -> BSW_TIP.get(v.num(S_BSW_X100, 100, 2)))
            .dial(150, 22, false, S_RET_S, 60, 10, Double.NaN, 1, MIN.get(), RETENTION.get(), null)
            .lamp(206, 24, S_FLAGS, SF_FLARING, LampColor.AMBER, FLARING.get())
            .lamp(206, 40, S_FLAGS, SF_CARRYOVER, LampColor.RED, CARRYOVER.get())
            .lamp(206, 56, S_FLAGS, SF_FULL, LampColor.RED, FULL.get())
            .readout(8, 104, 224, v -> SEPARATOR.get(v.raw(S_FLARE_M3H)), null)
            .noInventory().build();

    public static final PanelLayout COLUMN = PanelLayout.builder("crude_column", 300, 170, C_CHANNELS)
            .dial(8, 22, false, C_T_OUT, 1, 400, 380, 0, CELSIUS.get(), OUTLET.get(), null)
            .dial(64, 22, false, C_FEED_M3H10, 10, 12, Double.NaN, 1, M3H.get(), FEED.get(), null)
            .dial(120, 22, false, C_FUEL_M3H, 1, 400, Double.NaN, 0, M3H.get(), FUEL.get(), null)
            .glass(180, 22, 64, C_TANK0, 0xFFE0C060, Double.NaN, Double.NaN, GASOLINE.get(), null)
            .glass(208, 22, 64, C_TANK0 + 1, 0xFFC8B070, Double.NaN, Double.NaN, KEROSENE.get(), null)
            .glass(236, 22, 64, C_TANK0 + 2, 0xFFA08040, Double.NaN, Double.NaN, DIESEL.get(), null)
            .glass(264, 22, 64, C_TANK0 + 3, 0xFF302010, Double.NaN, Double.NaN, MAZUT.get(), null)
            .lamp(8, 100, C_FLAGS, CF_LIT, LampColor.AMBER, LIT.get())
            .lamp(70, 100, C_FLAGS, CF_READY, LampColor.GREEN, READY.get())
            .lamp(140, 100, C_FLAGS, CF_LEAK, LampColor.RED, LEAK.get())
            .readout(8, 86, 284, v -> SALT.get(v.num(C_SALT_X10, 10, 1), v.num(C_CORROSION_PERMIL, 10, 0)), null)
            .button(8, 116, 40, CB_T_DOWN, T_DOWN.get(), null)
            .button(52, 116, 40, CB_T_UP, T_UP.get(), null)
            .button(100, 116, 56, CB_R_DOWN, R_DOWN.get(), null)
            .button(160, 116, 56, CB_R_UP, R_UP.get(), null)
            .readout(8, 138, 284, v -> YIELDS.get(v.num(C_Y_GASOLINE, 10, 1), v.num(C_Y_KEROSENE, 10, 1), v.num(C_Y_DIESEL, 10, 1),
                    v.num(C_Y_RESIDUE, 10, 1)), null)
            .readout(8, 152, 284, v -> SHARPNESS.get(v.raw(C_TRAYS), v.num(C_REFLUX_X10, 10, 1), v.raw(C_OVERLAP_K)), null)
            .noInventory().build();

    private ProductionPanels() {
    }

    public static List<PanelLayout> all() {
        return List.of(WELLHEAD, SEPARATOR_PANEL, COLUMN);
    }
}
