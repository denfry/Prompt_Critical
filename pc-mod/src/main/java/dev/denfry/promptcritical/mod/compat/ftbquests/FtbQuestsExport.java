package dev.denfry.promptcritical.mod.compat.ftbquests;

import dev.denfry.promptcritical.mod.content.Localized;
import dev.denfry.promptcritical.mod.content.RouteCondition;
import dev.denfry.promptcritical.mod.content.RouteTask;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import net.minecraft.resources.ResourceLocation;

/**
 * The handbook route (R23, {@code handbook/route/*.json}) as an FTB Quests quest book (16 §16.11.2): one chapter per era,
 * one quest per route task, {@code requires} as quest dependencies. No FTB Quests API: the result is the SNBT folder FTB
 * Quests 2101.1 reads from {@code config/ftbquests/quests/} (file version 13, texts in {@code lang/<locale>.snbt}).
 *
 * <p>Tasks: {@code obtain} of an item is an {@code item} task; any other task is an {@code advancement} task when an
 * advancement with the task's id (or with the last segment of its path) exists, otherwise a {@code checkmark}. This is
 * the deviation from §16.11.2, which assumed every route task has an advancement: the route has 244 tasks of six kinds
 * and the mod about 70 advancements.
 *
 * <p>Object ids are the first 64 bits of SHA-256 of the task id, so a re-export keeps every quest's progress.
 */
public final class FtbQuestsExport {
    /** {@code BaseQuestFile.VERSION} of FTB Quests 2101.1. */
    public static final int FILE_VERSION = 13;
    public static final String GROUP_FILE = "chapter_groups.snbt", DATA_FILE = "data.snbt";

    /** Kind of FTB task a route task became, for the command's report and the test. */
    public enum TaskType { ITEM, ADVANCEMENT, CHECKMARK }

    /** Files by path relative to the quests folder, and the task types used. */
    public record Result(Map<String, String> files, Map<ResourceLocation, TaskType> types) {
    }

    private FtbQuestsExport() {
    }

    /**
     * @param route       eras in handbook order
     * @param advancement the existing advancement for an id, or {@code null} if there is none
     */
    public static Result export(List<RouteTask.Era> route, Function<ResourceLocation, ResourceLocation> advancement) {
        Map<String, String> files = new LinkedHashMap<>();
        Map<ResourceLocation, TaskType> types = new LinkedHashMap<>();
        Map<String, Map<String, Object>> lang = new TreeMap<>();
        String groupId = hex("group/promptcritical");

        Snbt data = new Snbt();
        data.open();
        data.kv("default_quest_shape", str("circle"));
        data.kv("fallback_locale", str(Localized.FALLBACK));
        data.kv("id", str(hex("file/promptcritical")));
        data.kv("version", Integer.toString(FILE_VERSION));
        data.close();
        files.put(DATA_FILE, data.toString());

        Snbt groups = new Snbt();
        groups.open();
        groups.line("chapter_groups: [{");
        groups.indent++;
        groups.kv("id", str(groupId));
        groups.indent--;
        groups.line("}]");
        groups.close();
        files.put(GROUP_FILE, groups.toString());
        text(lang, "chapter_group." + groupId + ".title", Localized.EMPTY, "Prompt Critical");

        int order = 0;
        for (RouteTask.Era era : route) {
            String chapterId = hex("chapter/" + era.id());
            String filename = "promptcritical_" + era.era().toLowerCase(Locale.ROOT);
            text(lang, "chapter." + chapterId + ".title", era.title(), era.era());
            Map<String, double[]> pos = layout(era);

            Snbt c = new Snbt();
            c.open();
            c.kv("default_hide_dependency_lines", "false");
            c.kv("default_quest_shape", str(""));
            c.kv("filename", str(filename));
            c.kv("group", str(groupId));
            RouteTask first = era.tasks().isEmpty() ? null : era.tasks().getFirst();
            if (first != null) c.kv("icon", item(first.icon(), 1));
            c.kv("id", str(chapterId));
            c.kv("order_index", Integer.toString(order++));
            c.kv("quest_links", "[ ]");
            c.line("quests: [");
            c.indent++;
            for (RouteTask t : era.tasks()) {
                String questId = hex("quest/" + t.id());
                text(lang, "quest." + questId + ".title", t.title(), t.id().toString());
                description(lang, "quest." + questId + ".quest_desc", t);
                c.line("{");
                c.indent++;
                List<String> deps = new ArrayList<>();
                for (ResourceLocation r : t.requires()) deps.add(str(hex("quest/" + r)));
                if (!deps.isEmpty()) c.kv("dependencies", "[" + String.join(", ", deps) + "]");
                c.kv("icon", item(t.icon(), 1));
                c.kv("id", str(questId));
                c.line("tasks: [{");
                c.indent++;
                TaskType type = task(c, t, advancement);
                types.put(t.id(), type);
                c.indent--;
                c.line("}]");
                double[] xy = pos.get(t.id().toString());
                c.kv("x", dbl(xy[0]));
                c.kv("y", dbl(xy[1]));
                c.indent--;
                c.line("}");
            }
            c.indent--;
            c.line("]");
            c.close();
            files.put("chapters/" + filename + ".snbt", c.toString());
        }

        for (Map.Entry<String, Map<String, Object>> l : lang.entrySet()) {
            Snbt s = new Snbt();
            s.open();
            for (Map.Entry<String, Object> e : l.getValue().entrySet()) {
                if (e.getValue() instanceof List<?> lines) {
                    List<String> q = new ArrayList<>();
                    for (Object o : lines) q.add(str((String) o));
                    s.kv(str(e.getKey()), "[" + String.join(", ", q) + "]");
                } else {
                    s.kv(str(e.getKey()), str((String) e.getValue()));
                }
            }
            s.close();
            files.put("lang/" + l.getKey() + ".snbt", s.toString());
        }
        return new Result(files, types);
    }

