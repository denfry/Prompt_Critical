package dev.denfry.promptcritical.mod.scada;

import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.panel.PanelLayout.LampColor;
import dev.denfry.promptcritical.mod.panel.PanelText;
import dev.denfry.promptcritical.mod.panel.PanelText.Tr;
import dev.denfry.promptcritical.mod.panel.PanelValues;
import dev.denfry.promptcritical.sim.automation.plc.PlcCpu;
import dev.denfry.promptcritical.sim.automation.twin.LoopMetrics;
import dev.denfry.promptcritical.sim.automation.twin.ScenarioCatalog;
import dev.denfry.promptcritical.sim.automation.twin.ScenarioRun;
import dev.denfry.promptcritical.sim.automation.twin.TwinPlant;
import dev.denfry.promptcritical.sim.automation.twin.TwinPlants;
import dev.denfry.promptcritical.sim.automation.twin.TwinSession;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Щиты цифрового двойника и тренажёра (03d §3d.9.1, 11 §11.3.2): инженерная станция (испытания FAT), шкаф тренажёра,
 * пульт оператора и место инструктора. Каналы у всех общие: установка, итог, время, скорость, ПЛК, отказы, показания
 * приборов (с отказами датчиков) и положения механизмов.
 */
public final class TwinPanels {
    public static final int PLANT = 0, STATUS = 1, TIME = 2, SPEED = 3, PLC = 4, FAULTS = 5, FORCES = 6, SCORE = 7, SIG0 = 8,
            SIGNALS = 6, ACT0 = 14, ACTUATORS = 4, LESSON = 18, IAE = 19, OVERSHOOT = 20, TRAVEL = 21, REMAIN = 22, FLAGS = 23,
            RUNNING_SIMS = 24, CHANNELS = 25;
    /** Нет показания (обрыв линии). */
    public static final int BAD = Integer.MIN_VALUE;
    /** Итог: 0 — нет прогона, 1 — идёт, 2 — пройден, 3 — провал. */
    public static final int IDLE = 0, RUN = 1, PASSED = 2, FAILED = 3;
    /** Флаги: программа прошла FAT и её можно загрузить в ПЛК. */
    public static final int F_DEPLOYABLE = 0;
    // станция
    public static final int W_PLANT = 0, W_FAT = 1, W_SPEED = 2, W_FAULT = 3, W_STOP = 4, W_DEPLOY = 5;
    // инструктор
    public static final int I_LESSON = 0, I_START = 1, I_SPEED = 2, I_FAULT = 3, I_STOP = 4, I_LICENSE = 5;
    // пульт оператора: механизм i — кнопки 2i («−», закрыть) и 2i+1 («+», открыть); снять форсировки
    public static final int C_FORCES = 8;
    /** Ступени скорости двойника, шагов за тик сервера. */
    public static final int[] SPEEDS = {0, 1, 2, 5, 10};

