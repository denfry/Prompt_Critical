package dev.denfry.promptcritical.sim.core;

/**
 * xxHash64 сохранённого состояния (05 §5.2.4): {@code /pc debug hash} и тесты детерминизма. Хешируются ключи (в
 * лексикографическом порядке — NBT порядок не хранит), теги типов и значения; числа с плавающей точкой — побитово.
 */
public final class StateHash {
    private static final long P1 = 0x9E3779B185EBCA87L;
    private static final long P2 = 0xC2B2AE3D27D4EB4FL;
    private static final long P3 = 0x165667B19E3779F9L;
    private static final long P4 = 0x85EBCA77C2B2AE63L;
    private static final long P5 = 0x27D4EB2F165667C5L;

    private long acc;
    private long count;

    private StateHash(long seed) {
        acc = seed + P5;
    }

    public static long of(MutableStateTree tree) {
        StateHash h = new StateHash(0);
        h.tree(tree);
        return h.finish();
    }

    private void tree(MutableStateTree t) {
        word(t.size());
        for (String k : new java.util.TreeSet<>(t.keys())) {
            string(k);
            Object v = t.get(k);
            switch (v) {
                case Integer i -> { word(1); word(i); }
                case Long l -> { word(2); word(l); }
                case Double d -> { word(3); word(Double.doubleToRawLongBits(d)); }
                case String s -> { word(4); string(s); }
                case double[] a -> { word(5); word(a.length); for (double d : a) word(Double.doubleToRawLongBits(d)); }
                case long[] a -> { word(6); word(a.length); for (long l : a) word(l); }
                case int[] a -> { word(7); word(a.length); for (int i : a) word(i); }
                case byte[] a -> { word(8); word(a.length); for (byte b : a) word(b); }
                case MutableStateTree c -> { word(9); tree(c); }
                default -> throw new IllegalStateException("unsupported state value " + v.getClass());
            }
        }
    }

    private void string(String s) {
        word(s.length());
        for (int i = 0; i < s.length(); i++) word(s.charAt(i));
    }

    /** Поток 8-байтовых слов в «хвостовой» схеме xxHash64 (без 32-байтовых полос — состояния небольшие). */
    private void word(long k) {
        long k1 = Long.rotateLeft(k * P2, 31) * P1;
        acc ^= k1;
        acc = Long.rotateLeft(acc, 27) * P1 + P4;
        count++;
    }

    private long finish() {
        long h = acc + count * 8;
        h ^= h >>> 33;
        h *= P2;
        h ^= h >>> 29;
        h *= P3;
        h ^= h >>> 32;
        return h;
    }
}
