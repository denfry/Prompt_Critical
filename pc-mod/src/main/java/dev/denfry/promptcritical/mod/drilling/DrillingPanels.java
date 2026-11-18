package dev.denfry.promptcritical.mod.drilling;

import static dev.denfry.promptcritical.mod.panel.PanelText.MPA;
import static dev.denfry.promptcritical.mod.panel.PanelText.RPM;
import static dev.denfry.promptcritical.mod.panel.PanelText.RUNNING;
import static dev.denfry.promptcritical.mod.panel.PanelText.SHAFT;

import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.panel.PanelLayout.LampColor;
import dev.denfry.promptcritical.mod.panel.PanelText;
import dev.denfry.promptcritical.mod.panel.PanelText.Tr;
import dev.denfry.promptcritical.sim.drilling.DrillersMethod;
import dev.denfry.promptcritical.sim.drilling.Well;
import java.util.List;

/**
 * Местные щиты буровой: пульт бурильщика (индикатор веса, нагрузка на долото, манометры стояка и затрубья,
 * уровнемер ёмкостей, сигнализация притока) и буровой насос.
 */
public final class DrillingPanels {
    // пульт бурильщика
    public static final int C_HOOK_T10 = 0, C_WOB_T10 = 1, C_SPP_X100 = 2, C_CSG_X100 = 3, C_PIT_PERMIL = 4, C_GAIN_L = 5,
            C_DEPTH_X10 = 6, C_ROP_X10 = 7, C_SPM = 8, C_RPM = 9, C_ECD = 10, C_PIT_RHO = 11, C_FLAGS = 12, C_STAGE = 13,
            C_KMW = 14, C_MAASP_X100 = 15, C_WOB_SET_T10 = 16, C_SPM_SET = 17, C_CHOKE_PERMIL = 18, C_FLOW_LS10 = 19,
            C_CHANNELS = 20;
    public static final int F_RIG = 0, F_KICK = 1, F_BOP = 2, F_GAS = 3, F_BLOWOUT = 4, F_KILL = 5, F_SHOE = 6;
    public static final int B_WOB_DOWN = 0, B_WOB_UP = 1, B_SPM_DOWN = 2, B_SPM_UP = 3, B_BOP = 4, B_KILL = 5, B_ZERO = 6;

    // буровой насос
    public static final int P_SHAFT_RPM10 = 0, P_SPM = 1, P_SPP_X100 = 2, P_FLAGS = 3, P_KW = 4, P_CHANNELS = 5;
    public static final int PF_RUNNING = 0;

