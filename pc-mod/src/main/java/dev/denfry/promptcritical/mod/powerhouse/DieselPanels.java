package dev.denfry.promptcritical.mod.powerhouse;

import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.panel.PanelLayout.LampColor;
import dev.denfry.promptcritical.mod.panel.PanelText;
import dev.denfry.promptcritical.mod.panel.PanelText.Tr;
import dev.denfry.promptcritical.mod.panel.PanelValues;
import dev.denfry.promptcritical.sim.combustion.DieselEngine;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * Местный щит аварийного ДГ (07 §7.11): ваттметр, частотомер, вольтметр, уровень расходного бака; лампы «готов»,
 * «работа», «выключатель», «автомат», «авария», «оперативный ток», «секвенсор», «подогрев»; ключи пуска, останова,
 * квитирования, режима, генераторного выключателя, регулятора и уставки скорости, класса напряжения.
 */
public final class DieselPanels {
    public static final int KW10 = 0, HZ100 = 1, V10 = 2, FUEL_PERMIL = 3, AIR = 4, BOOST = 5, STATUS = 6, CAUSE = 7, PENDING = 8,
            DAYS_SINCE_TEST = 9, P_FS = 10, FUEL_KGH = 11, MV = 12, FLAGS = 13, CHANNELS = 14;
    public static final int F_READY = 0, F_RUN = 1, F_BREAKER = 2, F_AUTO = 3, F_FAULT = 4, F_DC = 5, F_SEQ = 6, F_HEATER = 7,
            F_DROOP = 8;
    public static final int BUTTON_START = 0, BUTTON_STOP = 1, BUTTON_RESET = 2, BUTTON_AUTO = 3, BUTTON_BREAKER = 4,
            BUTTON_GOVERNOR = 5, BUTTON_LOWER = 6, BUTTON_RAISE = 7, BUTTON_VOLTAGE = 8;

