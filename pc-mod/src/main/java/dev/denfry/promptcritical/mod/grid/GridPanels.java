package dev.denfry.promptcritical.mod.grid;

import static dev.denfry.promptcritical.mod.panel.PanelText.CELSIUS;
import static dev.denfry.promptcritical.mod.panel.PanelText.PERCENT;
import static dev.denfry.promptcritical.mod.panel.PanelText.RPM;

import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.panel.PanelLayout.LampColor;
import dev.denfry.promptcritical.mod.panel.PanelText;
import dev.denfry.promptcritical.mod.panel.PanelText.Tr;
import dev.denfry.promptcritical.mod.panel.PanelValues;
import dev.denfry.promptcritical.sim.grid.BreakerState;
import dev.denfry.promptcritical.sim.grid.MachineState;
import dev.denfry.promptcritical.sim.grid.MotorState;
import dev.denfry.promptcritical.sim.grid.RelaySetting;
import dev.denfry.promptcritical.sim.grid.SwitchUnit;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * Местные щиты электросети I1 (07 §7.3–7.6): генератор, ячейка КРУ и щит 0,4 кВ, щиток, трансформатор, двигатель,
 * потребитель, шкаф РЗА, синхроноскоп, счётчик. Каналы — целые, масштаб в имени. Лампы по отечественной практике:
 * красная — включено, зелёная — отключено.
 */
public final class GridPanels {
    // --- кнопки (общие номера) ---
    public static final int BUTTON_CLOSE = 0, BUTTON_OPEN = 1, BUTTON_RATING = 2, BUTTON_SELECT = 3, BUTTON_A_DOWN = 4,
            BUTTON_A_UP = 5, BUTTON_B_DOWN = 6, BUTTON_B_UP = 7, BUTTON_RESET = 8, BUTTON_TAP_DOWN = 9, BUTTON_TAP_UP = 10,
            BUTTON_DEMAND = 11, BUTTON_SWITCH = 12, BUTTON_FIELD = 13, BUTTON_VREF_DOWN = 14, BUTTON_VREF_UP = 15,
            BUTTON_TRIM_DOWN = 16, BUTTON_TRIM_UP = 17, BUTTON_GEAR = 18, BUTTON_CLASS = 19, BUTTON_START = 20, BUTTON_STOP = 21;

    // --- аппарат (ячейка, щит, щиток) ---
    public static final int W_CURRENT_A10 = 0, W_POWER_KW10 = 1, W_BUS_V = 2, W_FEED_V = 3, W_FLAGS = 4, W_STATE = 5,
            W_RATING = 6, W_TRIPS = 7, W_RELAYS = 8, W_CHANNELS = 9;
    public static final int WF_CLOSED = 0, WF_FAULTED = 1, WF_BUS = 2, WF_FEEDER = 3, WF_BLOWN = 4, WF_FUSE = 5;
    // --- шкаф РЗА ---
    public static final int R_SELECTED = 0, R_FLAGS = 1, R_KIND = 2, R_A1000 = 3, R_B1000 = 4, R_CHANNELS = 5;
    // --- трансформатор ---
    public static final int T_LOAD_PERMIL = 0, T_HOTSPOT_C10 = 1, T_CURRENT_A10 = 2, T_POWER_KW10 = 3, T_TAP = 4, T_LIFE_H = 5,
            T_CHANNELS = 6;
    // --- потребитель ---
    public static final int C_DEMAND_KW = 0, C_POWER_KW10 = 1, C_FLAGS = 2, C_SHED_COUNT = 3, C_CHANNELS = 4;
    // --- генератор ---
    public static final int G_SHAFT_RPM10 = 0, G_FREQ_HZ100 = 1, G_VOLT_V = 2, G_P_KW10 = 3, G_Q_KVAR10 = 4, G_GEAR = 5,
            G_TRIM = 6, G_VREF_PERMIL = 7, G_CLASS_V = 8, G_STATE = 9, G_FLAGS = 10, G_CHANNELS = 11;
    // --- двигатель ---
    public static final int M_SPEED_PERMIL = 0, M_CURRENT_A10 = 1, M_P_KW10 = 2, M_TEMP_C10 = 3, M_STATE = 4, M_FLAGS = 5,
            M_GEAR = 6, M_OUT_RPM10 = 7, M_LOAD_KW10 = 8, M_CHANNELS = 9;
    // --- синхроноскоп ---
    public static final int S_ANGLE10 = 0, S_GEN_V = 1, S_BUS_V = 2, S_GEN_HZ100 = 3, S_BUS_HZ100 = 4, S_SLIP_MHZ = 5,
            S_DV_PERMIL = 6, S_FLAGS = 7, S_TRIM = 8, S_VREF_PERMIL = 9, S_CHANNELS = 10;
    // --- счётчик ---
    public static final int E_FWD_KWH10 = 0, E_REV_KWH10 = 1, E_P_KW10 = 2, E_FLAGS = 3, E_CHANNELS = 4;

