package dev.denfry.promptcritical.sim.core;

/** Нейтральный приёмник состояния (05 §5.2.8): домен не знает NBT. Ключи внутри объекта уникальны. */
public interface StateWriter {
    void beginObject(String key);

    void endObject();

    void putInt(String k, int v);

    void putLong(String k, long v);

    void putDouble(String k, double v);

    void putString(String k, String v);

    void putDoubles(String k, double[] a, int len);

    void putLongs(String k, long[] a, int len);

    void putInts(String k, int[] a, int len);

    void putBytes(String k, byte[] a, int len);

    default void putBoolean(String k, boolean v) {
        putInt(k, v ? 1 : 0);
    }
}
