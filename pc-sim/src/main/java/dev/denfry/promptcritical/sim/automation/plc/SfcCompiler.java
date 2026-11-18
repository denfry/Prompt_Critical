package dev.denfry.promptcritical.sim.automation.plc;

import dev.denfry.promptcritical.sim.automation.plc.IlProgram.IlError;
import dev.denfry.promptcritical.sim.automation.plc.IlProgram.IlError.Kind;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Последовательные функциональные схемы SFC (IEC 61131-3 §2.6) в текстовой форме стандарта:
 *
 * <pre>
 * (*SFC*)
 * VAR T1 : TON; END_VAR                          (* необязательно: блоки для условий *)
 * INITIAL_STEP Idle: END_STEP
 * STEP Fill: %QX0.0(N); END_STEP                 (* связь шага с действием: имя(квалификатор) *)
 * STEP Heat: Heater(N); Alarm(S); END_STEP
 * TRANSITION FROM Idle TO Fill := %IX0.0; END_TRANSITION
 * TRANSITION FROM Fill TO Heat := %IX0.1; END_TRANSITION
 * TRANSITION FROM Heat TO Idle := %IX0.2; END_TRANSITION
 * ACTION Heater: %QX0.1 := TRUE; END_ACTION     (* тело действия — ST *)
 * ACTION Alarm: %QX0.2 := TRUE; END_ACTION
 * </pre>
 *
 * Квалификаторы: {@code N} — пока шаг активен, {@code S} — установить, {@code R} — сбросить, {@code P} — один раз при
 * входе в шаг. Действием может быть и прямой адрес ({@code %QX0.0(N)}): он равен ИЛИ всех шагов с {@code N}.
 * Переходы срабатывают по состояниям шагов на начало цикла (одновременно), шаг «из» гаснет, шаг «в» зажигается.
 * Перевод — в ST ({@link StCompiler}); флаги шагов и переходов — биты {@code %MX48…%MX63} (не больше 15 шагов и
 * переходов вместе с битом первого цикла).
 */
public final class SfcCompiler {
    private static final Pattern STEP = Pattern.compile("(?is)\\b(INITIAL_STEP|STEP)\\s+([A-Za-z_]\\w*)\\s*:(.*?)END_STEP");
    private static final Pattern TRANS = Pattern.compile(
            "(?is)\\bTRANSITION\\s+FROM\\s+([A-Za-z_]\\w*)\\s+TO\\s+([A-Za-z_]\\w*)\\s*:=(.*?);\\s*END_TRANSITION");
    private static final Pattern ACTION = Pattern.compile("(?is)\\bACTION\\s+([A-Za-z_]\\w*)\\s*:(.*?)END_ACTION");
    private static final Pattern VAR = Pattern.compile("(?is)\\bVAR\\b(.*?)\\bEND_VAR\\b");
    private static final Pattern ASSOC = Pattern.compile("(?i)\\s*(%?[A-Za-z_][\\w.]*)\\s*\\(\\s*([NSRP])\\s*\\)\\s*");

    private SfcCompiler() {
    }

    public static boolean isSfc(String source) {
        if (source == null) return false;
        String c = source.strip().toUpperCase(Locale.ROOT).replace(" ", "");
        return c.startsWith("(*SFC*)") || c.startsWith("{SFC}");
    }

    private record Step(String name, boolean initial, int line, List<String[]> assoc) {
    }

    private record Trans(String from, String to, String cond, int line) {
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

    private static int lineOf(String src, int index) {
        int l = 1;
        for (int i = 0; i < index && i < src.length(); i++) if (src.charAt(i) == '\n') l++;
        return l;
    }

