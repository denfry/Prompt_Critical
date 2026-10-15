package dev.denfry.promptcritical.mod.content;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.config.DifficultyPreset;
import dev.denfry.promptcritical.mod.config.ServerConfig;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcDataComponents;
import dev.denfry.promptcritical.mod.registry.PcItems;
import java.util.Map;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Справочник на стороне сервера (11 §11.1): что открыто игроку, выдача книги при первом входе, инструкции к крупным
 * установкам. Содержимое — данные сервера, поэтому клиент получает готовое представление на своём языке
 * ({@link OpenHandbookPayload}); закрытые главы уходят без страниц.
 */
public final class HandbookService {
    /** Установки I0, при постройке которых выдаётся инструкция по эксплуатации (11 §11.1.2). */
    public static final Map<ResourceLocation, ResourceLocation> PROCEDURES = Map.of(
            PcBlocks.FIRE_TUBE_BOILER.getId(), PromptCritical.id("i0/procedure_boiler"),
            PcBlocks.MINE_HOIST.getId(), PromptCritical.id("i0/procedure_hoist"),
            PcBlocks.MAIN_MINE_FAN.getId(), PromptCritical.id("i0/procedure_ventilation"));

    private HandbookService() {
    }

    /** Открыть справочник ({@code document} — только глава {@code entry}, как бумажный регламент). */
    public static void open(ServerPlayer player, @Nullable ResourceLocation entry, boolean document) {
        open(player, entry, document, 0);
    }

    /** То же на странице {@code page} главы (с нуля). */
    public static void open(ServerPlayer player, @Nullable ResourceLocation entry, boolean document, int page) {
        JsonObject v = view(player, entry, document);
        if (page > 0) v.addProperty("page", page);
        PacketDistributor.sendToPlayer(player, new OpenHandbookPayload(v.toString()));
    }

    /** Первый вход: одна книга на игрока (T11.1). */
    public static boolean giveOnJoin(ServerPlayer player) {
        if (!ServerConfig.CONTENT_GIVE_HANDBOOK_ON_JOIN.get() || player.getData(ContentAttachments.HANDBOOK_GIVEN)) return false;
        player.setData(ContentAttachments.HANDBOOK_GIVEN, true);
        ItemStack book = new ItemStack(PcItems.ENGINEERING_HANDBOOK.get());
        if (!player.getInventory().add(book)) player.drop(book, false);
        return true;
    }

    /** Инструкция по эксплуатации установки {@code block}, если она у этой установки есть. */
    public static void issueProcedure(ServerPlayer player, Block block) {
        ResourceLocation ref = PROCEDURES.get(BuiltInRegistries.BLOCK.getKey(block));
        if (ref == null) return;
        player.addItem(procedure(ref, lang(player)));
    }

    public static ItemStack procedure(ResourceLocation ref, String lang) {
        ItemStack s = new ItemStack(PcItems.OPERATING_PROCEDURE.get());
        s.set(PcDataComponents.PROCEDURE_REF.get(), ref);
        HandbookEntry e = HandbookManager.data().entry(ref);
        if (e != null) s.set(DataComponents.ITEM_NAME, Component.literal(e.title().get(lang)));
        return s;
    }

    public static String lang(ServerPlayer player) {
        return player.clientInformation().language().toLowerCase(java.util.Locale.ROOT);
    }

    public static boolean unlocked(ServerPlayer p, UnlockCondition c) {
        if (ServerConfig.CONTENT_HANDBOOK_ALL_UNLOCKED.get()) return true;
        return switch (c) {
            case UnlockCondition.Always ignored -> true;
            case UnlockCondition.AdvancementDone a -> {
                AdvancementHolder h = p.server.getAdvancements().get(a.advancement());
                yield h != null && p.getAdvancements().getOrStartProgress(h).isDone();
            }
            case UnlockCondition.ItemObtained i -> {
                Item item = BuiltInRegistries.ITEM.get(i.item());
                yield item != Items.AIR && (p.getStats().getValue(Stats.ITEM_PICKED_UP.get(item)) > 0
                        || p.getStats().getValue(Stats.ITEM_CRAFTED.get(item)) > 0
                        || p.getInventory().hasAnyMatching(s -> s.is(item)));
            }
            case UnlockCondition.BlockPlaced b -> {
                Item item = BuiltInRegistries.BLOCK.get(b.block()).asItem();
                yield item != Items.AIR && p.getStats().getValue(Stats.ITEM_USED.get(item)) > 0;
            }
        };
    }

