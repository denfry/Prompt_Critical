package dev.denfry.promptcritical.sim.journal;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Станция (04d §4d.9.1): владелец, явно назначенные роли, измерение и чанки ({@code ChunkPos.asLong}). Неизменяема;
 * изменения возвращают копию. {@code ownerTeam} — UUID игрока-владельца или {@link #teamId(String) id команды}:
 * владелец и члены его команды получают {@link Role#OWNER} без явной записи в {@code members}.
 *
 * <p>Отличие от §4d.9.1: роли в {@code members} — неизменяемые {@code Set<Role>}, а не {@code EnumSet}, чтобы запись
 * нельзя было поменять снаружи.
 */
public record StationRecord(UUID stationId, String name, UUID ownerTeam, Map<UUID, Set<Role>> members,
                            int dimension, Set<Long> chunks, long createdTick) {

    public StationRecord {
        Objects.requireNonNull(stationId, "stationId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(ownerTeam, "ownerTeam");
        Map<UUID, Set<Role>> m = new LinkedHashMap<>();
        members.forEach((k, v) -> {
            if (!v.isEmpty()) m.put(k, Collections.unmodifiableSet(EnumSet.copyOf(v)));
        });
        members = Collections.unmodifiableMap(m);
        chunks = Collections.unmodifiableSet(new LinkedHashSet<>(chunks));
    }

    /** Новая станция с одним чанком; владелец — игрок или команда. */
    public static StationRecord create(UUID stationId, String name, UUID ownerTeam, int dimension, long chunk,
                                       long tick) {
        return new StationRecord(stationId, name, ownerTeam, Map.of(), dimension, Set.of(chunk), tick);
    }

    /** Детерминированный id ванильной команды (или FTB Teams) по её имени — значение {@code ownerTeam}. */
    public static UUID teamId(String teamName) {
        return UUID.nameUUIDFromBytes(("promptcritical:team:" + teamName).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Роли игрока: явные из {@code members} плюс {@link Role#OWNER}, если он сам владелец или его команда
     * ({@code playerTeam}, может быть {@code null}) владеет станцией.
     */
    public Set<Role> rolesOf(UUID player, UUID playerTeam) {
        EnumSet<Role> out = EnumSet.noneOf(Role.class);
        Set<Role> explicit = members.get(player);
        if (explicit != null) out.addAll(explicit);
        if (ownerTeam.equals(player) || ownerTeam.equals(playerTeam)) out.add(Role.OWNER);
        return out;
    }

    public boolean has(UUID player, UUID playerTeam, Permission p) {
        return Role.permissionsOf(rolesOf(player, playerTeam)).contains(p);
    }

    /** Член станции — владелец, член команды-владельца или игрок хотя бы с одной явной ролью. */
    public boolean isMember(UUID player, UUID playerTeam) {
        return !rolesOf(player, playerTeam).isEmpty();
    }

    public StationRecord withRole(UUID player, Role role) {
        Map<UUID, Set<Role>> m = new LinkedHashMap<>(members);
        EnumSet<Role> roles = EnumSet.of(role);
        roles.addAll(m.getOrDefault(player, Set.of()));
        m.put(player, roles);
        return new StationRecord(stationId, name, ownerTeam, m, dimension, chunks, createdTick);
    }

    /** Снимает явную роль; {@code null} — все явные роли игрока. Владение командой так не снимается. */
    public StationRecord withoutRole(UUID player, Role role) {
        Set<Role> had = members.get(player);
        if (had == null) return this;
        Map<UUID, Set<Role>> m = new LinkedHashMap<>(members);
        EnumSet<Role> roles = EnumSet.copyOf(had);
        if (role == null) roles.clear();
        else roles.remove(role);
        m.put(player, roles);
        return new StationRecord(stationId, name, ownerTeam, m, dimension, chunks, createdTick);
    }

    public StationRecord withChunk(long chunk) {
        if (chunks.contains(chunk)) return this;
        Set<Long> c = new LinkedHashSet<>(chunks);
        c.add(chunk);
        return new StationRecord(stationId, name, ownerTeam, members, dimension, c, createdTick);
    }
}
