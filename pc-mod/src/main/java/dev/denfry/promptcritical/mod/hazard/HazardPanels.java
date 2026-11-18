package dev.denfry.promptcritical.mod.hazard;

import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.panel.PanelLayout.LampColor;
import dev.denfry.promptcritical.mod.panel.PanelText;
import dev.denfry.promptcritical.mod.panel.PanelText.Tr;
import java.util.List;

/** Щиты A0 нефтегаза: ёмкость СУГ (09 §9.8.4, §9.11.6), камеры скребка и СКЗ (§9.8.2–9.8.3), уровнемер резервуара (§9.8.4). */
public final class HazardPanels {
    // ёмкость СУГ
    public static final int WALL_C = 0, PRESSURE = 1, FILL = 2, FIRE = 3, FLAGS = 4, CHANNELS = 5;
    public static final int F_RELIEF = 0, F_DELUGE = 1, F_FIRE = 2, F_HOT = 3;

    private static final Tr WALL = PanelText.tr("hazard.wall", "Wall", "Стенка"),
            BAR = PanelText.tr("hazard.bar", "bar", "бар"), PRESSURE_L = PanelText.tr("hazard.pressure", "Pressure", "Давление"),
            KW = PanelText.tr("hazard.kw", "kW/m²", "кВт/м²"), FIRE_L = PanelText.tr("hazard.fire", "Fire flux", "Поток пожара"),
            PROPANE = PanelText.tr("hazard.propane", "Propane", "Пропан"),
            RELIEF = PanelText.tr("hazard.relief", "Relief valve lifting", "Сброс через ПК"),
            DELUGE = PanelText.tr("hazard.deluge", "Deluge cooling", "Орошение"),
            ON_FIRE = PanelText.tr("hazard.on_fire", "Exposed to fire", "Под огнём"),
            HOT = PanelText.tr("hazard.hot", "Wall above 300 °C: BLEVE risk", "Стенка выше 300 °C: опасность BLEVE"),
            BULLET_STATUS = PanelText.tr("hazard.bullet_status", "Propane %s t · liquid %s °C · fails at %s bar",
                    "Пропан %s т · жидкость %s °C · разрушение при %s бар");
    /** Ёмкость СУГ: дополнительные каналы для строки состояния. */
    public static final int MASS_T10 = 5, LIQUID_C = 6, FAIL_BAR = 7, BULLET_CHANNELS = 8;

    public static final PanelLayout BULLET = PanelLayout.builder("gas_storage_bullet", 232, 140, BULLET_CHANNELS)
            .dial(8, 24, false, WALL_C, 1, 800, 300, 0, PanelText.CELSIUS.get(), WALL.get(), null)
            .dial(64, 24, false, PRESSURE, 10, 30, 18, 1, BAR.get(), PRESSURE_L.get(), null)
            .dial(120, 24, false, FIRE, 1, 300, 37.5, 0, KW.get(), FIRE_L.get(), null)
            .glass(176, 24, 56, FILL, 0xFFE8E0C0, Double.NaN, 0.85, PROPANE.get(), null)
            .lamp(200, 22, FLAGS, F_RELIEF, LampColor.AMBER, RELIEF.get()).lamp(200, 36, FLAGS, F_DELUGE, LampColor.GREEN, DELUGE.get())
            .lamp(200, 50, FLAGS, F_FIRE, LampColor.RED, ON_FIRE.get()).lamp(200, 64, FLAGS, F_HOT, LampColor.RED, HOT.get())
            .readout(8, 118, 216, v -> BULLET_STATUS.get(v.num(MASS_T10, 10, 1), v.raw(LIQUID_C), v.num(FAIL_BAR, 10, 1)), null).build();