    private static final int FUEL_COLOR = 0xFF7A5230;
    private static final Tr KW = PanelText.tr("unit.kw", "kW", "кВт"),
            HZ = PanelText.tr("unit.hz", "Hz", "Гц"),
            KV = PanelText.tr("unit.kv", "kV", "кВ"),
            POWER = PanelText.tr("dg.power", "Power", "Мощность"),
            FREQUENCY = PanelText.tr("dg.frequency", "Frequency", "Частота"),
            VOLTAGE = PanelText.tr("dg.voltage", "Voltage", "Напряжение"),
            FUEL = PanelText.tr("dg.fuel", "Day tank", "Расходный бак"),
            FUEL_TIP = PanelText.tr("dg.fuel_tip", "Day tank %s %% · burning %s kg/h", "Расходный бак %s %% · расход %s кг/ч"),
            READY = PanelText.tr("dg.ready", "Ready", "Готов"),
            RUN = PanelText.tr("dg.run", "Running", "Работа"),
            BREAKER = PanelText.tr("dg.breaker", "Breaker", "Выключатель"),
            AUTO = PanelText.tr("dg.auto", "Auto", "Автомат"),
            FAULT = PanelText.tr("dg.fault", "Fault", "Авария"),
            DC = PanelText.tr("dg.dc", "DC", "Опер. ток"),
            SEQ = PanelText.tr("dg.seq", "Sequencer", "Секвенсор"),
            HEATER = PanelText.tr("dg.heater", "Heater", "Подогрев"),
            START = PanelText.tr("dg.start", "Start", "Пуск"),
            START_TIP = PanelText.tr("dg.start_tip", "Air start: a test run, or start by hand", "Пуск сжатым воздухом: опробование или ручной пуск"),
            STOP = PanelText.tr("dg.stop", "Stop", "Стоп"),
            STOP_TIP = PanelText.tr("dg.stop_tip", "Open the breaker and stop the engine", "Отключить выключатель и остановить дизель"),
            RESET = PanelText.tr("dg.reset", "Reset", "Сброс"),
            RESET_TIP = PanelText.tr("dg.reset_tip", "Acknowledge a lockout: the unit goes back to standby",
                    "Квитировать блокировку: агрегат снова в резерве"),
            AUTO_TIP = PanelText.tr("dg.auto_tip", "Auto: start on loss of bus voltage for 1 s and close onto the dead bus",
                    "Автомат: пуск при потере напряжения шины на 1 с и включение на обесточенную шину"),
            BREAKER_TIP = PanelText.tr("dg.breaker_tip", "Close onto a dead bus only; parallel through a switchgear cell with synchronizing",
                    "Включение только на обесточенную шину; параллельно — через ячейку КРУ с синхронизацией"),
            GOVERNOR = PanelText.tr("dg.governor", "Governor", "Регулятор"),
            GOVERNOR_TIP = PanelText.tr("dg.governor_tip", "Isochronous for an island, 4 %% droop in parallel with the grid (now: %s)",
                    "Изохронный на изолированную шину, статизм 4 %% при параллельной работе (сейчас: %s)"),
            ISO = PanelText.tr("dg.iso", "isochronous", "изохронный"),
            DROOP = PanelText.tr("dg.droop", "droop", "статизм"),
            LOWER = PanelText.tr("dg.lower", "−", "−"),
            RAISE = PanelText.tr("dg.raise", "+", "+"),
            SPEED_TIP = PanelText.tr("dg.speed_tip", "Speed set point with droop: lower or raise the load", "Уставка скорости при статизме: убавить или прибавить нагрузку"),
            CLASS = PanelText.tr("dg.class", "0.4/6.3", "0,4/6,3"),
            CLASS_TIP = PanelText.tr("dg.class_tip", "Stator voltage: %s kV (change only when stopped)", "Напряжение статора: %s кВ (менять на остановленном)"),
            STATUS_LINE = PanelText.tr("dg.status_line", "%s · air %s starts · boost %s %% · p(fail to start) %s %% · %s days since test",
                    "%s · воздух на %s пусков · наддув %s %% · отказ пуска %s %% · %s сут после опробования"),
            CAUSE_LINE = PanelText.tr("dg.cause_line", "%s: %s", "%s: %s"),
            STEPS_LINE = PanelText.tr("dg.steps_line", "%s · %s load steps to go", "%s · ещё ступеней: %s"),
            NO_DC = PanelText.tr("dg.no_dc", "No station battery touches the unit: no control power to start",
                    "Агрегата не касается аккумулятор: нет оперативного тока для пуска");

    private static final Tr[] STATES = {
            PanelText.tr("dg.s.standby", "standby", "резерв"),
            PanelText.tr("dg.s.cranking", "cranking", "прокрутка"),
            PanelText.tr("dg.s.retry_wait", "failed to fire, retrying", "не запустился, повтор"),
            PanelText.tr("dg.s.accelerating", "accelerating", "разгон"),
            PanelText.tr("dg.s.running", "running", "работа"),
            PanelText.tr("dg.s.coasting", "coasting down", "выбег"),
            PanelText.tr("dg.s.locked_out", "locked out", "блокировка")};
    private static final Tr[] CAUSES = {
            PanelText.tr("dg.c.none", "no fault", "исправен"),
            PanelText.tr("dg.c.stopped", "stopped", "остановлен"),
            PanelText.tr("dg.c.fail_to_start", "failed to start", "отказ пуска"),
            PanelText.tr("dg.c.no_air", "starting air used up", "нет пускового воздуха"),
            PanelText.tr("dg.c.no_dc", "no control power", "нет оперативного тока"),
            PanelText.tr("dg.c.no_fuel", "day tank empty", "расходный бак пуст"),
            PanelText.tr("dg.c.run_failure", "failed while running", "отказ в работе"),
            PanelText.tr("dg.c.overspeed", "overspeed trip", "защита от разноса")};

