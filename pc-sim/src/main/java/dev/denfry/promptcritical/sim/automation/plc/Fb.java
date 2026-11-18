package dev.denfry.promptcritical.sim.automation.plc;

import java.util.List;
import java.util.Locale;

/**
 * Стандартные функциональные блоки ПЛК (IEC 61131-3 §2.5.2.3) и регулятор. Экземпляры не объявляются: имя задаёт
 * тип и номер ({@code TON3}, {@code CTU0}, {@code PID1}, {@code PC_ANTISURGE0}). Номер экземпляра в процессоре —
 * {@code kind.ordinal() * PER_KIND + n}.
 */
public final class Fb {
    /** Экземпляров каждого типа. */
    public static final int PER_KIND = 16;
    public static final int COUNT = Kind.values().length * PER_KIND;

    private Fb() {
    }

    /**
     * Тип блока: входы и выходы по стандарту. Таймеры считают в секундах (литерал {@code T#5S}); у счётчиков
     * CV — целое; ПИД — идеальная форма с ограничением и антинасыщением, вход и выход в вольтах шкалы.
     */
    public enum Kind {
        TON(List.of("IN", "PT"), List.of("Q", "ET"), 0b01),
        TOF(List.of("IN", "PT"), List.of("Q", "ET"), 0b01),
        TP(List.of("IN", "PT"), List.of("Q", "ET"), 0b01),
        CTU(List.of("CU", "R", "PV"), List.of("Q", "CV"), 0b01),
        CTD(List.of("CD", "LD", "PV"), List.of("Q", "CV"), 0b01),
        R_TRIG(List.of("CLK"), List.of("Q"), 0b1),
        F_TRIG(List.of("CLK"), List.of("Q"), 0b1),
        PID(List.of("PV", "SP", "KP", "TI", "TD", "LO", "HI", "MAN", "XM"), List.of("Y"), 0),
        /** Антипомпажный регулятор 03d §3d.5.12 ({@link AntiSurge}). */
        PC_ANTISURGE(List.of("DPO", "PS", "PD", "KS", "BCTRL", "BRTL", "KP", "TI", "RATE", "MAN", "XM"),
                List.of("Y", "SS", "DEV", "CNT"), 0);

        public final List<String> inputs, outputs;
        private final int boolOutputs;

        Kind(List<String> inputs, List<String> outputs, int boolOutputs) {
            this.inputs = inputs;
            this.outputs = outputs;
            this.boolOutputs = boolOutputs;
        }

        public boolean boolOutput(int member) {
            return (boolOutputs >> member & 1) == 1;
        }

        public int input(String name) {
            return inputs.indexOf(name.toUpperCase(Locale.ROOT));
        }

        public int output(String name) {
            return outputs.indexOf(name.toUpperCase(Locale.ROOT));
        }
    }

    public static Kind kindOf(int instance) {
        return Kind.values()[instance / PER_KIND];
    }

    /** Экземпляр по имени ({@code TON3}); -1 — не блок или номер вне диапазона. */
    public static int instance(String name) {
        String n = name.toUpperCase(Locale.ROOT);
        for (Kind k : Kind.values()) {
            String p = k.name();
            if (n.length() > p.length() && n.startsWith(p) && n.substring(p.length()).chars().allMatch(Character::isDigit)) {
                int i = Integer.parseInt(n.substring(p.length()));
                return i < PER_KIND ? k.ordinal() * PER_KIND + i : -1;
            }
        }
        return -1;
    }
}
