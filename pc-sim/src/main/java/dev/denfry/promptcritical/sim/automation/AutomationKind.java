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
    BREAKER_IO(d("+", "-", "CLOSE", "TRIP", "13", "14", "21", "22")),
    MOTOR_IO(d("A1", "A2", "13", "14")),
    CONSUMER_IO(d("13", "14"));

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

    private static Terminal out(String label, TerminalType type) {
        return new Terminal(label, type, Role.OUTPUT);
    }

    private static List<Terminal> d(String... labels) {
        List<Terminal> out = new ArrayList<>(labels.length);
        for (String l : labels) out.add(t(l));
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
