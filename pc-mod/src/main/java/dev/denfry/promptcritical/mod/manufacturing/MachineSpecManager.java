package dev.denfry.promptcritical.mod.manufacturing;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.sim.manufacturing.assembly.MachineSpec;
import dev.denfry.promptcritical.sim.manufacturing.assembly.PartRequirement;
import dev.denfry.promptcritical.sim.metallurgy.HeatTreatState;
import java.io.Reader;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

/**
 * Спецификации машин из датапака (18 §18.8.1, §18.15.1): {@code machine_spec_template/<класс>.json} — шаблоны
 * классов (насос, вентилятор, двигатель…), {@code machine_spec/<машина>.json} — машина по шаблону с
 * переопределениями ({@code replace}, {@code remove}, {@code add}) или целиком ({@code parts}). Битый файл не роняет
 * загрузку: он пропускается и попадает в {@link Data#problems()}.
 *
 * <p>Позиция: {@code role}, {@code items} (id предметов, без пространства имён — {@code promptcritical}),
 * {@code materials}, {@code count}, {@code it}, {@code min_hrc}, {@code heat_treat}, {@code aspects}.
 */
public final class MachineSpecManager extends SimplePreparableReloadListener<MachineSpecManager.Data> {
    public static final String SPECS = "machine_spec", TEMPLATES = "machine_spec_template";
    private static volatile Data current = Data.EMPTY;

    /** Спецификации по id результата и по своему id. */
    public record Data(Map<String, MachineSpec> byId, Map<String, MachineSpec> byOutput, List<String> problems) {
        static final Data EMPTY = new Data(Map.of(), Map.of(), List.of());

        @Nullable
        public MachineSpec byOutput(String itemId) {
            return byOutput.get(itemId);
        }
    }

    public static Data data() {
        return current;
    }

    @Override
    protected Data prepare(ResourceManager rm, ProfilerFiller profiler) {
        List<String> problems = new ArrayList<>();
        Map<String, MachineSpec> templates = new LinkedHashMap<>();
        for (var e : list(rm, TEMPLATES).entrySet()) {
            try {
                JsonObject o = e.getValue();
                templates.put(e.getKey(), new MachineSpec(e.getKey(), "", parts(o.getAsJsonArray("parts")),
                        bool(o, "run_in", true)));
            } catch (RuntimeException ex) {
                problems.add(TEMPLATES + "/" + e.getKey() + ": " + ex.getMessage());
            }
        }
        Map<String, MachineSpec> byId = new LinkedHashMap<>(), byOutput = new LinkedHashMap<>();
        for (var e : list(rm, SPECS).entrySet()) {
            try {
                MachineSpec spec = spec(e.getKey(), e.getValue(), templates);
                byId.put(spec.id(), spec);
                if (byOutput.put(spec.output(), spec) != null) {
                    problems.add(SPECS + "/" + e.getKey() + ": second spec for " + spec.output());
                }
            } catch (RuntimeException ex) {
                problems.add(SPECS + "/" + e.getKey() + ": " + ex.getMessage());
            }
        }
        return new Data(Map.copyOf(byId), Map.copyOf(byOutput), List.copyOf(problems));
    }

    @Override
    protected void apply(Data data, ResourceManager rm, ProfilerFiller profiler) {
        current = data;
        for (String p : data.problems()) PromptCritical.LOGGER.warn("machine_spec: {}", p);
    }

    /** Разбор одной спецификации; открыт для тестов. */
    static MachineSpec spec(String id, JsonObject o, Map<String, MachineSpec> templates) {
        String output = item(str(o, "output"));
        if (o.has("template")) {
            String t = ResourceLocation.parse(str(o, "template")).toString();
            MachineSpec template = templates.get(t);
            if (template == null) throw new IllegalArgumentException("unknown template " + t);
            Set<String> remove = new LinkedHashSet<>();
            if (o.has("remove")) for (JsonElement r : o.getAsJsonArray("remove")) remove.add(r.getAsString());
            return MachineSpec.fromTemplate(id, output, template, parts(o.getAsJsonArray("replace")), remove,
                    parts(o.getAsJsonArray("add")), o.has("run_in") ? o.get("run_in").getAsBoolean() : null);
        }
        return new MachineSpec(id, output, parts(o.getAsJsonArray("parts")), bool(o, "run_in", true));
    }

    private static List<PartRequirement> parts(@Nullable JsonArray a) {
        List<PartRequirement> out = new ArrayList<>();
        if (a == null) return out;
        for (JsonElement e : a) out.add(part(e.getAsJsonObject()));
        return out;
    }

    private static PartRequirement part(JsonObject o) {
        Set<String> items = new LinkedHashSet<>();
        for (JsonElement e : o.getAsJsonArray("items")) items.add(item(e.getAsString()));
        Set<String> materials = new LinkedHashSet<>();
        if (o.has("materials")) for (JsonElement e : o.getAsJsonArray("materials")) materials.add(e.getAsString());
        Set<HeatTreatState> ht = EnumSet.noneOf(HeatTreatState.class);
        if (o.has("heat_treat")) for (JsonElement e : o.getAsJsonArray("heat_treat")) ht.add(enumOf(HeatTreatState.class, e));
        Set<PartRequirement.Aspect> aspects = EnumSet.noneOf(PartRequirement.Aspect.class);
        if (o.has("aspects")) for (JsonElement e : o.getAsJsonArray("aspects")) aspects.add(enumOf(PartRequirement.Aspect.class, e));
        return new PartRequirement(str(o, "role"), items, materials, o.has("count") ? o.get("count").getAsInt() : 1,
                o.has("it") ? o.get("it").getAsInt() : 0, o.has("min_hrc") ? o.get("min_hrc").getAsInt() : 0, ht, aspects);
    }

    /** Полный id предмета: без пространства имён — {@code promptcritical}. */
    public static String item(String s) {
        return s.indexOf(':') < 0 ? ResourceLocation.fromNamespaceAndPath(PromptCritical.MOD_ID, s).toString()
                : ResourceLocation.parse(s).toString();
    }

    private static <E extends Enum<E>> E enumOf(Class<E> type, JsonElement e) {
        return Enum.valueOf(type, e.getAsString().toUpperCase(Locale.ROOT));
    }

    private static String str(JsonObject o, String k) {
        if (!o.has(k)) throw new IllegalArgumentException("missing " + k);
        return o.get(k).getAsString();
    }

    private static boolean bool(JsonObject o, String k, boolean def) {
        return o.has(k) ? o.get(k).getAsBoolean() : def;
    }

    /** Файлы каталога: id вида {@code ns:имя} без расширения. */
    private static Map<String, JsonObject> list(ResourceManager rm, String dir) {
        Map<String, JsonObject> out = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, Resource> r : rm.listResources(dir, p -> p.getPath().endsWith(".json")).entrySet()) {
            ResourceLocation key = r.getKey();
            String path = key.getPath().substring(dir.length() + 1, key.getPath().length() - ".json".length());
            try (Reader reader = r.getValue().openAsReader()) {
                out.put(key.getNamespace() + ":" + path, JsonParser.parseReader(reader).getAsJsonObject());
            } catch (Exception ex) {
                PromptCritical.LOGGER.warn("machine_spec: cannot read {}: {}", key, ex.getMessage());
            }
        }
        return out;
    }
}
