package dev.denfry.promptcritical.mod.automation;

import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.panel.PanelText;
import dev.denfry.promptcritical.mod.panel.PanelText.Tr;
import dev.denfry.promptcritical.sim.automation.AutomationKind;
import dev.denfry.promptcritical.sim.automation.AutomationUnit;
import dev.denfry.promptcritical.sim.automation.IntegratorMode;
import dev.denfry.promptcritical.sim.automation.MultiplierMode;
import dev.denfry.promptcritical.sim.automation.ProcessSwitchKind;
import dev.denfry.promptcritical.sim.automation.RelayBank;
import dev.denfry.promptcritical.sim.automation.RelayState;
import dev.denfry.promptcritical.sim.automation.StationMode;
import dev.denfry.promptcritical.sim.automation.TimeRelayMode;
import dev.denfry.promptcritical.sim.automation.TimeRelayType;
import dev.denfry.promptcritical.sim.core.Quality;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntFunction;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Щиты настройки блоков автоматики (03d §3d.3–3d.4): строка — надпись, табло и ручка «−/+» (с Shift — мелкий шаг,
 * как десятиоборотный потенциометр: коэффициенты 0,01, постоянные времени — 3 значащие цифры). Курсор выбирает
 * положение искателя, кулачок или точку функции. Каналы — по одному на строку, код значения знает строка.
 */
public final class AutomationPanels {
    public static final int BUTTON_REPLACE = 100, BUTTON_CALIBRATE = 101, BUTTON_AUTO = 102, BUTTON_MAN = 103,
            BUTTON_DOWN = 104, BUTTON_UP = 105;
    private static final int W = 232, ROW = 16, TOP = 22;

    /** Значение строки для канала (сервер). */
    @FunctionalInterface
    public interface Read {
        int read(AutomationUnit u, int cursor);
    }

    /** Ручка строки (сервер): шаг {@code dir} = ±1, {@code fine} — мелкий. */
    @FunctionalInterface
    public interface Adjust {
        void adjust(AutomationUnit u, Cursor cursor, int dir, boolean fine);
    }

    /** Курсор щита (выбранное положение, кулачок, точка) — память блока мира. */
    public interface Cursor {
        int get();

        void set(int v);
    }

    public record Row(Component label, Read read, IntFunction<Component> format, @Nullable Adjust adjust) {
    }

    public record Spec(PanelLayout layout, List<Row> rows) {
    }

    private static final Map<AutomationKind, Spec> SPECS = new EnumMap<>(AutomationKind.class);

    private static Tr tr(String key, String en, String ru) {
        return PanelText.tr("automation." + key, en, ru);
    }