    private static final Tr HZ = PanelText.tr("unit.hz", "Hz", "Гц"), KV = PanelText.tr("unit.kv", "kV", "кВ"),
            AMP = PanelText.tr("unit.a", "A", "А"), KW = PanelText.tr("unit.kw", "kW", "кВт");
    private static final Tr CLOSE = PanelText.tr("grid.close", "ON", "ВКЛ"), OPEN = PanelText.tr("grid.open", "OFF", "ОТКЛ"),
            CLOSE_TIP = PanelText.tr("grid.close_tip", "Close the breaker (the drive needs t_close)", "Включить выключатель (привод — t_вкл)"),
            OPEN_TIP = PanelText.tr("grid.open_tip", "Open the breaker", "Отключить выключатель"),
            CLOSED_LAMP = PanelText.tr("grid.closed", "On", "Вкл"), OPEN_LAMP = PanelText.tr("grid.open_lamp", "Off", "Откл"),
            FAULT_LAMP = PanelText.tr("grid.fault", "Fault", "Авария"),
            CURRENT = PanelText.tr("grid.current", "Current", "Ток"), VOLTAGE = PanelText.tr("grid.voltage", "Voltage", "Напряжение"),
            FREQUENCY = PanelText.tr("grid.frequency", "Frequency", "Частота"), POWER = PanelText.tr("grid.power", "Power", "Мощность"),
            RATING = PanelText.tr("grid.rating", "Rating", "Номинал"),
            RATING_TIP = PanelText.tr("grid.rating_tip", "Next rated current of the trip unit or fuse", "Следующий номинал расцепителя или вставки"),
            SWITCH_STATUS = PanelText.tr("grid.switch_status", "Bus %s kV · feeder %s kV · %s kW", "Шина %s кВ · присоединение %s кВ · %s кВт"),
            SWITCH_INFO = PanelText.tr("grid.switch_info", "%s · trips %s · relays %s", "%s · отключений %s · реле %s"),
            BREAKER_STATE = PanelText.tr("grid.breaker_state", "Breaker %s", "Выключатель: %s"),
            RATED = PanelText.tr("grid.rated", "Rated %s A · trips %s", "Номинал %s А · срабатываний %s"),
            BLOWN = PanelText.tr("grid.blown", "Blown", "Перегорела"), FUSE = PanelText.tr("grid.fuse", "Fuse", "Вставка"),
            NO_FUSE = PanelText.tr("grid.no_fuse", "No fuse — fit lv_fuse", "Вставки нет — вставьте lv_fuse");
    private static final Tr LOAD = PanelText.tr("grid.load", "Loading", "Загрузка"),
            HOTSPOT = PanelText.tr("grid.hotspot", "Hot spot", "Наиб. нагретая точка"),
            TRANSFORMER_STATUS = PanelText.tr("grid.transformer_status", "HV %s A · %s kW · tap %s (%s %%) · aging %s h",
                    "ВН %s А · %s кВт · ПБВ %s (%s %%) · износ %s ч"),
            TAP_DOWN = PanelText.tr("grid.tap_down", "Tap −", "ПБВ −"), TAP_UP = PanelText.tr("grid.tap_up", "Tap +", "ПБВ +"),
            TAP_TIP = PanelText.tr("grid.tap_tip", "Off-load tap changer ±2×2.5 %: only when de-energized",
                    "ПБВ ±2×2,5 %: только без напряжения");
    private static final Tr DEMAND = PanelText.tr("grid.demand", "Demand", "Мощность"),
            DEMAND_TIP = PanelText.tr("grid.demand_tip", "Next settlement demand", "Следующая мощность посёлка"),
            SWITCH = PanelText.tr("grid.switch", "Switch", "Рубильник"),
            ENERGIZED = PanelText.tr("grid.energized", "Supply", "Питание"), SHED = PanelText.tr("grid.shed", "UFLS", "АЧР"),
            CONSUMER_STATUS = PanelText.tr("grid.consumer_status", "Demand %s kW · now %s kW · shed %s times",
                    "Заявлено %s кВт · сейчас %s кВт · отключений АЧР %s"),
            MODULE_SLOT = PanelText.tr("grid.module_slot", "Relay module: UFLS, 27, 81U, 51 (49 for motors)",
                    "Модуль РЗА: АЧР, 27, 81U, 51 (49 — двигателю)");
    private static final Tr FIELD = PanelText.tr("grid.field", "Field", "Поле"),
            FIELD_TIP = PanelText.tr("grid.field_tip", "Field breaker: AVR holds the voltage / field suppressed",
                    "Автомат гашения поля: АРВ держит напряжение / поле погашено"),
            EXCITER = PanelText.tr("grid.exciter", "Exciter", "Возбуд."), PARALLEL = PanelText.tr("grid.parallel", "Parallel", "Сеть"),
            COUPLING = PanelText.tr("grid.coupling", "Pin", "Штифт"), DAMAGED = PanelText.tr("grid.damaged", "Damage", "Поврежд."),
            V_DOWN = PanelText.tr("grid.v_down", "V −", "U −"), V_UP = PanelText.tr("grid.v_up", "V +", "U +"),
            V_TIP = PanelText.tr("grid.v_tip", "Voltage setpoint of the AVR, step 0.5 %", "Уставка АРВ, шаг 0,5 %"),
            F_DOWN = PanelText.tr("grid.f_down", "f −", "f −"), F_UP = PanelText.tr("grid.f_up", "f +", "f +"),
            F_TIP = PanelText.tr("grid.f_tip", "Speed changer of the step-up gear, step 0.1 % (0.05 Hz)",
                    "Вариатор мультипликатора, шаг 0,1 % (0,05 Гц)"),
            GEAR = PanelText.tr("grid.gear", "Gear", "Передача"),
            GEAR_TIP = PanelText.tr("grid.gear_tip", "Create shaft speed giving rated frequency: 16…256 RPM",
                    "Обороты вала Create для номинальной частоты: 16…256 об/мин"),
            CLASS = PanelText.tr("grid.class", "kV", "кВ"),
            CLASS_TIP = PanelText.tr("grid.class_tip", "Rated voltage 0.4 / 6.3 kV (stopped machine only)",
                    "Номинальное напряжение 0,4 / 6,3 кВ (только на остановленной машине)"),
            GEN_STATUS = PanelText.tr("grid.gen_status", "%s kV · %s kW · %s kvar · %s", "%s кВ · %s кВт · %s квар · %s"),
            GEN_SETTINGS = PanelText.tr("grid.gen_settings", "Gear %s RPM · trim %s ‰ · V set %s · rated %s kV",
                    "Передача %s об/мин · вариатор %s ‰ · уставка %s · номинал %s кВ");
    private static final Tr SPEED = PanelText.tr("grid.speed", "Speed", "Скорость"),
            WINDING = PanelText.tr("grid.winding", "Winding", "Обмотка"),
            START = PanelText.tr("grid.start", "Start", "Пуск"), STOP = PanelText.tr("grid.stop", "Stop", "Стоп"),
            CONTACTOR = PanelText.tr("grid.contactor", "Contactor", "Пускатель"),
            MOTOR_STATUS = PanelText.tr("grid.motor_status", "%s · %s A · %s kW · shaft %s of %s RPM",
                    "%s · %s А · %s кВт · вал %s из %s об/мин");
    private static final Tr SELECT = PanelText.tr("grid.select", "Next", "Модуль"),
            A_DOWN = PanelText.tr("grid.a_down", "Set −", "Уст. −"), A_UP = PanelText.tr("grid.a_up", "Set +", "Уст. +"),
            B_DOWN = PanelText.tr("grid.b_down", "Time −", "Вр. −"), B_UP = PanelText.tr("grid.b_up", "Time +", "Вр. +"),
            RESET = PanelText.tr("grid.reset", "Reset", "Сброс"),
            TRIPPED = PanelText.tr("grid.tripped", "Tripped", "Сработала"), LINKED = PanelText.tr("grid.linked", "Cell", "Ячейка"),
            CABINET_SLOT = PanelText.tr("grid.cabinet_slot", "Relay module for the adjacent cell", "Модуль РЗА для соседней ячейки"),
            CABINET_EMPTY = PanelText.tr("grid.cabinet_empty", "Slot %s: empty", "Место %s: пусто"),
            CABINET_SEL = PanelText.tr("grid.cabinet_sel", "Slot %s: %s", "Место %s: %s");
    private static final Tr SCOPE = PanelText.tr("grid.scope", "Synchroscope", "Синхроноскоп"),
            SCOPE_TIP = PanelText.tr("grid.scope_tip", "Clockwise — generator faster; close slightly before 12 o'clock",
                    "По часовой — генератор быстрее; включать чуть раньше «12 часов»"),
            SCOPE_GEN = PanelText.tr("grid.scope_gen", "Generator %s kV · %s Hz", "Генератор %s кВ · %s Гц"),
            SCOPE_BUS = PanelText.tr("grid.scope_bus", "Bus %s kV · %s Hz", "Шина %s кВ · %s Гц"),
            SCOPE_DIFF = PanelText.tr("grid.scope_diff", "Δf %s Hz · ΔV %s %%", "Δf %s Гц · ΔV %s %%"),
            SCOPE_DEAD = PanelText.tr("grid.scope_dead", "One side is dead — no synchronizing needed",
                    "Одна сторона без напряжения — синхронизация не нужна"),
            SCOPE_NONE = PanelText.tr("grid.scope_none", "Hang the synchroscope on a switchgear cell", "Повесьте синхроноскоп на ячейку КРУ"),
            SCOPE_GENSET = PanelText.tr("grid.scope_genset", "Generator: trim %s ‰ · V set %s", "Генератор: вариатор %s ‰ · уставка %s"),
            SCOPE_NO_GEN = PanelText.tr("grid.scope_no_gen", "No generator on the feeder side", "На присоединении нет генератора");
    private static final Tr METER_FWD = PanelText.tr("grid.meter_fwd", "Delivered %s kWh", "Отпущено %s кВт·ч"),
            METER_REV = PanelText.tr("grid.meter_rev", "Returned %s kWh", "Принято %s кВт·ч"),
            METER_NOW = PanelText.tr("grid.meter_now", "Now %s kW", "Сейчас %s кВт");
    /** Строки, которые нужны вне раскладок. */
    public static final Tr RELAY_GENERATION = PanelText.tr("relay.generation", "A1 electromechanical", "A1 электромеханическое"),
            SPAN_NONE = PanelText.tr("grid.span_none", "No spans on this connector", "Пролётов нет"),
            SPAN_INFO = PanelText.tr("grid.span_info", "Span %s m %s: %s A, %s °C %s", "Пролёт %s м %s: %s А, %s °C %s"),
            SPAN_TOUCH = PanelText.tr("grid.span_touch", "— touching a block!", "— касается блока!");
    private static final Tr[] RELAY_KIND = {
            PanelText.tr("relay.overcurrent", "51/50 overcurrent: pickup %s A, TMS %s", "51/50 МТЗ: ток %s А, TMS %s"),
            PanelText.tr("relay.sync_check", "25 synchronism check (window from config)", "25 контроль синхронизма (окно из конфига)"),
            PanelText.tr("relay.reverse_power", "32R reverse power: %s × S, delay %s s", "32R обратная мощность: %s × S, выдержка %s с"),
            PanelText.tr("relay.frequency", "81U underfrequency: %s Hz, delay %s s", "81U понижение частоты: %s Гц, выдержка %s с"),
            PanelText.tr("relay.voltage", "27 undervoltage: %s pu, delay %s s", "27 минимальное напряжение: %s о.е., выдержка %s с"),
            PanelText.tr("relay.thermal", "49 thermal: trip at %s °C", "49 тепловая: отключение при %s °C"),
            PanelText.tr("relay.ufls", "UFLS stage %s with auto-reclose at 49.7 Hz", "АЧР, очередь %s, ЧАПВ на 49,7 Гц"),
            PanelText.tr("relay.autoreclose", "79 auto-reclose: dead time %s s, %s shot(s)", "79 АПВ: пауза %s с, циклов %s"),
            PanelText.tr("relay.breaker_failure", "50BF breaker failure: delay %s s", "50BF УРОВ: выдержка %s с")};
    private static final Tr[] MACHINE_STATES = {
            PanelText.tr("machine.stopped", "stopped", "стоит"), PanelText.tr("machine.rolling", "rolling", "вращается"),
            PanelText.tr("machine.excited", "excited", "возбуждён"), PanelText.tr("machine.synchronized", "in parallel", "в сети"),
            PanelText.tr("machine.loaded", "loaded", "под нагрузкой"), PanelText.tr("machine.async", "OUT OF STEP", "АСИНХРОННЫЙ ХОД"),
            PanelText.tr("machine.tripped", "tripped", "отключён"), PanelText.tr("machine.damaged", "DAMAGED", "ПОВРЕЖДЁН")};
    private static final Tr[] MOTOR_STATES = {
            PanelText.tr("motor.off", "off", "отключён"), PanelText.tr("motor.starting", "starting", "пуск"),
            PanelText.tr("motor.running", "running", "работает"), PanelText.tr("motor.stalled", "STALLED", "ОПРОКИНУЛСЯ"),
            PanelText.tr("motor.faulted", "WINDING FAULT", "ПРОБОЙ ОБМОТКИ")};
    private static final Tr[] BREAKER_STATES = {
            PanelText.tr("breaker.open", "open", "отключён"), PanelText.tr("breaker.closing", "closing", "включается"),
            PanelText.tr("breaker.closed", "closed", "включён"), PanelText.tr("breaker.opening", "opening", "отключается"),
            PanelText.tr("breaker.failed", "FAILED TO INTERRUPT", "НЕ ОТКЛЮЧИЛ ТОК"),
            PanelText.tr("breaker.faulted", "OUT OF SERVICE", "НЕИСПРАВЕН")};

