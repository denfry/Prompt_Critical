package dev.denfry.promptcritical.sim.automation;

import java.util.ArrayList;
import java.util.List;

/**
 * Блок автоматики мира и его клеммы (03d §3d.3.1, §3d.4.1, 03c §3c.2): обозначения по EN 50005, тип и роль клеммы.
 * Порядок клемм — порядок узлов {@link AutomationUnit#setNets}; положение на лицевой панели знает мод.
 *
 * <p>Клеммы механизмов ({@code *_IO}) — катушки и блок-контакты устройств мира: выключателя КРУ или щита
 * 0,4 кВ (+/− оперативного тока, электромагниты включения и отключения на «−»), пускателя двигателя, потребителя.
 */
public enum AutomationKind {
    RELAY(d("A1", "A2", "11", "12", "14", "21", "22", "24")),
    RELAY_LATCHING(d("A1", "A2", "B1", "B2", "11", "12", "14", "21", "22", "24")),
    TIME_RELAY(d("A1", "A2", "15", "16", "18", "25", "26", "28")),
    STEPPING_SWITCH(d("A1", "A2", "B1", "B2", "C1", "C2", "C3", "Q1", "Q2", "Q3", "Q4", "Q5", "Q6", "Q7", "Q8")),
    CAM_CONTROLLER(cams()),
    PROCESS_SWITCH(d("11", "12", "14")),
    POWER_SUPPLY(d("+24", "0")),
    PUSHBUTTON(d("13", "14", "11", "12")),
    SELECTOR_SWITCH(d("C", "1", "2", "3")),
    KEY_SWITCH(d("C", "ON", "OFF")),
    INDICATOR_LAMP(d("X1", "X2")),
    REDSTONE_INTERFACE(d("A1", "A2", "13", "14")),
    GAS_DETECTOR(List.of(t("11"), t("12"), t("14"), t("21"), t("22"), t("24"), out("OUT", TerminalType.ANALOG_CURRENT))),
    ANALOG_SUMMER(List.of(in("X1"), in("X2"), in("X3"), in("X4"), out("Y", TerminalType.ANALOG_VOLTAGE))),
    ANALOG_INTEGRATOR(List.of(in("X1"), in("X2"), in("X3"), in("IC"), out("Y", TerminalType.ANALOG_VOLTAGE), t("IC/M"),
            t("HOLD"), t("0"))),
    ANALOG_DIFFERENTIATOR(List.of(in("X"), out("Y", TerminalType.ANALOG_VOLTAGE))),
    ANALOG_MULTIPLIER(List.of(in("X1"), in("X2"), out("Y", TerminalType.ANALOG_VOLTAGE))),
    ANALOG_FUNCTION(List.of(in("X"), out("Y", TerminalType.ANALOG_VOLTAGE))),
    ANALOG_COMPARATOR(List.of(in("X1"), in("X2"), t("11"), t("12"), t("14"))),
    MANUAL_STATION(List.of(in("AUTO"), in("PV"), t("MAN"), t("0"), out("SP", TerminalType.ANALOG_VOLTAGE),
            out("OUT", TerminalType.ANALOG_CURRENT), out("TRK", TerminalType.ANALOG_VOLTAGE), t("13"), t("14"))),
    /** Направление задаёт уставка: I/V — вход «I», выход «U»; V/I — наоборот. */
    SIGNAL_CONVERTER(List.of(new Terminal("I", TerminalType.ANALOG_CURRENT, Role.CONFIGURED),
            new Terminal("U", TerminalType.ANALOG_VOLTAGE, Role.CONFIGURED))),
    /**
     * ПЛК (A3, {@link dev.denfry.promptcritical.sim.automation.plc.PlcCpu}): питание 24 В на L+/M; дискретные входы
     * I0…I7 — оптроны на M; релейные выходы Q0…Q7 — контакты от общего 1L; аналоговые входы AI0…AI3 и выходы
     * AO0…AO1 — сигналы A2 в вольтах.
     */
    PLC(plc()),
    /**
     * Логический блок ПАЗ (A3, {@link dev.denfry.promptcritical.sim.automation.sis.SisLogic}): питание +24/0, датчики
     * 4–20 мА на AI1…AI3, выходное реле 11-12-14 под током, пока процесс разрешён (снятие питания = останов),
     * сигнализация 21-24, вход квитирования RST на «0».
     */
    SIS_LOGIC(List.of(t("+24"), t("0"), inC("AI1"), inC("AI2"), inC("AI3"), t("11"), t("12"), t("14"), t("21"), t("24"), t("RST"))),
    BREAKER_IO(d("+", "-", "CLOSE", "TRIP", "13", "14", "21", "22")),
    MOTOR_IO(d("A1", "A2", "13", "14")),
    CONSUMER_IO(d("13", "14")),
    /**
     * Шкаф СУЗ энергетического реактора: «+/−» — контроль цепи, «SCRAM» — катушка внешней АЗ (сигнал ПАЗ к «−»),
     * «13–14» — контакт «АЗ сработала», «N» — мощность по ионизационной камере, 4–20 мА на 0–120 %.
     */
    REACTOR_IO(List.of(t("+"), t("-"), t("SCRAM"), t("13"), t("14"), out("N", TerminalType.ANALOG_CURRENT)));