    // --- общие надписи ---
    private static final Tr REPLACE = tr("replace", "Replace", "Заменить"), CALIBRATE = tr("calibrate", "Zero", "Нуль"),
            AUTO = tr("auto", "AUTO", "АВТ"), MAN = tr("man", "MAN", "РУЧ"), DOWN = tr("down", "▼", "▼"), UP = tr("up", "▲", "▲"),
            STATE = tr("state", "State", "Состояние"), OPS = tr("ops", "Operations", "Коммутаций"),
            COIL = tr("coil", "Coil", "Катушка"), OUTPUT = tr("output", "Output", "Выход"), QUALITY = tr("quality", "Quality", "Качество"),
            VARIANT = tr("variant", "Design", "Исполнение"), MODE = tr("mode", "Mode", "Режим"),
            SETPOINT = tr("setpoint", "Setpoint", "Уставка"), DELAY = tr("delay", "Actual delay", "Факт. выдержка"),
            TIMER = tr("timer", "Delayed contacts", "Контакты с выдержкой"),
            POSITION = tr("position", "Position", "Положение"), WIPER = tr("wiper", "Wiper %s →", "Ламель %s →"),
            DEBOUNCE = tr("debounce", "Step input", "Вход шага"), MISSED = tr("missed", "Missed steps", "Пропуск шагов"),
            NOW = tr("now", "Now at", "Сейчас"), RATIO = tr("ratio", "Gear 1:", "Передача 1:"), CAM = tr("cam", "Cam", "Кулачок"),
            CAM_ON = tr("cam_on", "Closes at", "Вкл с"), CAM_OFF = tr("cam_off", "Opens at", "Откл с"),
            CONTACT = tr("contact", "Contact", "Контакт"), ANGLE = tr("angle", "Drum", "Барабан"),
            KIND = tr("kind", "Kind", "Вид"), SENSE = tr("sense", "Trips on", "Срабатывает"),
            RETURN = tr("return", "Reset band", "Зона возврата"), MEASURED = tr("measured", "Measured", "Измерено"),
            RATING = tr("rating", "Rated current", "Номинальный ток"), VOLTAGE = tr("voltage", "Output", "Выход"),
            CURRENT = tr("current", "Load", "Нагрузка"), INPUT = tr("input", "Mains", "Сеть"),
            WARN = tr("warn", "Warning", "Предупр."), ALARM = tr("alarm", "Alarm", "Авария"), READING = tr("reading", "Reading", "Показание"),
            COEF = tr("coef", "k%s", "k%s"), TI = tr("ti", "Tᵢ", "Tᵢ"), TD = tr("td", "Td", "Td"), N = tr("n", "N", "N"),
            STATUS = tr("status", "Status", "Состояние"), POINT = tr("point", "Point x =", "Точка x ="),
            Y = tr("y", "y", "y"), HYST = tr("hysteresis", "Hysteresis", "Зона возврата"), SP = tr("sp", "SP", "SP"),
            OUT_MA = tr("out_ma", "Output", "Выход"), PV = tr("pv", "PV", "PV"), DIRECTION = tr("direction", "Direction", "Направление");
    private static final Tr[] RELAY_VARIANTS = {tr("relay.general", "General", "Общего назначения"),
            tr("relay.force_guided", "Force-guided", "С принуд. ведением")};
    private static final Tr[] RELAY_STATES = {tr("state.dropped", "Released", "Отпущено"), tr("state.picking", "Picking up", "Притягивается"),
            tr("state.picked", "Picked up", "Притянуто"), tr("state.releasing", "Releasing", "Отпускается"),
            tr("state.coil_open", "Coil open", "Обрыв катушки"), tr("state.welded", "Contact welded", "Сварка контакта"),
            tr("state.stuck_picked", "Stuck picked", "Залип притянутым"), tr("state.stuck_dropped", "Stuck released", "Залип отпущенным")};
    private static final Tr[] TIMER_TYPES = {tr("timer.pneumatic", "Pneumatic ±10 %", "Пневматическое ±10 %"),
            tr("timer.motor", "Motor ±1 %", "Моторное ±1 %"), tr("timer.electronic", "Electronic ±0.1 %", "Электронное ±0,1 %")};
    private static final Tr[] TIMER_MODES = {tr("timer.on_delay", "On-delay", "Задержка включения"),
            tr("timer.off_delay", "Off-delay", "Задержка отключения"), tr("timer.pulse", "Pulse", "Импульс"),
            tr("timer.flasher", "Flasher", "Мигание")};
    private static final Tr[] PROCESS_KINDS = {tr("process.pressure", "Pressure", "Давление"), tr("process.level", "Level", "Уровень"),
            tr("process.temperature", "Temperature", "Температура"), tr("process.flow", "Flow", "Расход")};
    private static final Tr[] SENSES = {tr("sense.low", "Falling", "Понижение"), tr("sense.high", "Rising", "Повышение")};
    private static final Tr[] INT_MODES = {tr("int.ic", "IC (reset)", "IC (сброс)"), tr("int.operate", "Operate", "Счёт"),
            tr("int.hold", "Hold", "Хранение")};
    private static final Tr[] MULT_MODES = {tr("mult.mul", "x₁x₂/10", "x₁x₂/10"), tr("mult.div", "10x₁/x₂", "10x₁/x₂"),
            tr("mult.square", "x²/10", "x²/10"), tr("mult.sqrt", "√(10x)", "√(10x)")};
    private static final Tr[] CONTACTS = {tr("contact.nc", "NC", "НЗ"), tr("contact.no", "NO", "НО")};
    private static final Tr[] DEBOUNCES = {tr("debounce.raw", "Raw (bounce)", "Без фильтра"), tr("debounce.rc", "RC filter", "RC-фильтр")};
    private static final Tr[] DIRECTIONS = {tr("dir.vi", "V → mA", "В → мА"), tr("dir.iv", "mA → V", "мА → В")};
    private static final Tr[] QUALITIES = {tr("q.good", "Good", "Норма"), tr("q.uncertain", "Uncertain", "Сомнительно"),
            tr("q.bad", "Bad", "Недостоверно")};
    private static final Tr TRIPPED = tr("tripped", "Tripped", "Сработало"), NORMAL = tr("normal", "Normal", "Норма"),
            STUCK = tr("stuck", "Stuck — replace", "Заклинило — замените"), FAILED = tr("failed", "Tube failed", "Отказ лампы"),
            LOOP = tr("loop", "Algebraic loop", "Алгебраический контур"), CAL_DUE = tr("cal_due", "Zero balance due", "Пора балансировать нуль"),
            CONFLICT = tr("conflict", "Two outputs on one net", "Два выхода на цепи"), NONE = tr("none", "—", "—"),
            AUTO_RETURN = tr("auto_return", "default", "по умолчанию"), OK = tr("ok", "OK", "Норма"),
            OVERLOAD = tr("overload", "Overload", "Перегрузка"), MODE_AUTO = tr("mode_auto", "Automatic", "Автомат"),
            MODE_MAN = tr("mode_man", "Manual", "Ручной");