    public static final PanelLayout CELL = switchLayout("switchgear_cell", 100, 8, false);
    public static final PanelLayout SWITCHBOARD = switchLayout("lv_switchboard", 1000, 0.5, true);

    public static final PanelLayout FUSE_BOX = PanelLayout.builder("lv_distribution_box", 176, 120, W_CHANNELS)
            .dial(8, 24, false, W_CURRENT_A10, 10, 1000, Double.NaN, 0, AMP.get(), CURRENT.get(), null)
            .lamp(70, 26, W_FLAGS, WF_FUSE, LampColor.WHITE, FUSE.get())
            .lamp(70, 42, W_FLAGS, WF_BLOWN, LampColor.RED, BLOWN.get())
            .readout(8, 84, 160, v -> v.bit(W_FLAGS, WF_FUSE) ? RATED.get(v.raw(W_RATING), v.raw(W_TRIPS))
                    : NO_FUSE.get().withStyle(ChatFormatting.RED), null)
            .button(8, 100, 70, BUTTON_RATING, RATING.get(), RATING_TIP.get())
            .noInventory()
            .build();

    public static final PanelLayout TRANSFORMER = PanelLayout.builder("distribution_transformer", 220, 130, T_CHANNELS)
            .dial(8, 24, true, T_LOAD_PERMIL, 10, 150, 100, 0, PERCENT.get(), LOAD.get(), null)
            .dial(84, 24, true, T_HOTSPOT_C10, 10, 160, 120, 0, CELSIUS.get(), HOTSPOT.get(), null)
            .readout(8, 98, 204, v -> TRANSFORMER_STATUS.get(v.num(T_CURRENT_A10, 10, 1), v.num(T_POWER_KW10, 10, 1),
                    v.raw(T_TAP), PanelText.num(v.raw(T_TAP) * 2.5, 1), v.raw(T_LIFE_H)), null)
            .button(158, 30, 54, BUTTON_TAP_DOWN, TAP_DOWN.get(), TAP_TIP.get())
            .button(158, 50, 54, BUTTON_TAP_UP, TAP_UP.get(), TAP_TIP.get())
            .noInventory()
            .build();

