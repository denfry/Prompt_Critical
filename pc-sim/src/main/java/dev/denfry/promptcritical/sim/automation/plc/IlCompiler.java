package dev.denfry.promptcritical.sim.automation.plc;

import dev.denfry.promptcritical.sim.automation.plc.IlProgram.Area;
import dev.denfry.promptcritical.sim.automation.plc.IlProgram.IlError;
import dev.denfry.promptcritical.sim.automation.plc.IlProgram.IlError.Kind;
import dev.denfry.promptcritical.sim.automation.plc.IlProgram.Instr;
import dev.denfry.promptcritical.sim.automation.plc.IlProgram.Op;
import dev.denfry.promptcritical.sim.automation.plc.IlProgram.Operand;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Компилятор IL (IEC 61131-3, 2-е изд., §3.2) — подмножество, которого хватает ПЛК 1970–80-х (Simatic S5 STL,
 * «Ломиконт»): строка — {@code [метка:] ОПЕРАТОР[модификаторы] [операнд]}; модификаторы N (инверсия операнда),
 * C (условный переход, возврат, вызов) и «(» (отложить оператор до «)»). Комментарии {@code (* … *)} и {@code //}.
 * Регистр не важен.
 *
 * <p>Операнды: {@code %IX0.0…%IX0.7} — дискретные входы, {@code %QX0.0…%QX0.7} — выходы, {@code %MX0…%MX63} —
 * биты памяти; {@code %IW0…%IW3} — аналоговые входы, {@code %QW0…%QW7} — аналоговые выходы (в мире работают те, что выведены на клеммы; все восемь — на стенде двойника), {@code %MW0…%MW31} —
 * слова памяти (в вольтах шкалы или любых единицах); константы {@code TRUE}, {@code FALSE}, числа и время
 * {@code T#1M30S}, {@code T#200MS} (в секундах); выходы блоков {@code TON3.Q}, {@code CTU0.CV}, {@code PID0.Y}.
 * Вызов блока: {@code CAL TON3(IN := %IX0.1, PT := T#5S)}; неуказанные входы сохраняют прошлые значения.
 *
 * <p>Пределы защищают сервер от игрока: не больше {@link #MAX_CHARS} знаков, {@link #MAX_INSTR} инструкций и
 * {@link #MAX_DEPTH} вложенных скобок.
 */
public final class IlCompiler {
    public static final int MAX_CHARS = 8192, MAX_INSTR = 512, MAX_DEPTH = 8;
    public static final int IX = 8, QX = 8, MX = 64, IW = 4, QW = 8, MW = 32;

    private static final Pattern LABEL = Pattern.compile("^([A-Za-z_][A-Za-z0-9_]*)\\s*:(?!=)\\s*(.*)$");
    private static final Pattern CALL = Pattern.compile("^([A-Za-z_][A-Za-z0-9_]*)\\s*(?:\\((.*)\\))?$");
    private static final Pattern TIME = Pattern.compile("(?i)^(?:T|TIME)#(?:(\\d+(?:\\.\\d+)?)H)?(?:(\\d+(?:\\.\\d+)?)M(?!S))?"
            + "(?:(\\d+(?:\\.\\d+)?)S)?(?:(\\d+(?:\\.\\d+)?)MS)?$");

    private IlCompiler() {
    }

    public static IlProgram compile(String source) {
        String src = source == null ? "" : source;
        List<IlError> errors = new ArrayList<>();
        if (src.length() > MAX_CHARS) return new IlProgram(src, List.of(), List.of(new IlError(0, Kind.TOO_LONG, "")));
        String text = stripBlockComments(src);
        String[] lines = text.split("\n", -1);
        List<Instr> code = new ArrayList<>();
        Map<String, Integer> labels = new HashMap<>();
        List<String> jumpLabels = new ArrayList<>();
        int depth = 0;
        for (int ln = 0; ln < lines.length; ln++) {
            int lineNo = ln + 1;
            String s = lines[ln];
            int slash = s.indexOf("//");
            if (slash >= 0) s = s.substring(0, slash);
            s = s.strip();
            Matcher lm = LABEL.matcher(s);
            if (lm.matches()) {
                String label = lm.group(1).toUpperCase(Locale.ROOT);
                if (labels.putIfAbsent(label, code.size()) != null) errors.add(new IlError(lineNo, Kind.DUPLICATE_LABEL, label));
                s = lm.group(2).strip();
            }
            if (s.isEmpty()) continue;
            if (code.size() >= MAX_INSTR) {
                errors.add(new IlError(lineNo, Kind.TOO_LONG, ""));
                break;
            }
            if (s.equals(")")) {
                if (depth == 0) errors.add(new IlError(lineNo, Kind.UNBALANCED_PARENTHESIS, ")"));
                else depth--;
                code.add(new Instr(Op.CLOSE, false, false, false, null, -1, null, null, -1, lineNo));
                jumpLabels.add(null);
                continue;
            }
            int sp = firstSpace(s);
            String word = (sp < 0 ? s : s.substring(0, sp)).toUpperCase(Locale.ROOT);
            String arg = sp < 0 ? "" : s.substring(sp).strip();
            boolean open = word.endsWith("(");
            if (open) word = word.substring(0, word.length() - 1);
            Parsed p = parseOperator(word);
            if (p == null) {
                errors.add(new IlError(lineNo, Kind.UNKNOWN_OPERATOR, word));
                jumpLabels.add(null);
                code.add(null);
                continue;
            }
            if (open) {
                if (!p.op.bracketable()) {
                    errors.add(new IlError(lineNo, Kind.UNKNOWN_OPERATOR, word + "("));
                } else if (++depth > MAX_DEPTH) {
                    errors.add(new IlError(lineNo, Kind.NESTING_TOO_DEEP, word + "("));
                }
            }
            Instr in = switch (p.op) {
                case JMP -> {
                    if (arg.isEmpty()) {
                        errors.add(new IlError(lineNo, Kind.MISSING_OPERAND, word));
                        jumpLabels.add(null);
                        yield null;
                    }
                    jumpLabels.add(arg.toUpperCase(Locale.ROOT));
                    yield new Instr(Op.JMP, p.negate, p.conditional, false, null, -1, null, null, -1, lineNo);
                }
                case RET, NOT -> {
                    if (!arg.isEmpty()) errors.add(new IlError(lineNo, Kind.UNEXPECTED_OPERAND, arg));
                    yield new Instr(p.op.base, p.negate, p.conditional, false, null, -1, null, null, -1, lineNo);
                }
                case CAL -> call(arg, p, lineNo, errors);
                default -> {
                    if (arg.isEmpty()) {
                        if (open) yield new Instr(p.op.base, p.negate, false, true, null, -1, null, null, -1, lineNo);
                        errors.add(new IlError(lineNo, Kind.MISSING_OPERAND, word));
                        yield null;
                    }
                    Operand o = operand(arg);
                    if (o == null) {
                        errors.add(new IlError(lineNo, Kind.BAD_OPERAND, arg));
                        yield null;
                    }
                    if (p.op.stores() && !o.writable()) {
                        errors.add(new IlError(lineNo, Kind.NOT_WRITABLE, arg));
                        yield null;
                    }
                    yield new Instr(p.op.base, p.negate, false, open, o, -1, null, null, -1, lineNo);
                }
            };
            if (p.op != Ops.JMP) jumpLabels.add(null);
            code.add(in);
        }
        if (depth != 0) errors.add(new IlError(lines.length, Kind.UNBALANCED_PARENTHESIS, "("));
        // переходы: метка → индекс инструкции
        for (int i = 0; i < code.size(); i++) {
            String target = jumpLabels.get(i);
            Instr in = code.get(i);
            if (target == null || in == null) continue;
            Integer t = labels.get(target);
            if (t == null) errors.add(new IlError(in.line(), Kind.UNKNOWN_LABEL, target));
            else code.set(i, new Instr(Op.JMP, in.negate(), in.conditional(), false, null, -1, null, null, t, in.line()));
        }
        if (!errors.isEmpty()) return new IlProgram(src, List.of(), List.copyOf(errors));
        return new IlProgram(src, List.copyOf(code), List.of());
    }

    // ---------------------------------------------------------------- операторы

    /** Оператор со всеми модификаторами. {@code base} — оператор исполнения. */
    private enum Ops {
        LD(Op.LD), ST(Op.ST), S(Op.S), R(Op.R), AND(Op.AND), OR(Op.OR), XOR(Op.XOR), NOT(Op.NOT),
        ADD(Op.ADD), SUB(Op.SUB), MUL(Op.MUL), DIV(Op.DIV), MOD(Op.MOD), GT(Op.GT), GE(Op.GE), EQ(Op.EQ), NE(Op.NE), LE(Op.LE), LT(Op.LT),
        JMP(Op.JMP), RET(Op.RET), CAL(Op.CAL);

        final Op base;

        Ops(Op base) {
            this.base = base;
        }

        boolean bracketable() {
            return switch (this) {
                case AND, OR, XOR, ADD, SUB, MUL, DIV, MOD, GT, GE, EQ, NE, LE, LT -> true;
                default -> false;
            };
        }

        boolean stores() {
            return this == ST || this == S || this == R;
        }

        boolean negatable() {
            return switch (this) {
                case LD, ST, AND, OR, XOR -> true;
                default -> false;
            };
        }
    }

    private record Parsed(Ops op, boolean negate, boolean conditional) {
    }

    /** {@code LDN}, {@code ANDN}, {@code JMPC}, {@code JMPCN}, {@code RETC}, {@code CALC}… */
    private static Parsed parseOperator(String w) {
        for (Ops o : Ops.values()) {
            String n = o.name();
            if (w.equals(n)) return new Parsed(o, false, false);
            if (o.negatable() && w.equals(n + "N")) return new Parsed(o, true, false);
            if ((o == Ops.JMP || o == Ops.RET || o == Ops.CAL) && w.equals(n + "C")) return new Parsed(o, false, true);
            if ((o == Ops.JMP || o == Ops.RET || o == Ops.CAL) && w.equals(n + "CN")) return new Parsed(o, true, true);
        }
        return null;
    }

    private static Instr call(String arg, Parsed p, int lineNo, List<IlError> errors) {
        Matcher m = CALL.matcher(arg);
        if (!m.matches()) {
            errors.add(new IlError(lineNo, Kind.BAD_CALL, arg));
            return null;
        }
        int fb = Fb.instance(m.group(1));
        if (fb < 0) {
            errors.add(new IlError(lineNo, Kind.BAD_CALL, m.group(1)));
            return null;
        }
        Fb.Kind kind = Fb.kindOf(fb);
        List<Integer> params = new ArrayList<>();
        List<Operand> args = new ArrayList<>();
        String list = m.group(2) == null ? "" : m.group(2).strip();
        if (!list.isEmpty()) {
            for (String part : list.split(",")) {
                int eq = part.indexOf(":=");
                if (eq < 0) {
                    errors.add(new IlError(lineNo, Kind.BAD_CALL, part.strip()));
                    return null;
                }
                int in = kind.input(part.substring(0, eq).strip());
                Operand o = operand(part.substring(eq + 2).strip());
                if (in < 0 || o == null) {
                    errors.add(new IlError(lineNo, Kind.BAD_CALL, part.strip()));
                    return null;
                }
                params.add(in);
                args.add(o);
            }
        }
        return new Instr(Op.CAL, p.negate, p.conditional, false, null, fb, params.stream().mapToInt(Integer::intValue).toArray(),
                args.toArray(Operand[]::new), -1, lineNo);
    }

    // ---------------------------------------------------------------- операнды

    /** Операнд по тексту; {@code null} — не разобрать или адрес вне диапазона. */
    static Operand operand(String raw) {
        String t = raw.strip().toUpperCase(Locale.ROOT);
        if (t.equals("TRUE")) return Operand.constant(1);
        if (t.equals("FALSE")) return Operand.constant(0);
        Matcher tm = TIME.matcher(t);
        if (t.contains("#") && tm.matches()) {
            double s = 0;
            if (tm.group(1) != null) s += Double.parseDouble(tm.group(1)) * 3600;
            if (tm.group(2) != null) s += Double.parseDouble(tm.group(2)) * 60;
            if (tm.group(3) != null) s += Double.parseDouble(tm.group(3));
            if (tm.group(4) != null) s += Double.parseDouble(tm.group(4)) / 1000;
            return t.length() > 2 ? Operand.constant(s) : null;
        }
        if (t.startsWith("%")) return address(t.substring(1));
        try {
            double v = Double.parseDouble(t);
            return Double.isFinite(v) && !t.endsWith("D") && !t.endsWith("F") ? Operand.constant(v) : null;
        } catch (NumberFormatException e) {
            // не число — может быть выход блока
        }
        int dot = t.indexOf('.');
        if (dot > 0) {
            int fb = Fb.instance(t.substring(0, dot));
            if (fb < 0) return null;
            int member = Fb.kindOf(fb).output(t.substring(dot + 1));
            return member < 0 ? null : new Operand(Area.FB, fb, member, 0);
        }
        return null;
    }

    private static Operand address(String a) {
        try {
            if (a.startsWith("IX0.")) return ranged(Area.IX, Integer.parseInt(a.substring(4)), IX);
            if (a.startsWith("QX0.")) return ranged(Area.QX, Integer.parseInt(a.substring(4)), QX);
            if (a.startsWith("MX")) return ranged(Area.MX, Integer.parseInt(a.substring(2)), MX);
            if (a.startsWith("IW")) return ranged(Area.IW, Integer.parseInt(a.substring(2)), IW);
            if (a.startsWith("QW")) return ranged(Area.QW, Integer.parseInt(a.substring(2)), QW);
            if (a.startsWith("MW")) return ranged(Area.MW, Integer.parseInt(a.substring(2)), MW);
        } catch (NumberFormatException e) {
            return null;
        }
        return null;
    }

    private static Operand ranged(Area area, int i, int n) {
        return i >= 0 && i < n ? new Operand(area, i, 0, 0) : null;
    }

    // ---------------------------------------------------------------- текст

    private static int firstSpace(String s) {
        for (int i = 0; i < s.length(); i++) if (Character.isWhitespace(s.charAt(i))) return i;
        return -1;
    }

    /** {@code (* … *)} заменяются пробелами, переводы строк сохраняются — номера строк не сдвигаются. */
    private static String stripBlockComments(String s) {
        StringBuilder b = new StringBuilder(s.length());
        boolean in = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!in && c == '(' && i + 1 < s.length() && s.charAt(i + 1) == '*') {
                in = true;
                i++;
                continue;
            }
            if (in && c == '*' && i + 1 < s.length() && s.charAt(i + 1) == ')') {
                in = false;
                i++;
                continue;
            }
            b.append(in && c != '\n' ? ' ' : c);
        }
        return b.toString().replace("\r", "");
    }
}
