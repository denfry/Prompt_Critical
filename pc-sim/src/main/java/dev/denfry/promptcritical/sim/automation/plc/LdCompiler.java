package dev.denfry.promptcritical.sim.automation.plc;

import dev.denfry.promptcritical.sim.automation.plc.IlProgram.IlError;
import dev.denfry.promptcritical.sim.automation.plc.IlProgram.IlError.Kind;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Релейно-контактные схемы LD (IEC 61131-3 §4.2) в текстовой записи ступеней — по строке на ступень, мнемоники
 * «нейтрального текста» релейных схем:
 *
 * <pre>
 * (*LD*)
 * XIC(%IX0.0) BST XIC(%QX0.0) NXB XIC(%IX0.2) BND XIO(%IX0.1) OTE(%QX0.0)   // пуск-стоп с самоподхватом
 * XIC(%QX0.0) TON(T1, T#5S) OTE(%QX0.1)                                      // реле времени
 * </pre>
 *
 * {@code XIC(a)} — замыкающий контакт, {@code XIO(a)} — размыкающий, {@code BST … NXB … BND} — параллельные ветви;
 * блоки {@code TON/TOF/TP(имя, время)} и {@code CTU(имя, уставка)} стоят в цепи — их вход — условие слева, выход
 * ({@code имя.Q}) продолжает цепь; катушки в конце ступени: {@code OTE} — выход, {@code OTL}/{@code OTU} — установка и
 * сброс, {@code RES(счётчик)} — сброс счётчика. Ступень переводится в ST ({@link StCompiler}): один процессор и одно
 * представление для всех языков. Входы блоков — промежуточные биты {@code %MX48…%MX63} (биты памяти выше 47 заняты
 * LD и SFC). Ошибка — с номером строки ступени.
 */
public final class LdCompiler {
    /** Первый промежуточный бит LD/SFC. */
    static final int TEMP_FIRST = 48;

    private LdCompiler() {
    }

    /** Текст LD: первая строка — прагма {@code (*LD*)} или {@code {LD}}. */
    public static boolean isLd(String source) {
        if (source == null) return false;
        String c = source.strip().toUpperCase(Locale.ROOT).replace(" ", "");
        return c.startsWith("(*LD*)") || c.startsWith("{LD}");
    }

    /** Перевод в ST с картой строк ST → строка ступени. */
    record Translation(String st, List<Integer> lineMap) {
    }

    public static IlProgram compile(String source) {
        String src = source == null ? "" : source;
        if (src.length() > IlCompiler.MAX_CHARS) return new IlProgram(src, List.of(), List.of(new IlError(0, Kind.TOO_LONG, "")));
        Translation t;
        try {
            t = translate(src);
        } catch (Fail f) {
            return new IlProgram(src, List.of(), List.of(f.error));
        }
        IlProgram p = StCompiler.compile(t.st());
        return remap(src, p, t.lineMap());
    }

    /** Ошибки ST-кода — на строки исходника; сам код — тот же. */
    static IlProgram remap(String src, IlProgram p, List<Integer> lineMap) {
        if (p.errors().isEmpty()) return new IlProgram(src, p.code(), List.of());
        List<IlError> out = new ArrayList<>();
        for (IlError e : p.errors()) {
            int l = e.line() >= 1 && e.line() <= lineMap.size() ? lineMap.get(e.line() - 1) : 0;
            out.add(new IlError(l, e.kind(), e.token()));
        }
        return new IlProgram(src, List.of(), out);
    }

    static final class Fail extends RuntimeException {
        final IlError error;

        Fail(int line, String token) {
            super(token, null, false, false);
            this.error = new IlError(line, Kind.SYNTAX, token);
        }
    }

    // ------------------------------------------------------------------ перевод

    private static final class Out {
        final List<String> lines = new ArrayList<>();
        final List<Integer> map = new ArrayList<>();
        final Map<String, String> instances = new LinkedHashMap<>();
        int temp = TEMP_FIRST;

        void add(int srcLine, String text) {
            lines.add(text);
            map.add(srcLine);
        }

        String tempBit(int srcLine) {
            if (temp > 63) throw new Fail(srcLine, "too many timer and counter boxes (16)");
            return "%MX" + temp++;
        }
    }

    static Translation translate(String src) {
        Out body = new Out();
        String[] lines = src.split("\\R", -1);
        for (int i = 0; i < lines.length; i++) {
            String raw = lines[i];
            int c = raw.indexOf("//");
            String l = (c >= 0 ? raw.substring(0, c) : raw).replaceAll("\\(\\*.*?\\*\\)", "").strip();
            if (i == 0 && isLd(raw)) continue;
            if (l.isEmpty()) continue;
            rung(i + 1, l, body);
        }
        // заголовок ST с объявлениями экземпляров блоков
        Out st = new Out();
        st.add(0, "PROGRAM ld");
        if (!body.instances.isEmpty()) {
            st.add(0, "VAR");
            for (Map.Entry<String, String> e : body.instances.entrySet()) st.add(0, e.getKey() + " : " + e.getValue() + ";");
            st.add(0, "END_VAR");
        }
        for (int k = 0; k < body.lines.size(); k++) st.add(body.map.get(k), body.lines.get(k));
        st.add(0, "END_PROGRAM");
        return new Translation(String.join("\n", st.lines), st.map);
    }

    private record Tok(String op, List<String> args) {
    }

    private static List<Tok> tokens(int line, String s) {
        List<Tok> out = new ArrayList<>();
        int i = 0, n = s.length();
        while (i < n) {
            char ch = s.charAt(i);
            if (Character.isWhitespace(ch)) {
                i++;
                continue;
            }
            int j = i;
            while (j < n && (Character.isLetterOrDigit(s.charAt(j)) || s.charAt(j) == '_')) j++;
            if (j == i) throw new Fail(line, String.valueOf(ch));
            String op = s.substring(i, j).toUpperCase(Locale.ROOT);
            List<String> args = new ArrayList<>();
            if (j < n && s.charAt(j) == '(') {
                int k = s.indexOf(')', j);
                if (k < 0) throw new Fail(line, op + "(");
                for (String a : s.substring(j + 1, k).split(",")) if (!a.isBlank()) args.add(a.strip());
                j = k + 1;
            }
            out.add(new Tok(op, args));
            i = j;
        }
        return out;
    }

    /** Ступень: условие (контакты, ветви, блоки) и катушки в конце. */
    private static void rung(int line, String text, Out out) {
        List<Tok> toks = tokens(line, text);
        int[] pos = {0};
        List<String> pre = new ArrayList<>();
        String cond = series(line, toks, pos, out, pre, false);
        for (String p : pre) out.add(line, p);
        if (pos[0] >= toks.size()) throw new Fail(line, "rung without a coil");
        String c = cond == null ? "TRUE" : cond;
        while (pos[0] < toks.size()) {
            Tok t = toks.get(pos[0]++);
            String a = arg(line, t, 0);
            switch (t.op()) {
                case "OTE" -> out.add(line, a + " := " + c + ";");
                case "OTL" -> out.add(line, "IF " + c + " THEN " + a + " := TRUE; END_IF;");
                case "OTU" -> out.add(line, "IF " + c + " THEN " + a + " := FALSE; END_IF;");
                case "RES" -> {
                    String type = out.instances.get(a);
                    if (!"CTU".equals(type)) throw new Fail(line, "RES(" + a + ")");
                    String bit = out.tempBit(line);
                    out.add(line, bit + " := " + c + ";");
                    out.add(line, a + "(CU := FALSE, R := " + bit + ");");
                }
                default -> throw new Fail(line, t.op());
            }
        }
    }

    /** Последовательная цепь до {@code NXB}/{@code BND} или первой катушки; null — пустая цепь (всегда замкнута). */
    private static String series(int line, List<Tok> toks, int[] pos, Out out, List<String> pre, boolean inBranch) {
        String acc = null;
        while (pos[0] < toks.size()) {
            Tok t = toks.get(pos[0]);
            switch (t.op()) {
                case "XIC" -> {
                    pos[0]++;
                    acc = and(acc, arg(line, t, 0));
                }
                case "XIO" -> {
                    pos[0]++;
                    acc = and(acc, "NOT " + arg(line, t, 0));
                }
                case "BST" -> {
                    pos[0]++;
                    List<String> alts = new ArrayList<>();
                    alts.add(series(line, toks, pos, out, pre, true));
                    while (pos[0] < toks.size() && toks.get(pos[0]).op().equals("NXB")) {
                        pos[0]++;
                        alts.add(series(line, toks, pos, out, pre, true));
                    }
                    if (pos[0] >= toks.size() || !toks.get(pos[0]).op().equals("BND")) throw new Fail(line, "BST without BND");
                    pos[0]++;
                    StringBuilder or = new StringBuilder("(");
                    for (int k = 0; k < alts.size(); k++) {
                        if (k > 0) or.append(" OR ");
                        or.append(alts.get(k) == null ? "TRUE" : alts.get(k));
                    }
                    acc = and(acc, or.append(")").toString());
                }
                case "TON", "TOF", "TP", "CTU" -> {
                    pos[0]++;
                    String name = arg(line, t, 0), set = arg(line, t, 1);
                    String prev = out.instances.putIfAbsent(name, t.op());
                    if (prev != null && !prev.equals(t.op())) throw new Fail(line, name);
                    String bit = out.tempBit(line);
                    pre.add(bit + " := " + (acc == null ? "TRUE" : acc) + ";");
                    pre.add(t.op().equals("CTU") ? name + "(CU := " + bit + ", PV := " + set + ");"
                            : name + "(IN := " + bit + ", PT := " + set + ");");
                    acc = name + ".Q";
                }
                case "NXB", "BND" -> {
                    if (!inBranch) throw new Fail(line, t.op());
                    return acc;
                }
                default -> {
                    return acc; // катушки — дело ступени
                }
            }
        }
        return acc;
    }

    private static String and(String acc, String term) {
        return acc == null ? term : "(" + acc + ") AND " + term;
    }

    private static String arg(int line, Tok t, int i) {
        if (t.args().size() <= i) throw new Fail(line, t.op() + "()");
        return t.args().get(i);
    }
}