    private static TaskType task(Snbt c, RouteTask t, Function<ResourceLocation, ResourceLocation> advancement) {
        c.kv("id", str(hex("task/" + t.id())));
        if (t.condition() instanceof RouteCondition.Obtain o && !o.tag()) {
            if (o.count() > 1) c.kv("count", o.count() + "L");
            c.kv("item", item(o.id(), 1));
            c.kv("type", str("item"));
            return TaskType.ITEM;
        }
        ResourceLocation adv = advancement.apply(t.id());
        if (adv == null) {
            String path = t.id().getPath();
            adv = advancement.apply(ResourceLocation.fromNamespaceAndPath(t.id().getNamespace(), path.substring(path.lastIndexOf('/') + 1)));
        }
        if (adv != null) {
            c.kv("advancement", str(adv.toString()));
            c.kv("criterion", str(""));
            c.kv("type", str("advancement"));
            return TaskType.ADVANCEMENT;
        }
        c.kv("type", str("checkmark"));
        return TaskType.CHECKMARK;
    }

    /** Columns by depth in the {@code requires} graph, rows by branch; quests of one branch and depth stack down. */
    static Map<String, double[]> layout(RouteTask.Era era) {
        Map<String, RouteTask> byId = new HashMap<>();
        for (RouteTask t : era.tasks()) byId.put(t.id().toString(), t);
        Map<String, Integer> depth = new HashMap<>();
        for (RouteTask t : era.tasks()) depth(t, byId, depth, new TreeSet<>());
        List<String> branches = new ArrayList<>();
        for (RouteTask.Branch b : era.branches()) branches.add(b.id());
        for (RouteTask t : era.tasks()) if (!branches.contains(t.branch())) branches.add(t.branch());
        // ряды ветки: столько, сколько заданий одной глубины в ней
        Map<String, Map<Integer, Integer>> stack = new HashMap<>();
        Map<String, double[]> out = new HashMap<>();
        Map<String, List<RouteTask>> perBranch = new LinkedHashMap<>();
        for (String b : branches) perBranch.put(b, new ArrayList<>());
        for (RouteTask t : era.tasks()) perBranch.get(t.branch()).add(t);
        double y0 = 0;
        for (Map.Entry<String, List<RouteTask>> b : perBranch.entrySet()) {
            int rows = 1;
            for (RouteTask t : b.getValue()) {
                int d = depth.get(t.id().toString());
                int row = stack.computeIfAbsent(b.getKey(), k -> new HashMap<>()).merge(d, 1, Integer::sum) - 1;
                rows = Math.max(rows, row + 1);
                out.put(t.id().toString(), new double[] {d * 1.5, y0 + row * 1.25});
            }
            y0 += rows * 1.25 + 1.0;
        }
        return out;
    }

