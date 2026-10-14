package dev.denfry.promptcritical.sim.mining;

import java.util.Arrays;

/** Открытая адресация long → int без упаковки (ключи блоков); удаление со сдвигом назад. */
final class LongIntMap {
    static final int MISSING = -1;

    private long[] keys;
    private int[] values;
    private boolean[] used;
    private int size, mask;

    LongIntMap(int expected) {
        int cap = Integer.highestOneBit(Math.max(4, expected * 2 - 1)) << 1;
        keys = new long[cap];
        values = new int[cap];
        used = new boolean[cap];
        mask = cap - 1;
    }

    int size() {
        return size;
    }

    int get(long key) {
        for (int i = slot(key); used[i]; i = (i + 1) & mask) if (keys[i] == key) return values[i];
        return MISSING;
    }

    void put(long key, int value) {
        if ((size + 1) * 2 > keys.length) grow();
        int i = slot(key);
        for (; used[i]; i = (i + 1) & mask)
            if (keys[i] == key) {
                values[i] = value;
                return;
            }
        used[i] = true;
        keys[i] = key;
        values[i] = value;
        size++;
    }

    void remove(long key) {
        int i = slot(key);
        while (used[i] && keys[i] != key) i = (i + 1) & mask;
        if (!used[i]) return;
        used[i] = false;
        size--;
        for (int j = (i + 1) & mask; used[j]; j = (j + 1) & mask) {
            int home = slot(keys[j]);
            // элемент j можно перенести в дыру i, если его «дом» не лежит в (i, j] по кругу
            if (((j - home) & mask) >= ((j - i) & mask)) {
                keys[i] = keys[j];
                values[i] = values[j];
                used[i] = true;
                used[j] = false;
                i = j;
            }
        }
    }

    /** Число слотов — для обхода {@link #usedAt}/{@link #keyAt}/{@link #valueAt}. */
    int capacity() {
        return keys.length;
    }

    boolean usedAt(int slot) {
        return used[slot];
    }

    long keyAt(int slot) {
        return keys[slot];
    }

    int valueAt(int slot) {
        return values[slot];
    }

    void clear() {
        Arrays.fill(used, false);
        size = 0;
    }

    private int slot(long key) {
        long h = key * 0x9E3779B97F4A7C15L;
        return (int) (h ^ (h >>> 32)) & mask;
    }

    private void grow() {
        long[] k = keys;
        int[] v = values;
        boolean[] u = used;
        keys = new long[k.length * 2];
        values = new int[k.length * 2];
        used = new boolean[k.length * 2];
        mask = keys.length - 1;
        size = 0;
        for (int i = 0; i < k.length; i++) if (u[i]) put(k[i], v[i]);
    }
}
