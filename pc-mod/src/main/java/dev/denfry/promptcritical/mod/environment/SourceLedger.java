package dev.denfry.promptcritical.mod.environment;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.config.ServerConfig;
import dev.denfry.promptcritical.sim.isotopes.IaeaCategory;
import dev.denfry.promptcritical.sim.radiation.ActivityInventory;
import dev.denfry.promptcritical.sim.radiation.PointSource;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.jetbrains.annotations.Nullable;

/**
 * Реестр учтённых источников и правило призрака (04d §4d.6.1, 16 §16.8.4). Предмет с активностью категорий 1–3 МАГАТЭ или
 * выше {@code transport.tracked_activity_bq} получает {@link #SOURCE_ID}, когда его впервые видят в инвентаре игрока или
 * на земле; дальше каждая встреча (инвентарь, предмет на земле, просмотренный контейнер) записывает место и время.
 * <p>
 * Хранилища, которые не просмотреть (AE2, Refined Storage, телепортирующие трубы), источник не прячут: если место, где
 * его видели последним, под наблюдением (чанк загружен, игрок ближе {@link ContainerActivityScan#RANGE_M} м), а предмета
 * нигде нет дольше {@link #GRACE_TICKS} тиков, он становится призраком {@link Status#UNSEEN} и излучает из этой точки.
 * Через {@code isotopes.orphan_after_s} призрак — бесхозный источник: последний державший его игрок получает тревогу,
 * запись уходит в журнал сервера. Призрак снимается, когда предмет снова увиден, когда он пропал рядом с нашей установкой
 * (она приняла его в свой учёт) или когда записано его уничтожение (лава, пустота, взрыв).
 * <p>
 * Пока держатель источника не в сети, запись заморожена: вышедший игрок унёс источник с собой.
 */
public final class SourceLedger extends SavedData {
    /** Метка учтённого источника (компонент в {@link ActivityData#SOURCE_ID}). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<UUID>> SOURCE_ID = ActivityData.SOURCE_ID;

    /** Сколько предмет может не попадаться на глаза под наблюдением, пока его ищет обход контейнеров, тиков. */
    public static final int GRACE_TICKS = 600;
    /** Встреча не старше этого считается свежей: игроки и предметы на земле пересматриваются раз в 20 тиков. */
    static final int FRESH_TICKS = 60;
    /** Пропал ближе этого к нашей установке — она приняла источник в свой учёт, м. */
    static final double CONSUMED_RADIUS_M = 6;

    public enum Status {
        SEEN, UNSEEN, ORPHAN
    }

    /** Запись реестра; позиция — последняя, где источник видели. */
    public static final class Entry {
        final UUID id;
        ResourceKey<Level> dim;
        Vec3 at;
        long lastSeen;
        long missingSince = -1, unseenSince = -1;
        ActivityData activity;
        int count;
        @Nullable
        UUID carriedBy, lastPlayer;
        boolean alarmed;

        Entry(UUID id, ResourceKey<Level> dim, Vec3 at, ActivityData activity, int count) {
            this.id = id;
            this.dim = dim;
            this.at = at;
            this.activity = activity;
            this.count = count;
        }

        public UUID id() {
            return id;
        }

        public ResourceKey<Level> dimension() {
            return dim;
        }

        public Vec3 at() {
            return at;
        }

        public Status status() {
            return unseenSince < 0 ? Status.SEEN : alarmed ? Status.ORPHAN : Status.UNSEEN;
        }

        /** Активность всего стека на момент {@code now}, Бк. */
        public double totalBq(long now) {
            return activity.inventory(now).totalBq() * count;
        }

        PointSource source(long now) {
            PointSource s = activity.inventory(now).source();
            return count == 1 ? s : s.scaled(count);
        }
    }

    private final Map<UUID, Entry> entries = new LinkedHashMap<>();

    public static void register() {
        NeoForge.EVENT_BUS.addListener(SourceLedger::onLeave);
    }

    @Nullable
    private static SourceLedger of(@Nullable MinecraftServer server) {
        if (server == null) return null;
        ServerLevel ow = server.overworld();
        return ow.getDataStorage().computeIfAbsent(new SavedData.Factory<>(SourceLedger::new, SourceLedger::load, null),
                PromptCritical.MOD_ID + "_sources");
    }