    // уровнемер резервуара
    public static final int T_LEVEL = 0, T_INFLOW = 1, T_LOSS = 2, T_SPILLED = 3, T_FLAGS = 4, T_CHANNELS = 5;
    public static final int TF_HIGH = 0, TF_HH = 1, TF_OVERFLOW = 2, TF_ROOF = 3;
    private static final Tr LEVEL = PanelText.tr("hazard.level", "Level", "Уровень"), LS = PanelText.tr("hazard.ls", "L/s", "л/с"),
            INFLOW = PanelText.tr("hazard.inflow", "Inflow", "Налив"), KGH = PanelText.tr("hazard.kgh", "kg/h", "кг/ч"),
            LOSS = PanelText.tr("hazard.loss", "Breathing", "Дыхания"),
            HIGH = PanelText.tr("hazard.high", "High level 90 %", "Верхний уровень 90 %"),
            HH = PanelText.tr("hazard.hh", "High-high 95 %: redstone trip", "Аварийный 95 %: сигнал ПАЗ"),
            OVERFLOW = PanelText.tr("hazard.overflow", "OVERFLOW", "ПЕРЕЛИВ"),
            ROOF = PanelText.tr("hazard.roof", "Floating roof", "Плавающая крыша"),
            TANK_STATUS = PanelText.tr("hazard.tank_status", "Spilt over the brim: %s L", "Перелито через край: %s л");

    public static final PanelLayout TANK_GAUGE = PanelLayout.builder("tank_gauge", 232, 140, T_CHANNELS)
            .glass(8, 24, 56, T_LEVEL, 0xFF5A4A30, Double.NaN, 0.9, LEVEL.get(), null)
            .dial(40, 24, false, T_INFLOW, 10, 100, Double.NaN, 1, LS.get(), INFLOW.get(), null)
            .dial(96, 24, false, T_LOSS, 10, 50, Double.NaN, 1, KGH.get(), LOSS.get(), null)
            .lamp(176, 22, T_FLAGS, TF_HIGH, LampColor.AMBER, HIGH.get()).lamp(176, 36, T_FLAGS, TF_HH, LampColor.RED, HH.get())
            .lamp(176, 50, T_FLAGS, TF_OVERFLOW, LampColor.RED, OVERFLOW.get()).lamp(176, 64, T_FLAGS, TF_ROOF, LampColor.GREEN, ROOF.get())
            .readout(8, 118, 216, v -> TANK_STATUS.get(v.raw(T_SPILLED)), null).build();

    // камеры скребка
    public static final int P_LENGTH = 0, P_DEPOSIT = 1, P_POS = 2, P_POTENTIAL = 3, P_DEFECTS = 4, P_DEEPEST = 5, P_BURST = 6, P_FLAGS = 7,
            P_CHANNELS = 8;
    public static final int PF_DOOR = 0, PF_VENTED = 1, PF_RUNNING = 2, PF_STUCK = 3, PF_PROTECTED = 4, PF_OVER = 5, PF_RECEIVER = 6, PF_PIG = 7;
    public static final int B_VENT = 0, B_DOOR = 1, B_LAUNCH = 2, B_CUT = 3;
    private static final Tr DEPOSIT = PanelText.tr("hazard.deposit", "Wax", "Парафин"), PCT = PanelText.tr("hazard.pct", "%", "%"),
            PIG_POS = PanelText.tr("hazard.pig_pos", "Pig", "Скребок"), MV = PanelText.tr("hazard.mv", "−mV", "−мВ"),
            POTENTIAL = PanelText.tr("hazard.potential", "Off potential", "Потенциал выкл"),
            DOOR = PanelText.tr("hazard.door", "Door open", "Дверца открыта"), VENTED = PanelText.tr("hazard.vented", "Vented", "Стравлено"),
            RUNNING = PanelText.tr("hazard.running", "Pig in the line", "Скребок в трубе"),
            STUCK = PanelText.tr("hazard.stuck", "Pig stuck", "Скребок застрял"),
            PROTECTED = PanelText.tr("hazard.protected", "Protected (≤ −0.85 V)", "Защищена (≤ −0,85 В)"),
            OVER = PanelText.tr("hazard.over", "Overprotected (< −1.2 V)", "Перезащита (< −1,2 В)"),
            RECEIVER = PanelText.tr("hazard.receiver", "Receiver found", "Камера приёма найдена"),
            PIG = PanelText.tr("hazard.pig", "Pig in a trap", "Скребок в камере"),
            VENT_B = PanelText.tr("hazard.vent_b", "Vent", "Сброс"), DOOR_B = PanelText.tr("hazard.door_b", "Door", "Дверца"),
            LAUNCH_B = PanelText.tr("hazard.launch_b", "Launch", "Пуск"), CUT_B = PanelText.tr("hazard.cut_b", "Cut out", "Вырезка"),
            VENT_TIP = PanelText.tr("hazard.vent_tip", "Vent or pressurise the trap; the door opens only when vented",
                    "Стравить или поднять давление; дверца открывается только стравленной"),
            CUT_TIP = PanelText.tr("hazard.cut_tip", "Vented section only: cut out the stuck pig", "Только стравленный участок: вырезать застрявший скребок"),
            PIPE_STATUS = PanelText.tr("hazard.pipe_status", "Section %s m · ILI: %s defects, deepest %s %% · burst %s bar",
                    "Участок %s м · ВТД: дефектов %s, глубочайший %s %% · разрушение %s бар");