    public static final PanelLayout CONSUMER = PanelLayout.builder("grid_consumer", 220, 130, C_CHANNELS)
            .dial(8, 24, true, C_POWER_KW10, 10, 2000, Double.NaN, 0, KW.get(), POWER.get(), null)
            .lamp(84, 26, C_FLAGS, 0, LampColor.WHITE, ENERGIZED.get())
            .lamp(84, 42, C_FLAGS, 1, LampColor.AMBER, SHED.get())
            .lamp(84, 58, C_FLAGS, 2, LampColor.RED, SWITCH.get())
            .slot(150, 26, 0, s -> s.getItem() instanceof RelayModuleItem m && ConsumerBlockEntity.accepts(m.kind()), MODULE_SLOT.get())
            .slot(172, 26, 1, s -> s.getItem() instanceof RelayModuleItem m && ConsumerBlockEntity.accepts(m.kind()), MODULE_SLOT.get())
            .readout(8, 98, 204, v -> CONSUMER_STATUS.get(v.raw(C_DEMAND_KW), v.num(C_POWER_KW10, 10, 0), v.raw(C_SHED_COUNT)), null)
            .button(140, 52, 72, BUTTON_DEMAND, DEMAND.get(), DEMAND_TIP.get())
            .button(140, 72, 72, BUTTON_SWITCH, SWITCH.get(), null)
            .build();

