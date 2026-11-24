package dev.denfry.promptcritical.mod.station;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.config.ServerConfig;
import dev.denfry.promptcritical.sim.core.Severity;
import dev.denfry.promptcritical.sim.journal.AccessDenialLimiter;
import dev.denfry.promptcritical.sim.journal.Actor;
import dev.denfry.promptcritical.sim.journal.BlockSeriesAggregator;
import dev.denfry.promptcritical.sim.journal.JournalAction;
import dev.denfry.promptcritical.sim.journal.JournalEntry;
import dev.denfry.promptcritical.sim.journal.Role;
import dev.denfry.promptcritical.sim.journal.StationJournal;
import dev.denfry.promptcritical.sim.journal.StationRecord;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

/**
 * Станции мира и их журнал (04d §4d.8–4d.9) в SavedData верхнего мира. Чанк принадлежит не больше чем одной станции.
 * Измерение в {@link StationRecord#dimension()} — номер в таблице измерений этого сохранения, а не id реестра.
 */
public final class StationRegistry extends SavedData {
    private static final String KEY = PromptCritical.MOD_ID + "_stations";

    private final Map<UUID, StationRecord> stations = new LinkedHashMap<>();
    /** Номер измерения → (чанк → станция). */
    private final Map<Integer, Map<Long, UUID>> chunkIndex = new HashMap<>();
    private final List<String> dimensions = new ArrayList<>();
    private final StationJournal journal;
    final AccessDenialLimiter denials = new AccessDenialLimiter();
    final BlockSeriesAggregator blockSeries = new BlockSeriesAggregator();

    private StationRegistry() {
        journal = new StationJournal(maxEntries());
    }

    private static int maxEntries() {
        return ServerConfig.SPEC.isLoaded() ? ServerConfig.JOURNAL_MAX_ENTRIES_PER_STATION.get()
                : StationJournal.DEFAULT_MAX_ENTRIES_PER_STATION;
    }

