package dev.denfry.promptcritical.sim.core;

/**
 * Позиция блока без ссылок на Minecraft (04d §4d.2.2). Упаковка в {@code long} совпадает с раскладкой
 * {@code BlockPos.asLong()} (x и z — по 26 бит, y — 12 бит), чтобы биндинг переводил её без пересчёта.
 */
public record GridPos(int x, int y, int z) {
    private static final int XZ_BITS = 26;
    private static final int Y_BITS = 12;
    private static final long XZ_MASK = (1L << XZ_BITS) - 1;
    private static final long Y_MASK = (1L << Y_BITS) - 1;
    private static final int X_SHIFT = Y_BITS + XZ_BITS;
    private static final int Z_SHIFT = Y_BITS;

    public long asLong() {
        return pack(x, y, z);
    }

    public static long pack(int x, int y, int z) {
        return ((x & XZ_MASK) << X_SHIFT) | ((z & XZ_MASK) << Z_SHIFT) | (y & Y_MASK);
    }

    public static GridPos fromLong(long packed) {
        return new GridPos(unpackX(packed), unpackY(packed), unpackZ(packed));
    }

    public static int unpackX(long packed) {
        return (int) (packed << (64 - X_SHIFT - XZ_BITS) >> (64 - XZ_BITS));
    }

    public static int unpackY(long packed) {
        return (int) (packed << (64 - Y_BITS) >> (64 - Y_BITS));
    }

    public static int unpackZ(long packed) {
        return (int) (packed << (64 - Z_SHIFT - XZ_BITS) >> (64 - XZ_BITS));
    }

    /** Чанк 16×16 по x/z, упакованный как {@code ChunkPos.toLong()}. */
    public static long chunkKey(long packed) {
        return chunkKey(unpackX(packed) >> 4, unpackZ(packed) >> 4);
    }

    public static long chunkKey(int chunkX, int chunkZ) {
        return (chunkX & 0xFFFF_FFFFL) | ((long) chunkZ << 32);
    }

    public GridPos offset(int dx, int dy, int dz) {
        return new GridPos(x + dx, y + dy, z + dz);
    }

    public double distanceSq(GridPos o) {
        double dx = x - o.x, dy = y - o.y, dz = z - o.z;
        return dx * dx + dy * dy + dz * dz;
    }
}