    public static final PanelLayout PIG_TRAP = PanelLayout.builder("pig_trap", 232, 140, P_CHANNELS)
            .dial(8, 24, false, P_DEPOSIT, 10, 30, 20, 0, PCT.get(), DEPOSIT.get(), null)
            .dial(64, 24, false, P_POS, 10, 100, Double.NaN, 0, PCT.get(), PIG_POS.get(), null)
            .dial(120, 24, false, P_POTENTIAL, 1, 1500, 1200, 0, MV.get(), POTENTIAL.get(), null)
            .lamp(176, 18, P_FLAGS, PF_DOOR, LampColor.AMBER, DOOR.get()).lamp(176, 28, P_FLAGS, PF_VENTED, LampColor.GREEN, VENTED.get())
            .lamp(176, 38, P_FLAGS, PF_RUNNING, LampColor.GREEN, RUNNING.get()).lamp(176, 48, P_FLAGS, PF_STUCK, LampColor.RED, STUCK.get())
            .lamp(176, 58, P_FLAGS, PF_PROTECTED, LampColor.GREEN, PROTECTED.get()).lamp(176, 68, P_FLAGS, PF_OVER, LampColor.RED, OVER.get())
            .lamp(176, 78, P_FLAGS, PF_RECEIVER, LampColor.GREEN, RECEIVER.get()).lamp(176, 88, P_FLAGS, PF_PIG, LampColor.AMBER, PIG.get())
            .button(8, 92, 40, B_VENT, VENT_B.get(), VENT_TIP.get()).button(52, 92, 40, B_DOOR, DOOR_B.get(), null)
            .button(96, 92, 36, B_LAUNCH, LAUNCH_B.get(), null).button(136, 92, 36, B_CUT, CUT_B.get(), CUT_TIP.get())
            .readout(8, 118, 216, v -> PIPE_STATUS.get(v.raw(P_LENGTH), v.raw(P_DEFECTS), v.raw(P_DEEPEST), v.num(P_BURST, 10, 1)), null).build();

    // станция катодной защиты
    public static final int C_SET = 0, C_POTENTIAL = 1, C_SECTIONS = 2, C_FLAGS = 3, C_CHANNELS = 4;
    public static final int B_DOWN = 0, B_UP = 1;
    private static final Tr AMPS = PanelText.tr("hazard.amps", "A", "А"), SET = PanelText.tr("hazard.set", "Current", "Ток"),
            MEAN_POT = PanelText.tr("hazard.mean_pot", "Mean off potential", "Средний потенциал выкл"),
            POWERED = PanelText.tr("hazard.powered", "Powered", "Питание"),
            CP_STATUS = PanelText.tr("hazard.cp_status", "Sections in reach: %s", "Участков в зоне: %s");

    public static final PanelLayout CP_RECTIFIER = PanelLayout.builder("cp_rectifier", 232, 140, C_CHANNELS)
            .dial(8, 24, false, C_SET, 1000, 10, Double.NaN, 1, AMPS.get(), SET.get(), null)
            .dial(64, 24, false, C_POTENTIAL, 1, 1500, 1200, 0, MV.get(), MEAN_POT.get(), null)
            .lamp(176, 22, C_FLAGS, 0, LampColor.GREEN, POWERED.get())
            .button(8, 92, 20, B_DOWN, net.minecraft.network.chat.Component.literal("−"), null)
            .button(30, 92, 20, B_UP, net.minecraft.network.chat.Component.literal("+"), null)
            .readout(8, 118, 216, v -> CP_STATUS.get(v.raw(C_SECTIONS)), null).build();

    private HazardPanels() {
    }

    public static List<PanelLayout> all() {
        return List.of(BULLET, TANK_GAUGE, PIG_TRAP, CP_RECTIFIER);
    }
}