    public static final PanelLayout GENERATOR = PanelLayout.builder("shaft_generator", 230, 168, G_CHANNELS)
            .dial(8, 24, true, G_FREQ_HZ100, 100, 60, 51.5, 1, HZ.get(), FREQUENCY.get(), null)
            .dial(82, 24, false, G_P_KW10, 10, 600, 400, 0, KW.get(), POWER.get(), null)
            .dial(82, 84, false, G_SHAFT_RPM10, 10, 300, Double.NaN, 0, RPM.get(), PanelText.SHAFT.get(),
                    v -> PanelText.SHAFT_TIP.get(v.num(G_SHAFT_RPM10, 10, 0)))
            .lamp(140, 24, G_FLAGS, 0, LampColor.WHITE, EXCITER.get())
            .lamp(140, 40, G_FLAGS, 1, LampColor.AMBER, FIELD.get())
            .lamp(140, 56, G_FLAGS, 2, LampColor.RED, PARALLEL.get())
            .lamp(140, 72, G_FLAGS, 5, LampColor.GREEN, COUPLING.get())
            .lamp(140, 88, G_FLAGS, 4, LampColor.RED, DAMAGED.get())
            .readout(8, 112, 214, v -> GEN_STATUS.get(PanelText.num(v.raw(G_VOLT_V) / 1000.0, 2), v.num(G_P_KW10, 10, 0),
                    v.num(G_Q_KVAR10, 10, 0), state(MACHINE_STATES, v.raw(G_STATE))), null)
            .readout(8, 126, 214, v -> GEN_SETTINGS.get(v.raw(G_GEAR), v.raw(G_TRIM), v.num(G_VREF_PERMIL, 1000, 3),
                    PanelText.num(v.raw(G_CLASS_V) / 1000.0, 1)), null)
            .button(8, 144, 34, BUTTON_FIELD, FIELD.get(), FIELD_TIP.get())
            .button(44, 144, 28, BUTTON_VREF_DOWN, V_DOWN.get(), V_TIP.get())
            .button(74, 144, 28, BUTTON_VREF_UP, V_UP.get(), V_TIP.get())
            .button(104, 144, 28, BUTTON_TRIM_DOWN, F_DOWN.get(), F_TIP.get())
            .button(134, 144, 28, BUTTON_TRIM_UP, F_UP.get(), F_TIP.get())
            .button(164, 144, 34, BUTTON_GEAR, GEAR.get(), GEAR_TIP.get())
            .button(200, 144, 22, BUTTON_CLASS, CLASS.get(), CLASS_TIP.get())
            .noInventory()
            .build();

