package dev.denfry.promptcritical.mod.drilling;

import dev.denfry.promptcritical.sim.drilling.PetroleumField;

/**
 * {@link PetroleumField} мира: одно на сид. Поле неизменяемо, поэтому общее для генератора чанков (потоки
 * воркеров), сейсморазведки и буровой.
 */
public final class PetroleumFields {
    private record Cached(long seed, PetroleumField field) {
    }

    private static volatile Cached cached;

    private PetroleumFields() {
    }

    public static PetroleumField get(long seed) {
        Cached c = cached;
        if (c != null && c.seed == seed) return c.field;
        PetroleumField f = new PetroleumField(seed);
        cached = new Cached(seed, f);
        return f;
    }
}
