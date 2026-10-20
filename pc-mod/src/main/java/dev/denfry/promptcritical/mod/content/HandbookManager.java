package dev.denfry.promptcritical.mod.content;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.denfry.promptcritical.mod.PromptCritical;
import java.io.IOException;
import java.io.Reader;
import com.mojang.serialization.JsonOps;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.advancements.critereon.MinMaxBounds;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.Nullable;

/**
 * Содержимое справочника из датапака (11 §11.1.2): {@code handbook/categories/*.json}, {@code handbook/entries/**.json}
 * (ID главы — путь без расширения), {@code handbook/pages/<lang>/<глава>/<file>.md}, «Маршрут» {@code handbook/route/<эпоха>.json}
 * (11 §11.1.5). Сервер может переопределить или дополнить любой файл. Битая глава или задание пропускаются с
 * предупреждением в лог, остальные грузятся.
 */
public final class HandbookManager extends SimplePreparableReloadListener<HandbookManager.Data> {
    private static final String ROOT = "handbook";
    private static volatile Data current = Data.EMPTY;

    /**
     * Загруженный справочник: разделы и главы по порядку, тексты {@code глава → язык → файл → Markdown}, эпохи маршрута
     * по порядку и все его задания по ID.
     */
    public record Data(List<HandbookCategory> categories, Map<ResourceLocation, HandbookEntry> entries,
            Map<ResourceLocation, Map<String, Map<String, String>>> texts, List<RouteTask.Era> route,
            Map<ResourceLocation, RouteTask> tasks, List<String> problems) {
        static final Data EMPTY = new Data(List.of(), Map.of(), Map.of(), List.of(), Map.of(), List.of());

        @Nullable
        public HandbookEntry entry(ResourceLocation id) {
            return entries.get(id);
        }

        /** Markdown страницы на языке {@code lang} (иначе английский, иначе любой); {@code null} — нет файла. */
        @Nullable
        public String text(ResourceLocation entry, String lang, String file) {
            Map<String, Map<String, String>> byLang = texts.getOrDefault(entry, Map.of());
            for (String l : new String[] {lang, Localized.FALLBACK}) {
                Map<String, String> t = byLang.get(l);
                if (t != null && t.containsKey(file)) return t.get(file);
            }
            for (Map<String, String> any : byLang.values()) if (any.containsKey(file)) return any.get(file);
            return null;
        }
    }

    public static Data data() {
        return current;
    }

