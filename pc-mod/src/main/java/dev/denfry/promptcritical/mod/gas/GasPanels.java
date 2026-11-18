package dev.denfry.promptcritical.mod.gas;

import static dev.denfry.promptcritical.mod.panel.PanelText.MPA;
import static dev.denfry.promptcritical.mod.panel.PanelText.PERCENT;

import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.panel.PanelLayout.LampColor;
import dev.denfry.promptcritical.mod.panel.PanelText;
import dev.denfry.promptcritical.mod.panel.PanelText.Tr;
import java.util.List;
import net.minecraft.network.chat.Component;

/**
 * Местные щиты газопереработки: установка НТС с осушкой и холодильником, газофракционирующая установка, центробежный
 * компрессор со встроенным антипомпажным регулятором.
 */
public final class GasPanels {
    // НТС
    public static final int L_P_X100 = 0, L_GAS_M3H = 1, L_POWER_KW = 2, L_PLUG_PERMIL = 3, L_NGL_PERMIL = 4, L_SALES_PERMIL = 5,
            L_MEOH_PERMIL = 6, L_CHILL_X10 = 7, L_TLTS_X10 = 8, L_DEW_X10 = 9, L_HYD_X10 = 10, L_TEG_X10 = 11, L_NGL_LH = 12,
            L_FLAGS = 13, L_MEOH_WT_X10 = 14, L_CHANNELS = 15;
    public static final int LF_COMPRESSOR = 0, LF_REGENERATOR = 1, LF_RISK = 2, LF_PLUG = 3, LF_METHANOL = 4;
    public static final int LB_P_DOWN = 0, LB_P_UP = 1, LB_CHILL_DOWN = 2, LB_CHILL_UP = 3, LB_METHANOL = 4;
    // ГФУ
    public static final int G_FEED_LH = 0, G_C3_X10 = 1, G_C4_X10 = 2, G_C5_X10 = 3, G_REFLUX_X10 = 4, G_TANK0 = 5, G_FLAGS = 8,
            G_CHANNELS = 9;
    public static final int GF_RUNNING = 0;
    public static final int GB_R_DOWN = 0, GB_R_UP = 1;
    // центробежный компрессор
    public static final int C_SPEED_PCT = 0, C_PD_X100 = 1, C_FLOW_X1000 = 2, C_LINE_X1000 = 3, C_RECYCLE_PCT = 4, C_SS_X1000 = 5,
            C_CYCLES = 6, C_VIB_X10 = 7, C_DAMAGE_PERMIL = 8, C_POWER_KW = 9, C_BCTRL_X1000 = 10, C_RATE_X1000 = 11,
            C_LINE_VALVE_PCT = 12, C_XM_PCT = 13, C_FLAGS = 14, C_CHANNELS = 15;
    public static final int CF_RUNNING = 0, CF_SURGE = 1, CF_AUTO = 2, CF_ONLINE = 3;
    public static final int CB_LINE_DOWN = 0, CB_LINE_UP = 1, CB_MODE = 2, CB_REC_DOWN = 3, CB_REC_UP = 4, CB_MARGIN_DOWN = 5,
            CB_MARGIN_UP = 6, CB_RATE_DOWN = 7, CB_RATE_UP = 8;

