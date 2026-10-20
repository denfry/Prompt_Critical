package dev.denfry.promptcritical.mod.content;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.denfry.promptcritical.mod.config.DifficultyPreset;
import dev.denfry.promptcritical.mod.config.ServerConfig;
import dev.denfry.promptcritical.sim.plant.SteamPlant;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

/**
 * «Маршрут» на стороне сервера (11 §11.1.5, R23): выполненные задания игрока ({@link ContentAttachments#ROUTE_DONE}),
 * проверка условий и награда — только опыт (C2). Предметы, постройки, находки и развилки проверяются раз в секунду,
 * события — по мере доставки триггеру {@code sim_event}. Отсчёт {@code reach}/{@code operate} живёт в памяти: после
 * перезахода он начинается заново.
 */
public final class RouteService {
    /** Перерыв в замерах, после которого отсчёт начинается заново, тиков: два пропущенных замера. */
    public static final long SAMPLE_GAP_TICKS = 2L * SteamPlant.SAMPLE_TICKS + 1;
    public static final int POLL_TICKS = 20;

    /** Начало и последний замер серии, попавшей в предикат (тики сервера). */
    private record Hold(long start, long last) {
    }

    private static final Map<UUID, Map<ResourceLocation, Hold>> HOLDS = new HashMap<>();

    private RouteService() {
    }

    public static boolean done(ServerPlayer p, ResourceLocation task) {
        return p.getData(ContentAttachments.ROUTE_DONE).contains(task);
    }

    /** Открыто: выполнены все требуемые задания. */
    public static boolean open(Set<ResourceLocation> done, RouteTask t) {
        return done.containsAll(t.requires());
    }

    /** Секунды текущей серии замеров задания {@code task} или 0. */
    public static double heldSeconds(ServerPlayer p, ResourceLocation task) {
        Hold h = HOLDS.getOrDefault(p.getUUID(), Map.of()).get(task);
        return h == null ? 0 : (h.last() - h.start()) / 20.0;
    }

    /** Событие домена или мира, засчитанное игроку (он рядом с источником или автор), в тик {@code tick}. */
    public static void onEvent(ServerPlayer p, String event, String tag, double value, long tick) {
        Set<ResourceLocation> done = p.getData(ContentAttachments.ROUTE_DONE);
        for (RouteTask t : HandbookManager.data().tasks().values()) {
            if (!(t.condition() instanceof RouteCondition.Event c) || !c.event().toString().equals(event) || done.contains(t.id())) continue;
            Map<ResourceLocation, Hold> holds = HOLDS.computeIfAbsent(p.getUUID(), k -> new HashMap<>());
            if (!c.matches(event, tag, value)) {
                holds.remove(t.id());
                continue;
            }
            if (c.durationS() <= 0) {
                complete(p, t);
                continue;
            }
            Hold h = holds.get(t.id());
            h = h == null || tick - h.last() > SAMPLE_GAP_TICKS ? new Hold(tick, tick) : new Hold(h.start(), tick);
            if ((h.last() - h.start()) / 20.0 >= c.durationS()) {
                holds.remove(t.id());
                complete(p, t);
            } else {
                holds.put(t.id(), h);
            }
        }
    }

    /** Раз в {@link #POLL_TICKS}: предметы, постройки, находки и развилки. */
    public static void poll(ServerPlayer p) {
        Set<ResourceLocation> done = p.getData(ContentAttachments.ROUTE_DONE);
        for (RouteTask t : HandbookManager.data().tasks().values()) {
            if (!done.contains(t.id()) && met(p, done, t.condition())) {
                complete(p, t);
                done = p.getData(ContentAttachments.ROUTE_DONE);
            }
        }
    }

    public static void forget(ServerPlayer p) {
        HOLDS.remove(p.getUUID());
    }

    private static boolean met(ServerPlayer p, Set<ResourceLocation> done, RouteCondition c) {
        return switch (c) {
            case RouteCondition.Obtain o -> {
                int n = 0;
                for (ItemStack s : p.getInventory().items) if (matches(s, o)) n += s.getCount();
                for (ItemStack s : p.getInventory().offhand) if (matches(s, o)) n += s.getCount();
                yield n >= o.count();
            }
            case RouteCondition.Build b -> {
                Item item = BuiltInRegistries.BLOCK.get(b.block()).asItem();
                yield item != Items.AIR && p.getStats().getValue(Stats.ITEM_USED.get(item)) >= b.count();
            }
            case RouteCondition.Discover d -> {
                if (!d.tag()) yield p.getStats().getValue(Stats.BLOCK_MINED.get(BuiltInRegistries.BLOCK.get(d.id()))) > 0;
                boolean any = false;
                for (Holder<Block> h : BuiltInRegistries.BLOCK.getTagOrEmpty(TagKey.create(Registries.BLOCK, d.id())))
                    any |= p.getStats().getValue(Stats.BLOCK_MINED.get(h.value())) > 0;
                yield any;
            }
            case RouteCondition.Choice ch -> ch.options().stream().anyMatch(done::contains);
            case RouteCondition.Event ignored -> false;
        };
    }

    private static boolean matches(ItemStack s, RouteCondition.Obtain o) {
        if (s.isEmpty()) return false;
        return o.tag() ? s.is(TagKey.create(Registries.ITEM, o.id())) : BuiltInRegistries.ITEM.getKey(s.getItem()).equals(o.id());
    }