    public static final PanelLayout MOTOR = PanelLayout.builder("electric_motor", 220, 140, M_CHANNELS)
            .dial(8, 24, true, M_SPEED_PERMIL, 10, 110, Double.NaN, 0, PERCENT.get(), SPEED.get(), null)
            .dial(84, 24, false, M_TEMP_C10, 10, 200, 140, 0, CELSIUS.get(), WINDING.get(), null)
            .lamp(84, 88, M_FLAGS, 0, LampColor.RED, CONTACTOR.get())
            .slot(150, 26, 0, s -> s.getItem() instanceof RelayModuleItem m && ElectricMotorBlockEntity.accepts(m.kind()), MODULE_SLOT.get())
            .slot(172, 26, 1, s -> s.getItem() instanceof RelayModuleItem m && ElectricMotorBlockEntity.accepts(m.kind()), MODULE_SLOT.get())
            .readout(8, 100, 204, v -> MOTOR_STATUS.get(state(MOTOR_STATES, v.raw(M_STATE)), v.num(M_CURRENT_A10, 10, 0),
                    v.num(M_P_KW10, 10, 0), v.num(M_OUT_RPM10, 10, 0), v.raw(M_GEAR)), null)
            .button(140, 50, 34, BUTTON_START, START.get(), null)
            .button(178, 50, 34, BUTTON_STOP, STOP.get(), null)
            .button(140, 70, 44, BUTTON_GEAR, GEAR.get(), null)
            .button(188, 70, 24, BUTTON_CLASS, CLASS.get(), CLASS_TIP.get())
            .build();

    public static final PanelLayout CABINET = PanelLayout.builder("relay_cabinet", 220, 120, R_CHANNELS)
            .slot(10, 26, 0, s -> RelayModuleItem.setting(s) != null, CABINET_SLOT.get())
            .slot(32, 26, 1, s -> RelayModuleItem.setting(s) != null, CABINET_SLOT.get())
            .slot(54, 26, 2, s -> RelayModuleItem.setting(s) != null, CABINET_SLOT.get())
            .slot(76, 26, 3, s -> RelayModuleItem.setting(s) != null, CABINET_SLOT.get())
            .slot(98, 26, 4, s -> RelayModuleItem.setting(s) != null, CABINET_SLOT.get())
            .slot(120, 26, 5, s -> RelayModuleItem.setting(s) != null, CABINET_SLOT.get())
            .lamp(150, 24, R_FLAGS, 0, LampColor.AMBER, TRIPPED.get())
            .lamp(150, 40, R_FLAGS, 1, LampColor.GREEN, LINKED.get())
            .readout(8, 56, 204, GridPanels::cabinetText, null)
            .button(8, 74, 40, BUTTON_SELECT, SELECT.get(), null)
            .button(52, 74, 38, BUTTON_A_DOWN, A_DOWN.get(), null)
            .button(92, 74, 38, BUTTON_A_UP, A_UP.get(), null)
            .button(134, 74, 38, BUTTON_B_DOWN, B_DOWN.get(), null)
            .button(174, 74, 38, BUTTON_B_UP, B_UP.get(), null)
            .button(8, 94, 50, BUTTON_RESET, RESET.get(), null)
            .build();