    private static final Tr M3H = PanelText.tr("unit.nm3h", "nm³/h", "нм³/ч"), KW = PanelText.tr("unit.kw", "kW", "кВт"),
            LTS_P = PanelText.tr("gas.lts_p", "LTS", "НТС"),
            LTS_P_TIP = PanelText.tr("gas.lts_p_tip", "Pressure after the choke %s MPa: lower — colder and more NGL, more hydrate risk",
                    "Давление после штуцера %s МПа: ниже — холоднее и больше ШФЛУ, выше риск гидратов"),
            GAS = PanelText.tr("gas.gas", "Gas", "Газ"),
            POWER = PanelText.tr("gas.power", "Shaft", "Вал"),
            PLUG = PanelText.tr("gas.plug", "Plug", "Пробка"),
            PLUG_TIP = PanelText.tr("gas.plug_tip", "Hydrate in the choke %s %%: at 100 %% the choke is blocked",
                    "Гидрат в штуцере %s %%: на 100 %% штуцер перекрыт"),
            NGL = PanelText.tr("gas.ngl", "NGL", "ШФЛУ"),
            SALES = PanelText.tr("gas.sales", "Gas out", "Газ"),
            MEOH = PanelText.tr("gas.meoh", "MeOH", "Метанол"),
            COMPRESSOR = PanelText.tr("gas.compressor", "Compr.", "Компр."),
            REGEN = PanelText.tr("gas.regen", "TEG", "ТЭГ"),
            RISK = PanelText.tr("gas.risk", "Hydrate", "Гидраты"),
            BLOCKED = PanelText.tr("gas.blocked", "Blocked", "Пробка"),
            INHIBIT = PanelText.tr("gas.inhibit", "Inhibit", "Ингибитор"),
            P_DOWN = PanelText.tr("gas.p_down", "P −", "P −"),
            P_UP = PanelText.tr("gas.p_up", "P +", "P +"),
            T_DOWN = PanelText.tr("gas.t_down", "Chill −", "Холод −"),
            T_UP = PanelText.tr("gas.t_up", "Chill +", "Холод +"),
            MEOH_BUTTON = PanelText.tr("gas.meoh_button", "Methanol", "Метанол"),
            MEOH_TIP = PanelText.tr("gas.meoh_tip", "Inject methanol before the chiller to keep 3 K below the hydrate temperature",
                    "Впрыск метанола перед холодильником: держать на 3 K ниже температуры гидратов"),
            TEMPS = PanelText.tr("gas.temps", "Chiller %s °C · LTS %s °C · dew point %s °C · hydrates %s °C",
                    "Холодильник %s °C · НТС %s °C · точка росы %s °C · гидраты %s °C"),
            GLYCOL = PanelText.tr("gas.glycol", "Lean TEG %s %% · NGL %s l/h · methanol in water %s %%",
                    "Тощий ТЭГ %s %% · ШФЛУ %s л/ч · метанол в воде %s %%"),
            PROPANE = PanelText.tr("gas.propane", "Propane", "Пропан"),
            BUTANE = PanelText.tr("gas.butane", "Butane", "Бутан"),
            GASOLINE = PanelText.tr("gas.gasoline", "Gasoline", "Бензин"),
            R_DOWN = PanelText.tr("gas.r_down", "Reflux −", "Флегма −"),
            R_UP = PanelText.tr("gas.r_up", "Reflux +", "Флегма +"),
            GFU = PanelText.tr("gas.gfu", "Feed %s l/h · reflux %s · 15 trays in each of three columns",
                    "Сырьё %s л/ч · флегмовое число %s · по 15 тарелок в трёх колоннах"),
            SPEED = PanelText.tr("cc.speed", "Speed", "Обороты"),
            DISCHARGE = PanelText.tr("cc.discharge", "Discharge", "Нагнет."),
            BAR = PanelText.tr("unit.bar", "bar", "бар"),
            M3S = PanelText.tr("unit.m3s", "m³/s", "м³/с"),
            LINE = PanelText.tr("cc.line", "To line", "В линию"),
            SURGE_MARGIN = PanelText.tr("cc.ss", "S_s", "S_s"),
            SS_TIP = PanelText.tr("cc.ss_tip", "Surge ratio S_s = q²_surge(Rc)/q²_r: 1 — on the surge line, the controller keeps it below 1/(1 + b_ctrl)",
                    "Запас S_s = q²_surge(Rc)/q²_r: 1 — на линии помпажа, регулятор держит его ниже 1/(1 + b_ctrl)"),
            VIBRATION = PanelText.tr("cc.vibration", "Vibration", "Вибрация"),
            MMS = PanelText.tr("unit.mms", "mm/s", "мм/с"),
            SURGE = PanelText.tr("cc.surge", "Surge", "Помпаж"),
            AUTO = PanelText.tr("cc.auto", "Anti-surge", "Антипомпаж"),
            ONLINE = PanelText.tr("cc.online", "On line", "В линии"),
            LINE_DOWN = PanelText.tr("cc.line_down", "Line −", "Линия −"),
            LINE_UP = PanelText.tr("cc.line_up", "Line +", "Линия +"),
            MODE = PanelText.tr("cc.mode", "Auto/Man", "Авт/Руч"),
            MODE_TIP = PanelText.tr("cc.mode_tip", "Anti-surge controller PC_ANTISURGE: automatic, or the recycle valve by hand",
                    "Антипомпажный регулятор PC_ANTISURGE: автомат или рецикл вручную"),
            REC_DOWN = PanelText.tr("cc.rec_down", "Recycle −", "Рецикл −"),
            REC_UP = PanelText.tr("cc.rec_up", "Recycle +", "Рецикл +"),
            MARGIN_DOWN = PanelText.tr("cc.margin_down", "Margin −", "Запас −"),
            MARGIN_UP = PanelText.tr("cc.margin_up", "Margin +", "Запас +"),
            RATE_DOWN = PanelText.tr("cc.rate_down", "Close −", "Закрытие −"),
            RATE_UP = PanelText.tr("cc.rate_up", "Close +", "Закрытие +"),
            CC_STATE = PanelText.tr("cc.state", "Recycle %s %% · flow %s m³/s · surge cycles %s · damage %s %% · shaft %s kW",
                    "Рецикл %s %% · расход %s м³/с · циклов помпажа %s · повреждение %s %% · вал %s кВт"),
            CC_TUNING = PanelText.tr("cc.tuning", "b_ctrl %s · closing %s %%/s · line valve %s %% · manual recycle %s %%",
                    "b_ctrl %s · закрытие %s %%/с · линейный клапан %s %% · рецикл вручную %s %%");

