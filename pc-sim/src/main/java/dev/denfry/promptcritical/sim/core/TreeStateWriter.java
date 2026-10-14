package dev.denfry.promptcritical.sim.core;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

/** {@link StateWriter} в {@link MutableStateTree}; массивы копируются до длины {@code len}. */
public final class TreeStateWriter implements StateWriter {
    private final MutableStateTree root;
    private final Deque<MutableStateTree> stack = new ArrayDeque<>();

    public TreeStateWriter() {
        this(new MutableStateTree());
    }

    public TreeStateWriter(MutableStateTree root) {
        this.root = root;
        stack.push(root);
    }

    public MutableStateTree root() {
        if (stack.size() != 1) throw new IllegalStateException("unbalanced beginObject/endObject");
        return root;
    }

    private MutableStateTree top() {
        return stack.peek();
    }

    private void put(String k, Object v) {
        if (top().has(k)) throw new IllegalStateException("duplicate key '" + k + "'");
        top().put(k, v);
    }

    @Override
    public void beginObject(String key) {
        MutableStateTree child = new MutableStateTree();
        put(key, child);
        stack.push(child);
    }

    @Override
    public void endObject() {
        if (stack.size() == 1) throw new IllegalStateException("endObject without beginObject");
        stack.pop();
    }

    @Override
    public void putInt(String k, int v) {
        put(k, v);
    }

    @Override
    public void putLong(String k, long v) {
        put(k, v);
    }

    @Override
    public void putDouble(String k, double v) {
        put(k, v);
    }

    @Override
    public void putString(String k, String v) {
        put(k, v);
    }

    @Override
    public void putDoubles(String k, double[] a, int len) {
        put(k, Arrays.copyOf(a, len));
    }

    @Override
    public void putLongs(String k, long[] a, int len) {
        put(k, Arrays.copyOf(a, len));
    }

    @Override
    public void putInts(String k, int[] a, int len) {
        put(k, Arrays.copyOf(a, len));
    }

    @Override
    public void putBytes(String k, byte[] a, int len) {
        put(k, Arrays.copyOf(a, len));
    }
}