    /** Роль клеммы в цепи сигналов A2: вход, выход (источник сигнала цепи) или узел A1. */
    public enum Role { NODE, INPUT, OUTPUT, CONFIGURED }

    public record Terminal(String label, TerminalType type, Role role) {
    }

    private final List<Terminal> terminals;

    AutomationKind(List<Terminal> terminals) {
        this.terminals = terminals;
    }

    public List<Terminal> terminals() {
        return terminals;
    }

    public int terminalCount() {
        return terminals.size();
    }

    public Terminal terminal(int i) {
        return terminals.get(i);
    }

    /** Индекс клеммы по обозначению; -1 — нет такой. */
    public int indexOf(String label) {
        for (int i = 0; i < terminals.size(); i++) if (terminals.get(i).label.equals(label)) return i;
        return -1;
    }

    private static Terminal t(String label) {
        return new Terminal(label, TerminalType.DISCRETE, Role.NODE);
    }

    private static Terminal in(String label) {
        return new Terminal(label, TerminalType.ANALOG_VOLTAGE, Role.INPUT);
    }

    private static Terminal inC(String label) {
        return new Terminal(label, TerminalType.ANALOG_CURRENT, Role.INPUT);
    }

    private static Terminal out(String label, TerminalType type) {
        return new Terminal(label, type, Role.OUTPUT);
    }

    private static List<Terminal> d(String... labels) {
        List<Terminal> out = new ArrayList<>(labels.length);
        for (String l : labels) out.add(t(l));
        return List.copyOf(out);
    }

    private static List<Terminal> plc() {
        List<Terminal> out = new ArrayList<>(List.of(t("L+"), t("M")));
        for (int i = 0; i < 8; i++) out.add(t("I" + i));
        out.add(t("1L"));
        for (int i = 0; i < 8; i++) out.add(t("Q" + i));
        for (int i = 0; i < 4; i++) out.add(in("AI" + i));
        for (int i = 0; i < 2; i++) out.add(out("AO" + i, TerminalType.ANALOG_VOLTAGE));
        return List.copyOf(out);
    }

    /** 12 кулачков по контакту: «Kn.1»–«Kn.2». */
    private static List<Terminal> cams() {
        List<Terminal> out = new ArrayList<>(2 * RelayBank.CAMS);
        for (int c = 1; c <= RelayBank.CAMS; c++) {
            out.add(t("K" + c + ".1"));
            out.add(t("K" + c + ".2"));
        }
        return List.copyOf(out);
    }
}