    @Nullable
    public static StationRegistry of(@Nullable MinecraftServer server) {
        if (server == null) return null;
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(StationRegistry::new, StationRegistry::load, null), KEY);
    }

    // ------------------------------------------------------------------ станции

    public int dimensionIndex(ResourceKey<Level> dim) {
        String id = dim.location().toString();
        int i = dimensions.indexOf(id);
        if (i >= 0) return i;
        dimensions.add(id);
        setDirty();
        return dimensions.size() - 1;
    }

    public String dimensionName(int index) {
        return index >= 0 && index < dimensions.size() ? dimensions.get(index) : "?";
    }

    @Nullable
    public StationRecord at(ResourceKey<Level> dim, ChunkPos chunk) {
        int d = dimensions.indexOf(dim.location().toString());
        if (d < 0) return null;
        UUID id = chunkIndex.getOrDefault(d, Map.of()).get(chunk.toLong());
        return id == null ? null : stations.get(id);
    }

    @Nullable
    public StationRecord get(UUID id) {
        return stations.get(id);
    }

    public Collection<StationRecord> all() {
        return List.copyOf(stations.values());
    }

    /** Станция по имени (без учёта регистра) или по началу id; {@code null}, если нет или неоднозначно. */
    @Nullable
    public StationRecord find(String nameOrId) {
        String q = nameOrId.toLowerCase(Locale.ROOT);
        StationRecord hit = null;
        for (StationRecord s : stations.values()) {
            if (s.name().equalsIgnoreCase(nameOrId)) return s;
            if (q.length() >= 4 && s.stationId().toString().startsWith(q)) {
                if (hit != null) return null;
                hit = s;
            }
        }
        return hit;
    }

    /** Записывает станцию (новую или изменённую) и переиндексирует её чанки. */
    public void put(StationRecord s) {
        StationRecord old = stations.put(s.stationId(), s);
        if (old != null) {
            Map<Long, UUID> idx = chunkIndex.get(old.dimension());
            if (idx != null) for (long c : old.chunks()) idx.remove(c, old.stationId());
        }
        Map<Long, UUID> idx = chunkIndex.computeIfAbsent(s.dimension(), k -> new HashMap<>());
        for (long c : s.chunks()) idx.put(c, s.stationId());
        setDirty();
    }

    public String uniqueName(String base) {
        Set<String> taken = new LinkedHashSet<>();
        for (StationRecord s : stations.values()) taken.add(s.name().toLowerCase(Locale.ROOT));
        for (int n = 1; ; n++) {
            String name = base + "-" + n;
            if (!taken.contains(name.toLowerCase(Locale.ROOT))) return name;
        }
    }

    // ------------------------------------------------------------------ журнал

    public JournalEntry append(JournalEntry e) {
        setDirty();
        return journal.append(e);
    }

    public StationJournal journal() {
        return journal;
    }

    // ------------------------------------------------------------------ сохранение

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag dims = new ListTag();
        for (String d : dimensions) dims.add(net.minecraft.nbt.StringTag.valueOf(d));
        tag.put("dimensions", dims);
        ListTag list = new ListTag();
        for (StationRecord s : stations.values()) {
            CompoundTag c = new CompoundTag();
            c.putUUID("id", s.stationId());
            c.putString("name", s.name());
            c.putUUID("owner", s.ownerTeam());
            c.putInt("dim", s.dimension());
            c.put("chunks", new LongArrayTag(s.chunks().stream().mapToLong(Long::longValue).toArray()));
            c.putLong("created", s.createdTick());
            ListTag members = new ListTag();
            s.members().forEach((uuid, roles) -> {
                CompoundTag m = new CompoundTag();
                m.putUUID("uuid", uuid);
                int bits = 0;
                for (Role r : roles) bits |= 1 << r.ordinal();
                m.putInt("roles", bits);
                members.add(m);
            });
            c.put("members", members);
            ListTag entries = new ListTag();
            for (JournalEntry e : journal.entries(s.stationId())) entries.add(JournalNbt.write(e));
            c.put("journal", entries);
            list.add(c);
        }
        tag.put("stations", list);
        return tag;
    }

    private static StationRegistry load(CompoundTag tag, HolderLookup.Provider registries) {
        StationRegistry r = new StationRegistry();
        for (Tag t : tag.getList("dimensions", Tag.TAG_STRING)) r.dimensions.add(t.getAsString());
        for (Tag t : tag.getList("stations", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            if (!c.hasUUID("id") || !c.hasUUID("owner")) continue; // испорченная запись: пропускаем, а не роняем мир
            Map<UUID, Set<Role>> members = new LinkedHashMap<>();
            for (Tag mt : c.getList("members", Tag.TAG_COMPOUND)) {
                CompoundTag m = (CompoundTag) mt;
                if (!m.hasUUID("uuid")) continue;
                EnumSet<Role> roles = EnumSet.noneOf(Role.class);
                int bits = m.getInt("roles");
                for (Role role : Role.values()) if ((bits & 1 << role.ordinal()) != 0) roles.add(role);
                members.put(m.getUUID("uuid"), roles);
            }
            Set<Long> chunks = new LinkedHashSet<>();
            for (long ch : c.getLongArray("chunks")) chunks.add(ch);
            StationRecord s = new StationRecord(c.getUUID("id"), c.getString("name"), c.getUUID("owner"), members,
                    c.getInt("dim"), chunks, c.getLong("created"));
            r.put(s);
            for (Tag et : c.getList("journal", Tag.TAG_COMPOUND)) {
                JournalEntry e = JournalNbt.read((CompoundTag) et, s.stationId());
                if (e != null) r.journal.restore(e);
            }
        }
        r.setDirty(false);
        return r;
    }

    /** Запись в журнал от имени автоматики, без игрока. */
    public static JournalEntry auto(UUID station, long tick, String system, JournalAction a, String target,
                                    String detail, Severity s) {
        return new JournalEntry(0, tick, tick / 20.0, station, new Actor.Automatic(system), a, target, detail, s);
    }
}