    static final Tr[] STATUS_TEXT = {
            PanelText.tr("twin.status.idle", "no run", "нет прогона"),
            PanelText.tr("twin.status.running", "running", "идёт"),
            PanelText.tr("twin.status.passed", "PASSED", "ПРОЙДЕН"),
            PanelText.tr("twin.status.failed", "FAILED", "ПРОВАЛ")};
    static final Tr[] PLANT_TEXT = {
            PanelText.tr("twin.plant.tank_farm", "Tank farm", "Резервуарный парк"),
            PanelText.tr("twin.plant.boiler_i0", "Boiler I0", "Котёл I0"),
            PanelText.tr("twin.plant.generator_sync", "Generator and grid", "Генератор и сеть")};
    static final Tr[] LESSON_TEXT = {
            PanelText.tr("twin.lesson.tank_overfill", "Tank overfill at night", "Перелив резервуара ночью"),
            PanelText.tr("twin.lesson.forced_bit", "The forced bit", "Забытая форсировка"),
            PanelText.tr("twin.lesson.out_of_phase_sync", "Out-of-phase closing", "Включение в противофазе"),
            PanelText.tr("twin.lesson.boiler_low_water", "Low water in the boiler", "Упуск воды в котле")};
    private static final Tr[] PLC_TEXT = {
            PanelText.tr("twin.plc.none", "no PLC", "без ПЛК"),
            PanelText.tr("twin.plc.off", "OFF", "ВЫКЛ"),
            PanelText.tr("twin.plc.stop", "STOP", "СТОП"),
            PanelText.tr("twin.plc.run", "RUN", "РАБОТА"),
            PanelText.tr("twin.plc.fault", "FAULT", "ОТКАЗ")};
    private static final Tr RUN_LINE = PanelText.tr("twin.run", "%s · %s · %s s · ×%s", "%s · %s · %s с · ×%s"),
            PLC_LINE = PanelText.tr("twin.plc_line", "PLC %s · faults %s · forces %s", "ПЛК %s · отказов %s · форсировок %s"),
            METRICS = PanelText.tr("twin.metrics", "IAE %s · overshoot %s %% · valve travel %s · score %s",
                    "IAE %s · перерегулирование %s %% · ход клапана %s · балл %s"),
            LESSON_LINE = PanelText.tr("twin.lesson_line", "Lesson: %s · %s · %s s left · score %s",
                    "Урок: %s · %s · осталось %s с · балл %s"),
            CABINET = PanelText.tr("twin.cabinet", "%s · %s s · simulators running %s",
                    "%s · %s с · тренажёров в работе %s"),
            BAD_TEXT = PanelText.tr("twin.bad", "BAD", "НЕТ"),
            PLANT_B = PanelText.tr("twin.b.plant", "Plant", "Установка"),
            FAT_B = PanelText.tr("twin.b.fat", "FAT", "FAT"),
            SPEED_B = PanelText.tr("twin.b.speed", "Speed", "Скорость"),
            FAULT_B = PanelText.tr("twin.b.fault", "Fault", "Отказ"),
            STOP_B = PanelText.tr("twin.b.stop", "Stop", "Стоп"),
            DEPLOY_B = PanelText.tr("twin.b.deploy", "To PLC", "В ПЛК"),
            LESSON_B = PanelText.tr("twin.b.lesson", "Lesson", "Урок"),
            START_B = PanelText.tr("twin.b.start", "Start", "Пуск"),
            LICENSE_B = PanelText.tr("twin.b.license", "License", "Допуск"),
            FORCES_B = PanelText.tr("twin.b.forces", "Unforce", "Снять форсы"),
            FORCES_L = PanelText.tr("twin.forces", "Forces", "Форсировки"),
            DEPLOY_L = PanelText.tr("twin.deployable", "FAT passed", "FAT пройден"),
            WS_TIP = PanelText.tr("twin.ws_tip", "FAT runs the program of the nearest PLC (16 blocks) on a copy of the plant; nothing in the world moves",
                    "FAT прогоняет программу ближайшего ПЛК (16 блоков) на копии установки; в мире ничего не движется"),
            CONSOLE_TIP = PanelText.tr("twin.console_tip", "Readings come from the simulator's instruments, faults included",
                    "Показания — с приборов тренажёра, вместе с их отказами");

