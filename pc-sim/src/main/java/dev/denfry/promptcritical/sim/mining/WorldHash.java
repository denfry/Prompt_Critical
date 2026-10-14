package dev.denfry.promptcritical.sim.mining;

/**
 * Детерминированные хеши позиции для генерации мира (08 §8.1): одно и то же (сид, координаты, соль) даёт один и тот
 * же результат на любом потоке и любой JVM — только целочисленная арифметика (SplitMix64).
 */
public final class WorldHash {
    private WorldHash() {}

    /** Финализатор SplitMix64. */
    public static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** Хеш (сид, x, y, z, соль). */
    public static long hash(long seed, int x, int y, int z, int salt) {
        long h = mix(seed ^ 0x9E3779B97F4A7C15L * (salt + 1L));
        h = mix(h ^ x * 0xC2B2AE3D27D4EB4FL);
        h = mix(h ^ y * 0x165667B19E3779F9L);
        return mix(h ^ z * 0x27D4EB2F165667C5L);
    }

    /** Равномерное [0, 1) из хеша (53 старших бита). */
    public static double unit(long h) {
        return (h >>> 11) * 0x1.0p-53;
    }

    /** Равномерное [0, 1) по (сид, x, y, z, соль). */
    public static double unit(long seed, int x, int y, int z, int salt) {
        return unit(hash(seed, x, y, z, salt));
    }
}