    static boolean hints() {
        return ServerConfig.CONTENT_HINTS_BY_DIFFICULTY.get() && ServerConfig.DIFFICULTY_PRESET.get() == DifficultyPreset.TRAINING;
    }

    /** Представление для клиента: разделы, главы (закрытые — с намёком, без страниц), стартовая глава. */
    public static JsonObject view(ServerPlayer player, @Nullable ResourceLocation open, boolean document) {
        HandbookManager.Data d = HandbookManager.data();
        String lang = lang(player);
        JsonObject root = new JsonObject();
        root.addProperty("document", document);
        if (open != null) root.addProperty("open", open.toString());
        JsonArray cats = new JsonArray();
        for (HandbookCategory c : d.categories()) {
            JsonObject o = new JsonObject();
            o.addProperty("id", c.id().toString());
            o.addProperty("title", c.title().get(lang));
            o.addProperty("icon", c.icon().toString());
            cats.add(o);
        }
        root.add("categories", cats);
        boolean hints = hints();
        JsonArray entries = new JsonArray();
        for (HandbookEntry e : d.entries().values()) {
            if (document && !e.id().equals(open)) continue;
            boolean unlocked = document || unlocked(player, e.unlock());
            JsonObject o = new JsonObject();
            o.addProperty("id", e.id().toString());
            o.addProperty("category", e.category().toString());
            o.addProperty("icon", e.icon().toString());
            o.addProperty("unlocked", unlocked);
            if (!unlocked) {
                o.addProperty("title", "???");
                o.addProperty("hint", e.hint().get(lang));
                entries.add(o);
                continue;
            }
            o.addProperty("title", e.title().get(lang));
            if (hints && !e.next().isEmpty()) o.addProperty("next", e.next().get(lang));
            JsonArray pages = new JsonArray();
            for (HandbookPage p : e.pages()) pages.add(page(d, e, p, lang));
            o.add("pages", pages);
            entries.add(o);
        }
        root.add("entries", entries);
        return root;
    }

    private static JsonObject page(HandbookManager.Data d, HandbookEntry e, HandbookPage p, String lang) {
        JsonObject o = new JsonObject();
        switch (p) {
            case HandbookPage.TextPage t -> {
                o.addProperty("type", "text");
                String md = d.text(e.id(), lang, t.file());
                o.addProperty("text", md == null ? "" : md);
            }
            case HandbookPage.ImagePage i -> {
                o.addProperty("type", "image");
                o.addProperty("texture", i.texture().toString());
                o.addProperty("width", i.width());
                o.addProperty("height", i.height());
                o.addProperty("caption", i.caption().get(lang));
                JsonArray zones = new JsonArray();
                for (HandbookPage.Zone z : i.zones()) {
                    JsonObject zo = new JsonObject();
                    zo.addProperty("x", z.x());
                    zo.addProperty("y", z.y());
                    zo.addProperty("w", z.w());
                    zo.addProperty("h", z.h());
                    zo.addProperty("entry", z.entry().toString());
                    zones.add(zo);
                }
                o.add("zones", zones);
            }
            case HandbookPage.RecipePage r -> {
                o.addProperty("type", "recipe");
                o.addProperty("recipe", r.recipe().toString());
                o.addProperty("caption", r.caption().get(lang));
            }
            case HandbookPage.MultiblockPage m -> {
                o.addProperty("type", "multiblock");
                o.addProperty("name", m.name().get(lang));
                o.addProperty("caption", m.caption().get(lang));
                JsonArray blocks = new JsonArray();
                for (HandbookPage.Placed b : m.blocks()) {
                    JsonObject bo = new JsonObject();
                    JsonArray pos = new JsonArray();
                    pos.add(b.x());
                    pos.add(b.y());
                    pos.add(b.z());
                    bo.add("pos", pos);
                    bo.addProperty("state", b.state());
                    blocks.add(bo);
                }
                o.add("blocks", blocks);
            }
        }
        return o;
    }
}