    private static final Tr TONNES = PanelText.tr("unit.tonnes", "t", "т"),
            SPM = PanelText.tr("unit.spm", "spm", "ход/мин"),
            HOOK = PanelText.tr("drill.hook", "Hook load", "Вес на крюке"),
            HOOK_TIP = PanelText.tr("drill.hook_tip", "Weight indicator: string in mud plus the travelling block, minus weight on bit",
                    "Индикатор веса: колонна в растворе и талевый блок минус нагрузка на долото"),
            WOB = PanelText.tr("drill.wob", "Weight on bit", "Нагрузка"),
            WOB_TIP = PanelText.tr("drill.wob_tip", "Weight on bit %s t (set %s t, at most 85 %% of the collars in mud)",
                    "Нагрузка на долото %s т (уставка %s т, не больше 85 %% веса УБТ в растворе)"),
            SPP = PanelText.tr("drill.spp", "Standpipe", "Стояк"),
            SPP_TIP = PanelText.tr("drill.spp_tip", "Drill pipe pressure %s MPa; shut in it reads SIDPP",
                    "Давление в трубах %s МПа; на закрытой скважине — избыточное (ИДТ)"),
            CASING = PanelText.tr("drill.casing", "Casing", "Затрубье"),
            CASING_TIP = PanelText.tr("drill.casing_tip", "Casing pressure at the choke %s MPa · MAASP %s MPa · red: preventor rating",
                    "Давление в затрубье у штуцера %s МПа · МДДК %s МПа · красная зона — предел превентора"),
            PITS = PanelText.tr("drill.pits", "Pits", "Ёмкости"),
            PITS_TIP = PanelText.tr("drill.pits_tip", "Active pits %s %% · gain %s m³ (alarm at 1.6 m³)",
                    "Рабочие ёмкости %s %% · прирост %s м³ (сигнал при 1,6 м³)"),
            RIG = PanelText.tr("drill.rig", "Rig", "Буровая"),
            KICK = PanelText.tr("drill.kick", "Kick", "Приток"),
            BOP = PanelText.tr("drill.bop", "BOP shut", "ПВО закрыт"),
            GAS = PanelText.tr("drill.gas", "Gas", "Газ"),
            BLOWOUT = PanelText.tr("drill.blowout", "Blowout", "Фонтан"),
            KILL = PanelText.tr("drill.kill", "Kill", "Глушение"),
            SHOE = PanelText.tr("drill.shoe", "Losses", "Поглощ."),
            WOB_DOWN = PanelText.tr("drill.wob_down", "WOB −", "Нагр. −"),
            WOB_UP = PanelText.tr("drill.wob_up", "WOB +", "Нагр. +"),
            SPM_DOWN = PanelText.tr("drill.spm_down", "SPM −", "Ходы −"),
            SPM_UP = PanelText.tr("drill.spm_up", "SPM +", "Ходы +"),
            BOP_BUTTON = PanelText.tr("drill.bop_button", "BOP", "ПВО"),
            BOP_BUTTON_TIP = PanelText.tr("drill.bop_tip", "Close or open the annular preventer; close it at the first sign of a kick",
                    "Закрыть или открыть кольцевой превентор; при первом признаке притока — закрыть"),
            KILL_BUTTON = PanelText.tr("drill.kill_button", "Kill", "Глушить"),
            KILL_TIP = PanelText.tr("drill.kill_tip",
                    "Driller's method: on a shut-in well with pumps stopped record SIDPP/SICP and start the first circulation; "
                            + "after weighting up start the second", "Метод бурильщика: на закрытой скважине при стоящих насосах записать ИДТ и ИДК и начать первый цикл; после утяжеления — второй"),
            ZERO = PanelText.tr("drill.zero", "Zero", "Ноль"),
            ZERO_TIP = PanelText.tr("drill.zero_tip", "Reset the pit gain totalizer", "Обнулить счётчик прироста в ёмкостях"),
            DRILLING = PanelText.tr("drill.drilling", "Depth %s m · %s m/h · ECD %s g/cm³ · flow out %s l/s",
                    "Забой %s м · %s м/ч · ЭЦП %s г/см³ · выход %s л/с"),
            PUMPS = PanelText.tr("drill.pumps", "Pumps %s spm (set %s) · rotary %s rpm · mud %s g/cm³ · choke %s %%",
                    "Насосы %s ход/мин (уставка %s) · ротор %s об/мин · раствор %s г/см³ · штуцер %s %%"),
            STAGE = PanelText.tr("drill.stage", "Kill: %s · kill mud %s g/cm³", "Глушение: %s · раствор глушения %s г/см³"),
            PUMP_SPM = PanelText.tr("pump.spm", "Strokes", "Ходы"),
            DISCHARGE = PanelText.tr("drill.discharge", "Discharge", "Нагнетание"),
            PUMP_STATUS = PanelText.tr("drill.pump_status", "Triplex 6½″ × 12″ · %s l/s · shaft power %s kW",
                    "Триплекс 6½″ × 12″ · %s л/с · мощность на валу %s кВт");
    private static final Tr[] STAGES = {PanelText.tr("drill.stage.idle", "—", "—"),
            PanelText.tr("drill.stage.first", "1st circulation, hold ICP", "1-й цикл, держать Pнач"),
            PanelText.tr("drill.stage.weight", "weight up the pits", "утяжелить раствор"),
            PanelText.tr("drill.stage.second", "2nd circulation, ICP→FCP", "2-й цикл, Pнач→Pкон"),
            PanelText.tr("drill.stage.done", "killed: bleed, open, flow check", "заглушена: стравить, открыть, проверить перелив")};