    @Override
    protected Data prepare(ResourceManager rm, ProfilerFiller profiler) {
        List<String> problems = new ArrayList<>();
        List<HandbookCategory> categories = new ArrayList<>();
        for (Map.Entry<ResourceLocation, Resource> r : rm.listResources(ROOT + "/categories", p -> p.getPath().endsWith(".json")).entrySet()) {
            ResourceLocation id = idOf(r.getKey(), ROOT + "/categories/", ".json");
            try {
                JsonObject o = read(r.getValue()).getAsJsonObject();
                categories.add(new HandbookCategory(id, Localized.of(o, "title"),
                        ResourceLocation.parse(GsonHelper.getAsString(o, "icon", "minecraft:book")), GsonHelper.getAsInt(o, "sort", 0)));
            } catch (RuntimeException | IOException e) {
                problems.add("category " + id + ": " + e.getMessage());
            }
        }
        categories.sort(Comparator.comparingInt(HandbookCategory::sortIndex).thenComparing(c -> c.id().toString()));

        List<HandbookEntry> list = new ArrayList<>();
        for (Map.Entry<ResourceLocation, Resource> r : rm.listResources(ROOT + "/entries", p -> p.getPath().endsWith(".json")).entrySet()) {
            ResourceLocation id = idOf(r.getKey(), ROOT + "/entries/", ".json");
            try {
                list.add(parseEntry(id, read(r.getValue()).getAsJsonObject()));
            } catch (RuntimeException | IOException e) {
                problems.add("entry " + id + ": " + e.getMessage());
            }
        }
        list.sort(Comparator.comparingInt(HandbookEntry::sortIndex).thenComparing(e -> e.id().toString()));
        Map<ResourceLocation, HandbookEntry> entries = new LinkedHashMap<>();
        for (HandbookEntry e : list) entries.put(e.id(), e);

        Map<ResourceLocation, Map<String, Map<String, String>>> texts = new HashMap<>();
        for (Map.Entry<ResourceLocation, Resource> r : rm.listResources(ROOT + "/pages", p -> p.getPath().endsWith(".md")).entrySet()) {
            // handbook/pages/<lang>/<путь главы>/<file>.md
            String rest = r.getKey().getPath().substring((ROOT + "/pages/").length());
            int a = rest.indexOf('/'), b = rest.lastIndexOf('/');
            if (a < 0 || b <= a) {
                problems.add("page " + r.getKey() + ": expected pages/<lang>/<entry>/<file>.md");
                continue;
            }
            ResourceLocation entry = ResourceLocation.fromNamespaceAndPath(r.getKey().getNamespace(), rest.substring(a + 1, b));
            String file = rest.substring(b + 1, rest.length() - ".md".length());
            try (Reader in = r.getValue().openAsReader()) {
                StringBuilder sb = new StringBuilder();
                char[] buf = new char[4096];
                for (int n = in.read(buf); n > 0; n = in.read(buf)) sb.append(buf, 0, n);
                texts.computeIfAbsent(entry, k -> new HashMap<>()).computeIfAbsent(rest.substring(0, a), k -> new HashMap<>())
                        .put(file, sb.toString().replace("\r", ""));
            } catch (IOException e) {
                problems.add("page " + r.getKey() + ": " + e.getMessage());
            }
        }

        List<RouteTask.Era> route = new ArrayList<>();
        Map<ResourceLocation, RouteTask> tasks = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, Resource> r : rm.listResources(ROOT + "/route", p -> p.getPath().endsWith(".json")).entrySet()) {
            ResourceLocation id = idOf(r.getKey(), ROOT + "/route/", ".json");
            try {
                RouteTask.Era era = parseEra(id, read(r.getValue()).getAsJsonObject(), problems);
                route.add(era);
                for (RouteTask t : era.tasks()) {
                    if (tasks.putIfAbsent(t.id(), t) != null) problems.add("route " + id + ": duplicate task " + t.id());
                }
            } catch (RuntimeException | IOException e) {
                problems.add("route " + id + ": " + e.getMessage());
            }
        }
        route.sort(Comparator.comparingInt(RouteTask.Era::sortIndex).thenComparing(e -> e.id().toString()));