    public static Collection<Entry> entries(MinecraftServer server) {
        SourceLedger l = of(server);
        return l == null ? List.of() : List.copyOf(l.entries.values());
    }

    @Nullable
    public static Entry get(MinecraftServer server, UUID id) {
        SourceLedger l = of(server);
        return l == null ? null : l.entries.get(id);
    }

    // ------------------------------------------------------------------ учёт

    static double trackedBq() {
        return ServerConfig.SPEC.isLoaded() ? ServerConfig.TRANSPORT_TRACKED_ACTIVITY_BQ.get() : 1e12;
    }

    static long orphanTicks() {
        return 20L * (ServerConfig.SPEC.isLoaded() ? ServerConfig.ISOTOPES_ORPHAN_AFTER_S.get() : 600);
    }

    /** Учитывается ли источник: категории 1–3 МАГАТЭ или активность выше порога (04d §4d.6.1). */
    public static boolean tracked(ActivityData a, int count, long now) {
        ActivityInventory inv = a.inventory(now);
        return inv.totalBq() * count > trackedBq() || IaeaCategory.of(IaeaCategory.ratio(inv) * count) <= 3;
    }

    /**
     * Источник увиден в точке {@code at}.
     *
     * @param carrier игрок, у которого он в инвентаре; {@code null} — контейнер, земля, установка
     * @param mayTag  можно ли повесить метку: только на стеки, которые разрешено менять (инвентарь игрока, предмет на
     *                земле); чужой обработчик предметов менять нельзя, там видим лишь уже помеченные
     * @return метка источника; {@code null} — источник не учитывается
     */
    @Nullable
    public static UUID sight(ServerLevel level, ItemStack stack, Vec3 at, @Nullable ServerPlayer carrier, boolean mayTag) {
        if (stack.isEmpty()) return null;
        ActivityData a = stack.get(ActivityData.ACTIVITY.get());
        if (a == null) return null;
        UUID id = stack.get(SOURCE_ID.get());
        long now = level.getGameTime();
        if (id == null) {
            if (!mayTag || !tracked(a, stack.getCount(), now)) return null;
            id = UUID.randomUUID();
            stack.set(SOURCE_ID.get(), id);
        }
        SourceLedger l = of(level.getServer());
        if (l == null) return id;
        Entry e = l.entries.get(id);
        if (e == null) {
            if (!tracked(a, stack.getCount(), now)) return null; // распался ниже порога: метка остаётся, учёт — нет
            e = new Entry(id, level.dimension(), at, a, stack.getCount());
            l.entries.put(id, e);
        } else if (e.unseenSince >= 0) {
            ServerLevel old = level.getServer().getLevel(e.dim);
            if (old != null) RadiationService.removeSource(old, e.at);
        }
        e.dim = level.dimension();
        e.at = at;
        e.activity = a;
        e.count = stack.getCount();
        e.lastSeen = now;
        e.missingSince = -1;
        e.unseenSince = -1;
        e.alarmed = false;
        e.carriedBy = carrier == null ? null : carrier.getUUID();
        if (carrier != null) e.lastPlayer = carrier.getUUID();
        l.setDirty();
        return id;
    }

    /** Источник на земле не исчезает через 5 минут (04d §4d.6.2). */
    static void sightOnGround(ServerLevel level, ItemEntity item) {
        if (sight(level, item.getItem(), item.position().add(0, 0.2, 0), null, true) != null) item.setUnlimitedLifetime();
    }

    /** Всё, что игрок несёт, включая предмет на курсоре открытого меню. */
    static void sightCarried(ServerLevel level, ServerPlayer p) {
        Vec3 at = p.position().add(0, 0.9, 0);
        for (ItemStack st : p.getInventory().items) sight(level, st, at, p, true);
        for (ItemStack st : p.getInventory().offhand) sight(level, st, at, p, true);
        for (ItemStack st : p.getInventory().armor) sight(level, st, at, p, true);
        sight(level, p.containerMenu.getCarried(), at, p, true);
        for (ItemStack st : dev.denfry.promptcritical.mod.compat.WornGear.accessories(p)) sight(level, st, at, p, false);
    }

    // ------------------------------------------------------------------ призраки

