package dev.denfry.promptcritical.sim.automation.plc;

import dev.denfry.promptcritical.sim.automation.plc.IlProgram.IlError;
import dev.denfry.promptcritical.sim.automation.plc.IlProgram.IlError.Kind;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Функциональные блок-схемы FBD (IEC 61131-3 §4.3). Графический язык записан списком соединений — по строке на выход
 * блока, вложенные блоки — как провода между ними:
 *
 * <pre>
 * (*FBD*)
 * %QX0.0 := AND(OR(%IX0.0, %QX0.0), NOT(%IX0.1))    // пуск-стоп
 * T1 := TON(%QX0.0, T#5S)                            // экземпляр блока; его выход — T1.Q
 * %QX0.1 := T1.Q
 * %QW0 := MUL(%IW0, 2)
 * </pre>
 *
 * Функции: {@code AND OR XOR NOT MOVE ADD SUB MUL DIV GT GE LT LE EQ NE}; функциональные блоки с позиционными
 * входами: {@code TON/TOF/TP(IN, PT)}, {@code CTU(CU, R, PV)}, {@code CTD(CD, LD, PV)}, {@code R_TRIG/F_TRIG(CLK)},
 * регуляторы {@code PID(PV, SP, KP, TI, TD, LO, HI, MAN, XM)} и {@code PC_ANTISURGE(DPO, PS, PD, KS, BCTRL, BRTL, KP,
 * TI, RATE, MAN, XM)} (03d §3d.5.12).
 * Перевод — в ST ({@link StCompiler}); входы блоков, которые не переменная и не константа, проходят через биты
 * {@code %MX48…%MX63} (BOOL) или слова {@code %MW24…%MW31} (числа).
 */
public final class FbdCompiler {
    private static final Map<String, String> BINARY = Map.ofEntries(Map.entry("AND", "AND"), Map.entry("OR", "OR"),
            Map.entry("XOR", "XOR"), Map.entry("ADD", "+"), Map.entry("SUB", "-"), Map.entry("MUL", "*"), Map.entry("DIV", "/"),
            Map.entry("GT", ">"), Map.entry("GE", ">="), Map.entry("LT", "<"), Map.entry("LE", "<="), Map.entry("EQ", "="),
            Map.entry("NE", "<>"));
    private static final int WORD_FIRST = 24;

    private FbdCompiler() {
    }

    public static boolean isFbd(String source) {
        if (source == null) return false;
        String c = source.strip().toUpperCase(Locale.ROOT).replace(" ", "");
        return c.startsWith("(*FBD*)") || c.startsWith("{FBD}");
    }

    public static IlProgram compile(String source) {
        String src = source == null ? "" : source;
        if (src.length() > IlCompiler.MAX_CHARS) return new IlProgram(src, List.of(), List.of(new IlError(0, Kind.TOO_LONG, "")));
        try {
            LdCompiler.Translation t = translate(src);
            return LdCompiler.remap(src, StCompiler.compile(t.st()), t.lineMap());
        } catch (LdCompiler.Fail f) {
            return new IlProgram(src, List.of(), List.of(f.error));
        }
    }

    private static final class Ctx {
        final List<String> out = new ArrayList<>();
        final List<Integer> map = new ArrayList<>();
        final Map<String, String> instances = new LinkedHashMap<>();
        int bit = LdCompiler.TEMP_FIRST, word = WORD_FIRST;
        int line;

        void add(String s) {
            out.add(s);
            map.add(line);
        }
    }

    static LdCompiler.Translation translate(String src) {
        Ctx c = new Ctx();
        String[] lines = src.split("\\R", -1);
        for (int i = 0; i < lines.length; i++) {
            String raw = lines[i];
            if (i == 0 && isFbd(raw)) continue;
            int k = raw.indexOf("//");
            String l = (k >= 0 ? raw.substring(0, k) : raw).replaceAll("\\(\\*.*?\\*\\)", "").strip();
            if (l.isEmpty() || l.equalsIgnoreCase("NETWORK")) continue;
            c.line = i + 1;
            int eq = l.indexOf(":=");
            if (eq <= 0) throw new LdCompiler.Fail(c.line, l);
            String target = l.substring(0, eq).strip(), rhs = l.substring(eq + 2).strip();
            if (rhs.endsWith(";")) rhs = rhs.substring(0, rhs.length() - 1).strip();
            Call top = parse(rhs, new int[] {0}, c.line);
            String fb = top != null && isFb(top.name) ? top.name : null;
            if (fb != null) {
                String prev = c.instances.putIfAbsent(target, fb);
                if (prev != null && !prev.equals(fb)) throw new LdCompiler.Fail(c.line, target);
                List<String> formals = Fb.Kind.valueOf(fb).inputs;
                if (top.args.size() > formals.size()) throw new LdCompiler.Fail(c.line, fb + " has " + formals.size() + " inputs");
                StringBuilder call = new StringBuilder(target).append("(");
                for (int a = 0; a < top.args.size(); a++) {
                    String v = operand(top.args.get(a), c, !isTime(top.args.get(a)) && !isNumericInput(fb, a));
                    if (a > 0) call.append(", ");
                    call.append(formals.get(a)).append(" := ").append(v);
                }
                c.add(call.append(");").toString());
            } else {
                c.add(target + " := " + expr(top, rhs, c) + ";");
            }
        }
        List<String> out = new ArrayList<>();
        List<Integer> map = new ArrayList<>();
        out.add("PROGRAM fbd");
        map.add(0);
        if (!c.instances.isEmpty()) {
            out.add("VAR");
            map.add(0);
            for (Map.Entry<String, String> e : c.instances.entrySet()) {
                out.add(e.getKey() + " : " + e.getValue() + ";");
                map.add(0);
            }
            out.add("END_VAR");
            map.add(0);
        }
        out.addAll(c.out);
        map.addAll(c.map);
        out.add("END_PROGRAM");
        map.add(0);
        return new LdCompiler.Translation(String.join("\n", out), map);
    }

    private static boolean isFb(String name) {
        return switch (name) {
            case "TON", "TOF", "TP", "CTU", "CTD", "R_TRIG", "F_TRIG", "PID", "PC_ANTISURGE" -> true;
            default -> false;
        };
    }

    private static boolean isNumericInput(String fb, int i) {
        if (fb.equals("PID")) return i != 7; // MAN — BOOL
        if (fb.equals("PC_ANTISURGE")) return i != 9;
        return (fb.equals("CTU") || fb.equals("CTD")) && i == 2;
    }

    private static boolean isTime(String s) {
        String u = s.strip().toUpperCase(Locale.ROOT);
        return u.startsWith("T#") || u.startsWith("TIME#");
    }

    /** Вход блока: переменная или константа — как есть, выражение — через промежуточный бит или слово. */
    private static String operand(String arg, Ctx c, boolean bool) {
        String a = arg.strip();
        if (a.matches("(?i)%?[A-Za-z_][\\w.]*|-?\\d+(\\.\\d+)?|(?i)TRUE|FALSE|T#\\w+|TIME#\\w+")) return a;
        Call call = parse(a, new int[] {0}, c.line);
        String e = expr(call, a, c);
        String tmp;
        if (bool) {
            if (c.bit > 63) throw new LdCompiler.Fail(c.line, "too many intermediate wires");
            tmp = "%MX" + c.bit++;
        } else {
            if (c.word > 31) throw new LdCompiler.Fail(c.line, "too many intermediate wires");
            tmp = "%MW" + c.word++;
        }
        c.add(tmp + " := " + e + ";");
        return tmp;
    }

    private record Call(String name, List<String> args) {
    }

    /** Разбор {@code NAME(a, b(…), c)}; null — не вызов (переменная или константа). */
    private static Call parse(String s, int[] pos, int line) {
        String t = s.strip();
        int p = t.indexOf('(');
        if (p <= 0 || !t.endsWith(")") || !t.substring(0, p).strip().matches("[A-Za-z_]\\w*")) return null;
        String name = t.substring(0, p).strip().toUpperCase(Locale.ROOT);
        List<String> args = new ArrayList<>();
        int depth = 0, start = p + 1;
        for (int i = p + 1; i < t.length() - 1; i++) {
            char ch = t.charAt(i);
            if (ch == '(') depth++;
            else if (ch == ')') depth--;
            else if (ch == ',' && depth == 0) {
                args.add(t.substring(start, i).strip());
                start = i + 1;
            }
            if (depth < 0) throw new LdCompiler.Fail(line, ")");
        }
        if (depth != 0) throw new LdCompiler.Fail(line, "(");
        String last = t.substring(start, t.length() - 1).strip();
        if (!last.isEmpty()) args.add(last);
        return new Call(name, args);
    }

    /** Выражение ST для функции (вложенные функции — скобками). */
    private static String expr(Call call, String raw, Ctx c) {
        if (call == null) return raw.strip();
        if (isFb(call.name)) throw new LdCompiler.Fail(c.line, call.name + " must have its own line: name := " + call.name + "(…)");
        List<String> a = new ArrayList<>();
        for (String s : call.args) a.add(expr(parse(s, new int[] {0}, c.line), s, c));
        switch (call.name) {
            case "NOT" -> {
                if (a.size() != 1) throw new LdCompiler.Fail(c.line, "NOT");
                return "NOT (" + a.get(0) + ")";
            }
            case "MOVE" -> {
                if (a.size() != 1) throw new LdCompiler.Fail(c.line, "MOVE");
                return a.get(0);
            }
            default -> {
                String op = BINARY.get(call.name);
                if (op == null) throw new LdCompiler.Fail(c.line, call.name);
                if (a.size() < 2) throw new LdCompiler.Fail(c.line, call.name + " needs two inputs");
                StringBuilder b = new StringBuilder("(");
                for (int i = 0; i < a.size(); i++) {
                    if (i > 0) b.append(' ').append(op).append(' ');
                    b.append('(').append(a.get(i)).append(')');
                }
                return b.append(')').toString();
            }
        }
    }
}