    /** Ряд E6×1,5: 1; 1,5; 2; 3; 5; 7,5 ×10ⁿ — ступени времени и передачи. */
    private static final double[] SERIES = {1, 1.5, 2, 3, 5, 7.5};

    static {
        SPECS.put(AutomationKind.RELAY, spec(AutomationKind.RELAY, List.of(
                new Row(VARIANT.get(), (u, c) -> u.intParam(AutomationUnit.I_VARIANT), v -> name(RELAY_VARIANTS, v),
                        (u, c, d, f) -> cycle(u, AutomationUnit.I_VARIANT, d, 2)),
                relayState(), ops(), coil()), BUTTON_REPLACE));
        SPECS.put(AutomationKind.TIME_RELAY, spec(AutomationKind.TIME_RELAY, List.of(
                new Row(VARIANT.get(), (u, c) -> u.intParam(AutomationUnit.I_VARIANT), v -> name(TIMER_TYPES, v), AutomationPanels::timerType),
                new Row(MODE.get(), (u, c) -> u.intParam(AutomationUnit.I_MODE), v -> name(TIMER_MODES, v),
                        (u, c, d, f) -> cycle(u, AutomationUnit.I_MODE, d, TimeRelayMode.values().length)),
                new Row(SETPOINT.get(), (u, c) -> milli(u.param(AutomationUnit.D_SET)), v -> seconds(v / 1000.0), AutomationPanels::timerSet),
                new Row(DELAY.get(), (u, c) -> milli(u.timerDelayS()), v -> seconds(v / 1000.0), null),
                new Row(TIMER.get(), (u, c) -> u.timerOutput() ? 1 : 0, v -> v == 1 ? TRIPPED.get() : NORMAL.get(), null),
                relayState(), ops()), BUTTON_REPLACE));
        SPECS.put(AutomationKind.STEPPING_SWITCH, spec(AutomationKind.STEPPING_SWITCH, List.of(
                new Row(POSITION.get(), (u, c) -> c + 1, v -> Component.literal(Integer.toString(v)),
                        (u, c, d, f) -> c.set(Math.floorMod(c.get() + d, RelayBank.STEPPER_POSITIONS))),
                wiper(0), wiper(1), wiper(2),
                new Row(DEBOUNCE.get(), (u, c) -> u.intParam(AutomationUnit.I_DEBOUNCED), v -> name(DEBOUNCES, v),
                        (u, c, d, f) -> cycle(u, AutomationUnit.I_DEBOUNCED, d, 2)),
                new Row(NOW.get(), (u, c) -> u.stepperStuck() ? -1 : u.stepperPosition(), v -> v < 0 ? STUCK.get() : Component.literal(Integer.toString(v)), null),
                new Row(MISSED.get(), (u, c) -> (int) Math.min(Integer.MAX_VALUE, u.missedSteps()), v -> Component.literal(Integer.toString(v)), null)),
                BUTTON_REPLACE));
        SPECS.put(AutomationKind.CAM_CONTROLLER, spec(AutomationKind.CAM_CONTROLLER, List.of(
                new Row(RATIO.get(), (u, c) -> (int) Math.round(u.param(AutomationUnit.D_SPAN) * 10), v -> Component.literal(PanelText.num(v / 10.0, v % 10 == 0 ? 0 : 1)),
                        (u, c, d, f) -> u.setParam(AutomationUnit.D_SPAN, series(u.param(AutomationUnit.D_SPAN), d, 1, 3600))),
                new Row(CAM.get(), (u, c) -> c + 1, v -> Component.literal(Integer.toString(v)),
                        (u, c, d, f) -> c.set(Math.floorMod(c.get() + d, RelayBank.CAMS))),
                new Row(CAM_ON.get(), (u, c) -> (int) u.param(AutomationUnit.D_TABLE + 2 * c), AutomationPanels::degrees,
                        (u, c, d, f) -> camAngle(u, AutomationUnit.D_TABLE + 2 * c.get(), d)),
                new Row(CAM_OFF.get(), (u, c) -> (int) u.param(AutomationUnit.D_TABLE + 2 * c + 1), AutomationPanels::degrees,
                        (u, c, d, f) -> camAngle(u, AutomationUnit.D_TABLE + 2 * c.get() + 1, d)),
                new Row(CONTACT.get(), (u, c) -> u.intParam(AutomationUnit.I_TABLE + c), v -> name(CONTACTS, v),
                        (u, c, d, f) -> cycle(u, AutomationUnit.I_TABLE + c.get(), d, 2)),
                new Row(ANGLE.get(), (u, c) -> (int) u.camAngleDeg(), AutomationPanels::degrees, null))));
        SPECS.put(AutomationKind.PROCESS_SWITCH, spec(AutomationKind.PROCESS_SWITCH, List.of(
                new Row(KIND.get(), (u, c) -> u.intParam(AutomationUnit.I_VARIANT), v -> name(PROCESS_KINDS, v),
                        (u, c, d, f) -> u.setIntParam(AutomationUnit.I_VARIANT, Math.floorMod(u.intParam(AutomationUnit.I_VARIANT) + d, 4))),
                new Row(SENSE.get(), (u, c) -> u.intParam(AutomationUnit.I_MODE), v -> name(SENSES, v),
                        (u, c, d, f) -> cycle(u, AutomationUnit.I_MODE, d, 2)),
                new Row(SETPOINT.get(), (u, c) -> processCode(u, u.param(AutomationUnit.D_SET)), AutomationPanels::processValue,
                        (u, c, d, f) -> processStep(u, AutomationUnit.D_SET, d, f)),
                new Row(RETURN.get(), (u, c) -> Double.isNaN(u.param(AutomationUnit.D_RETURN)) ? Integer.MIN_VALUE
                        : processDeltaCode(u, u.param(AutomationUnit.D_RETURN)), AutomationPanels::processDelta,
                        (u, c, d, f) -> processReturn(u, d, f)),
                new Row(MEASURED.get(), (u, c) -> processCode(u, u.processMeasured()), AutomationPanels::processValue, null),
                new Row(STATE.get(), (u, c) -> u.processStuck() ? 2 : u.processTripped() ? 1 : 0,
                        v -> v == 2 ? STUCK.get() : v == 1 ? TRIPPED.get() : NORMAL.get(), null)), BUTTON_REPLACE));
        SPECS.put(AutomationKind.POWER_SUPPLY, spec(AutomationKind.POWER_SUPPLY, List.of(
                new Row(RATING.get(), (u, c) -> (int) u.supplyRatedA(), v -> Component.literal(v + " A"),
                        (u, c, d, f) -> cycle(u, AutomationUnit.I_VARIANT, d, AutomationUnit.POWER_SUPPLY_RATINGS.length)),
                new Row(VOLTAGE.get(), (u, c) -> (int) Math.round(u.supplyVoltage() * 10), v -> Component.literal(PanelText.num(v / 10.0, 1) + " V"), null),
                new Row(CURRENT.get(), (u, c) -> (int) Math.round(u.supplyCurrent() * 100), v -> Component.literal(PanelText.num(v / 100.0, 2) + " A"), null),
                new Row(INPUT.get(), (u, c) -> (int) Math.round(u.input(AutomationUnit.IN_MAIN) * 100), v -> Component.literal(v + " %"), null),
                new Row(STATE.get(), (u, c) -> u.supplyCurrent() >= u.supplyRatedA() - 1e-9 && u.supplyVoltage() > 0 || u.input(AutomationUnit.IN_MAIN) > 0
                        && u.supplyVoltage() < 1 ? 1 : 0, v -> v == 1 ? OVERLOAD.get() : OK.get(), null))));
        SPECS.put(AutomationKind.GAS_DETECTOR, spec(AutomationKind.GAS_DETECTOR, List.of(
                new Row(WARN.get(), (u, c) -> (int) u.param(AutomationUnit.D_SET), v -> Component.literal(v + " % LEL"),
                        (u, c, d, f) -> u.setParam(AutomationUnit.D_SET, clamp(u.param(AutomationUnit.D_SET) + d * (f ? 1 : 5), 5, 60))),
                new Row(ALARM.get(), (u, c) -> (int) u.param(AutomationUnit.D_RETURN), v -> Component.literal(v + " % LEL"),
                        (u, c, d, f) -> u.setParam(AutomationUnit.D_RETURN, clamp(u.param(AutomationUnit.D_RETURN) + d * (f ? 1 : 5), 10, 100))),
                new Row(READING.get(), (u, c) -> (int) Math.round(u.input(AutomationUnit.IN_MAIN) * 10),
                        v -> Component.literal(PanelText.num(v / 10.0, 1) + " % LEL"), null))));
        List<Row> summer = new ArrayList<>();
        for (int i = 0; i < 4; i++) summer.add(coef(i));
        summer.add(output());
        summer.add(quality());
        SPECS.put(AutomationKind.ANALOG_SUMMER, spec(AutomationKind.ANALOG_SUMMER, summer, BUTTON_REPLACE));
        List<Row> integ = new ArrayList<>();
        for (int i = 0; i < 3; i++) integ.add(coef(i));
        integ.add(new Row(TI.get(), (u, c) -> milli(u.param(AutomationUnit.D_SPAN)), v -> seconds(v / 1000.0),
                (u, c, d, f) -> u.setParam(AutomationUnit.D_SPAN, timeKnob(u.param(AutomationUnit.D_SPAN), d, f, 0.1, 1e4))));
        integ.add(new Row(MODE.get(), (u, c) -> u.intParam(AutomationUnit.I_MODE), v -> name(INT_MODES, v),
                (u, c, d, f) -> cycle(u, AutomationUnit.I_MODE, d, IntegratorMode.values().length)));
        integ.add(output());
        integ.add(quality());
        SPECS.put(AutomationKind.ANALOG_INTEGRATOR, spec(AutomationKind.ANALOG_INTEGRATOR, integ, BUTTON_CALIBRATE, BUTTON_REPLACE));
        SPECS.put(AutomationKind.ANALOG_DIFFERENTIATOR, spec(AutomationKind.ANALOG_DIFFERENTIATOR, List.of(
                new Row(TD.get(), (u, c) -> milli(u.param(AutomationUnit.D_SPAN)), v -> seconds(v / 1000.0),
                        (u, c, d, f) -> u.setParam(AutomationUnit.D_SPAN, timeKnob(u.param(AutomationUnit.D_SPAN), d, f, 0.01, 1e3))),
                new Row(N.get(), (u, c) -> (int) u.param(AutomationUnit.D_RETURN), v -> Component.literal(Integer.toString(v)),
                        (u, c, d, f) -> u.setParam(AutomationUnit.D_RETURN, clamp(u.param(AutomationUnit.D_RETURN) + d, 2, 20))),
                output(), quality()), BUTTON_REPLACE));
        SPECS.put(AutomationKind.ANALOG_MULTIPLIER, spec(AutomationKind.ANALOG_MULTIPLIER, List.of(
                new Row(MODE.get(), (u, c) -> u.intParam(AutomationUnit.I_MODE), v -> name(MULT_MODES, v),
                        (u, c, d, f) -> cycle(u, AutomationUnit.I_MODE, d, MultiplierMode.values().length)),
                output(), quality()), BUTTON_REPLACE));
        SPECS.put(AutomationKind.ANALOG_FUNCTION, spec(AutomationKind.ANALOG_FUNCTION, List.of(
                new Row(POINT.get(), (u, c) -> -10 + 2 * c, v -> Component.literal(v + " V"), (u, c, d, f) -> c.set(Math.floorMod(c.get() + d, 11))),
                new Row(Y.get(), (u, c) -> centi(u.param(AutomationUnit.D_TABLE + c)), AutomationPanels::volts,
                        (u, c, d, f) -> u.setParam(AutomationUnit.D_TABLE + c.get(),
                                knob(u.param(AutomationUnit.D_TABLE + c.get()) + d * (f ? 0.01 : 0.1), -10.5, 10.5))),
                output(), quality()), BUTTON_REPLACE));
        SPECS.put(AutomationKind.ANALOG_COMPARATOR, spec(AutomationKind.ANALOG_COMPARATOR, List.of(
                new Row(HYST.get(), (u, c) -> centi(u.param(AutomationUnit.D_SET)), AutomationPanels::volts,
                        (u, c, d, f) -> u.setParam(AutomationUnit.D_SET, knob(u.param(AutomationUnit.D_SET) + d * (f ? 0.01 : 0.05), 0, 5))),
                new Row(OUTPUT.get(), (u, c) -> u.output() > 0.5 ? 1 : 0, v -> v == 1 ? TRIPPED.get() : NORMAL.get(), null),
                quality()), BUTTON_REPLACE));
        SPECS.put(AutomationKind.MANUAL_STATION, spec(AutomationKind.MANUAL_STATION, List.of(
                new Row(SP.get(), (u, c) -> centi(u.param(AutomationUnit.D_SET)), AutomationPanels::volts,
                        (u, c, d, f) -> u.setParam(AutomationUnit.D_SET, knob(u.param(AutomationUnit.D_SET) + d * (f ? 0.01 : 0.1), 0, 10))),
                new Row(MODE.get(), (u, c) -> u.stationMode() == StationMode.MANUAL ? 1 : 0, v -> v == 1 ? MODE_MAN.get() : MODE_AUTO.get(), null),
                new Row(OUT_MA.get(), (u, c) -> centi(u.output(0)), v -> Component.literal(PanelText.num(v / 100.0, 2) + " mA"), null),
                new Row(PV.get(), (u, c) -> Double.isNaN(u.stationPv()) ? Integer.MIN_VALUE : centi(u.stationPv()), AutomationPanels::volts, null)),
                BUTTON_AUTO, BUTTON_MAN, BUTTON_DOWN, BUTTON_UP));
        SPECS.put(AutomationKind.SIGNAL_CONVERTER, spec(AutomationKind.SIGNAL_CONVERTER, List.of(
                new Row(DIRECTION.get(), (u, c) -> u.intParam(AutomationUnit.I_POSITION), v -> name(DIRECTIONS, v),
                        (u, c, d, f) -> cycle(u, AutomationUnit.I_POSITION, d, 2)),
                new Row(OUTPUT.get(), (u, c) -> centi(u.output()), v -> Component.literal(PanelText.num(v / 100.0, 2)), null),
                quality()), BUTTON_REPLACE));
    }

