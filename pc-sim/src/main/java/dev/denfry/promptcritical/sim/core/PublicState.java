package dev.denfry.promptcritical.sim.core;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Публичная часть состояния мира (05 §5.2.7): измеренные значения каналов и их качество — то, что видят CC, сеть
 * и GUI через {@link SimSnapshot}. Слоты выдаются при изменении топологии; в тике подсистемы только пишут значения.
 */
public final class PublicState {
    private static final Quality[] QUALITIES = Quality.values();

    private final Map<String, Integer> slots = new LinkedHashMap<>();
    private TagIndex index = TagIndex.EMPTY;
    private double[] value = new double[64];
    private byte[] quality = new byte[64];
    private int size;

    /** Выдать слот тегу (или вернуть уже выданный). Только вне тика — может расширить массивы. */
    public int register(String tag) {
        Integer existing = slots.get(tag);
        if (existing != null) return existing;
        if (size == value.length) {
            value = Arrays.copyOf(value, size * 2);
            quality = Arrays.copyOf(quality, size * 2);
        }
        int slot = size++;
        quality[slot] = (byte) Quality.UNCERTAIN.ordinal();
        slots.put(tag, slot);
        index = new TagIndex(slots);
        return slot;
    }

    public void set(int slot, double v, Quality q) {
        value[slot] = v;
        quality[slot] = (byte) q.ordinal();
    }

    public double value(int slot) {
        return value[slot];
    }

    public Quality quality(int slot) {
        return QUALITIES[quality[slot]];
    }

    public int size() {
        return size;
    }

    public TagIndex tags() {
        return index;
    }

    double[] values() {
        return value;
    }

    byte[] qualities() {
        return quality;
    }
}