    static LdCompiler.Translation translate(String rawSrc) {
        String src = rawSrc.replaceAll("(?s)\\(\\*(?!\\s*SFC\\s*\\*\\)).*?\\*\\)", " ").replaceAll("//[^\\n]*", "");
        Map<String, Step> steps = new LinkedHashMap<>();
        Matcher m = STEP.matcher(src);
        while (m.find()) {
            int line = lineOf(src, m.start());
            List<String[]> assoc = new ArrayList<>();
            for (String a : m.group(3).split(";")) {
                if (a.isBlank()) continue;
                Matcher am = ASSOC.matcher(a);
                if (!am.matches()) throw new LdCompiler.Fail(line, a.strip());
                assoc.add(new String[] {am.group(1), am.group(2).toUpperCase(Locale.ROOT)});
            }
            if (steps.putIfAbsent(m.group(2), new Step(m.group(2), m.group(1).equalsIgnoreCase("INITIAL_STEP"), line, assoc)) != null)
                throw new LdCompiler.Fail(line, "duplicate step " + m.group(2));
        }
        if (steps.isEmpty()) throw new LdCompiler.Fail(1, "no STEP");
        if (steps.values().stream().filter(Step::initial).count() != 1) throw new LdCompiler.Fail(1, "exactly one INITIAL_STEP");
        List<Trans> trans = new ArrayList<>();
        m = TRANS.matcher(src);
        while (m.find()) {
            int line = lineOf(src, m.start());
            if (!steps.containsKey(m.group(1))) throw new LdCompiler.Fail(line, m.group(1));
            if (!steps.containsKey(m.group(2))) throw new LdCompiler.Fail(line, m.group(2));
            trans.add(new Trans(m.group(1), m.group(2), m.group(3).strip(), line));
        }
        Map<String, String[]> actions = new LinkedHashMap<>();
        m = ACTION.matcher(src);
        while (m.find()) actions.put(m.group(1), new String[] {m.group(2).strip(), String.valueOf(lineOf(src, m.start()))});
        if (steps.size() + trans.size() + 1 > 64 - LdCompiler.TEMP_FIRST)
            throw new LdCompiler.Fail(1, "too many steps and transitions (15)");

        List<String> out = new ArrayList<>();
        List<Integer> map = new ArrayList<>();
        int bit = LdCompiler.TEMP_FIRST;
        Map<String, String> x = new LinkedHashMap<>(), f = new LinkedHashMap<>();
        String first = "%MX" + bit++;
        for (String s : steps.keySet()) x.put(s, "%MX" + bit++);
        for (int i = 0; i < trans.size(); i++) f.put("t" + i, "%MX" + bit++);
        int pulses = 0;
        add(out, map, 0, "PROGRAM sfc");
        add(out, map, 0, "VAR");
        Matcher vm = VAR.matcher(src);
        if (vm.find()) for (String d : vm.group(1).split(";")) if (!d.isBlank()) add(out, map, lineOf(src, vm.start()), d.strip() + ";");
        for (Step s : steps.values())
            for (String[] a : s.assoc()) if (a[1].equals("P")) add(out, map, s.line(), "P" + (pulses++) + "_" + s.name() + " : R_TRIG;");
        add(out, map, 0, "END_VAR");
        // первый цикл: зажечь начальный шаг
        Step init = steps.values().stream().filter(Step::initial).findFirst().orElseThrow();
        add(out, map, init.line(), "IF NOT " + first + " THEN " + x.get(init.name()) + " := TRUE; " + first + " := TRUE; END_IF;");
        // переходы: по состояниям на начало цикла
        for (int i = 0; i < trans.size(); i++) {
            Trans t = trans.get(i);
            add(out, map, t.line(), f.get("t" + i) + " := " + x.get(t.from()) + " AND (" + t.cond() + ");");
        }
        for (int i = 0; i < trans.size(); i++) {
            Trans t = trans.get(i);
            add(out, map, t.line(), "IF " + f.get("t" + i) + " THEN " + x.get(t.from()) + " := FALSE; " + x.get(t.to()) + " := TRUE; END_IF;");
        }
        // действия
        Map<String, List<String>> directN = new LinkedHashMap<>();
        pulses = 0;
        for (Step s : steps.values()) {
            String xs = x.get(s.name());
            for (String[] a : s.assoc()) {
                String name = a[0], q = a[1];
                String[] body = actions.get(name);
                if (body == null && !name.startsWith("%")) throw new LdCompiler.Fail(s.line(), "no ACTION " + name);
                String guard = xs;
                if (q.equals("P")) {
                    String inst = "P" + (pulses++) + "_" + s.name();
                    add(out, map, s.line(), inst + "(CLK := " + xs + ");");
                    guard = inst + ".Q";
                }
                if (body == null) {
                    switch (q) {
                        case "N" -> directN.computeIfAbsent(name, k -> new ArrayList<>()).add(xs);
                        case "S", "P" -> add(out, map, s.line(), "IF " + guard + " THEN " + name + " := TRUE; END_IF;");
                        default -> add(out, map, s.line(), "IF " + guard + " THEN " + name + " := FALSE; END_IF;");
                    }
                } else {
                    int bl = Integer.parseInt(body[1]);
                    if (q.equals("R")) throw new LdCompiler.Fail(s.line(), name + "(R) needs a variable, not an ACTION");
                    add(out, map, bl, "IF " + guard + " THEN");
                    for (String st : body[0].split("\\R")) if (!st.isBlank()) add(out, map, bl, st.strip());
                    add(out, map, bl, "END_IF;");
                }
            }
        }
        for (Map.Entry<String, List<String>> e : directN.entrySet())
            add(out, map, 0, e.getKey() + " := " + String.join(" OR ", e.getValue()) + ";");
        add(out, map, 0, "END_PROGRAM");
        return new LdCompiler.Translation(String.join("\n", out), map);
    }

    private static void add(List<String> out, List<Integer> map, int line, String s) {
        out.add(s);
        map.add(line);
    }
}
