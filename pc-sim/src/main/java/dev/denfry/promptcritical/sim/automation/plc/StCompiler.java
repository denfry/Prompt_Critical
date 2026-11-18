package dev.denfry.promptcritical.sim.automation.plc;

import dev.denfry.promptcritical.sim.automation.plc.IlProgram.Area;
import dev.denfry.promptcritical.sim.automation.plc.IlProgram.IlError;
import dev.denfry.promptcritical.sim.automation.plc.IlProgram.IlError.Kind;
import dev.denfry.promptcritical.sim.automation.plc.IlProgram.Instr;
import dev.denfry.promptcritical.sim.automation.plc.IlProgram.Op;
import dev.denfry.promptcritical.sim.automation.plc.IlProgram.Operand;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Компилятор ST (Structured Text, IEC 61131-3 §3.3) в те же инструкции, что и {@link IlCompiler}: один
 * процессор, одно промежуточное представление для всех языков. Выражение раскладывается в цепочку
 * «загрузить — применить оператор», правый составной операнд уходит в скобку IL ({@code AND(} … {@code )}),
 * поэтому глубина вложенности ограничена тем же {@link IlCompiler#MAX_DEPTH}. Управляющие конструкции становятся
 * условными переходами; циклы не ограничены компилятором — их держит сторожевой таймер процессора.
 *
 * <p>Поддержано: {@code PROGRAM … END_PROGRAM} (необязательно), {@code VAR … END_VAR} с экземплярами блоков
 * ({@code T1 : TON;} — берёт свободный экземпляр с номера 15 вниз) и именами адресов
 * ({@code Start AT %IX0.0 : BOOL;}); присваивание {@code :=}; {@code IF/ELSIF/ELSE}, {@code CASE} с целыми
 * метками и диапазонами {@code 1..3}, {@code WHILE}, {@code REPEAT}, {@code FOR … BY константа}, {@code EXIT},
 * {@code RETURN}; вызов блока {@code T1(IN := %IX0.0, PT := T#5S, Q => %QX0.0)} — входы только переменные и
 * константы. Приоритет операторов по таблице 55: скобки; унарные {@code -}, {@code NOT}; {@code * / MOD};
 * {@code + -}; сравнения; {@code = <>}; {@code AND &}; {@code XOR}; {@code OR}. Комментарии {@code (* *)},
 * {@code //} и прагмы {@code { }}.
 *
 * <p>Язык программы определяет {@link #isSt}: текст начинается с {@code PROGRAM}, {@code (*ST*)} или
 * {@code {ST}}. Ошибка одна — первая, с номером строки.
 */
public final class StCompiler {
    private static final Set<String> KEYWORDS = Set.of("IF", "THEN", "ELSIF", "ELSE", "END_IF", "CASE", "OF", "END_CASE",
            "WHILE", "DO", "END_WHILE", "REPEAT", "UNTIL", "END_REPEAT", "FOR", "TO", "BY", "END_FOR", "EXIT", "RETURN",
            "PROGRAM", "END_PROGRAM", "VAR", "END_VAR", "AT", "NOT", "AND", "OR", "XOR", "MOD");
    private static final Set<String> STOP = Set.of("ELSE", "ELSIF", "END_IF", "END_CASE", "END_WHILE", "UNTIL",
            "END_REPEAT", "END_FOR", "END_PROGRAM");

    private StCompiler() {
    }

    /** ST, а не IL: первое слово {@code PROGRAM} или первая строка — прагма {@code (*ST*)} / {@code {ST}}. */
    public static boolean isSt(String source) {
        if (source == null) return false;
        String s = source.strip().toUpperCase(Locale.ROOT);
        String compact = s.replace(" ", "");
        if (compact.startsWith("(*ST*)") || compact.startsWith("{ST}")) return true;
        return s.startsWith("PROGRAM") && (s.length() == 7 || !Character.isLetterOrDigit(s.charAt(7)) && s.charAt(7) != '_');
    }

    public static IlProgram compile(String source) {
        String src = source == null ? "" : source;
        if (src.length() > IlCompiler.MAX_CHARS) return new IlProgram(src, List.of(), List.of(new IlError(0, Kind.TOO_LONG, "")));
        try {
            Parser p = new Parser(lex(src));
            p.program();
            List<Instr> code = p.link();
            if (code.size() > IlCompiler.MAX_INSTR)
                return new IlProgram(src, List.of(), List.of(new IlError(code.get(IlCompiler.MAX_INSTR).line(), Kind.TOO_LONG, "")));
            return new IlProgram(src, List.copyOf(code), List.of());
        } catch (Fail f) {
            return new IlProgram(src, List.of(), List.of(f.error));
        }
    }

    // ---------------------------------------------------------------- лексер

    private enum T { WORD, NUM, SYM, EOF }

    /** Лексема; {@code text} — в верхнем регистре. */
    private record Tok(T type, String text, int line) {
    }

    private static final class Fail extends RuntimeException {
        final IlError error;

        Fail(int line, Kind kind, String token) {
            super(null, null, false, false);
            this.error = new IlError(line, kind, token.length() > 40 ? token.substring(0, 40) : token);
        }
    }

    private static List<Tok> lex(String s) {
        List<Tok> out = new ArrayList<>();
        int line = 1, i = 0, n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (c == '\n') {
                line++;
                i++;
            } else if (Character.isWhitespace(c)) {
                i++;
            } else if (c == '(' && i + 1 < n && s.charAt(i + 1) == '*') {
                int start = line;
                i += 2;
                while (i < n && !(s.charAt(i) == '*' && i + 1 < n && s.charAt(i + 1) == ')')) if (s.charAt(i++) == '\n') line++;
                if (i >= n) throw new Fail(start, Kind.SYNTAX, "(* not closed");
                i += 2;
            } else if (c == '{') {
                int start = line;
                while (i < n && s.charAt(i) != '}') if (s.charAt(i++) == '\n') line++;
                if (i >= n) throw new Fail(start, Kind.SYNTAX, "{ not closed");
                i++;
            } else if (c == '/' && i + 1 < n && s.charAt(i + 1) == '/') {
                while (i < n && s.charAt(i) != '\n') i++;
            } else if (c == '%') {
                int j = i + 1;
                while (j < n && (Character.isLetterOrDigit(s.charAt(j)) || s.charAt(j) == '.')) j++;
                out.add(new Tok(T.WORD, s.substring(i, j).toUpperCase(Locale.ROOT), line));
                i = j;
            } else if (Character.isLetter(c) || c == '_') {
                int j = i;
                while (j < n && (Character.isLetterOrDigit(s.charAt(j)) || s.charAt(j) == '_')) j++;
                if (j < n && s.charAt(j) == '#') { // T#5S, TIME#1M30S
                    j++;
                    while (j < n && (Character.isLetterOrDigit(s.charAt(j)) || s.charAt(j) == '.' || s.charAt(j) == '_')) j++;
                } else if (j + 1 < n && s.charAt(j) == '.' && Character.isLetter(s.charAt(j + 1))) { // TON0.Q
                    j++;
                    while (j < n && (Character.isLetterOrDigit(s.charAt(j)) || s.charAt(j) == '_')) j++;
                }
                out.add(new Tok(T.WORD, s.substring(i, j).toUpperCase(Locale.ROOT), line));
                i = j;
            } else if (Character.isDigit(c)) {
                int j = i;
                while (j < n && Character.isDigit(s.charAt(j))) j++;
                if (j + 1 < n && s.charAt(j) == '.' && Character.isDigit(s.charAt(j + 1))) {
                    j++;
                    while (j < n && Character.isDigit(s.charAt(j))) j++;
                }
                if (j < n && (s.charAt(j) == 'e' || s.charAt(j) == 'E')) {
                    int k = j + 1;
                    if (k < n && (s.charAt(k) == '+' || s.charAt(k) == '-')) k++;
                    if (k < n && Character.isDigit(s.charAt(k))) {
                        j = k;
                        while (j < n && Character.isDigit(s.charAt(j))) j++;
                    }
                }
                out.add(new Tok(T.NUM, s.substring(i, j), line));
                i = j;
            } else {
                String two = i + 1 < n ? s.substring(i, i + 2) : "";
                if (two.equals(":=") || two.equals("<=") || two.equals(">=") || two.equals("<>") || two.equals("..") || two.equals("=>")) {
                    out.add(new Tok(T.SYM, two, line));
                    i += 2;
                } else if ("();,:+-*/=<>&".indexOf(c) >= 0) {
                    out.add(new Tok(T.SYM, String.valueOf(c), line));
                    i++;
                } else {
                    throw new Fail(line, Kind.SYNTAX, String.valueOf(c));
                }
            }
        }
        out.add(new Tok(T.EOF, "end of program", line));
        return out;
    }

    // ---------------------------------------------------------------- дерево выражения

    /** Узел выражения: лист-операнд, бинарный оператор, NOT или унарный минус. */
    private record Node(Operand leaf, Op op, Node l, Node r, boolean not, boolean neg) {
        static Node leaf(Operand o) {
            return new Node(o, null, null, null, false, false);
        }

        boolean isLeaf() {
            return leaf != null;
        }
    }

    // ---------------------------------------------------------------- разбор и генерация

    private static final class Parser {
        private final List<Tok> toks;
        private int pos;
        private final List<Instr> code = new ArrayList<>();
        /** Для переходов: номер метки на месте {@code target} до {@link #link()}. */
        private final List<Integer> labelAt = new ArrayList<>();
        private final Map<String, Operand> names = new HashMap<>();
        private final Map<String, Integer> fbNames = new HashMap<>();
        private final int[] nextFree = new int[Fb.Kind.values().length];
        private final Deque<Integer> loopEnds = new ArrayDeque<>();

        Parser(List<Tok> toks) {
            this.toks = toks;
            java.util.Arrays.fill(nextFree, Fb.PER_KIND - 1);
        }

        // ---- лексемы

        private Tok peek() {
            return toks.get(pos);
        }

        private Tok peek(int k) {
            return toks.get(Math.min(pos + k, toks.size() - 1));
        }

        private Tok next() {
            Tok t = toks.get(pos);
            if (t.type != T.EOF) pos++;
            return t;
        }

        private boolean is(String text) {
            Tok t = peek();
            return t.type != T.NUM && t.text.equals(text);
        }

        private boolean accept(String text) {
            if (!is(text)) return false;
            next();
            return true;
        }

        private Tok expect(String text) {
            if (!is(text)) throw new Fail(peek().line, Kind.SYNTAX, text + " expected, got " + peek().text);
            return next();
        }

        // ---- программа

        void program() {
            if (accept("PROGRAM")) {
                if (peek().type == T.WORD && !KEYWORDS.contains(peek().text)) next();
            }
            while (is("VAR")) vars();
            statements();
            accept("END_PROGRAM");
            if (peek().type != T.EOF) throw new Fail(peek().line, Kind.SYNTAX, "unexpected " + peek().text);
        }

        /** {@code VAR имя [AT %адрес] : ТИП; … END_VAR}. */
        private void vars() {
            expect("VAR");
            while (!accept("END_VAR")) {
                Tok name = next();
                if (name.type != T.WORD || KEYWORDS.contains(name.text) || name.text.contains(".") || name.text.startsWith("%"))
                    throw new Fail(name.line, Kind.SYNTAX, "variable name expected, got " + name.text);
                if (names.containsKey(name.text) || fbNames.containsKey(name.text))
                    throw new Fail(name.line, Kind.DUPLICATE_LABEL, name.text);
                Operand at = null;
                if (accept("AT")) {
                    Tok a = next();
                    at = a.text.startsWith("%") ? IlCompiler.operand(a.text) : null;
                    if (at == null) throw new Fail(a.line, Kind.BAD_OPERAND, a.text);
                }
                expect(":");
                Tok type = next();
                Fb.Kind kind = fbKind(type.text);
                if (kind != null) {
                    if (at != null) throw new Fail(type.line, Kind.SYNTAX, "a block cannot have AT");
                    if (nextFree[kind.ordinal()] < 0) throw new Fail(type.line, Kind.TOO_LONG, "more than " + Fb.PER_KIND + " " + kind);
                    fbNames.put(name.text, kind.ordinal() * Fb.PER_KIND + nextFree[kind.ordinal()]--);
                } else {
                    if (type.type != T.WORD || KEYWORDS.contains(type.text)) throw new Fail(type.line, Kind.SYNTAX, "type expected, got " + type.text);
                    if (at == null) throw new Fail(name.line, Kind.SYNTAX, name.text + ": give an address with AT");
                    names.put(name.text, at);
                }
                if (is(":=")) throw new Fail(peek().line, Kind.SYNTAX, "initial values are not supported");
                expect(";");
            }
        }

        private static Fb.Kind fbKind(String t) {
            for (Fb.Kind k : Fb.Kind.values()) if (k.name().equals(t)) return k;
            return null;
        }

        // ---- операторы

        private void statements() {
            while (true) {
                Tok t = peek();
                if (t.type == T.EOF || t.type == T.NUM || STOP.contains(t.text) || t.text.equals("-")) return;
                statement();
            }
        }

        private void statement() {
            Tok t = peek();
            switch (t.text) {
                case ";" -> next();
                case "IF" -> ifStatement();
                case "CASE" -> caseStatement();
                case "WHILE" -> whileStatement();
                case "REPEAT" -> repeatStatement();
                case "FOR" -> forStatement();
                case "EXIT" -> {
                    next();
                    if (loopEnds.isEmpty()) throw new Fail(t.line, Kind.SYNTAX, "EXIT outside a loop");
                    jump(false, false, loopEnds.peek(), t.line);
                    expect(";");
                }
                case "RETURN" -> {
                    next();
                    emit(new Instr(Op.RET, false, false, false, null, -1, null, null, -1, t.line));
                    expect(";");
                }
                default -> {
                    if (t.type != T.WORD || KEYWORDS.contains(t.text)) throw new Fail(t.line, Kind.SYNTAX, "statement expected, got " + t.text);
                    next();
                    if (is("(")) call(t);
                    else assignment(t);
                    expect(";");
                }
            }
        }

        private void assignment(Tok target) {
            expect(":=");
            Operand dst = operand(target);
            if (!dst.writable()) throw new Fail(target.line, Kind.NOT_WRITABLE, target.text);
            Node e = expression();
            load(e, 0, target.line);
            emit(new Instr(Op.ST, false, false, false, dst, -1, null, null, -1, target.line));
        }

        /** {@code T1(IN := x, PT := T#5S, Q => %QX0.0)}. */
        private void call(Tok name) {
            int fb = fbNames.containsKey(name.text) ? fbNames.get(name.text) : Fb.instance(name.text);
            if (fb < 0) throw new Fail(name.line, Kind.BAD_CALL, name.text);
            Fb.Kind kind = Fb.kindOf(fb);
            expect("(");
            List<Integer> params = new ArrayList<>();
            List<Operand> args = new ArrayList<>();
            List<Operand[]> outs = new ArrayList<>();
            if (!is(")")) {
                do {
                    Tok p = next();
                    if (accept("=>")) {
                        int member = kind.output(p.text);
                        if (member < 0) throw new Fail(p.line, Kind.BAD_CALL, p.text);
                        Tok d = next();
                        Operand dst = operand(d);
                        if (!dst.writable()) throw new Fail(d.line, Kind.NOT_WRITABLE, d.text);
                        outs.add(new Operand[] {new Operand(Area.FB, fb, member, 0), dst});
                        continue;
                    }
                    expect(":=");
                    int in = kind.input(p.text);
                    if (in < 0) throw new Fail(p.line, Kind.BAD_CALL, p.text);
                    Node a = expression();
                    if (!a.isLeaf()) throw new Fail(p.line, Kind.BAD_CALL, p.text + ": only a variable or constant");
                    params.add(in);
                    args.add(a.leaf);
                } while (accept(","));
            }
            expect(")");
            emit(new Instr(Op.CAL, false, false, false, null, fb, params.stream().mapToInt(Integer::intValue).toArray(),
                    args.toArray(Operand[]::new), -1, name.line));
            for (Operand[] o : outs) {
                emit(new Instr(Op.LD, false, false, false, o[0], -1, null, null, -1, name.line));
                emit(new Instr(Op.ST, false, false, false, o[1], -1, null, null, -1, name.line));
            }
        }

        private void ifStatement() {
            int end = newLabel();
            Tok t = expect("IF");
            while (true) {
                Node c = expression();
                expect("THEN");
                int skip = newLabel();
                load(c, 0, t.line);
                jump(true, true, skip, t.line);
                statements();
                jump(false, false, end, t.line);
                place(skip);
                if (is("ELSIF")) {
                    t = next();
                    continue;
                }
                if (accept("ELSE")) statements();
                break;
            }
            expect("END_IF");
            place(end);
            accept(";");
        }

        private void caseStatement() {
            Tok t = expect("CASE");
            Node sel = expression();
            expect("OF");
            int end = newLabel();
            while (!is("ELSE") && !is("END_CASE")) {
                int body = newLabel(), nextCase = newLabel();
                do {
                    int line = peek().line;
                    double lo = caseLabel();
                    if (accept("..")) {
                        double hi = caseLabel();
                        int skip = newLabel();
                        load(sel, 0, line);
                        emit(new Instr(Op.GE, false, false, false, Operand.constant(lo), -1, null, null, -1, line));
                        jump(true, true, skip, line);
                        load(sel, 0, line);
                        emit(new Instr(Op.LE, false, false, false, Operand.constant(hi), -1, null, null, -1, line));
                        jump(true, false, body, line);
                        place(skip);
                    } else {
                        load(sel, 0, line);
                        emit(new Instr(Op.EQ, false, false, false, Operand.constant(lo), -1, null, null, -1, line));
                        jump(true, false, body, line);
                    }
                } while (accept(","));
                expect(":");
                jump(false, false, nextCase, t.line);
                place(body);
                statements();
                jump(false, false, end, t.line);
                place(nextCase);
            }
            if (accept("ELSE")) statements();
            expect("END_CASE");
            place(end);
            accept(";");
        }

        private double caseLabel() {
            boolean neg = accept("-");
            Tok n = next();
            if (n.type != T.NUM || n.text.contains(".") || n.text.contains("E") || n.text.contains("e"))
                throw new Fail(n.line, Kind.SYNTAX, "integer case label expected, got " + n.text);
            double v = Double.parseDouble(n.text);
            return neg ? -v : v;
        }

        private void whileStatement() {
            Tok t = expect("WHILE");
            int top = newLabel(), end = newLabel();
            place(top);
            Node c = expression();
            expect("DO");
            load(c, 0, t.line);
            jump(true, true, end, t.line);
            loopEnds.push(end);
            statements();
            loopEnds.pop();
            expect("END_WHILE");
            jump(false, false, top, t.line);
            place(end);
            accept(";");
        }

        private void repeatStatement() {
            expect("REPEAT");
            int top = newLabel(), end = newLabel();
            place(top);
            loopEnds.push(end);
            statements();
            loopEnds.pop();
            Tok u = expect("UNTIL");
            Node c = expression();
            load(c, 0, u.line);
            jump(true, true, top, u.line);
            expect("END_REPEAT");
            place(end);
            accept(";");
        }

        /** {@code FOR i := a TO b [BY константа] DO … END_FOR}; счётчик — слово {@code %MW}/{@code %QW}. */
        private void forStatement() {
            Tok t = expect("FOR");
            Tok v = next();
            Operand var = operand(v);
            if (var.area() != Area.MW && var.area() != Area.QW) throw new Fail(v.line, Kind.NOT_WRITABLE, v.text + ": FOR needs a word");
            expect(":=");
            Node from = expression();
            expect("TO");
            Node to = expression();
            double step = 1;
            if (accept("BY")) {
                Node s = expression();
                if (!s.isLeaf() || s.leaf.area() != Area.CONST || s.leaf.value() == 0)
                    throw new Fail(t.line, Kind.SYNTAX, "BY needs a non-zero constant");
                step = s.leaf.value();
            }
            expect("DO");
            int top = newLabel(), end = newLabel();
            load(from, 0, t.line);
            emit(new Instr(Op.ST, false, false, false, var, -1, null, null, -1, t.line));
            place(top);
            emit(new Instr(Op.LD, false, false, false, var, -1, null, null, -1, t.line));
            apply(step > 0 ? Op.GT : Op.LT, to, 0, t.line);
            jump(true, false, end, t.line);
            loopEnds.push(end);
            statements();
            loopEnds.pop();
            expect("END_FOR");
            emit(new Instr(Op.LD, false, false, false, var, -1, null, null, -1, t.line));
            emit(new Instr(Op.ADD, false, false, false, Operand.constant(step), -1, null, null, -1, t.line));
            emit(new Instr(Op.ST, false, false, false, var, -1, null, null, -1, t.line));
            jump(false, false, top, t.line);
            place(end);
            accept(";");
        }

        // ---- выражения: приоритет по таблице 55 IEC 61131-3

        private Node expression() {
            Node l = xorExpr();
            while (accept("OR")) l = bin(Op.OR, l, xorExpr());
            return l;
        }

        private Node xorExpr() {
            Node l = andExpr();
            while (accept("XOR")) l = bin(Op.XOR, l, andExpr());
            return l;
        }

        private Node andExpr() {
            Node l = equality();
            while (accept("AND") || accept("&")) l = bin(Op.AND, l, equality());
            return l;
        }

        private Node equality() {
            Node l = comparison();
            while (true) {
                if (accept("=")) l = bin(Op.EQ, l, comparison());
                else if (accept("<>")) l = bin(Op.NE, l, comparison());
                else return l;
            }
        }

        private Node comparison() {
            Node l = additive();
            while (true) {
                if (accept("<")) l = bin(Op.LT, l, additive());
                else if (accept(">")) l = bin(Op.GT, l, additive());
                else if (accept("<=")) l = bin(Op.LE, l, additive());
                else if (accept(">=")) l = bin(Op.GE, l, additive());
                else return l;
            }
        }

        private Node additive() {
            Node l = term();
            while (true) {
                if (accept("+")) l = bin(Op.ADD, l, term());
                else if (accept("-")) l = bin(Op.SUB, l, term());
                else return l;
            }
        }

        private Node term() {
            Node l = unary();
            while (true) {
                if (accept("*")) l = bin(Op.MUL, l, unary());
                else if (accept("/")) l = bin(Op.DIV, l, unary());
                else if (accept("MOD")) l = bin(Op.MOD, l, unary());
                else return l;
            }
        }

        private Node unary() {
            if (accept("NOT")) return new Node(null, null, unary(), null, true, false);
            if (accept("-")) {
                Node e = unary();
                if (e.isLeaf() && e.leaf.area() == Area.CONST) return Node.leaf(Operand.constant(-e.leaf.value()));
                return new Node(null, null, e, null, false, true);
            }
            if (accept("+")) return unary();
            return primary();
        }

        private Node primary() {
            Tok t = peek();
            if (accept("(")) {
                Node e = expression();
                expect(")");
                return e;
            }
            if (t.type == T.NUM || t.type == T.WORD && !KEYWORDS.contains(t.text)) {
                next();
                return Node.leaf(operand(t));
            }
            throw new Fail(t.line, Kind.SYNTAX, "value expected, got " + t.text);
        }

        private static Node bin(Op op, Node l, Node r) {
            return new Node(null, op, l, r, false, false);
        }

        /** Операнд по лексеме: имя из VAR, выход блока по имени из VAR, затем синтаксис IL. */
        private Operand operand(Tok t) {
            Operand named = names.get(t.text);
            if (named != null) return named;
            int dot = t.text.indexOf('.');
            if (dot > 0 && !t.text.startsWith("%")) {
                Integer fb = fbNames.get(t.text.substring(0, dot));
                if (fb != null) {
                    int member = Fb.kindOf(fb).output(t.text.substring(dot + 1));
                    if (member < 0) throw new Fail(t.line, Kind.BAD_OPERAND, t.text);
                    return new Operand(Area.FB, fb, member, 0);
                }
            }
            Operand o = IlCompiler.operand(t.text);
            if (o == null) throw new Fail(t.line, Kind.BAD_OPERAND, t.text);
            return o;
        }

        // ---- генерация

        /** Результат выражения в текущий результат (CR). {@code depth} — открытые скобки IL. */
        private void load(Node n, int depth, int line) {
            if (n.isLeaf()) {
                emit(new Instr(Op.LD, false, false, false, n.leaf, -1, null, null, -1, line));
            } else if (n.not) {
                if (n.l.isLeaf()) {
                    emit(new Instr(Op.LD, true, false, false, n.l.leaf, -1, null, null, -1, line));
                } else {
                    load(n.l, depth, line);
                    emit(new Instr(Op.NOT, false, false, false, null, -1, null, null, -1, line));
                }
            } else if (n.neg) {
                emit(new Instr(Op.LD, false, false, false, Operand.constant(0), -1, null, null, -1, line));
                apply(Op.SUB, n.l, depth, line);
            } else {
                load(n.l, depth, line);
                apply(n.op, n.r, depth, line);
            }
        }

        /** CR := CR op right; составной правый операнд — в скобке IL. */
        private void apply(Op op, Node r, int depth, int line) {
            boolean logic = op == Op.AND || op == Op.OR || op == Op.XOR;
            if (r.isLeaf()) {
                emit(new Instr(op, false, false, false, r.leaf, -1, null, null, -1, line));
            } else if (logic && r.not && r.l.isLeaf()) {
                emit(new Instr(op, true, false, false, r.l.leaf, -1, null, null, -1, line));
            } else {
                if (depth + 1 > IlCompiler.MAX_DEPTH) throw new Fail(line, Kind.NESTING_TOO_DEEP, op.name() + "(");
                emit(new Instr(op, false, false, true, null, -1, null, null, -1, line));
                load(r, depth + 1, line);
                emit(new Instr(Op.CLOSE, false, false, false, null, -1, null, null, -1, line));
            }
        }

        private final List<Integer> labels = new ArrayList<>();

        private int newLabel() {
            labels.add(-1);
            return labels.size() - 1;
        }

        private void place(int label) {
            labels.set(label, code.size());
        }

        /** Переход на метку; {@code conditional}/{@code negate} — как JMPC/JMPCN. */
        private void jump(boolean conditional, boolean negate, int label, int line) {
            code.add(new Instr(Op.JMP, negate, conditional, false, null, -1, null, null, -1, line));
            labelAt.add(label);
        }

        private void emit(Instr in) {
            code.add(in);
            labelAt.add(-1);
            if (code.size() > IlCompiler.MAX_INSTR + 1) throw new Fail(in.line(), Kind.TOO_LONG, "");
        }

        /** Метки → индексы инструкций. */
        List<Instr> link() {
            for (int i = 0; i < code.size(); i++) {
                int l = labelAt.get(i);
                if (l < 0) continue;
                Instr in = code.get(i);
                code.set(i, new Instr(Op.JMP, in.negate(), in.conditional(), false, null, -1, null, null, labels.get(l), in.line()));
            }
            return code;
        }
    }
}