    public static final PanelLayout PLANT = PanelLayout.builder("low_temperature_separator", 300, 176, L_CHANNELS)
            .dial(8, 22, false, L_P_X100, 100, 7, Double.NaN, 1, MPA.get(), LTS_P.get(), v -> LTS_P_TIP.get(v.num(L_P_X100, 100, 2)))
            .dial(64, 22, false, L_GAS_M3H, 1, 3600, Double.NaN, 0, M3H.get(), GAS.get(), null)
            .dial(120, 22, false, L_POWER_KW, 1, 400, 380, 0, KW.get(), POWER.get(), null)
            .glass(178, 22, 64, L_PLUG_PERMIL, 0xFFDDEEFF, Double.NaN, Double.NaN, PLUG.get(), v -> PLUG_TIP.get(v.num(L_PLUG_PERMIL, 10, 0)))
            .glass(206, 22, 64, L_NGL_PERMIL, 0xFFD8C890, Double.NaN, Double.NaN, NGL.get(), null)
            .glass(234, 22, 64, L_SALES_PERMIL, 0xFFB0B8C0, Double.NaN, Double.NaN, SALES.get(), null)
            .glass(262, 22, 64, L_MEOH_PERMIL, 0xFFC0D0E0, Double.NaN, Double.NaN, MEOH.get(), null)
            .lamp(8, 100, L_FLAGS, LF_COMPRESSOR, LampColor.GREEN, COMPRESSOR.get())
            .lamp(66, 100, L_FLAGS, LF_REGENERATOR, LampColor.GREEN, REGEN.get())
            .lamp(124, 100, L_FLAGS, LF_RISK, LampColor.AMBER, RISK.get())
            .lamp(182, 100, L_FLAGS, LF_PLUG, LampColor.RED, BLOCKED.get())
            .lamp(240, 100, L_FLAGS, LF_METHANOL, LampColor.GREEN, INHIBIT.get())
            .button(8, 116, 40, LB_P_DOWN, P_DOWN.get(), null)
            .button(52, 116, 40, LB_P_UP, P_UP.get(), null)
            .button(100, 116, 52, LB_CHILL_DOWN, T_DOWN.get(), null)
            .button(156, 116, 52, LB_CHILL_UP, T_UP.get(), null)
            .button(216, 116, 76, LB_METHANOL, MEOH_BUTTON.get(), MEOH_TIP.get())
            .readout(8, 140, 284, v -> TEMPS.get(v.num(L_CHILL_X10, 10, 1), v.num(L_TLTS_X10, 10, 1), v.num(L_DEW_X10, 10, 1),
                    v.num(L_HYD_X10, 10, 1)), null)
            .readout(8, 156, 284, v -> GLYCOL.get(v.num(L_TEG_X10, 10, 1), v.raw(L_NGL_LH), v.num(L_MEOH_WT_X10, 10, 1)), null)
            .noInventory().build();

