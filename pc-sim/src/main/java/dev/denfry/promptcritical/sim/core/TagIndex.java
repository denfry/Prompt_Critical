package dev.denfry.promptcritical.sim.core;

import java.util.Map;

/** Отображение тег → слот публичного состояния (05 §5.2.7); неизменяемо между изменениями топологии. */
public final class TagIndex {
    public static final TagIndex EMPTY = new TagIndex(Map.of());

    private final Map<String, Integer> slots;

    TagIndex(Map<String, Integer> slots) {
        this.slots = Map.copyOf(slots);
    }

    /** Слот тега или −1. */
    public int slot(String tag) {
        Integer s = slots.get(tag);
        return s == null ? -1 : s;
    }

    public int size() {
        return slots.size();
    }

    public Iterable<String> tags() {
        return slots.keySet();
    }
}
