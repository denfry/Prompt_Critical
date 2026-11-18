package dev.denfry.promptcritical.mod.gas;

import static dev.denfry.promptcritical.mod.panel.PanelText.MPA;
import static dev.denfry.promptcritical.mod.panel.PanelText.PERCENT;

import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.panel.PanelLayout.LampColor;
import dev.denfry.promptcritical.mod.panel.PanelText;
import dev.denfry.promptcritical.mod.panel.PanelText.Tr;
import java.util.List;

/** Местные щиты газопереработки: установка НТС с осушкой и холодильником, газофракционирующая установка. */
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
                    "Сырьё %s л/ч · флегмовое число %s · по 15 тарелок в трёх колоннах");

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

    private GasPanels() {
    }

    public static List<PanelLayout> all() {
        return List.of(PLANT, FRACTIONATOR);
    }
}
