package dev.denfry.promptcritical.sim.core;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Set;

/** {@link StateReader} над {@link MutableStateTree}. Массивы отдаются без копирования. */
public final class TreeStateReader implements StateReader {
    private final Deque<MutableStateTree> stack = new ArrayDeque<>();

    public TreeStateReader(MutableStateTree root) {
        stack.push(root);
    }

    private MutableStateTree top() {
        return stack.peek();
    }

    private <T> T get(String k, Class<T> type) {
        Object v = top().get(k);
        if (v == null) throw new IllegalStateException("missing key '" + k + "'");
        if (!type.isInstance(v))
            throw new IllegalStateException("key '" + k + "' is " + v.getClass().getSimpleName() + ", expected " + type.getSimpleName());
        return type.cast(v);
    }

    @Override
    public boolean has(String key) {
        return top().has(key);
    }

    @Override
    public Set<String> keys() {
        return top().keys();
    }

    @Override
    public void beginObject(String key) {
        stack.push(get(key, MutableStateTree.class));
    }

    @Override
    public void endObject() {
        if (stack.size() == 1) throw new IllegalStateException("endObject without beginObject");
        stack.pop();
    }

    @Override
    public int getInt(String k) {
        return get(k, Integer.class);
    }

    @Override
    public long getLong(String k) {
        Object v = top().get(k);
        if (v instanceof Integer i) return i;                  // NBT может сузить тип при ручной правке
        return get(k, Long.class);
    }

    @Override
    public double getDouble(String k) {
        return get(k, Double.class);
    }

    @Override
    public String getString(String k) {
        return get(k, String.class);
    }

    @Override
    public double[] getDoubles(String k) {
        return get(k, double[].class);
    }

    @Override
    public long[] getLongs(String k) {
        return get(k, long[].class);
    }

    @Override
    public int[] getInts(String k) {
        return get(k, int[].class);
    }

    @Override
    public byte[] getBytes(String k) {
        return get(k, byte[].class);
    }
}
