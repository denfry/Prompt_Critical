package dev.denfry.promptcritical.sim.core;

import java.util.Set;

/**
 * Чтение состояния, симметрично {@link StateWriter} (05 §5.2.8). Обязательные геттеры бросают
 * {@link IllegalStateException}, если ключа нет или тип не совпал; {@code opt*} возвращают значение по умолчанию.
 */
public interface StateReader {
    boolean has(String key);

    /** Ключи текущего объекта в порядке записи. */
    Set<String> keys();

    void beginObject(String key);

    void endObject();

    int getInt(String k);

    long getLong(String k);

    double getDouble(String k);

    String getString(String k);

    double[] getDoubles(String k);

    long[] getLongs(String k);

    int[] getInts(String k);

    byte[] getBytes(String k);

    default boolean getBoolean(String k) {
        return getInt(k) != 0;
    }

    default int optInt(String k, int def) {
        return has(k) ? getInt(k) : def;
    }

    default long optLong(String k, long def) {
        return has(k) ? getLong(k) : def;
    }

    default double optDouble(String k, double def) {
        return has(k) ? getDouble(k) : def;
    }

    default String optString(String k, String def) {
        return has(k) ? getString(k) : def;
    }
}