    public static final PanelLayout FRACTIONATOR = PanelLayout.builder("ngl_fractionator", 240, 130, G_CHANNELS)
            .dial(8, 22, false, G_C3_X10, 10, 100, Double.NaN, 1, PERCENT.get(), PROPANE.get(), null)
            .dial(64, 22, false, G_C4_X10, 10, 100, Double.NaN, 1, PERCENT.get(), BUTANE.get(), null)
            .dial(120, 22, false, G_C5_X10, 10, 100, Double.NaN, 1, PERCENT.get(), GASOLINE.get(), null)
            .glass(176, 22, 64, G_TANK0, 0xFFE0E8F0, Double.NaN, Double.NaN, PROPANE.get(), null)
            .glass(196, 22, 64, G_TANK0 + 1, 0xFFD0D8E0, Double.NaN, Double.NaN, BUTANE.get(), null)
            .glass(216, 22, 64, G_TANK0 + 2, 0xFFE0D090, Double.NaN, Double.NaN, GASOLINE.get(), null)
            .button(8, 92, 56, GB_R_DOWN, R_DOWN.get(), null)
            .button(68, 92, 56, GB_R_UP, R_UP.get(), null)
            .readout(8, 112, 224, v -> GFU.get(v.raw(G_FEED_LH), v.num(G_REFLUX_X10, 10, 1)), null)
            .noInventory().build();

    public static final PanelLayout CENTRIFUGAL = PanelLayout.builder("centrifugal_compressor", 300, 176, C_CHANNELS)
            .dial(8, 22, false, C_SPEED_PCT, 1, 110, 105, 0, PERCENT.get(), SPEED.get(), null)
            .dial(64, 22, false, C_PD_X100, 100, 12, 11, 1, BAR.get(), DISCHARGE.get(), null)
            .dial(120, 22, false, C_LINE_X1000, 1000, 1.4, Double.NaN, 2, M3S.get(), LINE.get(), null)
            .dial(176, 22, false, C_SS_X1000, 1000, 1.2, 1.0, 2, Component.literal(""), SURGE_MARGIN.get(), v -> SS_TIP.get())
            .dial(232, 22, false, C_VIB_X10, 10, 20, 11.2, 1, MMS.get(), VIBRATION.get(), null)
            .lamp(8, 96, C_FLAGS, CF_RUNNING, LampColor.GREEN, PanelText.RUNNING.get())
            .lamp(80, 96, C_FLAGS, CF_ONLINE, LampColor.GREEN, ONLINE.get())
            .lamp(152, 96, C_FLAGS, CF_AUTO, LampColor.GREEN, AUTO.get())
            .lamp(224, 96, C_FLAGS, CF_SURGE, LampColor.RED, SURGE.get())
            .button(8, 112, 52, CB_LINE_DOWN, LINE_DOWN.get(), null)
            .button(64, 112, 52, CB_LINE_UP, LINE_UP.get(), null)
            .button(124, 112, 52, CB_MODE, MODE.get(), MODE_TIP.get())
            .button(184, 112, 52, CB_REC_DOWN, REC_DOWN.get(), null)
            .button(240, 112, 52, CB_REC_UP, REC_UP.get(), null)
            .button(8, 130, 66, CB_MARGIN_DOWN, MARGIN_DOWN.get(), null)
            .button(78, 130, 66, CB_MARGIN_UP, MARGIN_UP.get(), null)
            .button(156, 130, 66, CB_RATE_DOWN, RATE_DOWN.get(), null)
            .button(226, 130, 66, CB_RATE_UP, RATE_UP.get(), null)
            .readout(8, 150, 284, v -> CC_STATE.get(v.raw(C_RECYCLE_PCT), v.num(C_FLOW_X1000, 1000, 2), v.raw(C_CYCLES),
                    v.num(C_DAMAGE_PERMIL, 10, 1), v.raw(C_POWER_KW)), null)
            .readout(8, 162, 284, v -> CC_TUNING.get(v.num(C_BCTRL_X1000, 1000, 2), v.num(C_RATE_X1000, 10, 1),
                    v.raw(C_LINE_VALVE_PCT), v.raw(C_XM_PCT)), null)
            .noInventory().build();

    private GasPanels() {
    }

    public static List<PanelLayout> all() {
        return List.of(PLANT, FRACTIONATOR, CENTRIFUGAL);
    }
}
