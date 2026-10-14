package dev.denfry.promptcritical.sim.core;

import java.util.SplittableRandom;

/** Зёрна случайности (05 §5.2.4): {@code SplittableRandom} с зерном {@code mix(worldSeed, simId, streamId)}. */
public final class SimRandom {
    private SimRandom() {
    }

    public static long mix(long worldSeed, long simId, int streamId) {
        long h = worldSeed;
        h = mix64(h ^ (simId * 0x9E3779B97F4A7C15L));
        h = mix64(h ^ (streamId * 0xC2B2AE3D27D4EB4FL));
        return h;
    }

    public static SplittableRandom of(long worldSeed, SimId id, int streamId) {
        return new SplittableRandom(mix(worldSeed, id.raw(), streamId));
    }

    /** Финализатор SplitMix64. */
    private static long mix64(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