    /** Раз в секунду: поиски пропавших, призраки, бесхозные. */
    static void tick(ServerLevel level, long now) {
        List<Vec3> observers = new ArrayList<>();
        for (ServerPlayer p : level.players()) if (!p.isSpectator()) observers.add(p.position());
        tick(level, now, observers);
    }

    /** То же с явными наблюдателями (GameTest: в тестовом мире игроков нет). */
    public static void tick(ServerLevel level, long now, List<Vec3> observers) {
        SourceLedger l = of(level.getServer());
        if (l == null || l.entries.isEmpty()) return;
        double r2 = ContainerActivityScan.RANGE_M * ContainerActivityScan.RANGE_M;
        for (var it = l.entries.values().iterator(); it.hasNext(); ) {
            Entry e = it.next();
            if (e.dim != level.dimension() || now - e.lastSeen <= FRESH_TICKS) continue;
            if (e.carriedBy != null && level.getServer().getPlayerList().getPlayer(e.carriedBy) == null) continue;
            if (e.unseenSince >= 0) {
                RadiationService.source(level, e.at, e.source(now));
                if (!e.alarmed && now - e.unseenSince >= orphanTicks()) {
                    e.alarmed = true;
                    l.setDirty();
                    alarm(level, e);
                }
                continue;
            }
            boolean watched = level.isLoaded(BlockPos.containing(e.at)) && observers.stream().anyMatch(o -> o.distanceToSqr(e.at) <= r2);
            if (!watched) {
                e.missingSince = -1; // не видно, потому что некому смотреть: не пропажа
                continue;
            }
            if (e.missingSince < 0) {
                e.missingSince = now;
                continue;
            }
            if (now - e.missingSince < GRACE_TICKS) continue;
            if (nearOurInstallation(level, e.at)) {
                PromptCritical.LOGGER.info("Tracked source {} taken into an installation at {}", e.id, BlockPos.containing(e.at));
                it.remove();
            } else {
                e.unseenSince = now;
                PromptCritical.LOGGER.info("Tracked source {} not seen near {}: radiating from its last position", e.id, BlockPos.containing(e.at));
                RadiationService.source(level, e.at, e.source(now));
            }
            l.setDirty();
        }
    }

    private static void alarm(ServerLevel level, Entry e) {
        BlockPos p = BlockPos.containing(e.at);
        PromptCritical.LOGGER.warn("Orphan source {} at {} {}: unseen for {} s", e.id, e.dim.location(), p.toShortString(), orphanTicks() / 20);
        ServerPlayer owner = e.lastPlayer == null ? null : level.getServer().getPlayerList().getPlayer(e.lastPlayer);
        if (owner != null)
            owner.displayClientMessage(RadiationService.msg("source_orphan", shortId(e.id), p.getX(), p.getY(), p.getZ())
                    .withStyle(ChatFormatting.RED), false);
    }