    public static final PanelLayout SYNCHROSCOPE = PanelLayout.builder("instrument_synchroscope", 220, 150, S_CHANNELS)
            .scope(8, 24, S_ANGLE10, SCOPE.get(), v -> SCOPE_TIP.get())
            .readout(80, 26, 132, v -> SCOPE_GEN.get(PanelText.num(v.raw(S_GEN_V) / 1000.0, 2), v.num(S_GEN_HZ100, 100, 2)), null)
            .readout(80, 42, 132, v -> SCOPE_BUS.get(PanelText.num(v.raw(S_BUS_V) / 1000.0, 2), v.num(S_BUS_HZ100, 100, 2)), null)
            .readout(80, 58, 132, GridPanels::scopeDiff, null)
            .readout(80, 74, 132, v -> v.bit(S_FLAGS, 3) ? SCOPE_GENSET.get(v.raw(S_TRIM), v.num(S_VREF_PERMIL, 1000, 3))
                    : SCOPE_NO_GEN.get().withStyle(ChatFormatting.GRAY), null)
            .lamp(80, 92, S_FLAGS, 1, LampColor.RED, CLOSED_LAMP.get())
            .button(8, 104, 40, BUTTON_CLOSE, CLOSE.get(), CLOSE_TIP.get())
            .button(52, 104, 40, BUTTON_OPEN, OPEN.get(), OPEN_TIP.get())
            .button(8, 124, 30, BUTTON_TRIM_DOWN, F_DOWN.get(), F_TIP.get())
            .button(40, 124, 30, BUTTON_TRIM_UP, F_UP.get(), F_TIP.get())
            .button(80, 124, 30, BUTTON_VREF_DOWN, V_DOWN.get(), V_TIP.get())
            .button(112, 124, 30, BUTTON_VREF_UP, V_UP.get(), V_TIP.get())
            .noInventory()
            .build();

    public static final PanelLayout METER = PanelLayout.builder("energy_meter", 176, 80, E_CHANNELS)
            .readout(8, 26, 160, v -> METER_FWD.get(v.num(E_FWD_KWH10, 10, 1)), null)
            .readout(8, 42, 160, v -> METER_REV.get(v.num(E_REV_KWH10, 10, 1)), null)
            .readout(8, 58, 160, v -> METER_NOW.get(v.num(E_P_KW10, 10, 1)), null)
            .noInventory()
            .build();

    private GridPanels() {
    }

    public static List<PanelLayout> all() {
        return List.of(CELL, SWITCHBOARD, FUSE_BOX, TRANSFORMER, CONSUMER, GENERATOR, MOTOR, CABINET, SYNCHROSCOPE, METER);
    }

    private static PanelLayout switchLayout(String id, double maxA, double maxKv, boolean rating) {
        PanelLayout.Builder b = PanelLayout.builder(id, 220, 136, W_CHANNELS)
                .dial(8, 24, true, W_CURRENT_A10, 10, maxA, Double.NaN, 0, AMP.get(), CURRENT.get(), null)
                .dial(84, 24, false, W_BUS_V, 1000, maxKv, Double.NaN, 1, KV.get(), VOLTAGE.get(), null)
                .lamp(150, 24, W_FLAGS, WF_CLOSED, LampColor.RED, CLOSED_LAMP.get())
                .lamp(150, 40, W_FLAGS, WF_FAULTED, LampColor.AMBER, FAULT_LAMP.get())
                .readout(8, 96, 204, v -> SWITCH_STATUS.get(PanelText.num(v.raw(W_BUS_V) / 1000.0, 2),
                        PanelText.num(v.raw(W_FEED_V) / 1000.0, 2), v.num(W_POWER_KW10, 10, 1)), null)
                .readout(8, 80, 204, v -> SWITCH_INFO.get(BREAKER_STATE.get(state(BREAKER_STATES, v.raw(W_STATE))),
                        v.raw(W_TRIPS), v.raw(W_RELAYS)), null)
                .button(84, 114, 40, BUTTON_CLOSE, CLOSE.get(), CLOSE_TIP.get())
                .button(128, 114, 40, BUTTON_OPEN, OPEN.get(), OPEN_TIP.get());
        if (rating) b.button(8, 114, 70, BUTTON_RATING, RATING.get(), RATING_TIP.get());
        return b.noInventory().build();
    }