    private static int depth(RouteTask t, Map<String, RouteTask> byId, Map<String, Integer> memo, TreeSet<String> path) {
        String id = t.id().toString();
        Integer m = memo.get(id);
        if (m != null) return m;
        if (!path.add(id)) return 0; // цикл ловит HandbookManager.routeCycle; здесь просто не зацикливаемся
        int d = 0;
        for (ResourceLocation r : t.requires()) {
            RouteTask p = byId.get(r.toString());
            if (p != null) d = Math.max(d, depth(p, byId, memo, path) + 1); // требования из других эпох — вне главы
        }
        path.remove(id);
        memo.put(id, d);
        return d;
    }

    private static void text(Map<String, Map<String, Object>> lang, String key, Localized text, String fallback) {
        Map<String, String> by = text.byLang();
        if (by.isEmpty() || by.keySet().equals(java.util.Set.of(""))) {
            String s = by.getOrDefault("", fallback);
            lang.computeIfAbsent(Localized.FALLBACK, k -> new TreeMap<>()).put(key, s);
            return;
        }
        for (Map.Entry<String, String> e : by.entrySet()) {
            String locale = e.getKey().isEmpty() ? Localized.FALLBACK : e.getKey();
            lang.computeIfAbsent(locale, k -> new TreeMap<>()).put(key, e.getValue());
        }
    }

    /** What to do, then an empty line and how to do it (the handbook shows the hint only in TRAINING). */
    private static void description(Map<String, Map<String, Object>> lang, String key, RouteTask t) {
        TreeSet<String> locales = new TreeSet<>();
        locales.addAll(t.text().byLang().keySet());
        locales.addAll(t.hint().byLang().keySet());
        for (String l : locales) {
            String locale = l.isEmpty() ? Localized.FALLBACK : l;
            List<String> lines = new ArrayList<>();
            String text = t.text().byLang().containsKey(l) ? t.text().byLang().get(l) : t.text().get(locale);
            String hint = t.hint().byLang().containsKey(l) ? t.hint().byLang().get(l) : t.hint().get(locale);
            if (text != null && !text.isBlank()) lines.addAll(List.of(text.replace("\r", "").split("\n")));
            if (hint != null && !hint.isBlank()) {
                if (!lines.isEmpty()) lines.add("");
                lines.addAll(List.of(hint.replace("\r", "").split("\n")));
            }
            if (!lines.isEmpty()) lang.computeIfAbsent(locale, k -> new TreeMap<>()).put(key, lines);
        }
    }

    /** 16 upper-case hex digits (FTB Quests' {@code %016X}), never zero. */
    public static String hex(String key) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(("promptcritical:ftbquests/" + key).getBytes(StandardCharsets.UTF_8));
            long v = 0;
            for (int i = 0; i < 8; i++) v = (v << 8) | (d[i] & 0xFF);
            if (v == 0) v = 1;
            return String.format(Locale.ROOT, "%016X", v);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String item(ResourceLocation id, int count) {
        return "{ count: " + count + ", id: " + str(id.toString()) + " }";
    }

    private static String dbl(double v) {
        return String.format(Locale.ROOT, "%.2fd", v);
    }

    /** SNBT string in double quotes. */
    static String str(String s) {
        StringBuilder b = new StringBuilder(s.length() + 2).append('"');
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\t' -> b.append("\\t");
                default -> b.append(ch);
            }
        }
        return b.append('"').toString();
    }

    /** FTB-style SNBT: one key per line, tab indents, no commas between lines. */
    private static final class Snbt {
        private final StringBuilder b = new StringBuilder();
        int indent;

        void line(String s) {
            b.append("\t".repeat(indent)).append(s).append('\n');
        }

        void kv(String key, String value) {
            line(key + ": " + value);
        }

        void open() {
            line("{");
            indent++;
        }

        void close() {
            indent--;
            line("}");
        }

        @Override
        public String toString() {
            return b.toString();
        }
    }
}