    private AutomationPanels() {
    }

    /** Щит блока; {@code null} — у блока щита нет (кнопки, лампы, ключи). */
    @Nullable
    public static Spec of(AutomationKind kind) {
        return SPECS.get(kind);
    }

    public static List<PanelLayout> all() {
        List<PanelLayout> out = new ArrayList<>();
        for (Spec s : SPECS.values()) out.add(s.layout);
        return out;
    }

    private static Spec spec(AutomationKind kind, List<Row> rows, int... extra) {
        int h = TOP + rows.size() * ROW + (extra.length > 0 ? 26 : 6);
        PanelLayout.Builder b = PanelLayout.builder("automation_" + kind.name().toLowerCase(Locale.ROOT), W, h, rows.size()).noInventory();
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            int y = TOP + i * ROW, ch = i;
            b.caption(8, y + 3, r.label);
            b.readout(84, y, r.adjust != null ? 96 : 140, v -> r.format.apply(v.raw(ch)), null);
            if (r.adjust != null) {
                b.button(184, y, 20, 2 * i, Component.literal("−"), null);
                b.button(206, y, 20, 2 * i + 1, Component.literal("+"), null);
            }
        }
        int x = 8, y = TOP + rows.size() * ROW + 6;
        for (int id : extra) {
            Tr label = switch (id) {
                case BUTTON_REPLACE -> REPLACE;
                case BUTTON_CALIBRATE -> CALIBRATE;
                case BUTTON_AUTO -> AUTO;
                case BUTTON_MAN -> MAN;
                case BUTTON_DOWN -> DOWN;
                default -> UP;
            };
            b.button(x, y, 52, id, label.get(), null);
            x += 56;
        }
        return new Spec(b.build(), List.copyOf(rows));
    }

    // ================================================================ строки

    private static Row relayState() {
        return new Row(STATE.get(), (u, c) -> u.relayState().ordinal(), v -> name(RELAY_STATES, v), null);
    }

    private static Row ops() {
        return new Row(OPS.get(), (u, c) -> (int) Math.min(Integer.MAX_VALUE, u.relayOps()), v -> Component.literal(Integer.toString(v)), null);
    }

    private static Row coil() {
        return new Row(COIL.get(), (u, c) -> (int) Math.round(u.coilVoltage() * 10), v -> Component.literal(PanelText.num(v / 10.0, 1) + " V"), null);
    }

    private static Row output() {
        return new Row(OUTPUT.get(), (u, c) -> centi(u.output()), AutomationPanels::volts, null);
    }

    private static Row quality() {
        return new Row(STATUS.get(), (u, c) -> u.conflict() ? 10 : u.algebraicLoop() ? 11 : u.tubeFailed() ? 12 : u.calDue() ? 13 : u.quality().ordinal(),
                v -> switch (v) {
                    case 10 -> CONFLICT.get();
                    case 11 -> LOOP.get();
                    case 12 -> FAILED.get();
                    case 13 -> CAL_DUE.get();
                    default -> name(QUALITIES, v);
                }, null);
    }

    private static Row coef(int i) {
        int p = AutomationUnit.D_TABLE + i;
        return new Row(COEF.get(i + 1), (u, c) -> centi(u.param(p)), v -> Component.literal(PanelText.num(v / 100.0, 2)),
                (u, c, d, f) -> u.setParam(p, knob(u.param(p) + d * (f ? 0.01 : 0.1), -10, 10)));
    }

    private static Row wiper(int w) {
        return new Row(WIPER.get(w + 1), (u, c) -> u.intParam(AutomationUnit.I_TABLE + c * RelayBank.STEPPER_WIPERS + w),
                v -> v == 0 ? NONE.get() : Component.literal("Q" + v),
                (u, c, d, f) -> cycle(u, AutomationUnit.I_TABLE + c.get() * RelayBank.STEPPER_WIPERS + w, d, 9));
    }

    // ================================================================ ручки

    private static void cycle(AutomationUnit u, int param, int dir, int n) {
        u.setIntParam(param, Math.floorMod(u.intParam(param) + dir, n));
    }

    private static void timerType(AutomationUnit u, Cursor c, int dir, boolean fine) {
        cycle(u, AutomationUnit.I_VARIANT, dir, TimeRelayType.values().length);
        TimeRelayType t = TimeRelayType.values()[u.intParam(AutomationUnit.I_VARIANT)];
        u.setParam(AutomationUnit.D_SET, clamp(u.param(AutomationUnit.D_SET), t.minS, t.maxS));
    }

    private static void timerSet(AutomationUnit u, Cursor c, int dir, boolean fine) {
        TimeRelayType t = TimeRelayType.values()[Math.floorMod(u.intParam(AutomationUnit.I_VARIANT), 3)];
        double v = fine ? timeKnob(u.param(AutomationUnit.D_SET), dir, true, t.minS, t.maxS)
                : series(u.param(AutomationUnit.D_SET), dir, t.minS, t.maxS);
        u.setParam(AutomationUnit.D_SET, v);
    }

    private static void camAngle(AutomationUnit u, int param, int dir) {
        u.setParam(param, Math.floorMod((int) u.param(param) + 5 * dir, 360));
    }

    /** Шаг уставки реле процесса: 1 % шкалы, мелкий 0,1 %. */
    private static void processStep(AutomationUnit u, int param, int dir, boolean fine) {
        double span = u.param(AutomationUnit.D_SPAN);
        u.setParam(param, clamp(u.param(param) + dir * span * (fine ? 0.001 : 0.01), 0, span * 1.2));
    }

    private static void processReturn(AutomationUnit u, int dir, boolean fine) {
        double span = u.param(AutomationUnit.D_SPAN), v = u.param(AutomationUnit.D_RETURN);
        if (Double.isNaN(v)) v = 0.05 * span;
        u.setParam(AutomationUnit.D_RETURN, clamp(v + dir * span * (fine ? 0.001 : 0.01), 0, span * 0.5));
    }

    /** Следующая ступень ряда {@link #SERIES} в пределах. */
    static double series(double v, int dir, double lo, double hi) {
        List<Double> steps = new ArrayList<>();
        for (int e = -2; e <= 4; e++) for (double s : SERIES) steps.add(s * Math.pow(10, e));
        double best = dir > 0 ? hi : lo;
        for (double s : steps) {
            if (s < lo - 1e-9 || s > hi + 1e-9) continue;
            if (dir > 0 && s > v * (1 + 1e-9) && s < best) best = s;
            if (dir < 0 && s < v * (1 - 1e-9) && s > best) best = s;
        }
        return best;
    }

    /** Ручка постоянной времени: ±1 в третьей значащей цифре (мелко) или ряд. */
    private static double timeKnob(double v, int dir, boolean fine, double lo, double hi) {
        if (!fine) return series(v, dir, lo, hi);
        double step = Math.pow(10, Math.floor(Math.log10(v)) - 2);
        return clamp(Math.round((v + dir * step) / step) * step, lo, hi);
    }

    private static double knob(double v, double lo, double hi) {
        return clamp(Math.round(v * 100) / 100.0, lo, hi);
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : v > hi ? hi : v;
    }

    // ================================================================ коды и надписи

    private static int milli(double v) {
        return (int) Math.round(Math.min(2e6, v) * 1000);
    }

    private static int centi(double v) {
        return (int) Math.round(v * 100);
    }

    private static Component name(Tr[] names, int i) {
        return i >= 0 && i < names.length ? names[i].get() : NONE.get();
    }

    private static Component seconds(double s) {
        if (s >= 60) return Component.literal(PanelText.num(s / 60, s >= 600 ? 0 : 1) + " min");
        return Component.literal(PanelText.num(s, s < 1 ? 2 : s < 10 ? 2 : 1) + " s");
    }

    private static Component degrees(int v) {
        return Component.literal(v + "°");
    }

    private static Component volts(int v) {
        return v == Integer.MIN_VALUE ? NONE.get() : Component.literal(PanelText.num(v / 100.0, 2) + " V");
    }

    /** Величина реле процесса в единицах шкалы: кПа ×10, мм, °C ×10, кг/с ×100; старшие 2 бита — вид. */
    private static int processCode(AutomationUnit u, double x) {
        int kind = Math.floorMod(u.intParam(AutomationUnit.I_VARIANT), 4);
        int v = switch (ProcessSwitchKind.values()[kind]) {
            case PRESSURE -> (int) Math.round(x / 100);
            case LEVEL -> (int) Math.round(x * 1000);
            case TEMPERATURE -> (int) Math.round((x - 273.15) * 10);
            case FLOW -> (int) Math.round(x * 100);
        };
        return (Math.max(-(1 << 28), Math.min(1 << 28, v)) << 2) | kind;
    }

    private static int processDeltaCode(AutomationUnit u, double dx) {
        int kind = Math.floorMod(u.intParam(AutomationUnit.I_VARIANT), 4);
        double scaled = switch (ProcessSwitchKind.values()[kind]) {
            case PRESSURE -> dx / 100;
            case LEVEL -> dx * 1000;
            case TEMPERATURE -> dx * 10;
            case FLOW -> dx * 100;
        };
        return ((int) Math.round(Math.min(1 << 28, scaled)) << 2) | kind;
    }

    private static Component processValue(int code) {
        int kind = code & 3, v = code >> 2;
        return switch (ProcessSwitchKind.values()[kind]) {
            case PRESSURE -> Component.literal(PanelText.num(v / 10.0, 1) + " kPa");
            case LEVEL -> Component.literal(v + " mm");
            case TEMPERATURE -> Component.literal(PanelText.num(v / 10.0, 1) + " °C");
            case FLOW -> Component.literal(PanelText.num(v / 100.0, 2) + " kg/s");
        };
    }

    private static Component processDelta(int code) {
        return code == Integer.MIN_VALUE ? AUTO_RETURN.get() : processValue(code);
    }

    /** Состояния реле — для подписи в сообщениях. */
    static Component relayStateName(RelayState s) {
        return RELAY_STATES[s.ordinal()].get();
    }

    static Component qualityName(Quality q) {
        return QUALITIES[q.ordinal()].get();
    }
}