    /** Показания аппарата в каналы {@code W_*}. */
    static void readSwitch(SwitchUnit u, int[] out) {
        out[W_CURRENT_A10] = (int) Math.round(u.currentA() * 10);
        out[W_POWER_KW10] = (int) Math.round(u.powerW() / 100);
        out[W_BUS_V] = (int) Math.round(u.voltageV(0));
        out[W_FEED_V] = (int) Math.round(u.voltageV(1));
        BreakerState s = u.breakerState();
        out[W_FLAGS] = (u.closed() ? 1 << WF_CLOSED : 0)
                | (s == BreakerState.FAULTED || s == BreakerState.FAILED_TO_INTERRUPT ? 1 << WF_FAULTED : 0)
                | (u.voltageV(0) > 0 ? 1 << WF_BUS : 0) | (u.voltageV(1) > 0 ? 1 << WF_FEEDER : 0)
                | (u.fuseBlown() ? 1 << WF_BLOWN : 0) | (u.kind() == SwitchUnit.Kind.FUSE && u.fuseInstalled() ? 1 << WF_FUSE : 0);
        out[W_STATE] = s == null ? (u.closed() ? BreakerState.CLOSED : BreakerState.OPEN).ordinal() : s.ordinal();
        out[W_RATING] = u.ratedA();
        out[W_TRIPS] = u.protectionTrips();
        out[W_RELAYS] = u.relays().size();
    }

    /** Модуль РЗА с уставками строкой. */
    public static Component relayText(RelaySetting.Kind kind, double a, double b) {
        Tr t = RELAY_KIND[kind.ordinal()];
        return switch (kind) {
            case OVERCURRENT -> t.get(PanelText.num(a, 0), PanelText.num(b, 2));
            case SYNC_CHECK -> t.get();
            case REVERSE_POWER -> t.get(PanelText.num(a, 2), PanelText.num(b, 1));
            case UNDERFREQUENCY -> t.get(PanelText.num(a, 1), PanelText.num(b, 1));
            case UNDERVOLTAGE -> t.get(PanelText.num(a, 2), PanelText.num(b, 1));
            case THERMAL -> t.get(PanelText.num(a, 0));
            case UFLS -> t.get(PanelText.num(a, 0));
            case AUTORECLOSE -> t.get(PanelText.num(a, 1), PanelText.num(b, 0));
            case BREAKER_FAILURE -> t.get(PanelText.num(a, 2));
        };
    }

    private static Component cabinetText(PanelValues v) {
        int slot = v.raw(R_SELECTED) + 1, kind = v.raw(R_KIND);
        if (kind == 0) return CABINET_EMPTY.get(slot);
        RelaySetting.Kind k = RelaySetting.Kind.values()[kind - 1];
        return CABINET_SEL.get(slot, relayText(k, v.get(R_A1000, 1000), v.get(R_B1000, 1000)));
    }

    private static Component scopeDiff(PanelValues v) {
        if (!v.bit(S_FLAGS, 0)) return SCOPE_NONE.get().withStyle(ChatFormatting.GRAY);
        if (!v.bit(S_FLAGS, 2)) return SCOPE_DEAD.get().withStyle(ChatFormatting.GRAY);
        String df = (v.raw(S_SLIP_MHZ) >= 0 ? "+" : "") + v.num(S_SLIP_MHZ, 1000, 3);
        String dv = (v.raw(S_DV_PERMIL) >= 0 ? "+" : "") + v.num(S_DV_PERMIL, 10, 1);
        return SCOPE_DIFF.get(df, dv);
    }

    private static Component state(Tr[] names, int ordinal) {
        return ordinal >= 0 && ordinal < names.length ? names[ordinal].get() : Component.literal("?");
    }

    static {
        // порядок имён состояний должен совпадать с перечислениями домена
        if (MACHINE_STATES.length != MachineState.values().length || MOTOR_STATES.length != MotorState.values().length
                || BREAKER_STATES.length != BreakerState.values().length || RELAY_KIND.length != RelaySetting.Kind.values().length)
            throw new IllegalStateException("panel state names are out of sync with the domain enums");
    }
}