    public static final PanelLayout WORKSTATION = PanelLayout.builder("simulation_workstation", 248, 150, CHANNELS)
            .readout(8, 22, 232, v -> RUN_LINE.get(plant(v), STATUS_TEXT[clampIdx(v.raw(STATUS), 4)].get(), v.raw(TIME), v.raw(SPEED)),
                    v -> WS_TIP.get())
            .readout(8, 36, 232, v -> signals(v, 0, 3), null)
            .readout(8, 48, 232, v -> signals(v, 3, 6), null)
            .readout(8, 60, 232, v -> actuators(v), null)
            .readout(8, 72, 232, v -> PLC_LINE.get(PLC_TEXT[clampIdx(v.raw(PLC) + 1, 5)].get(), v.raw(FAULTS), v.raw(FORCES)), null)
            .readout(8, 84, 232, v -> METRICS.get(v.num(IAE, 100, 1), v.num(OVERSHOOT, 10, 1), v.num(TRAVEL, 100, 2),
                    v.num(SCORE, 1000, 2)), null)
            .lamp(8, 98, FLAGS, F_DEPLOYABLE, LampColor.GREEN, DEPLOY_L.get())
            .button(8, 116, 36, W_PLANT, PLANT_B.get(), null)
            .button(48, 116, 36, W_FAT, FAT_B.get(), null)
            .button(88, 116, 36, W_SPEED, SPEED_B.get(), null)
            .button(128, 116, 36, W_FAULT, FAULT_B.get(), null)
            .button(168, 116, 36, W_STOP, STOP_B.get(), null)
            .button(208, 116, 36, W_DEPLOY, DEPLOY_B.get(), null)
            .noInventory().build();

    public static final PanelLayout SIMULATOR = PanelLayout.builder("training_simulator", 232, 60, CHANNELS)
            .readout(8, 22, 216, v -> CABINET.get(STATUS_TEXT[clampIdx(v.raw(STATUS), 4)].get(), v.raw(TIME), v.raw(RUNNING_SIMS)), null)
            .readout(8, 36, 216, v -> lessonName(v), null)
            .noInventory().build();

    public static final PanelLayout INSTRUCTOR = PanelLayout.builder("instructor_station", 248, 120, CHANNELS)
            .readout(8, 22, 232, v -> LESSON_LINE.get(lessonName(v), STATUS_TEXT[clampIdx(v.raw(STATUS), 4)].get(), v.raw(REMAIN),
                    v.num(SCORE, 1000, 2)), null)
            .readout(8, 36, 232, v -> RUN_LINE.get(plant(v), STATUS_TEXT[clampIdx(v.raw(STATUS), 4)].get(), v.raw(TIME), v.raw(SPEED)), null)
            .readout(8, 48, 232, v -> signals(v, 0, 3), null)
            .readout(8, 60, 232, v -> PLC_LINE.get(PLC_TEXT[clampIdx(v.raw(PLC) + 1, 5)].get(), v.raw(FAULTS), v.raw(FORCES)), null)
            .button(8, 88, 36, I_LESSON, LESSON_B.get(), null)
            .button(48, 88, 36, I_START, START_B.get(), null)
            .button(88, 88, 36, I_SPEED, SPEED_B.get(), null)
            .button(128, 88, 36, I_FAULT, FAULT_B.get(), null)
            .button(168, 88, 36, I_STOP, STOP_B.get(), null)
            .button(208, 88, 36, I_LICENSE, LICENSE_B.get(), null)
            .noInventory().build();

    public static final PanelLayout CONSOLE;

    static {
        PanelLayout.Builder b = PanelLayout.builder("simulator_console", 248, 150, CHANNELS)
                .readout(8, 22, 232, v -> LESSON_LINE.get(lessonName(v), STATUS_TEXT[clampIdx(v.raw(STATUS), 4)].get(), v.raw(REMAIN),
                        v.num(SCORE, 1000, 2)), v -> CONSOLE_TIP.get())
                .readout(8, 36, 232, v -> signals(v, 0, 3), null)
                .readout(8, 48, 232, v -> signals(v, 3, 6), null)
                .readout(8, 60, 232, v -> actuators(v), null)
                .lamp(8, 74, FORCES, 0, LampColor.AMBER, FORCES_L.get());
        for (int i = 0; i < ACTUATORS; i++) {
            b.button(8 + i * 60, 96, 26, 2 * i, Component.literal((i + 1) + " −"), null);
            b.button(36 + i * 60, 96, 26, 2 * i + 1, Component.literal((i + 1) + " +"), null);
        }
        CONSOLE = b.button(8, 120, 80, C_FORCES, FORCES_B.get(), null).noInventory().build();
    }

    private TwinPanels() {
    }