    public static final PanelLayout DG = PanelLayout.builder("diesel_generator", 248, 152, CHANNELS)
            .dial(8, 24, false, KW10, 10, 1250, 1100, 0, KW.get(), POWER.get(), null)
            .dial(56, 24, false, HZ100, 100, 60, 52.5, 1, HZ.get(), FREQUENCY.get(), null)
            .dial(104, 24, false, V10, 100, 7.5, 7, 2, KV.get(), VOLTAGE.get(), null)
            .glass(152, 24, 56, FUEL_PERMIL, FUEL_COLOR, 100, Double.NaN, FUEL.get(),
                    v -> FUEL_TIP.get(v.num(FUEL_PERMIL, 10, 0), v.raw(FUEL_KGH)))
            .lamp(176, 24, FLAGS, F_READY, LampColor.GREEN, READY.get())
            .lamp(176, 38, FLAGS, F_RUN, LampColor.GREEN, RUN.get())
            .lamp(176, 52, FLAGS, F_BREAKER, LampColor.RED, BREAKER.get())
            .lamp(176, 66, FLAGS, F_AUTO, LampColor.AMBER, AUTO.get())
            .lamp(212, 24, FLAGS, F_FAULT, LampColor.RED, FAULT.get())
            .lamp(212, 38, FLAGS, F_DC, LampColor.GREEN, DC.get())
            .lamp(212, 52, FLAGS, F_SEQ, LampColor.WHITE, SEQ.get())
            .lamp(212, 66, FLAGS, F_HEATER, LampColor.WHITE, HEATER.get())
            .button(8, 88, 44, BUTTON_START, START.get(), START_TIP.get())
            .button(56, 88, 44, BUTTON_STOP, STOP.get(), STOP_TIP.get())
            .button(104, 88, 44, BUTTON_RESET, RESET.get(), RESET_TIP.get())
            .button(152, 88, 88, BUTTON_AUTO, AUTO.get(), AUTO_TIP.get())
            .button(8, 108, 68, BUTTON_BREAKER, BREAKER.get(), BREAKER_TIP.get())
            .button(80, 108, 64, BUTTON_GOVERNOR, GOVERNOR.get(), null)
            .button(148, 108, 20, BUTTON_LOWER, LOWER.get(), SPEED_TIP.get())
            .button(172, 108, 20, BUTTON_RAISE, RAISE.get(), SPEED_TIP.get())
            .button(196, 108, 44, BUTTON_VOLTAGE, CLASS.get(), null)
            .readout(8, 132, 232, DieselPanels::status, DieselPanels::tip)
            .noInventory().build();

    private DieselPanels() {
    }

    public static List<PanelLayout> all() {
        return List.of(DG);
    }

    private static Component status(PanelValues v) {
        if (!v.bit(FLAGS, F_DC)) return NO_DC.get().withStyle(ChatFormatting.GOLD);
        Component state = STATES[Math.floorMod(v.raw(STATUS), STATES.length)].get();
        int cause = Math.floorMod(v.raw(CAUSE), CAUSES.length);
        if (cause != DieselEngine.Cause.NONE.ordinal() && cause != DieselEngine.Cause.STOPPED.ordinal())
            return CAUSE_LINE.get(state, CAUSES[cause].get()).withStyle(ChatFormatting.RED);
        if (v.raw(PENDING) > 0) return STEPS_LINE.get(state, v.raw(PENDING));
        return state.copy();
    }

    private static Component tip(PanelValues v) {
        return Component.empty().append(STATUS_LINE.get(STATES[Math.floorMod(v.raw(STATUS), STATES.length)].get(), v.raw(AIR),
                        v.raw(BOOST), v.num(P_FS, 100, 2), v.raw(DAYS_SINCE_TEST)))
                .append("\n").append(GOVERNOR_TIP.get((v.bit(FLAGS, F_DROOP) ? DROOP : ISO).get()))
                .append("\n").append(CLASS_TIP.get(v.raw(MV) != 0 ? "6.3" : "0.4"));
    }
}