    public static final PanelLayout CONSOLE = PanelLayout.builder("drillers_console", 320, 196, C_CHANNELS)
            .dial(8, 20, true, C_HOOK_T10, 10, 200, Double.NaN, 1, TONNES.get(), HOOK.get(), v -> HOOK_TIP.get())
            .dial(80, 28, false, C_WOB_T10, 10, 25, Double.NaN, 1, TONNES.get(), WOB.get(),
                    v -> WOB_TIP.get(v.num(C_WOB_T10, 10, 1), v.num(C_WOB_SET_T10, 10, 1)))
            .dial(136, 20, true, C_SPP_X100, 100, 35, Well.BOP_RATED_PA / 1e6, 2, MPA.get(), SPP.get(),
                    v -> SPP_TIP.get(v.num(C_SPP_X100, 100, 2)))
            .dial(208, 20, true, C_CSG_X100, 100, 35, Well.BOP_RATED_PA / 1e6, 2, MPA.get(), CASING.get(),
                    v -> CASING_TIP.get(v.num(C_CSG_X100, 100, 2), v.num(C_MAASP_X100, 100, 2)))
            .glass(284, 20, 74, C_PIT_PERMIL, 0xFF6B5A44, Double.NaN, 0.8, PITS.get(),
                    v -> PITS_TIP.get(v.num(C_PIT_PERMIL, 10, 0), PanelText.num(v.raw(C_GAIN_L) / 1000.0, 2)))
            .lamp(8, 100, C_FLAGS, F_RIG, LampColor.GREEN, RIG.get())
            .lamp(70, 100, C_FLAGS, F_KICK, LampColor.RED, KICK.get())
            .lamp(132, 100, C_FLAGS, F_BOP, LampColor.AMBER, BOP.get())
            .lamp(194, 100, C_FLAGS, F_GAS, LampColor.AMBER, GAS.get())
            .lamp(256, 100, C_FLAGS, F_BLOWOUT, LampColor.RED, BLOWOUT.get())
            .lamp(8, 114, C_FLAGS, F_KILL, LampColor.GREEN, KILL.get())
            .lamp(70, 114, C_FLAGS, F_SHOE, LampColor.RED, SHOE.get())
            .button(8, 130, 38, B_WOB_DOWN, WOB_DOWN.get(), null)
            .button(50, 130, 38, B_WOB_UP, WOB_UP.get(), null)
            .button(92, 130, 38, B_SPM_DOWN, SPM_DOWN.get(), null)
            .button(134, 130, 38, B_SPM_UP, SPM_UP.get(), null)
            .button(180, 130, 40, B_BOP, BOP_BUTTON.get(), BOP_BUTTON_TIP.get())
            .button(224, 130, 44, B_KILL, KILL_BUTTON.get(), KILL_TIP.get())
            .button(272, 130, 40, B_ZERO, ZERO.get(), ZERO_TIP.get())
            .readout(8, 152, 304, v -> DRILLING.get(v.num(C_DEPTH_X10, 10, 1), v.num(C_ROP_X10, 10, 1),
                    PanelText.num(v.raw(C_ECD) / 1000.0, 3), v.num(C_FLOW_LS10, 10, 1)), null)
            .readout(8, 166, 304, v -> PUMPS.get(v.raw(C_SPM), v.raw(C_SPM_SET), v.raw(C_RPM),
                    PanelText.num(v.raw(C_PIT_RHO) / 1000.0, 3), v.num(C_CHOKE_PERMIL, 10, 0)), null)
            .readout(8, 180, 304, v -> STAGE.get(STAGES[Math.clamp(v.raw(C_STAGE), 0, STAGES.length - 1)].get(),
                    PanelText.num(v.raw(C_KMW) / 1000.0, 3)), null)
            .noInventory().build();

    public static final PanelLayout PUMP = PanelLayout.builder("mud_pump", 232, 100, P_CHANNELS)
            .dial(8, 24, false, P_SHAFT_RPM10, 10, 256, Double.NaN, 0, RPM.get(), SHAFT.get(), null)
            .dial(64, 24, false, P_SPM, 1, MudPumpBlockEntity.MAX_SPM, Double.NaN, 0, SPM.get(), PUMP_SPM.get(), null)
            .dial(120, 24, false, P_SPP_X100, 100, 35, Well.BOP_RATED_PA / 1e6, 1, MPA.get(), DISCHARGE.get(), null)
            .lamp(178, 26, P_FLAGS, PF_RUNNING, LampColor.GREEN, RUNNING.get())
            .readout(8, 84, 216, v -> PUMP_STATUS.get(PanelText.num(v.raw(P_SPM) * MudPumpBlockEntity.M3_PER_STROKE / 60 * 1000, 1),
                    v.raw(P_KW)), null)
            .noInventory().build();

    private DrillingPanels() {
    }

    /** Номер стадии глушения для табло. */
    public static int stage(DrillersMethod.Stage s) {
        return s.ordinal();
    }

    public static List<PanelLayout> all() {
        return List.of(CONSOLE, PUMP);
    }
}