    public static List<PanelLayout> all() {
        return List.of(WORKSTATION, SIMULATOR, INSTRUCTOR, CONSOLE);
    }

    private static int clampIdx(int i, int n) {
        return Math.max(0, Math.min(n - 1, i));
    }

    static String template(PanelValues v) {
        return TwinPlants.IDS.get(clampIdx(v.raw(PLANT), TwinPlants.IDS.size()));
    }

    private static Component plant(PanelValues v) {
        return PLANT_TEXT[clampIdx(v.raw(PLANT), PLANT_TEXT.length)].get();
    }

    private static Component lessonName(PanelValues v) {
        return LESSON_TEXT[clampIdx(v.raw(LESSON), LESSON_TEXT.length)].get();
    }

    private static Component signals(PanelValues v, int from, int to) {
        List<String> names = TwinPlants.signals(template(v));
        MutableComponent out = Component.empty();
        for (int i = from; i < Math.min(to, names.size()); i++) {
            if (i > from) out.append(" · ");
            int raw = v.raw(SIG0 + i);
            out.append(names.get(i) + " ").append(raw == BAD ? BAD_TEXT.get() : Component.literal(v.num(SIG0 + i, 1000, 2)));
        }
        return out;
    }

    private static Component actuators(PanelValues v) {
        List<String> names = TwinPlants.actuators(template(v));
        MutableComponent out = Component.empty();
        for (int i = 0; i < Math.min(ACTUATORS, names.size()); i++) {
            if (i > 0) out.append(" · ");
            out.append((i + 1) + ": " + names.get(i) + " " + v.num(ACT0 + i, 1000, 2));
        }
        return out;
    }

    // ------------------------------------------------------------------ сервер

    /** Заполнить каналы по прогону; {@code run == null} — только итог и установка. */
    static void fill(int[] out, ScenarioRun run, int plant, int status, double score) {
        Arrays.fill(out, 0);
        out[PLANT] = plant;
        out[STATUS] = status;
        out[SCORE] = (int) Math.round(score * 1000);
        out[PLC] = -1;
        out[RUNNING_SIMS] = TwinSessions.running(TwinSessions.Kind.SIMULATOR);
        if (run == null) return;
        TwinSession s = run.session();
        TwinPlant p = s.plant();
        out[PLANT] = Math.max(0, TwinPlants.IDS.indexOf(p.template()));
        out[TIME] = (int) s.timeS();
        out[REMAIN] = (int) Math.ceil(run.remainingS());
        out[SPEED] = s.speed();
        PlcCpu plc = s.plc();
        out[PLC] = plc == null ? -1 : plc.mode().ordinal();
        out[FAULTS] = s.faults().size();
        out[FORCES] = s.forceCount();
        List<String> sig = p.signals();
        for (int i = 0; i < Math.min(SIGNALS, sig.size()); i++) {
            double m = s.measured(sig.get(i));
            out[SIG0 + i] = Double.isNaN(m) ? BAD : (int) Math.round(Math.max(-2e6, Math.min(2e6, m)) * 1000);
        }
        List<String> act = p.actuators();
        for (int i = 0; i < Math.min(ACTUATORS, act.size()); i++) out[ACT0 + i] = (int) Math.round(p.actuator(act.get(i)) * 1000);
        if (!s.loops().isEmpty()) {
            LoopMetrics m = s.loops().getFirst();
            out[IAE] = (int) Math.min(Integer.MAX_VALUE, Math.round(m.iae() * 100));
            out[OVERSHOOT] = (int) Math.round(m.overshoot() * 1000);
            out[TRAVEL] = (int) Math.min(Integer.MAX_VALUE, Math.round(m.valveTravel() * 100));
        }
        out[LESSON] = Math.max(0, new ArrayList<>(ScenarioCatalog.LESSONS.keySet()).indexOf(run.scenario().id()));
    }

    /** Следующая ступень скорости. */
    static int nextSpeed(int speed) {
        for (int s : SPEEDS) if (s > speed) return s;
        return SPEEDS[0];
    }
}