    /** Наша установка рядом (свой учёт активности или блок-сущность мода) — пропажу считаем приёмом в установку. */
    private static boolean nearOurInstallation(ServerLevel level, Vec3 at) {
        double r2 = CONSUMED_RADIUS_M * CONSUMED_RADIUS_M;
        int c0x = (int) Math.floor(at.x - CONSUMED_RADIUS_M) >> 4, c1x = (int) Math.floor(at.x + CONSUMED_RADIUS_M) >> 4;
        int c0z = (int) Math.floor(at.z - CONSUMED_RADIUS_M) >> 4, c1z = (int) Math.floor(at.z + CONSUMED_RADIUS_M) >> 4;
        for (int cx = c0x; cx <= c1x; cx++)
            for (int cz = c0z; cz <= c1z; cz++) {
                LevelChunk ch = level.getChunkSource().getChunkNow(cx, cz);
                if (ch == null) continue;
                for (Map.Entry<BlockPos, BlockEntity> b : ch.getBlockEntities().entrySet()) {
                    if (Vec3.atCenterOf(b.getKey()).distanceToSqr(at) > r2) continue;
                    BlockEntity be = b.getValue();
                    if (be instanceof SelfEmittingSource) return true;
                    ResourceLocation type = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType());
                    if (type != null && type.getNamespace().equals(PromptCritical.MOD_ID)) return true;
                }
            }
        return false;
    }

    /** Предмет на земле сгорел в лаве, упал в пустоту, взорван: журнал и тревога державшему (04d §4d.6.2). */
    private static void onLeave(EntityLeaveLevelEvent ev) {
        if (!(ev.getEntity() instanceof ItemEntity item) || !(ev.getLevel() instanceof ServerLevel level)) return;
        if (!destroyed(item, level)) return;
        ItemStack stack = item.getItem();
        UUID id = stack.isEmpty() ? null : stack.get(SOURCE_ID.get());
        if (id == null) return;
        SourceLedger l = of(level.getServer());
        Entry e = l == null ? null : l.entries.remove(id);
        if (e == null) return;
        l.setDirty();
        if (e.unseenSince >= 0) RadiationService.removeSource(level, e.at);
        BlockPos p = item.blockPosition();
        PromptCritical.LOGGER.warn("Tracked source {} destroyed at {} {}", id, level.dimension().location(), p.toShortString());
        ServerPlayer owner = e.lastPlayer == null ? null : level.getServer().getPlayerList().getPlayer(e.lastPlayer);
        if (owner != null)
            owner.displayClientMessage(RadiationService.msg("source_destroyed", shortId(id), p.getX(), p.getY(), p.getZ())
                    .withStyle(ChatFormatting.RED), false);
    }

    /**
     * Уничтожен ли снятый с уровня предмет: убит командой, сгорел в лаве или огне, упал в пустоту. Прочее снятие
     * (подобран, слит со стеком, забран воронкой или воронкой Create, не опустошившей стек) уничтожением не считаем:
     * если предмет и правда пропал, его закроет правило призрака.
     */
    private static boolean destroyed(ItemEntity item, ServerLevel level) {
        Entity.RemovalReason why = item.getRemovalReason();
        if (why == Entity.RemovalReason.KILLED) return true;
        if (why != Entity.RemovalReason.DISCARDED) return false;
        return item.isInLava() || item.isOnFire() || item.getY() < level.getMinBuildHeight() - 32;
    }

    public static String shortId(UUID id) {
        return id.toString().substring(0, 8);
    }

    // ------------------------------------------------------------------ сохранение

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Entry e : entries.values()) {
            CompoundTag c = new CompoundTag();
            c.putUUID("id", e.id);
            c.putString("dim", e.dim.location().toString());
            c.putDouble("x", e.at.x);
            c.putDouble("y", e.at.y);
            c.putDouble("z", e.at.z);
            c.putLong("seen", e.lastSeen);
            c.putLong("missing", e.missingSince);
            c.putLong("unseen", e.unseenSince);
            c.putInt("count", e.count);
            c.putBoolean("alarmed", e.alarmed);
            if (e.carriedBy != null) c.putUUID("carried", e.carriedBy);
            if (e.lastPlayer != null) c.putUUID("player", e.lastPlayer);
            ActivityData.CODEC.encodeStart(NbtOps.INSTANCE, e.activity).result().ifPresent(t -> c.put("activity", t));
            list.add(c);
        }
        tag.put("sources", list);
        return tag;
    }

    private static SourceLedger load(CompoundTag tag, HolderLookup.Provider registries) {
        SourceLedger l = new SourceLedger();
        for (Tag t : tag.getList("sources", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            ResourceLocation dim = ResourceLocation.tryParse(c.getString("dim"));
            ActivityData a = ActivityData.CODEC.parse(NbtOps.INSTANCE, c.get("activity")).result().orElse(null);
            if (!c.hasUUID("id") || dim == null || a == null) continue; // испорченная запись: пропускаем, а не роняем мир
            Entry e = new Entry(c.getUUID("id"), ResourceKey.create(Registries.DIMENSION, dim),
                    new Vec3(c.getDouble("x"), c.getDouble("y"), c.getDouble("z")), a, Math.max(1, c.getInt("count")));
            e.lastSeen = c.getLong("seen");
            e.missingSince = c.getLong("missing");
            e.unseenSince = c.getLong("unseen");
            e.alarmed = c.getBoolean("alarmed");
            e.carriedBy = c.hasUUID("carried") ? c.getUUID("carried") : null;
            e.lastPlayer = c.hasUUID("player") ? c.getUUID("player") : null;
            l.entries.put(e.id, e);
        }
        return l;
    }
}