        Data d = new Data(List.copyOf(categories), Collections.unmodifiableMap(entries), texts, List.copyOf(route),
                Collections.unmodifiableMap(tasks), List.of());
        problems.addAll(validate(d));
        return new Data(d.categories(), d.entries(), texts, d.route(), d.tasks(), List.copyOf(problems));
    }

    @Override
    protected void apply(Data data, ResourceManager rm, ProfilerFiller profiler) {
        current = data;
        for (String p : data.problems()) PromptCritical.LOGGER.warn("handbook: {}", p);
        PromptCritical.LOGGER.info("handbook: {} categories, {} entries, {} route tasks", data.categories().size(),
                data.entries().size(), data.tasks().size());
    }

    /**
     * Связность без реестров: раздел есть, текст есть хотя бы на английском, зоны ведут к главам; у маршрута (T11.17)
     * каждое {@code requires} и вариант развилки существуют, главы есть, граф без циклов.
     */
    public static List<String> validate(Data d) {
        List<String> out = new ArrayList<>();
        Set<ResourceLocation> cats = new HashSet<>();
        for (HandbookCategory c : d.categories()) cats.add(c.id());
        for (HandbookEntry e : d.entries().values()) {
            if (!cats.contains(e.category())) out.add("entry " + e.id() + ": unknown category " + e.category());
            for (HandbookPage p : e.pages()) {
                switch (p) {
                    case HandbookPage.TextPage t -> {
                        Map<String, String> en = d.texts().getOrDefault(e.id(), Map.of()).get(Localized.FALLBACK);
                        if (en == null || !en.containsKey(t.file())) out.add("entry " + e.id() + ": no en_us text " + t.file());
                    }
                    case HandbookPage.ImagePage i -> {
                        for (HandbookPage.Zone z : i.zones())
                            if (!d.entries().containsKey(z.entry())) out.add("entry " + e.id() + ": zone to unknown entry " + z.entry());
                    }
                    case HandbookPage.RecipePage ignored -> {
                    }
                    case HandbookPage.MultiblockPage m -> {
                        if (m.blocks().isEmpty()) out.add("entry " + e.id() + ": empty multiblock");
                    }
                    case HandbookPage.FormulaPage f -> {
                        if (f.vars().isEmpty()) out.add("entry " + e.id() + ": formula " + f.formula() + " without variables");
                    }
                    case HandbookPage.PonderPage ignored -> {
                    }
                }
            }
        }
        for (RouteTask t : d.tasks().values()) {
            for (ResourceLocation r : t.requires())
                if (!d.tasks().containsKey(r)) out.add("route task " + t.id() + ": requires unknown " + r);
            if (t.condition() instanceof RouteCondition.Choice c) {
                for (ResourceLocation o : c.options())
                    if (o.equals(t.id()) || !d.tasks().containsKey(o)) out.add("route task " + t.id() + ": bad choice option " + o);
            }
            if (t.entry() != null && !d.entries().containsKey(t.entry())) out.add("route task " + t.id() + ": unknown entry " + t.entry());
        }
        ResourceLocation cycle = routeCycle(d.tasks());
        if (cycle != null) out.add("route task " + cycle + ": requires form a cycle");
        return out;
    }

    /** Задание на цикле {@code requires} (и вариантов развилок) или {@code null}: обход в глубину с тремя цветами. */
    @Nullable
    public static ResourceLocation routeCycle(Map<ResourceLocation, RouteTask> tasks) {
        Map<ResourceLocation, Integer> color = new HashMap<>();
        for (ResourceLocation start : tasks.keySet()) {
            if (color.containsKey(start)) continue;
            Deque<ResourceLocation> path = new ArrayDeque<>();
            Deque<Iterator<ResourceLocation>> next = new ArrayDeque<>();
            color.put(start, 1);
            path.push(start);
            next.push(edges(tasks.get(start)).iterator());
            while (!path.isEmpty()) {
                if (!next.peek().hasNext()) {
                    color.put(path.pop(), 2);
                    next.pop();
                    continue;
                }
                ResourceLocation n = next.peek().next();
                if (!tasks.containsKey(n)) continue;
                Integer c = color.get(n);
                if (c == null) {
                    color.put(n, 1);
                    path.push(n);
                    next.push(edges(tasks.get(n)).iterator());
                } else if (c == 1) {
                    return n;
                }
            }
        }
        return null;
    }

    private static List<ResourceLocation> edges(RouteTask t) {
        if (!(t.condition() instanceof RouteCondition.Choice c)) return t.requires();
        List<ResourceLocation> all = new ArrayList<>(t.requires());
        all.addAll(c.options());
        return all;
    }

    static RouteTask.Era parseEra(ResourceLocation id, JsonObject o, List<String> problems) {
        String era = GsonHelper.getAsString(o, "era");
        List<RouteTask> tasks = new ArrayList<>();
        for (JsonElement el : GsonHelper.getAsJsonArray(o, "tasks")) {
            JsonObject t = el.getAsJsonObject();
            try {
                tasks.add(parseTask(era, t));
            } catch (RuntimeException e) {
                problems.add("route " + id + ": task " + GsonHelper.getAsString(t, "id", "?") + ": " + e.getMessage());
            }
        }
        return new RouteTask.Era(id, era, Localized.of(o, "title"), GsonHelper.getAsInt(o, "sort", 0), List.copyOf(tasks));
    }

    public static RouteTask parseTask(String era, JsonObject o) {
        List<ResourceLocation> requires = new ArrayList<>();
        for (JsonElement r : GsonHelper.getAsJsonArray(o, "requires", new JsonArray())) requires.add(ResourceLocation.parse(r.getAsString()));
        RouteTask.Kind kind = RouteTask.Kind.of(GsonHelper.getAsString(o, "type"));
        JsonObject c = GsonHelper.getAsJsonObject(o, "condition");
        RouteCondition cond = switch (kind) {
            case OBTAIN -> c.has("tag")
                    ? new RouteCondition.Obtain(ResourceLocation.parse(GsonHelper.getAsString(c, "tag")), true, GsonHelper.getAsInt(c, "count", 1))
                    : new RouteCondition.Obtain(ResourceLocation.parse(GsonHelper.getAsString(c, "item")), false, GsonHelper.getAsInt(c, "count", 1));
            case BUILD -> new RouteCondition.Build(ResourceLocation.parse(GsonHelper.getAsString(c, "block")), GsonHelper.getAsInt(c, "count", 1));
            case DISCOVER -> c.has("tag")
                    ? new RouteCondition.Discover(ResourceLocation.parse(GsonHelper.getAsString(c, "tag")), true)
                    : new RouteCondition.Discover(ResourceLocation.parse(GsonHelper.getAsString(c, "block")), false);
            case REACH, OPERATE -> {
                Map<String, MinMaxBounds.Doubles> pred = new LinkedHashMap<>();
                for (Map.Entry<String, JsonElement> p : GsonHelper.getAsJsonObject(c, "predicate", new JsonObject()).entrySet())
                    pred.put(p.getKey(), MinMaxBounds.Doubles.CODEC.parse(JsonOps.INSTANCE, p.getValue()).getOrThrow());
                if (pred.size() > 1) throw new IllegalArgumentException("an event carries one value: one predicate key");
                yield new RouteCondition.Event(ResourceLocation.parse(GsonHelper.getAsString(c, "event")), Map.copyOf(pred),
                        GsonHelper.getAsDouble(c, "duration_s", 0));
            }
            case CHOICE -> {
                List<ResourceLocation> options = new ArrayList<>();
                for (JsonElement e : GsonHelper.getAsJsonArray(c, "options")) options.add(ResourceLocation.parse(e.getAsString()));
                if (options.size() < 2) throw new IllegalArgumentException("a choice needs two options or more");
                yield new RouteCondition.Choice(List.copyOf(options));
            }
            case SCENARIO, PROGRAM -> throw new IllegalArgumentException("type " + kind.key() + " needs the trainer (11 §11.3) and PLC (03d)");
        };
        JsonObject reward = GsonHelper.getAsJsonObject(o, "reward", new JsonObject());
        return new RouteTask(ResourceLocation.parse(GsonHelper.getAsString(o, "id")), era, List.copyOf(requires), kind, cond,
                o.has("entry") ? ResourceLocation.parse(GsonHelper.getAsString(o, "entry")) : null,
                o.has("ponder") ? ResourceLocation.parse(GsonHelper.getAsString(o, "ponder")) : null,
                GsonHelper.getAsInt(reward, "xp", 0), ResourceLocation.parse(GsonHelper.getAsString(o, "icon", "minecraft:book")),
                Localized.of(o, "title"), Localized.of(o, "text"), Localized.of(o, "hint"));
    }

    static HandbookEntry parseEntry(ResourceLocation id, JsonObject o) {
        List<HandbookPage> pages = new ArrayList<>();
        for (JsonElement el : GsonHelper.getAsJsonArray(o, "pages")) pages.add(parsePage(el.getAsJsonObject()));
        return new HandbookEntry(id, ResourceLocation.parse(GsonHelper.getAsString(o, "category")), Localized.of(o, "title"),
                ResourceLocation.parse(GsonHelper.getAsString(o, "icon", "minecraft:book")), List.copyOf(pages),
                parseUnlock(o.get("unlock")), GsonHelper.getAsInt(o, "sort", 0), Localized.of(o, "hint"), Localized.of(o, "next"));
    }

    private static HandbookPage parsePage(JsonObject o) {
        String type = GsonHelper.getAsString(o, "type");
        return switch (type) {
            case "text" -> new HandbookPage.TextPage(GsonHelper.getAsString(o, "file"));
            case "image" -> {
                List<HandbookPage.Zone> zones = new ArrayList<>();
                for (JsonElement z : GsonHelper.getAsJsonArray(o, "zones", new JsonArray())) {
                    JsonObject zo = z.getAsJsonObject();
                    zones.add(new HandbookPage.Zone(GsonHelper.getAsInt(zo, "x"), GsonHelper.getAsInt(zo, "y"),
                            GsonHelper.getAsInt(zo, "w"), GsonHelper.getAsInt(zo, "h"),
                            ResourceLocation.parse(GsonHelper.getAsString(zo, "entry"))));
                }
                yield new HandbookPage.ImagePage(ResourceLocation.parse(GsonHelper.getAsString(o, "texture")),
                        GsonHelper.getAsInt(o, "width"), GsonHelper.getAsInt(o, "height"), Localized.of(o, "caption"), List.copyOf(zones));
            }
            case "recipe" -> new HandbookPage.RecipePage(ResourceLocation.parse(GsonHelper.getAsString(o, "recipe")),
                    Localized.of(o, "caption"));
            case "multiblock" -> {
                List<HandbookPage.Placed> blocks = new ArrayList<>();
                for (JsonElement b : GsonHelper.getAsJsonArray(o, "blocks")) {
                    JsonObject bo = b.getAsJsonObject();
                    JsonArray pos = GsonHelper.getAsJsonArray(bo, "pos");
                    blocks.add(new HandbookPage.Placed(pos.get(0).getAsInt(), pos.get(1).getAsInt(), pos.get(2).getAsInt(),
                            GsonHelper.getAsString(bo, "state")));
                }
                yield new HandbookPage.MultiblockPage(Localized.of(o, "name"), List.copyOf(blocks), Localized.of(o, "caption"));
            }
            case "formula" -> {
                List<HandbookPage.Variable> vars = new ArrayList<>();
                for (JsonElement v : GsonHelper.getAsJsonArray(o, "vars", new JsonArray())) {
                    JsonObject vo = v.getAsJsonObject();
                    vars.add(new HandbookPage.Variable(GsonHelper.getAsString(vo, "symbol"), Localized.of(vo, "text")));
                }
                yield new HandbookPage.FormulaPage(ResourceLocation.parse(GsonHelper.getAsString(o, "formula")), List.copyOf(vars),
                        Localized.of(o, "caption"));
            }
            case "ponder" -> new HandbookPage.PonderPage(ResourceLocation.parse(GsonHelper.getAsString(o, "item")), Localized.of(o, "caption"));
            default -> throw new IllegalArgumentException("unknown page type " + type);
        };
    }

    private static UnlockCondition parseUnlock(@Nullable JsonElement e) {
        if (e == null) return UnlockCondition.ALWAYS;
        JsonObject o = e.getAsJsonObject();
        String type = GsonHelper.getAsString(o, "type");
        return switch (type) {
            case "always" -> UnlockCondition.ALWAYS;
            case "advancement" -> new UnlockCondition.AdvancementDone(ResourceLocation.parse(GsonHelper.getAsString(o, "id")));
            case "item_obtained" -> new UnlockCondition.ItemObtained(ResourceLocation.parse(GsonHelper.getAsString(o, "item")));
            case "block_placed" -> new UnlockCondition.BlockPlaced(ResourceLocation.parse(GsonHelper.getAsString(o, "block")));
            default -> throw new IllegalArgumentException("unknown unlock " + type);
        };
    }

    private static ResourceLocation idOf(ResourceLocation file, String prefix, String suffix) {
        String p = file.getPath();
        return ResourceLocation.fromNamespaceAndPath(file.getNamespace(), p.substring(prefix.length(), p.length() - suffix.length()));
    }

    private static JsonElement read(Resource r) throws IOException {
        try (Reader in = r.openAsReader()) {
            return JsonParser.parseReader(in);
        }
    }
}
