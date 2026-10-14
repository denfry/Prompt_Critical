package dev.denfry.promptcritical.sim.core;

import java.util.HashMap;
import java.util.Map;

/** Цепочка миграций сохранения (05 §5.2.8): ровно одна миграция на каждую версию схемы. */
public final class MigrationRegistry {
    private final Map<Integer, Migration> byFrom = new HashMap<>();

    public void register(Migration m) {
        if (m.from() < 0) throw new IllegalArgumentException("migration from < 0");
        if (byFrom.putIfAbsent(m.from(), m) != null)
            throw new IllegalStateException("migration from schema " + m.from() + " is already registered");
    }

    /**
     * Поднять дерево с версии {@code fromVersion} до {@code toVersion}.
     *
     * @throws IllegalStateException если в цепочке нет шага
     */
    public MutableStateTree upgrade(MutableStateTree t, int fromVersion, int toVersion) {
        if (fromVersion > toVersion)
            throw new IllegalStateException("save schema " + fromVersion + " is newer than supported " + toVersion);
        for (int v = fromVersion; v < toVersion; v++) {
            Migration m = byFrom.get(v);
            if (m == null) throw new IllegalStateException("no migration from schema " + v);
            m.apply(t);
        }
        return t;
    }
}
