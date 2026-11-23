package dev.denfry.promptcritical.sim.ops;

import dev.denfry.promptcritical.sim.core.SimId;
import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Лицензии команд профиля licensing (спецификация профиля, §1): эпохи, на которые у команды есть лицензия, выполненные
 * ею задания маршрута, принятые типы установок и владельцы установок. Владелец — команда, первой проводившая проверку на
 * установке. Порядок обхода и сохранения детерминирован: команды — по UUID, задания — по строке, типы — по порядку.
 */
public final class LicenceRegistry {
    private static final class Team {
        final EnumSet<PlantEra> licences = EnumSet.noneOf(PlantEra.class);
        final Set<String> tasks = new TreeSet<>();
        final EnumSet<UnitKind> accepted = EnumSet.noneOf(UnitKind.class);
    }

    private final Map<UUID, Team> teams = new TreeMap<>();
    private final Map<SimId, UUID> owners = new TreeMap<>();

    /** I0 открыта всем. */
    public boolean licensed(UUID team, PlantEra era) {
        if (era == PlantEra.I0) return true;
        Team t = teams.get(team);
        return t != null && t.licences.contains(era);
    }

    /** @param tick тик выдачи — для журнала у вызывающего; в реестре не хранится. */
    public void grant(UUID team, PlantEra era, long tick) {
        team(team).licences.add(era);
    }

    public Set<PlantEra> licences(UUID team) {
        Team t = teams.get(team);
        return t == null ? Set.of() : Collections.unmodifiableSet(EnumSet.copyOf(t.licences));
    }

    public void taskDone(UUID team, String taskId) {
        team(team).tasks.add(taskId);
    }

    public Set<String> tasksDone(UUID team) {
        Team t = teams.get(team);
        return t == null ? Set.of() : Collections.unmodifiableSet(new TreeSet<>(t.tasks));
    }

    public void accepted(UUID team, UnitKind kind) {
        team(team).accepted.add(kind);
    }

    public Set<UnitKind> acceptedKinds(UUID team) {
        Team t = teams.get(team);
        return t == null ? Set.of() : Collections.unmodifiableSet(EnumSet.copyOf(t.accepted));
    }

    /** Первый вызов для установки задаёт владельца, следующие ничего не меняют. */
    public void owner(SimId unit, UUID team) {
        owners.putIfAbsent(unit, team);
    }

    public Optional<UUID> owner(SimId unit) {
        return Optional.ofNullable(owners.get(unit));
    }

    public void forget(SimId unit) {
        owners.remove(unit);
    }

    public void save(StateWriter out) {
        out.putInt("teams", teams.size());
        int i = 0;
        for (Map.Entry<UUID, Team> e : teams.entrySet()) {
            Team t = e.getValue();
            out.beginObject("t" + i++);
            out.putString("id", e.getKey().toString());
            out.putInts("licences", t.licences.stream().mapToInt(Enum::ordinal).toArray(), t.licences.size());
            out.putInts("accepted", t.accepted.stream().mapToInt(Enum::ordinal).toArray(), t.accepted.size());
            out.putInt("tasks", t.tasks.size());
            int k = 0;
            for (String task : t.tasks) out.putString("task" + k++, task);
            out.endObject();
        }
        out.putInt("owners", owners.size());
        int o = 0;
        for (Map.Entry<SimId, UUID> e : owners.entrySet()) {
            out.beginObject("o" + o++);
            out.putLong("unit", e.getKey().raw());
            out.putString("team", e.getValue().toString());
            out.endObject();
        }
    }

    /** Сохранение до профиля licensing: реестр пуст. */
    public void clear() {
        teams.clear();
        owners.clear();
    }

    /** Пустое состояние читается как пустой реестр. */
    public void load(StateReader in) {
        clear();
        PlantEra[] eras = PlantEra.values();
        UnitKind[] kinds = UnitKind.values();
        int n = in.optInt("teams", 0);
        for (int i = 0; i < n; i++) {
            in.beginObject("t" + i);
            Team t = team(UUID.fromString(in.getString("id")));
            for (int e : in.getInts("licences")) t.licences.add(eras[e]);
            for (int k : in.getInts("accepted")) t.accepted.add(kinds[k]);
            int tasks = in.getInt("tasks");
            for (int k = 0; k < tasks; k++) t.tasks.add(in.getString("task" + k));
            in.endObject();
        }
        int m = in.optInt("owners", 0);
        for (int i = 0; i < m; i++) {
            in.beginObject("o" + i);
            owners.put(new SimId(in.getLong("unit")), UUID.fromString(in.getString("team")));
            in.endObject();
        }
    }

    private Team team(UUID id) {
        return teams.computeIfAbsent(id, k -> new Team());
    }
}
