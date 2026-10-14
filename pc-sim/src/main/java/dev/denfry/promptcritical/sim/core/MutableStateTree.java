package dev.denfry.promptcritical.sim.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Дерево ключ-значение, независимое от NBT (05 §5.2.8): промежуточная форма сохранения, над которой работают
 * миграции. Значения — {@link Integer}, {@link Long}, {@link Double}, {@link String}, {@code double[]},
 * {@code long[]}, {@code int[]}, {@code byte[]} или вложенное дерево; порядок ключей — порядок записи.
 */
public final class MutableStateTree {
    private final Map<String, Object> entries = new LinkedHashMap<>();

    public boolean has(String key) {
        return entries.containsKey(key);
    }

    public Object get(String key) {
        return entries.get(key);
    }

    public Set<String> keys() {
        return Collections.unmodifiableSet(entries.keySet());
    }

    public MutableStateTree put(String key, Object value) {
        Objects.requireNonNull(key, "key");
        entries.put(key, checkValue(value));
        return this;
    }

    public Object remove(String key) {
        return entries.remove(key);
    }

    /** Переименовать ключ; он переносится в конец порядка записи. */
    public void rename(String from, String to) {
        if (!entries.containsKey(from)) return;
        entries.put(to, entries.remove(from));
    }

    public MutableStateTree child(String key) {
        Object v = entries.get(key);
        if (v instanceof MutableStateTree t) return t;
        throw new IllegalStateException("no object at '" + key + "'");
    }

    /** Вложенный объект; создаётся, если его нет. */
    public MutableStateTree childOrCreate(String key) {
        Object v = entries.get(key);
        if (v instanceof MutableStateTree t) return t;
        MutableStateTree t = new MutableStateTree();
        entries.put(key, t);
        return t;
    }

    public int size() {
        return entries.size();
    }

    private static Object checkValue(Object v) {
        if (v instanceof Integer || v instanceof Long || v instanceof Double || v instanceof String
                || v instanceof double[] || v instanceof long[] || v instanceof int[] || v instanceof byte[]
                || v instanceof MutableStateTree) {
            return v;
        }
        throw new IllegalArgumentException("unsupported state value: " + (v == null ? "null" : v.getClass()));
    }
}