    /** Отметить задание и выдать опыт; развилки, которые оно закрывает, отмечаются следом. */
    public static void complete(ServerPlayer p, RouteTask t) {
        Set<ResourceLocation> done = new LinkedHashSet<>(p.getData(ContentAttachments.ROUTE_DONE));
        if (!done.add(t.id())) return;
        p.setData(ContentAttachments.ROUTE_DONE, Set.copyOf(done));
        if (t.xp() > 0) p.giveExperiencePoints(t.xp());
        p.sendSystemMessage(Component.translatable(ContentText.ROUTE_DONE, t.title().get(HandbookService.lang(p)), t.xp())
                .withStyle(ChatFormatting.GOLD));
        for (RouteTask other : HandbookManager.data().tasks().values()) {
            if (other.condition() instanceof RouteCondition.Choice c && !done.contains(other.id()) && c.options().contains(t.id()))
                complete(p, other);
        }
    }

    /** Уровень подсказок: {@code TRAINING} — «как сделать», {@code STANDARD} — «что сделать», {@code HARDCORE} — названия. */
    public static DifficultyPreset level() {
        return ServerConfig.CONTENT_HINTS_BY_DIFFICULTY.get() ? ServerConfig.DIFFICULTY_PRESET.get() : DifficultyPreset.STANDARD;
    }

    /** Представление вкладки «Маршрут»: эпохи и задания со статусом; на {@code HARDCORE} — только выполненные и открытые. */
    public static JsonObject view(ServerPlayer p, String lang) {
        DifficultyPreset level = level();
        Set<ResourceLocation> done = p.getData(ContentAttachments.ROUTE_DONE);
        JsonObject root = new JsonObject();
        root.addProperty("level", level.name().toLowerCase(java.util.Locale.ROOT));
        JsonArray eras = new JsonArray();
        for (RouteTask.Era era : HandbookManager.data().route()) {
            JsonObject e = new JsonObject();
            e.addProperty("id", era.id().toString());
            e.addProperty("era", era.era());
            e.addProperty("title", era.title().get(lang));
            int n = 0;
            JsonArray tasks = new JsonArray();
            for (RouteTask t : era.tasks()) {
                boolean isDone = done.contains(t.id()), isOpen = open(done, t);
                if (isDone) n++;
                if (level == DifficultyPreset.HARDCORE && !isDone && !isOpen) continue;
                tasks.add(task(p, t, lang, level, isDone ? "done" : isOpen ? "open" : "locked"));
            }
            e.addProperty("done", n);
            e.addProperty("total", era.tasks().size());
            e.add("tasks", tasks);
            eras.add(e);
        }
        root.add("eras", eras);
        return root;
    }

    private static JsonObject task(ServerPlayer p, RouteTask t, String lang, DifficultyPreset level, String status) {
        JsonObject o = new JsonObject();
        o.addProperty("id", t.id().toString());
        o.addProperty("title", t.title().get(lang));
        o.addProperty("icon", t.icon().toString());
        o.addProperty("type", t.kind().key());
        o.addProperty("status", status);
        o.addProperty("xp", t.xp());
        JsonArray req = new JsonArray();
        for (ResourceLocation r : t.requires()) req.add(r.toString());
        o.add("requires", req);
        if (t.entry() != null) o.addProperty("entry", t.entry().toString());
        if (t.ponder() != null) o.addProperty("ponder", t.ponder().toString());
        if (level == DifficultyPreset.HARDCORE) return o;
        o.addProperty("text", t.text().get(lang));
        if (level == DifficultyPreset.TRAINING && !t.hint().isEmpty()) o.addProperty("hint", t.hint().get(lang));
        switch (t.condition()) {
            case RouteCondition.Obtain c -> {
                o.addProperty(c.tag() ? "target_tag" : "target", c.id().toString());
                o.addProperty("count", c.count());
            }
            case RouteCondition.Build c -> {
                o.addProperty("target", BuiltInRegistries.BLOCK.get(c.block()).asItem() == Items.AIR ? "" : c.block().toString());
                o.addProperty("count", c.count());
            }
            case RouteCondition.Discover c -> o.addProperty(c.tag() ? "target_tag" : "target", c.id().toString());
            case RouteCondition.Event c -> {
                if (c.durationS() > 0) {
                    o.addProperty("duration_s", c.durationS());
                    o.addProperty("held_s", heldSeconds(p, t.id()));
                }
            }
            case RouteCondition.Choice c -> {
                JsonArray opts = new JsonArray();
                for (ResourceLocation r : c.options()) opts.add(r.toString());
                o.add("options", opts);
            }
        }
        return o;
    }

    /** Все ID реестров, на которые ссылается задание (T11.17): предметы, блоки, теги — с префиксом вида. */
    public static List<String> registryRefs(RouteTask t) {
        List<String> out = new java.util.ArrayList<>();
        out.add("item:" + t.icon());
        if (t.ponder() != null) out.add("item:" + t.ponder());
        switch (t.condition()) {
            case RouteCondition.Obtain c -> out.add((c.tag() ? "item_tag:" : "item:") + c.id());
            case RouteCondition.Build c -> out.add("block:" + c.block());
            case RouteCondition.Discover c -> out.add((c.tag() ? "block_tag:" : "block:") + c.id());
            case RouteCondition.Event ignored -> {
            }
            case RouteCondition.Choice ignored -> {
            }
        }
        return out;
    }
}
